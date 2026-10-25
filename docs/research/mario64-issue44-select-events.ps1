param(
  [Parameter(Mandatory=$true)][string]$ProjectRoot,
  [string]$OutputPath
)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($ProjectRoot)
$captures=Join-Path $root 'tools\issue11-local\pj64-portable'
if(-not $OutputPath){$OutputPath=Join-Path $root 'docs\research\MARIO64-ISSUE44-HEALTH-EVENTS.jsonl'}
$out=[IO.Path]::GetFullPath($OutputPath)
$trials=@('issue44-lava-route-10','issue44-fall-route-06','issue44-water-pitch-03','issue44-water-drowning-04')
$writer=[IO.StreamWriter]::new($out,$false,[Text.UTF8Encoding]::new($false))
try {
  foreach($trial in $trials){
    $path=Join-Path $captures ($trial+'.jsonl')
    if(-not (Test-Path -LiteralPath $path)){throw "Missing local capture: $path"}
    $previous=$null
    foreach($line in [IO.File]::ReadLines($path)){
      $r=$line|ConvertFrom-Json
      if($r.trialId -ne $trial){throw "Trial id mismatch in $path"}
      $health=[int]$r.candidateHealthU16AtAE
      $delta=0
      if($null -ne $previous){$delta=$health-[int]$previous.candidateHealthU16AtAE}
      $actionChanged=($null -ne $previous -and $r.action -ne $previous.action)
      $b2Changed=($null -ne $previous -and $r.candidateByteB2 -ne $previous.candidateByteB2)
      $b3Changed=($null -ne $previous -and $r.candidateByteB3 -ne $previous.candidateByteB3)
      $deathWindow=($trial -eq 'issue44-water-drowning-04' -and (($r.sequence -ge 3200 -and $r.sequence -le 3600) -or $health -le 0x120))
      $keep=($delta -ne 0 -or $b2Changed -or $b3Changed -or ($actionChanged -and ($trial -ne 'issue44-water-drowning-04' -or $deathWindow -or $trial -eq 'issue44-fall-route-06')))
      if($keep){
        $kinds=[Collections.Generic.List[string]]::new()
        if($delta -ne 0){$kinds.Add('health')}
        if($b2Changed){$kinds.Add('hurtQueue')}
        if($b3Changed){$kinds.Add('healQueue')}
        if($actionChanged){$kinds.Add('action')}
        $row=[ordered]@{
          trialId=$trial; sequence=$r.sequence; hostUnixMs=$r.hostUnixMs
          events=@($kinds.ToArray()); loopCounterBefore=$r.loopCounterBefore; loopCounterAfter=$r.loopCounterAfter; counterPairEqual=$r.counterPairEqual
          previousAction=$r.previousAction; action=$r.action; actionState=$r.actionState; actionTimer=$r.actionTimer; flags=$r.flagsCandidate
          health=$health; healthHex=('0x{0:X}' -f $health); healthDelta=$delta; hurtTicks=$r.candidateByteB2; healTicks=$r.candidateByteB3
          invincibilityTimer=$r.candidateInvincibilityTimerS16At26; peakHeight=$r.candidatePeakHeightF32AtBC; facePitch=$r.candidateFacePitchS16At2C
          x=$r.x; y=$r.y; z=$r.z; velocityY=$r.velocityY; floorHeight=$r.floorHeightCandidate; floorType=$r.floorType; waterLevel=$r.waterLevelS16
        }
        $writer.WriteLine(($row|ConvertTo-Json -Compress -Depth 4))
      }
      $previous=$r
    }
  }
} finally {$writer.Dispose()}
Write-Output "Wrote sanitized selected events: $out"
