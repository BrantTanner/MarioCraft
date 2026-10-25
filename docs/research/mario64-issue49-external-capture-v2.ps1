param(
  [Parameter(Mandatory=$true)][ValidatePattern('^issue49-[a-z0-9-]+$')][string]$TrialId,
  [Parameter(Mandatory=$true)][int]$TargetPid,
  [Parameter(Mandatory=$true)][long]$TargetHwnd,
  [Parameter(Mandatory=$true)][string]$HostBaseHex,
  [Parameter(Mandatory=$true)][string]$ProjectRoot,
  [Parameter(Mandatory=$true)][string]$AnalysisRomPath,
  [ValidateSet(20,40,100)][int]$StickRange=100,
  [ValidateRange(1,7200)][int]$Limit=3600,
  [ValidateRange(5,100)][int]$PollMs=20
)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($ProjectRoot)
$portable=Join-Path $root 'tools\issue11-local\pj64-portable'
$exe=Join-Path $portable 'Project64.exe'
$expectedExeHash='C257C662D4E0B2CE1732B7F4A7D5DD4682F604CB082BBDBFCE5181470D6DBFB8'
$expectedRomHash='17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91'
$rom=[IO.Path]::GetFullPath($AnalysisRomPath)
$ini=Join-Path $portable 'Config\NRage.ini'
$out=Join-Path $portable "$TrialId.jsonl"
$progress=Join-Path $portable "$TrialId.progress.jsonl"
$metadata=Join-Path $portable "$TrialId.metadata.json"
$stop=Join-Path $portable "$TrialId.stop"
foreach($existing in @($out,$progress,$metadata,$stop,($progress+'.status.json'))){if(Test-Path -LiteralPath $existing){throw "Refusing to overwrite existing capture artifact: $existing"}}
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
function H32([uint32]$v){return ('0x{0:X8}' -f $v)}
function Assert-RomPrefix {
  $live=Read-Guest 0x80246000 16
  for($i=0;$i -lt $live.Length;$i+=4){[Array]::Reverse($live,$i,4)}
  $romBytes=[IO.File]::ReadAllBytes($rom)
  $expected=[byte[]]$romBytes[0x1000..0x100F]
  $liveHex=($live|ForEach-Object {$_.ToString('X2')}) -join ''
  $expectedHex=($expected|ForEach-Object {$_.ToString('X2')}) -join ''
  if($liveHex -cne $expectedHex){throw 'Live RDRAM prefix does not match pinned USA rev0 ROM bytes after per-word byte-order normalization.'}
}
function Read-Row([int]$seq){
  Assert-Target
  if($seq -eq 0){Assert-RomPrefix}
  $readStarted=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  $before=U32 (Read-Guest 0x8032D5D4 4) 0
  $m=Read-Guest 0x8033B170 0xC4
  $hud=Read-Guest 0x8033B260 0x0C
  $pause=Read-Guest 0x8033B238 4
  foreach($off in @(0,2,4,6,8,0xA)){if((U16Guest $hud $off) -ne (U16FromAlignedWord $hud $off)){throw "HUD export u16 decode disagrees with aligned-word decode at +$('{0:X}' -f $off)."}}
  foreach($off in @(0,2)){if((U16Guest $pause $off) -ne (U16FromAlignedWord $pause $off)){throw "Pause-state u16 decode disagrees with aligned-word decode at +$('{0:X}' -f $off)."}}
  $row=[ordered]@{
    trialId=$TrialId;sequence=$seq;hostUnixMs=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();hostReadStartUnixMs=$readStarted
    loopCounterBefore=$before;playModeS16=(S16Guest $pause 0);pauseTimerCandidateS16=(S16Guest $pause 2);action=(H32 (U32 $m 0x0C));actionState=(U16Guest $m 0x18);healthS16=(S16Guest $m 0xAE);pendingHurtTicks=(U8Guest $m 0xB2);pendingHealTicks=(U8Guest $m 0xB3)
    livesExportS16=(S16Guest $hud 0);coinDisplayS16=(S16Guest $hud 2);starsExportS16=(S16Guest $hud 4);healthSegmentsExportS16=(S16Guest $hud 6);keysExportS16=(S16Guest $hud 8);hudFlagsU16=(U16Guest $hud 0xA)
  }
  $after=U32 (Read-Guest 0x8032D5D4 4) 0
  $row.loopCounterAfter=$after;$row.counterPairEqual=($before -eq $after);$row.hostReadEndUnixMs=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds();$row.hostReadDurationMs=$row.hostReadEndUnixMs-$readStarted
  return [pscustomobject]$row
}
$meta=[ordered]@{trialId=$TrialId;samplerVersion='issue49-aligned-hud-pause-v2';samplerScriptPath=$PSCommandPath;samplerScriptSha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash;addedFields='aligned halfwords from HUD export block 0x8033B260..0x8033B26B; Mario health and hurt/heal counters; play-mode signed halfword and adjacent pause-timer candidate from aligned 0x8033B238 block';startedUtc=[DateTime]::UtcNow.ToString('o');processId=$TargetPid;processStartUtc=$pStart;windowHandle=('0x{0:X}' -f $TargetHwnd);executable=$exe;executableSha256=$expectedExeHash;analysisRomSha256=$expectedRomHash;nrageIniSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $ini).Hash;stickRange=$StickRange;padDeadZone=5;realN64Range=$true;hostBase=('0x{0:X}' -f $base);mapping='Initial RAM correspondence PC=0x80245000+ROM offset for ROM offsets 0x1000..0xE855F; raw host reads use per-word byte order normalized only for the 16-byte ROM signature check; native u32 and float reads, u16/s16 guest offset XOR 2, u8 guest offset XOR 3; HUD block begins at aligned word 0x8033B260 and u16 XOR2 decodes are checked against aligned native-word extraction';liveRomPrefix='16 bytes read at guest 0x80246000; each 4-byte word reversed to canonical ROM order and compared byte-for-byte with pinned ROM[0x1000..0x1010)';access='OpenProcess(PROCESS_QUERY_INFORMATION|PROCESS_VM_READ), ReadProcessMemory only';counterSemantics='loop counter read before state blocks and after; equality is not an atomicity guarantee';pauseStateSemantics='8033B238 is signed S16 mode set by FUN_80249764, with adjacent S16 timer candidate at +2; both guest decodes checked against aligned-word extraction';limit=$Limit;pollMs=$PollMs}
$meta|ConvertTo-Json -Compress|Set-Content -LiteralPath $metadata -Encoding UTF8
$rows=[Collections.Generic.List[object]]::new();$seq=0
try {
  $start=Read-Row $seq;$rows.Add($start);$seq++
  $first=[ordered]@{trialId=$TrialId;event='started';metadata=$meta;firstRow=$start}
  ($first|ConvertTo-Json -Depth 8 -Compress)|Set-Content -LiteralPath $progress -Encoding UTF8
  while($rows.Count -lt $Limit -and -not (Test-Path -LiteralPath $stop)){
    Start-Sleep -Milliseconds $PollMs
    $r=Read-Row $seq;$rows.Add($r);$seq++
    if(($rows.Count % 100)-eq 0){$tick=[ordered]@{trialId=$TrialId;event='progress';count=$rows.Count;last=$r;utc=[DateTime]::UtcNow.ToString('o')};($tick|ConvertTo-Json -Depth 6 -Compress)|Add-Content -LiteralPath $progress -Encoding UTF8}
  }
  $jsonl=($rows|ForEach-Object {$_|ConvertTo-Json -Depth 8 -Compress}) -join "`n"
  [IO.File]::WriteAllText($out,$jsonl+"`n",[Text.UTF8Encoding]::new($false))
  $summary=[ordered]@{trialId=$TrialId;status='complete';count=$rows.Count;first=$rows[0];last=$rows[$rows.Count-1];trace=$out;progress=$progress;metadata=$metadata}
  ($summary|ConvertTo-Json -Depth 8 -Compress)|Set-Content -LiteralPath ($progress+'.status.json') -Encoding UTF8
  $summary|ConvertTo-Json -Depth 8 -Compress
} catch {
  $originalError=$_.Exception.Message
  try{$partial=($rows|ForEach-Object {$_|ConvertTo-Json -Depth 8 -Compress}) -join "`n";if($rows.Count){[IO.File]::WriteAllText($out,$partial+"`n",[Text.UTF8Encoding]::new($false))};$state=[ordered]@{trialId=$TrialId;status='error';count=$rows.Count;error=$originalError;trace=$out;metadata=$metadata;endedUtc=[DateTime]::UtcNow.ToString('o')};$state|ConvertTo-Json -Depth 8 -Compress|Set-Content -LiteralPath ($progress+'.status.json') -Encoding UTF8}catch{Write-Warning "Could not preserve partial trace/status: $($_.Exception.Message)"}
  throw
} finally {if($handle -ne [IntPtr]::Zero){[void][MarioCraftExternalReadOnlyMemory]::CloseHandle($handle)}}
