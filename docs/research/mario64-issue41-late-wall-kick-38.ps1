[CmdletBinding()]
param([Parameter(Mandatory=$true)][int]$ExpectedPid,[Parameter(Mandatory=$true)][long]$ExpectedHwnd,[Parameter(Mandatory=$true)][string]$Project64Path,[Parameter(Mandatory=$true)][string]$TraceDirectory)
$ErrorActionPreference='Stop'
$portable=[IO.Path]::GetFullPath((Split-Path -Parent $Project64Path))
$helper=Join-Path $PSScriptRoot 'issue39-project64-input.ps1'
$TraceDirectory=[IO.Path]::GetFullPath($TraceDirectory)
[void][IO.Directory]::CreateDirectory($TraceDirectory)
$progress=Join-Path $TraceDirectory 'issue41-late-wall-kick-38.progress.jsonl'
$log=Join-Path $TraceDirectory 'issue41-late-wall-kick-38-events.jsonl'
if(Test-Path -LiteralPath $log){throw "Refusing to overwrite existing input event log: $log"}
$exe=[IO.Path]::GetFullPath($Project64Path)
$trial='issue41-late-wall-kick-38'
$expectedExeHash='C257C662D4E0B2CE1732B7F4A7D5DD4682F604CB082BBDBFCE5181470D6DBFB8'
$expectedHelper='C82B9AEAE0651C2EA964D22D310D5CCBA550CE517F0036DAA987AFB1D839D169'
if((Get-FileHash -Algorithm SHA256 -LiteralPath $helper).Hash -ne $expectedHelper){throw 'Guarded helper hash mismatch.'}
if((Get-FileHash -Algorithm SHA256 -LiteralPath $exe).Hash -ne $expectedExeHash){throw 'Project64 binary hash mismatch.'}
$process=Get-Process -Id $ExpectedPid -ErrorAction Stop
if([IO.Path]::GetFullPath($process.Path) -ne [IO.Path]::GetFullPath($exe) -or [long]$process.MainWindowHandle -ne $ExpectedHwnd){throw 'Unexpected emulator process/window.'}
if(-not ('Project64ScopedInputNative' -as [type])){
  Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class Project64ScopedInputNative {
  [StructLayout(LayoutKind.Sequential)] public struct KEYBDINPUT { public ushort wVk; public ushort wScan; public uint dwFlags; public uint time; public UIntPtr dwExtraInfo; }
  [StructLayout(LayoutKind.Explicit, Size=40)] public struct INPUT64 { [FieldOffset(0)] public uint type; [FieldOffset(8)] public KEYBDINPUT ki; }
  [DllImport("user32.dll",SetLastError=true)] public static extern uint SendInput(uint nInputs,INPUT64[] pInputs,int cbSize);
  [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hWnd,out uint processId);
  [DllImport("user32.dll",CharSet=CharSet.Unicode,SetLastError=true)] public static extern int GetWindowTextW(IntPtr hWnd,System.Text.StringBuilder text,int maxCount);
  [DllImport("user32.dll")] public static extern bool IsWindow(IntPtr hWnd);
  public static bool SendScan(ushort scan,bool extended,bool keyUp) { const uint KEYBOARD=1,EXT=1,UP=2,SCANCODE=8; uint flags=SCANCODE;if(extended)flags|=EXT;if(keyUp)flags|=UP;var input=new INPUT64{type=KEYBOARD,ki=new KEYBDINPUT{wVk=0,wScan=scan,dwFlags=flags,time=0,dwExtraInfo=UIntPtr.Zero}};return SendInput(1,new INPUT64[]{input},Marshal.SizeOf(typeof(INPUT64)))==1; }
}
'@
}
$hwnd=[IntPtr]$ExpectedHwnd
$titleBuffer=[Text.StringBuilder]::new(512);[uint32]$windowPid=0;[void][Project64ScopedInputNative]::GetWindowThreadProcessId($hwnd,[ref]$windowPid);[void][Project64ScopedInputNative]::GetWindowTextW($hwnd,$titleBuffer,$titleBuffer.Capacity)
if($windowPid -ne $ExpectedPid -or -not [Project64ScopedInputNative]::IsWindow($hwnd) -or $titleBuffer.ToString() -notmatch '^Super Mario 64 \(U\) - Project64 3\.0\.1\.5664-'){throw 'Project64 HWND ownership/title validation failed.'}
$scan=@{A=@{Code=0x2D;Extended=$false};AnalogUp=@{Code=0x48;Extended=$true};AnalogDown=@{Code=0x50;Extended=$true};AnalogLeft=@{Code=0x4B;Extended=$true};AnalogRight=@{Code=0x4D;Extended=$true}}
$allowed=@('A','AnalogUp','AnalogDown','AnalogLeft','AnalogRight')
$pointerPattern='^0x80[0-3][0-9a-fA-F]{5}$'
$activeStopwatch=[Diagnostics.Stopwatch]::new()
function Assert-Foreground {
  if($activeStopwatch.IsRunning -and $activeStopwatch.ElapsedMilliseconds -ge 3000){throw 'Total held-input deadline reached (3000ms).'}
  $fg=[Project64ScopedInputNative]::GetForegroundWindow(); [uint32]$fp=0
  [void][Project64ScopedInputNative]::GetWindowThreadProcessId($fg,[ref]$fp)
  if($fg -ne $hwnd -or $fp -ne $ExpectedPid){throw "Foreground changed (HWND=$($fg.ToInt64()), PID=$fp)."}
  $p=Get-Process -Id $ExpectedPid -ErrorAction Stop
  if([IO.Path]::GetFullPath($p.Path) -ne [IO.Path]::GetFullPath($exe) -or [long]$p.MainWindowHandle -ne $ExpectedHwnd){throw 'Verified Project64 process/window changed.'}
}
function Read-Fresh([int]$seq=-1,[long]$notBefore=0){
  $sw=[Diagnostics.Stopwatch]::StartNew()
  do {
    Assert-Foreground
    if(Test-Path -LiteralPath $progress){
      try{$lines=[IO.File]::ReadAllLines($progress)}catch{Start-Sleep -Milliseconds 10;continue}
      if($lines.Count){
        try{$r=$lines[-1]|ConvertFrom-Json;$age=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()-[int64]$r.hostUnixMs}catch{Start-Sleep -Milliseconds 10;continue}
        if($r.trialId -ne $trial){throw 'Unexpected telemetry trial ID.'}
        if($age -lt -100 -or $age -gt 500){throw "Stale telemetry ($age ms)."}
        $valid=($r.counterPairEqual -eq $true -and [int]$r.sequence -gt $seq -and [int64]$r.hostReadStartUnixMs -ge $notBefore)
        foreach($f in @('areaPointer','cameraPointer','floorPointer')){if([string]$r.$f -notmatch $pointerPattern){$valid=$false}}
        foreach($f in @('x','y','z','cameraYaw','forwardSpeed','floorNormalY','floorType')){if($null -eq $r.PSObject.Properties[$f] -or $null -eq $r.$f -or -not [double]::IsFinite([double]$r.$f)){$valid=$false}}
        if($valid){return $r}
      }
    }
    Start-Sleep -Milliseconds 10
  } while($sw.ElapsedMilliseconds -lt 250)
  throw "No fresh telemetry after sequence $seq."
}
function Assert-State($r,[bool]$air=$false){
  if($r.profile.plugin -ne 'NRage' -or $r.profile.stickRange -ne 100 -or -not $r.profile.realN64Range){throw 'Unexpected N-Rage profile.'}
  foreach($f in @('areaPointer','cameraPointer','floorPointer')){if([string]$r.$f -notmatch $pointerPattern){throw "Invalid $f."}}
  foreach($f in @('x','y','z','cameraYaw','forwardSpeed','floorNormalY','floorType')){if($null -eq $r.PSObject.Properties[$f] -or $null -eq $r.$f -or -not [double]::IsFinite([double]$r.$f)){throw "Invalid $f."}}
  if([int]$r.cameraYaw -lt -32768 -or [int]$r.cameraYaw -gt 32767){throw 'Invalid camera yaw.'}
  if($air){if($r.x -lt -650 -or $r.x -gt 300 -or $r.y -lt 750 -or $r.y -gt 1400 -or $r.z -lt -3050 -or $r.z -gt -2150){throw 'Outside bounded airborne wall approach.'}}
  else {if($r.x -lt -450 -or $r.x -gt 300 -or $r.y -lt 780 -or $r.y -gt 830 -or $r.z -lt -3000 -or $r.z -gt -2200 -or $r.floorNormalY -lt .99 -or $r.floorType -ne 0){throw 'Outside verified wide-platform ground bounds.'}}
}
function Wrap([double]$a){while($a -gt 32767){$a-=65536};while($a -lt -32768){$a+=65536};return $a}
$dirs=@(@{keys=@('AnalogUp');yaw=-32768},@{keys=@('AnalogRight');yaw=16384},@{keys=@('AnalogDown');yaw=0},@{keys=@('AnalogLeft');yaw=-16384},@{keys=@('AnalogUp','AnalogRight');yaw=24576},@{keys=@('AnalogUp','AnalogLeft');yaw=-24576},@{keys=@('AnalogDown','AnalogRight');yaw=8192},@{keys=@('AnalogDown','AnalogLeft');yaw=-8192})
function Choose-Dir($r,[double]$tx,[double]$tz){$bearing=[Math]::Atan2($tx-[double]$r.x,$tz-[double]$r.z)*65536/(2*[Math]::PI);$want=Wrap($bearing-[double]$r.cameraYaw);return $dirs|Sort-Object {[Math]::Abs((Wrap([double]$_.yaw-$want)))}|Select-Object -First 1}
$held=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
function Send-Key([string]$key,[bool]$up){if($key -notin $allowed){throw "Non-whitelisted key $key."};if(-not [Project64ScopedInputNative]::SendScan([UInt16]$scan[$key].Code,[bool]$scan[$key].Extended,$up)){throw "SendInput failed for $key; Win32 $([Runtime.InteropServices.Marshal]::GetLastWin32Error())."}}
function Set-Held([string[]]$desired){$want=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal);foreach($k in $desired){if($k -notin $allowed){throw "Non-whitelisted key $k."};[void]$want.Add($k)};foreach($k in @($held)){if(-not $want.Contains($k)){Assert-Foreground;Send-Key $k $true;[void]$held.Remove($k);Write-Event 'keyup' $k $script:r}};foreach($k in $want){if(-not $held.Contains($k)){Assert-Foreground;Send-Key $k $false;[void]$held.Add($k);Write-Event 'keydown' $k $script:r}}}
function Write-Event([string]$event,[string]$key,$state,[string]$utcOverride=''){$eventUtc=if($utcOverride){$utcOverride}else{[DateTime]::UtcNow.ToString('o')};$obj=[ordered]@{trialId=$trial;event=$event;key=$key;utc=$eventUtc;sampleSequence=$state.sequence;action=$state.action;forwardSpeed=$state.forwardSpeed;x=$state.x;y=$state.y;z=$state.z;cameraYaw=$state.cameraYaw;wallPointer=$state.wallPointer};$obj|ConvertTo-Json -Compress|Add-Content -LiteralPath $log -Encoding UTF8}
function Press-Late-A($state){
  Assert-Foreground
  Send-Key 'A' $false
  [void]$held.Add('A')
  $aDownUtc=[DateTime]::UtcNow.ToString('o')
  $released=[Collections.Generic.List[object]]::new()
  foreach($key in @($held)){if($key -ne 'A' -and $key -like 'Analog*'){$edgeUtc=[DateTime]::UtcNow.ToString('o');$ok=[Project64ScopedInputNative]::SendScan([UInt16]$scan[$key].Code,[bool]$scan[$key].Extended,$true);if($ok){[void]$held.Remove($key)};$released.Add([pscustomobject]@{key=$key;utc=$edgeUtc;released=$ok})}}
  Write-Event 'late-A-trigger' '' $state
  Write-Event 'keydown' 'A' $state $aDownUtc
  foreach($edge in $released){Write-Event 'keyup' $edge.key $state $edge.utc;if(-not $edge.released){throw "Analog key-up failed for $($edge.key); it remains held for finally cleanup."}}
}
function Wait-State($before,[int]$ms,[bool]$air=$false){$sw=[Diagnostics.Stopwatch]::StartNew();while($sw.ElapsedMilliseconds -lt $ms){Assert-Foreground;Start-Sleep -Milliseconds 5};$notBefore=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();$script:r=Read-Fresh ([int]$before.sequence) $notBefore;Assert-State $script:r $air;return $script:r}
$null=[IO.Directory]::CreateDirectory((Split-Path -Parent $log))
$r=Read-Fresh;Assert-State $r
if($r.x -lt 150 -or $r.x -gt 190 -or $r.y -lt 780 -or $r.y -gt 830 -or $r.z -lt -2720 -or $r.z -gt -2620 -or $r.action -notin @('0x0c400201','0x0c000203','0x0c400202') -or [Math]::Abs($r.forwardSpeed) -gt 1){throw 'Expected verified F7 wide-platform runway near (169.7,803,-2679.4).'}
"started=$($r|ConvertTo-Json -Compress)";Write-Event 'start' '' $r
try {
  Assert-Foreground
  Set-Held @('AnalogUp');$r=Wait-State $r 30;Set-Held @()
  Start-Sleep -Seconds 2;$r=Read-Fresh ([int]$r.sequence);Assert-State $r
  $activeStopwatch.Start();Assert-Foreground
  $ready=$false;$timer=[Diagnostics.Stopwatch]::StartNew()
  while($timer.ElapsedMilliseconds -lt 2200){
    $dir=Choose-Dir $r ([double]$r.x-50) -2679;Set-Held @($dir.keys);$before=$r;$r=Wait-State $before 40
    "runup=$($r|ConvertTo-Json -Compress)";Write-Event 'runup-state' '' $r
    if($r.forwardSpeed -gt 20 -and $r.x -ge -400 -and $r.x -le -280){$ready=$true;break}
  }
  if(-not $ready){throw 'No safe runup reached fSpeed>20 in the verified wide-platform launch window; no A press sent.'}
  $dir=Choose-Dir $r -600 -2679;$jumpKeys=@('A')+@($dir.keys);Set-Held $jumpKeys;$r=Wait-State $r 80 $true
  "A-edge=$($r|ConvertTo-Json -Compress)";Write-Event 'A-edge-state' '' $r
  Set-Held @($dir.keys);if($r.action -ne '0x03000880'){throw 'A did not select ordinary jump; air steering stopped.'}
  $lateAttempted=$false;$sw=[Diagnostics.Stopwatch]::StartNew()
  while($sw.ElapsedMilliseconds -lt 600){
    if(-not $lateAttempted -and $r.action -eq '0x010208B6' -and $r.previousAction -eq '0x000008A7' -and [int]$r.wallKickTimerCandidate -ge 2){
      $lateAttempted=$true;Press-Late-A $r;$before=$r;$r=Wait-State $before 80 $true;Write-Event 'late-A-edge-state' '' $r;"late-A-edge=$($r|ConvertTo-Json -Compress)";Set-Held @()
      if($r.action -eq '0x03000886'){break}
    }
    $dir=Choose-Dir $r -600 -2679;Set-Held @($dir.keys);$before=$r;$r=Wait-State $before 10 $true;"air=$($r|ConvertTo-Json -Compress)";Write-Event 'air-state' '' $r
    if($r.action -eq '0x03000886'){break}
  }
  if(-not $lateAttempted){Write-Event 'late-A-not-triggered' '' $r}
} finally {
  $releaseResults=[Collections.Generic.List[object]]::new()
  foreach($key in @($held)){
    $attempts=0;$released=$false;$errors=[Collections.Generic.List[string]]::new()
    while($attempts -lt 2 -and -not $released){$attempts++;try{$released=[Project64ScopedInputNative]::SendScan([UInt16]$scan[$key].Code,[bool]$scan[$key].Extended,$true);if(-not $released){$errors.Add("attempt $attempts returned false; Win32 $([Runtime.InteropServices.Marshal]::GetLastWin32Error())")}}catch{$errors.Add("attempt $attempts threw: $($_.Exception.Message)")}}
    $releaseResults.Add([pscustomobject]@{key=$key;released=$released;attempts=$attempts;error=($errors -join '; ')})
    [void]$held.Remove($key)
  }
  foreach($result in $releaseResults){try{$obj=[ordered]@{trialId=$trial;event='finally-keyup';key=$result.key;utc=[DateTime]::UtcNow.ToString('o');released=$result.released;releaseAttempts=$result.attempts;releaseError=$result.error;sampleSequence=$r.sequence;action=$r.action;forwardSpeed=$r.forwardSpeed;x=$r.x;y=$r.y;z=$r.z;cameraYaw=$r.cameraYaw;wallPointer=$r.wallPointer};$obj|ConvertTo-Json -Compress|Add-Content -LiteralPath $log -Encoding UTF8}catch{Write-Warning "Could not append key-up result for $($result.key): $($_.Exception.Message)"}}
  $failed=@($releaseResults|Where-Object{-not $_.released});if($failed.Count){throw "Failed to release all held keys after retries: $($failed.key -join ', ')."}
}
"finished=$($r|ConvertTo-Json -Compress)"





