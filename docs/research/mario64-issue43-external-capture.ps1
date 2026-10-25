param(
  [Parameter(Mandatory=$true)][ValidatePattern('^issue43-[a-z0-9-]+$')][string]$TrialId,
  [Parameter(Mandatory=$true)][int]$TargetPid,
  [Parameter(Mandatory=$true)][long]$TargetHwnd,
  [Parameter(Mandatory=$true)][string]$HostBaseHex,
  [Parameter(Mandatory=$true)][string]$ProjectRoot,
  [Parameter(Mandatory=$true)][string]$AnalysisRomPath,
  [ValidateSet(20,40,100)][int]$StickRange=100,
  [int]$Limit=7200,
  [int]$PollMs=5
)
$ErrorActionPreference='Stop'
$trial=$TrialId
$root=[IO.Path]::GetFullPath($ProjectRoot)
$portable=Join-Path $root 'tools\issue11-local\pj64-portable'
$exe=Join-Path $portable 'Project64.exe'
$expectedExeHash='C257C662D4E0B2CE1732B7F4A7D5DD4682F604CB082BBDBFCE5181470D6DBFB8'
$expectedRomHash='17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91'
$rom=[IO.Path]::GetFullPath($AnalysisRomPath)
$ini=Join-Path $portable 'Config\NRage.ini'
$out=Join-Path $portable "$trial.jsonl"
$progress=Join-Path $portable "$trial.progress.jsonl"
$metadata=Join-Path $portable "$trial.metadata.json"
$stop=Join-Path $portable "$trial.stop"
if($Limit -lt 1 -or $Limit -gt 7200 -or $PollMs -lt 5 -or $PollMs -gt 100){throw 'Capture bounds invalid.'}
if((Get-FileHash -Algorithm SHA256 -LiteralPath $exe).Hash -ne $expectedExeHash){throw 'Project64 executable hash mismatch.'}
if((Get-FileHash -Algorithm SHA256 -LiteralPath $rom).Hash -ne $expectedRomHash){throw 'Analysis-copy ROM hash mismatch.'}
$iniText=Get-Content -Raw -LiteralPath $ini
$controller1=[regex]::Match($iniText,'(?ms)^\[Controller 1\]\s*(.*?)(?=^\[|\z)').Groups[1].Value
foreach($check in @(@{key='Plugged';value='1'},@{key='RealN64Range';value='1'},@{key='StickRange';value=[string]$StickRange},@{key='PadDeadZone';value='5'})){if($controller1 -notmatch ('(?m)^'+$check.key+'='+[regex]::Escape($check.value)+'\s*$')){throw "NRage.ini Controller 1 $($check.key) does not match expected $($check.value)."}}
$proc=Get-Process -Id $TargetPid -ErrorAction Stop
if([IO.Path]::GetFullPath($proc.Path) -ne [IO.Path]::GetFullPath($exe) -or [long]$proc.MainWindowHandle -ne $TargetHwnd){throw 'Unexpected Project64 PID/HWND.'}
$pStart=$proc.StartTime.ToUniversalTime().ToString('o')
$base=[Convert]::ToInt64(($HostBaseHex -replace '^0x',''),16)
if($base -le 0){throw 'Invalid process-specific RDRAM base.'}
if(-not ('MarioCraftExternalReadOnlyMemory' -as [type])){
  Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class MarioCraftExternalReadOnlyMemory {
 [DllImport("kernel32.dll",SetLastError=true)] public static extern IntPtr OpenProcess(uint access,bool inherit,int pid);
 [DllImport("kernel32.dll",SetLastError=true)] public static extern bool CloseHandle(IntPtr h);
 [DllImport("kernel32.dll",SetLastError=true)] public static extern bool ReadProcessMemory(IntPtr h,IntPtr address,byte[] buffer,IntPtr size,out IntPtr read);
 [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hwnd,out uint pid);
 public static byte[] Read(IntPtr h,long a,int n){byte[] b=new byte[n];IntPtr got;if(!ReadProcessMemory(h,new IntPtr(a),b,new IntPtr(n),out got)||got.ToInt64()!=n)throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error(),"ReadProcessMemory failed/partial");return b;}
}
'@
}
$handle=[MarioCraftExternalReadOnlyMemory]::OpenProcess(0x0410,$false,$TargetPid)
if($handle -eq [IntPtr]::Zero){throw 'OpenProcess(PROCESS_QUERY_INFORMATION|PROCESS_VM_READ) failed.'}
function Assert-Target {
  $p=Get-Process -Id $TargetPid -ErrorAction Stop
  if([IO.Path]::GetFullPath($p.Path) -ne [IO.Path]::GetFullPath($exe) -or [long]$p.MainWindowHandle -ne $TargetHwnd -or $p.StartTime.ToUniversalTime().ToString('o') -ne $pStart){throw 'Project64 process identity changed.'}
  $fg=[MarioCraftExternalReadOnlyMemory]::GetForegroundWindow();[uint32]$fp=0;[void][MarioCraftExternalReadOnlyMemory]::GetWindowThreadProcessId($fg,[ref]$fp)
  if($fg.ToInt64() -ne $TargetHwnd -or $fp -ne $TargetPid){throw "Project64 lost foreground (HWND=$($fg.ToInt64()), PID=$fp)."}
}
function Read-Guest([long]$address,[int]$length){$guest=[uint32]($address -band 4294967295L);if([long]$guest -lt 0x80000000L -or ([long]$guest+$length) -gt 0x80400000L){throw ('Guest address outside RDRAM: 0x{0:X8}+{1}' -f $guest,$length)};return [MarioCraftExternalReadOnlyMemory]::Read($handle,($base+([long]$guest-0x80000000L)), $length)}
function U32([byte[]]$b,[int]$o){return [BitConverter]::ToUInt32($b,$o)}
function F32([byte[]]$b,[int]$o){return [BitConverter]::ToSingle($b,$o)}
function U16Guest([byte[]]$b,[int]$guestOffset){return [BitConverter]::ToUInt16($b,($guestOffset -bxor 2))}
function S16Guest([byte[]]$b,[int]$guestOffset){return [BitConverter]::ToInt16($b,($guestOffset -bxor 2))}
function U8Guest([byte[]]$b,[int]$guestOffset){return [int]$b[$guestOffset -bxor 3]}
function U16FromAlignedWord([byte[]]$b,[int]$guestOffset){$w=U32 $b ($guestOffset -band 0xFFFFFFFC);if(($guestOffset -band 2)-eq 0){return [uint16](($w -shr 16)-band 0xFFFF)}else{return [uint16]($w -band 0xFFFF)}}
function U8FromAlignedWord([byte[]]$b,[int]$guestOffset){$w=U32 $b ($guestOffset -band 0xFFFFFFFC);$shift=24-(($guestOffset -band 3)*8);return [int](($w -shr $shift)-band 0xFF)}
function H32([uint32]$v){return ('0x{0:X8}' -f $v)}
function Read-Row([int]$seq){
  Assert-Target
  $readStarted=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  $before=U32 (Read-Guest 0x8032D5D4 4) 0
  $m=Read-Guest 0x8033B170 0xC4
  $c=U32 $m 0x9C
  if([long]$c -lt 0x80000000L -or [long]$c -ge 0x80400000L){throw ('Invalid controller pointer '+(H32 $c))}
  $cb=Read-Guest $c 0x18
  foreach($off in @(0x10,0x12)){if((U16Guest $cb $off) -ne (U16FromAlignedWord $cb $off)){throw "u16 XOR2 decode disagrees with aligned native-word decode at controller+$('{0:X}' -f $off)."}}
  foreach($off in @(0x2A,0x2B,0xB4)){if((U8Guest $m $off) -ne (U8FromAlignedWord $m $off)){throw "u8 XOR3 decode disagrees with aligned native-word decode at Mario+$('{0:X}' -f $off)."}}
  foreach($off in @(0x18,0x1A)){if((U16Guest $m $off) -ne (U16FromAlignedWord $m $off)){throw "u16 XOR2 decode disagrees with aligned native-word decode at Mario+$('{0:X}' -f $off)."}}
  $floor=U32 $m 0x68
  $area=U32 $m 0x90
  $row=[ordered]@{
    trialId=$trial;sequence=$seq;hostUnixMs=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();hostReadStartUnixMs=$readStarted
    controllerPointer=(H32 $c);profile=@{plugin='NRage';realN64Range=$true;stickRange=$StickRange;padDeadZone=5}
    loopCounterBefore=$before
    rawX=(S16Guest $cb 0);rawY=(S16Guest $cb 2);processedX=(F32 $cb 4);processedY=(F32 $cb 8);inputMagnitude=(F32 $cb 0x0C)
    buttonsDown=(U16Guest $cb 0x10);buttonsPressed=(U16Guest $cb 0x12)
    previousAction=(H32 (U32 $m 0x10));derivedInput=(U16Guest $m 0x02);action=(H32 (U32 $m 0x0C));actionState=(U16Guest $m 0x18);actionArgument=(U32 $m 0x1C);actionTimer=(U16Guest $m 0x1A)
    wallKickTimerCandidate=(U8Guest $m 0x2A);doubleJumpTimerCandidate=(U8Guest $m 0x2B);squishTimerCandidate=(U8Guest $m 0xB4);flagsCandidate=(H32 (U32 $m 0x04))
    intendedMagnitude=(F32 $m 0x20);quicksandDepth=(F32 $m 0xC0);slopeDecelThreshold=(F32 (Read-Guest 0x80336AD0 4) 0);intendedYaw=(S16Guest $m 0x24);faceYaw=(S16Guest $m 0x2E)
    x=(F32 $m 0x3C);y=(F32 $m 0x40);z=(F32 $m 0x44)
    velocityX=(F32 $m 0x48);velocityY=(F32 $m 0x4C);velocityZ=(F32 $m 0x50);forwardSpeed=(F32 $m 0x54)
    wallPointer=(H32 (U32 $m 0x60));ceilingPointer=(H32 (U32 $m 0x64));floorPointer=(H32 $floor);areaPointer=(H32 $area)
    floorHeightCandidate=(F32 $m 0x70);ceilingHeightCandidate=(F32 $m 0x6C)
  }
  if([long]$floor -ge 0x80000000L -and [long]$floor -lt 0x80400000L){$fb=Read-Guest $floor 0x24;$row.floorType=(S16Guest $fb 0);$row.floorNormalY=(F32 $fb 0x20)}
  $row.waterLevelS16=(S16Guest $m 0x76)
  $ceiling=U32 $m 0x64
  if([long]$ceiling -ge 0x80000000L -and [long]$ceiling -lt 0x80400000L){$cbuf=Read-Guest $ceiling 0x30;$row.ceilingType=(S16Guest $cbuf 0);$row.ceilingNormalX=(F32 $cbuf 0x1C);$row.ceilingNormalY=(F32 $cbuf 0x20);$row.ceilingNormalZ=(F32 $cbuf 0x24);$row.ceilingOriginOffsetCandidate=(F32 $cbuf 0x28)}
  $marioObject=U32 $m 0x88
  $row.marioObjectPointer=(H32 $marioObject)
  if([long]$marioObject -ge 0x80000000L -and [long]$marioObject -lt 0x80400000L){$ob=Read-Guest $marioObject 0x220;$row.objectHitboxRadiusCandidate=(F32 $ob 0x1F8);$row.objectHitboxHeightCandidate=(F32 $ob 0x1FC);$row.objectHurtboxRadiusCandidate=(F32 $ob 0x200);$row.objectHurtboxHeightCandidate=(F32 $ob 0x204);$row.objectHitboxDownOffsetCandidate=(F32 $ob 0x208);$row.objectCollisionDataPointer=(H32 (U32 $ob 0x218))}
  $wall=U32 $m 0x60
  if([long]$wall -ge 0x80000000L -and [long]$wall -lt 0x80400000L){$wb=Read-Guest $wall 0x28;$row.wallType=(S16Guest $wb 0);$row.wallNormalX=(F32 $wb 0x1C);$row.wallNormalY=(F32 $wb 0x20);$row.wallNormalZ=(F32 $wb 0x24)}
  if([long]$area -ge 0x80000000L -and [long]$area -lt 0x80400000L){$ab=Read-Guest $area 0x28;$camera=U32 $ab 0x24;$row.cameraPointer=(H32 $camera);if([long]$camera -ge 0x80000000L -and [long]$camera -lt 0x80400000L){$cam=Read-Guest $camera 4;$row.cameraYaw=(S16Guest $cam 2)}}
  $after=U32 (Read-Guest 0x8032D5D4 4) 0
  $row.loopCounterAfter=$after;$row.counterPairEqual=($before -eq $after);$row.hostReadEndUnixMs=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();$row.hostReadDurationMs=$row.hostReadEndUnixMs-$readStarted
  return [pscustomobject]$row
}
$meta=[ordered]@{trialId=$trial;addedFields='ceiling/floor heights, ceiling surface, MarioState+0x76 signed waterLevelS16 via guest-offset XOR2, mario object hitbox/hurtbox fields (candidate until exact-ROM corroboration)';startedUtc=[DateTime]::UtcNow.ToString('o');processId=$TargetPid;processStartUtc=$pStart;windowHandle=('0x{0:X}' -f $TargetHwnd);executable=$exe;executableSha256=$expectedExeHash;analysisRomSha256=$expectedRomHash;nrageIniSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $ini).Hash;stickRange=$StickRange;padDeadZone=5;realN64Range=$true;hostBase=('0x{0:X}' -f $base);mapping='ROM file offset 0x1000 prefix maps to RDRAM guest 0x80246000, hostBase=match-0x246000; mode 1 native u32/f32, u16/s16 guest offset XOR 2, u8 guest offset XOR 3; byte-width decodes checked against aligned-word extraction';access='OpenProcess(PROCESS_QUERY_INFORMATION|PROCESS_VM_READ), ReadProcessMemory only';counterSemantics='loop counter read before Mario/controller/surface fields and after all fields; equality is not an atomicity guarantee';limit=$Limit;pollMs=$PollMs}
foreach($existing in @($out,$progress,$metadata,$stop,($progress+'.status.json'))){if(Test-Path -LiteralPath $existing){throw "Refusing to overwrite existing capture artifact: $existing"}}
$meta|ConvertTo-Json -Compress|Set-Content -LiteralPath $metadata -Encoding UTF8
$rows=[Collections.Generic.List[object]]::new();$seq=0;$nextProgress=0
try {
  $start=Read-Row $seq;$rows.Add($start);$seq++
  $first=[ordered]@{trialId=$trial;event='started';metadata=$meta;firstRow=$start}
  ($first|ConvertTo-Json -Depth 8 -Compress)|Set-Content -LiteralPath $progress -Encoding UTF8
  while($rows.Count -lt $Limit -and -not (Test-Path -LiteralPath $stop)){
    Start-Sleep -Milliseconds $PollMs
    $r=Read-Row $seq;$rows.Add($r);$seq++
    if(($rows.Count % 1) -eq 0){$payload=($rows|Select-Object -Last 4|ForEach-Object {$_|ConvertTo-Json -Depth 8 -Compress}) -join "`n";[IO.File]::WriteAllText($progress,$payload+"`n",[Text.UTF8Encoding]::new($false))}
  }
  $payload=($rows|ForEach-Object {$_|ConvertTo-Json -Depth 8 -Compress}) -join "`n"
  [IO.File]::WriteAllText($out,$payload+"`n",[Text.UTF8Encoding]::new($false))
  [pscustomobject]@{trialId=$trial;status='complete';count=$rows.Count;first=$rows[0];last=$rows[-1];trace=$out;progress=$progress;metadata=$metadata}|ConvertTo-Json -Depth 8 -Compress
} catch {
  $originalError=$_.Exception.Message
  try{$partial=($rows|ForEach-Object {$_|ConvertTo-Json -Depth 8 -Compress}) -join "`n";if($rows.Count){[IO.File]::WriteAllText($out,$partial+"`n",[Text.UTF8Encoding]::new($false))};$state=[ordered]@{trialId=$trial;status='error';count=$rows.Count;error=$originalError;trace=$out;metadata=$metadata;endedUtc=[DateTime]::UtcNow.ToString('o')};$state|ConvertTo-Json -Depth 8 -Compress|Set-Content -LiteralPath ($progress+'.status.json') -Encoding UTF8}catch{Write-Warning "Could not preserve partial trace/status: $($_.Exception.Message)"}
  throw
} finally {if($handle -ne [IntPtr]::Zero){[void][MarioCraftExternalReadOnlyMemory]::CloseHandle($handle)}}










