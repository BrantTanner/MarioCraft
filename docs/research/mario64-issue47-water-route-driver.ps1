[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][int]$TargetPid,
  [Parameter(Mandatory=$true)][long]$TargetHwnd,
  [Parameter(Mandatory=$true)][string]$HostBaseHex,
  [Parameter(Mandatory=$true)][string]$ProjectRoot,
  [Parameter(Mandatory=$true)][string]$AnalysisRomPath,
  [Parameter(Mandatory=$true)][ValidatePattern('^issue47-water-entry-[0-9]{2}$')][string]$TrialId
)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($ProjectRoot)
$portable=Join-Path $root 'tools\issue11-local\pj64-portable'
$sampler=Join-Path $root 'docs\research\mario64-issue47-external-capture-v2.ps1'
$inputHelper=Join-Path $root 'docs\research\mario64-issue47-project64-input.ps1'
$progress=Join-Path $portable "$TrialId.progress.jsonl"
$trace=Join-Path $portable "$TrialId.jsonl"
$metadata=Join-Path $portable "$TrialId.metadata.json"
$status=Join-Path $portable "$TrialId.progress.jsonl.status.json"
$stop=Join-Path $portable "$TrialId.stop"
$inputLog=Join-Path $root 'tools\issue11-local\issue40-project64-input-log.jsonl'
foreach($path in @($progress,$trace,$metadata,$status,$stop)){if(Test-Path -LiteralPath $path){throw "Refusing to overwrite existing route artifact: $path"}}

function Get-LatestValidRow {
  for($attempt=0;$attempt -lt 5;$attempt++){
    try {$lines=@(Get-Content -LiteralPath $progress -Tail 4 -ErrorAction Stop)} catch {Start-Sleep -Milliseconds 20;continue}
    [array]::Reverse($lines)
    foreach($line in $lines){
      try {$r=$line|ConvertFrom-Json -ErrorAction Stop} catch {continue}
      $nums=@($r.x,$r.y,$r.z,$r.areaCameraControlYaw,$r.waterLevelS16,$r.sequence,$r.hostReadStartUnixMs,$r.hostReadEndUnixMs)
      $valid=$true
      foreach($n in $nums){if($null -eq $n -or -not [double]::IsFinite([double]$n)){$valid=$false;break}}
      if($valid -and $r.trialId -eq $TrialId -and $r.counterPairEqual -eq $true -and $r.floorPointer -match '^0x80[0-3A-Fa-f]') {return $r}
    }
    Start-Sleep -Milliseconds 20
  }
  return $null
}
function Wait-FreshRow([long]$AfterSequence,[long]$AfterStartMs,[int]$TimeoutMs=3500,[long]$AfterCounter=-1){
  $watch=[Diagnostics.Stopwatch]::StartNew()
  while($watch.ElapsedMilliseconds -lt $TimeoutMs){
    $r=Get-LatestValidRow
    if($null -ne $r -and [long]$r.sequence -gt $AfterSequence -and [long]$r.hostReadStartUnixMs -ge $AfterStartMs -and ($AfterCounter -lt 0 -or [long]$r.loopCounterAfter -ne $AfterCounter)){return $r}
    Start-Sleep -Milliseconds 15
  }
  throw "No fresh stable sample after sequence $AfterSequence / hostStartMs $AfterStartMs."
}
function Get-NewestInputRecord([datetime]$AfterUtc){
  $records=@(Get-Content -LiteralPath $inputLog -Tail 10)
  [array]::Reverse($records)
  $afterMs=([DateTimeOffset]$AfterUtc).ToUnixTimeMilliseconds()
  foreach($line in $records){
    try {$r=$line|ConvertFrom-Json -ErrorAction Stop} catch {continue}
    if($r.targetInstance -eq 'Issue11Portable' -and $r.pid -eq $TargetPid -and [long]$r.hwnd -eq $TargetHwnd -and ([DateTimeOffset]::Parse($r.startedUtc.ToString('o')).ToUnixTimeMilliseconds() -ge $afterMs)){return $r}
  }
  throw 'Could not correlate input-helper completion with its appended event log.'
}
function Invoke-Pulse([string[]]$Keys,[int]$HoldMs,[long]$AfterSequence,[long]$AfterCounter){
  $beforeUtc=[datetime]::UtcNow
  $helperArgs=@{ExpectedPid=$TargetPid;ExpectedHwnd=$TargetHwnd;TargetInstance='Issue11Portable';Key=$Keys[0];HoldMs=$HoldMs}
  if($Keys.Count -gt 1){$helperArgs.AdditionalKeys=@($Keys | Select-Object -Skip 1)}
  $helperOutput=@(& $inputHelper @helperArgs)
  if($helperOutput.Count -lt 1){throw 'Input helper produced no completion record.'}
  try{$helperResult=[string]$helperOutput[-1]|ConvertFrom-Json -ErrorAction Stop}catch{throw 'Input helper completion output was not valid JSON.'}
  if($helperResult.outcome -ne 'completed' -or -not $helperResult.keyUpUtc){throw 'Input helper output did not confirm a completed key release.'}
  $record=Get-NewestInputRecord $beforeUtc
  if($record.outcome -ne 'completed' -or -not $record.keyUpUtc){throw 'Input helper did not complete and release its key.'}
  $downMs=[DateTimeOffset]::Parse($record.keyDownUtc.ToString('o')).ToUnixTimeMilliseconds()
  $upStamp=[DateTimeOffset]::Parse($record.keyUpUtc.ToString('o'));$upMs=$upStamp.ToUnixTimeMilliseconds();if(($upStamp.Ticks % [TimeSpan]::TicksPerMillisecond) -ne 0){$upMs++}
  $finishedStamp=[DateTimeOffset]::Parse($record.finishedUtc.ToString('o'));$finishedMs=$finishedStamp.ToUnixTimeMilliseconds();if(($finishedStamp.Ticks % [TimeSpan]::TicksPerMillisecond) -ne 0){$finishedMs++}
  if($downMs -gt $upMs -or $upMs -gt $finishedMs){throw 'Input log key edge chronology is invalid.'}
  $nowMs=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  if([math]::Abs($nowMs-$upMs) -gt 5000){throw 'Input key-up timestamp is not within five seconds of current UTC.'}
  return Wait-FreshRow $AfterSequence $upMs 3500 $AfterCounter
}
function Wrap-Angle([double]$angle){while($angle -gt 32767){$angle-=65536};while($angle -lt -32768){$angle+=65536};return $angle}
$directions=@(
  @{keys=@('AnalogUp');yaw=-32768},@{keys=@('AnalogUp','AnalogRight');yaw=24576},
  @{keys=@('AnalogRight');yaw=16384},@{keys=@('AnalogDown','AnalogRight');yaw=8192},
  @{keys=@('AnalogDown');yaw=0},@{keys=@('AnalogDown','AnalogLeft');yaw=-8192},
  @{keys=@('AnalogLeft');yaw=-16384},@{keys=@('AnalogUp','AnalogLeft');yaw=-24576}
)
$waypoints=@(@{x=-1328.0;z=3300.0},@{x=-185.0;z=3300.0},@{x=0.0;z=3300.0},@{x=800.0;z=3300.0},@{x=1600.0;z=3300.0},@{x=2200.0;z=3300.0},@{x=2450.0;z=3300.0})
$samplerProc=$null;$pulseCount=0;$blockedCount=0;$reverseProgressCount=0;$waypointIndex=0;$routeStatus='error'
try {
  $samplerArgs=@('-NoProfile','-ExecutionPolicy','Bypass','-File',"`"$sampler`"",'-TrialId',$TrialId,'-TargetPid',[string]$TargetPid,'-TargetHwnd',[string]$TargetHwnd,'-HostBaseHex',$HostBaseHex,'-ProjectRoot',"`"$root`"",'-AnalysisRomPath',"`"$AnalysisRomPath`"",'-Limit','7200','-PollMs','15')
  $samplerProc=Start-Process -FilePath 'pwsh.exe' -WindowStyle Hidden -PassThru -ArgumentList $samplerArgs
  $row=$null;$startWait=[Diagnostics.Stopwatch]::StartNew()
  while($null -eq $row -and $startWait.ElapsedMilliseconds -lt 10000){$row=Get-LatestValidRow;if($null -eq $row){Start-Sleep -Milliseconds 20}}
  if($null -eq $row){throw 'Sampler did not produce an initial stable sample within 10 seconds.'}
  if([string]$row.action -eq '0x0C000203'){$row=Invoke-Pulse @('A') 150 ([long]$row.sequence) ([long]$row.loopCounterAfter);$pulseCount++;Start-Sleep -Seconds 2;$settleStart=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();$row=Wait-FreshRow ([long]$row.sequence) $settleStart 3500 ([long]$row.loopCounterAfter)}
  $row=Invoke-Pulse @('AnalogUp') 150 ([long]$row.sequence) ([long]$row.loopCounterAfter);$pulseCount++
  Start-Sleep -Seconds 2
  $settleStart=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  $row=Wait-FreshRow ([long]$row.sequence) $settleStart 3500 ([long]$row.loopCounterAfter)
  for($i=0;$i -lt 36;$i++){
    if($row.x -lt -1500 -or $row.x -gt 2800 -or $row.z -lt 3000 -or $row.z -gt 4400 -or $row.y -lt -700 -or $row.y -gt 1200){throw ('Route left bounded observation area at x/y/z={0}/{1}/{2}.' -f $row.x,$row.y,$row.z)}
    if([double]$row.waterLevelS16 -gt -10000 -and [double]$row.y -lt ([double]$row.waterLevelS16-100)){$routeStatus='waterline-crossed';$observedSeq=[long]$row.sequence;Start-Sleep -Milliseconds 500;$observationStart=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();$row=Wait-FreshRow $observedSeq $observationStart 3500 ([long]$row.loopCounterAfter);break}
    while($waypointIndex -lt $waypoints.Count){
      $w=$waypoints[$waypointIndex]
      $distance=[math]::Sqrt([math]::Pow($w.x-[double]$row.x,2)+[math]::Pow($w.z-[double]$row.z,2))
      $waypointTolerance=if($waypointIndex -eq ($waypoints.Count-1)){35}else{150}
      if($distance -gt $waypointTolerance){break}
      $waypointIndex++
    }
    if($waypointIndex -ge $waypoints.Count){$routeStatus='last-waypoint-reached';$observedSeq=[long]$row.sequence;Start-Sleep -Milliseconds 500;$observationStart=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();$row=Wait-FreshRow $observedSeq $observationStart 3500 ([long]$row.loopCounterAfter);break}
    $target=$waypoints[$waypointIndex]
    $dx=[double]$target.x-[double]$row.x;$dz=[double]$target.z-[double]$row.z
    $world=[math]::Atan2($dx,$dz)*65536/(2*[math]::PI)
    $desired=Wrap-Angle ($world-[double]$row.areaCameraControlYaw)
    $direction=$directions|Sort-Object {[math]::Abs((Wrap-Angle ($desired-[double]$_.yaw)))}|Select-Object -First 1
    $oldX=[double]$row.x;$oldZ=[double]$row.z;$oldAction=[string]$row.action;$oldWaypointDistance=$distance
    $pulseHoldMs=if($waypointIndex -eq ($waypoints.Count-1) -and $distance -lt 250){150}else{300}
    $row=Invoke-Pulse $direction['keys'] $pulseHoldMs ([long]$row.sequence) ([long]$row.loopCounterAfter);$pulseCount++
    $newWaypointDistance=[math]::Sqrt([math]::Pow($target.x-[double]$row.x,2)+[math]::Pow($target.z-[double]$row.z,2))
    $moved=[math]::Sqrt([math]::Pow(([double]$row.x-$oldX),2)+[math]::Pow(([double]$row.z-$oldZ),2))
    if($moved -lt 10 -and [string]$row.action -eq $oldAction){$blockedCount++}else{$blockedCount=0}
    if($newWaypointDistance -gt ($oldWaypointDistance+35)){$reverseProgressCount++}else{$reverseProgressCount=0}
    if($reverseProgressCount -ge 2){$routeStatus='reverse-progress';throw ('Distance from active waypoint increased twice at x/y/z={0}/{1}/{2}; distance {3} -> {4}.' -f $row.x,$row.y,$row.z,$oldWaypointDistance,$newWaypointDistance)}
    if($blockedCount -ge 2){$routeStatus='stalled';throw ('Route stalled for two consecutive pulses at x/y/z={0}/{1}/{2}; action={3}.' -f $row.x,$row.y,$row.z,$row.action)}
  }
  if($routeStatus -eq 'error'){$routeStatus='pulse-budget-exhausted'}
  [pscustomobject]@{trialId=$TrialId;status=$routeStatus;pulses=$pulseCount;lastSequence=$row.sequence;lastXYZ=@($row.x,$row.y,$row.z);floorTypeDecimal=$row.floorType;waterLevelS16=$row.waterLevelS16;action=$row.action;waypointIndex=$waypointIndex}|ConvertTo-Json -Compress
} finally {
  if($null -ne $samplerProc){
    if(Test-Path -LiteralPath $progress){[IO.File]::WriteAllText($stop,'',[Text.UTF8Encoding]::new($false))}
    try{Wait-Process -Id $samplerProc.Id -Timeout 20 -ErrorAction Stop}catch{throw 'Sampler did not exit after its stop marker within 20 seconds.'}
    if(-not (Test-Path -LiteralPath $trace)){throw 'Sampler exited without flushing the numeric trace.'}
  }
}
