<#
Bounded read-only Project64 sound-request queue sampler for issue #50.

Records only submitted 32-bit sound IDs, queue read/write indices, their
loop-counter brackets, and timing. It deliberately drops each queue record's
position pointer and does not claim that a request was accepted or played.
No waveform, raw RAM block, save state, or screenshot is captured.
#>
[CmdletBinding()]
param(
  [Parameter(Mandatory=$true)][ValidatePattern('^issue50-[a-z0-9-]+$')][string]$TrialId,
  [Parameter(Mandatory=$true)][int]$TargetPid,
  [Parameter(Mandatory=$true)][long]$TargetHwnd,
  [Parameter(Mandatory=$true)][string]$HostBaseHex,
  [Parameter(Mandatory=$true)][string]$ProjectRoot,
  [Parameter(Mandatory=$true)][string]$AnalysisRomPath,
  [ValidateRange(1,7200)][int]$Limit=1800,
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
$metadata=Join-Path $portable "$TrialId.metadata.json"
$status=Join-Path $portable "$TrialId.status.json"
${progress}=Join-Path $portable "$TrialId.progress.jsonl"
$stop=Join-Path $portable "$TrialId.stop"
foreach($existing in @($out,$metadata,$status,$progress,$stop)){if(Test-Path -LiteralPath $existing){throw "Refusing to overwrite existing capture artifact: $existing"}}
if((Get-FileHash -Algorithm SHA256 -LiteralPath $exe).Hash -ne $expectedExeHash){throw 'Project64 executable hash mismatch.'}
if((Get-FileHash -Algorithm SHA256 -LiteralPath $rom).Hash -ne $expectedRomHash){throw 'Analysis-copy ROM hash mismatch.'}
$romBytes=[IO.File]::ReadAllBytes($rom)
$expectedRomPrefix=[byte[]]$romBytes[0x1000..0x100F]
$proc=Get-Process -Id $TargetPid -ErrorAction Stop
if([IO.Path]::GetFullPath($proc.Path) -ne [IO.Path]::GetFullPath($exe) -or [long]$proc.MainWindowHandle -ne $TargetHwnd){throw 'Unexpected Project64 PID/HWND.'}
$pStart=$proc.StartTime.ToUniversalTime().ToString('o')
$base=[Convert]::ToInt64(($HostBaseHex -replace '^0x',''),16)
if($base -le 0){throw 'Invalid process-specific RDRAM base.'}
if(-not ('MarioCraftIssue50ReadOnly' -as [type])){
  Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;
public static class MarioCraftIssue50ReadOnly {
 [DllImport("kernel32.dll",SetLastError=true)] public static extern IntPtr OpenProcess(uint access,bool inherit,int pid);
 [DllImport("kernel32.dll",SetLastError=true)] public static extern bool CloseHandle(IntPtr h);
 [DllImport("kernel32.dll",SetLastError=true)] public static extern bool ReadProcessMemory(IntPtr h,IntPtr address,byte[] buffer,IntPtr size,out IntPtr read);
 [DllImport("user32.dll")] public static extern IntPtr GetForegroundWindow();
 [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr hwnd,out uint pid);
 public static byte[] Read(IntPtr h,long a,int n){byte[] b=new byte[n];IntPtr got;if(!ReadProcessMemory(h,new IntPtr(a),b,new IntPtr(n),out got)||got.ToInt64()!=n)throw new System.ComponentModel.Win32Exception(Marshal.GetLastWin32Error(),"ReadProcessMemory failed/partial");return b;}
}
'@
}
$handle=[MarioCraftIssue50ReadOnly]::OpenProcess(0x0410,$false,$TargetPid)
if($handle -eq [IntPtr]::Zero){throw 'OpenProcess(PROCESS_QUERY_INFORMATION|PROCESS_VM_READ) failed.'}
function Assert-Target {
  $p=Get-Process -Id $TargetPid -ErrorAction Stop
  if([IO.Path]::GetFullPath($p.Path) -ne [IO.Path]::GetFullPath($exe) -or [long]$p.MainWindowHandle -ne $TargetHwnd -or $p.StartTime.ToUniversalTime().ToString('o') -ne $pStart){throw 'Project64 process identity changed.'}
  $fg=[MarioCraftIssue50ReadOnly]::GetForegroundWindow();[uint32]$fp=0;[void][MarioCraftIssue50ReadOnly]::GetWindowThreadProcessId($fg,[ref]$fp)
  if($fg.ToInt64() -ne $TargetHwnd -or $fp -ne $TargetPid){throw "Project64 lost foreground (HWND=$($fg.ToInt64()), PID=$fp)."}
}
function Read-Guest([long]$address,[int]$length){$guest=[uint32]($address -band 4294967295L);if([long]$guest -lt 0x80000000L -or ([long]$guest+$length) -gt 0x80400000L){throw ('Guest address outside RDRAM: 0x{0:X8}+{1}' -f $guest,$length)};return [MarioCraftIssue50ReadOnly]::Read($handle,($base+([long]$guest-0x80000000L)), $length)}
function U32([byte[]]$b,[int]$o){return [BitConverter]::ToUInt32($b,$o)}
function U8Guest([byte[]]$b,[int]$guestOffset){return [int]$b[$guestOffset -bxor 3]}
function U16Guest([byte[]]$b,[int]$guestOffset){return [BitConverter]::ToUInt16($b,($guestOffset -bxor 2))}
function S16Guest([byte[]]$b,[int]$guestOffset){return [BitConverter]::ToInt16($b,($guestOffset -bxor 2))}
function H32([uint32]$v){return ('0x{0:X8}' -f $v)}
function Assert-RomPrefix {
  $live=Read-Guest 0x80246000 16
  for($i=0;$i -lt $live.Length;$i+=4){[Array]::Reverse($live,$i,4)}
  $liveHex=($live|ForEach-Object {$_.ToString('X2')}) -join ''
  $expectedHex=($expectedRomPrefix|ForEach-Object {$_.ToString('X2')}) -join ''
  if($liveHex -cne $expectedHex){throw 'Live RDRAM prefix does not match pinned USA rev0 ROM bytes after per-word byte-order normalization.'}
}
function Read-Index([long]$address){$b=Read-Guest $address 4;return (U8Guest $b 0)}
function Read-Event([int]$index){$slot=$index -band 0xFF;$entry=Read-Guest (0x80361498L + ($slot * 8)) 8;return (U32 $entry 0)}
function Read-Row([int]$seq,[int]$lastWrite){
  Assert-Target
  Assert-RomPrefix
  $started=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
  $before=U32 (Read-Guest 0x8032D5D4 4) 0
  $mario=Read-Guest 0x8033B170 0x1C
  $pause=Read-Guest 0x8033B238 4
  $readBefore=Read-Index 0x80332F40
  $writeBefore=Read-Index 0x80332F44
  $delta=($writeBefore-$lastWrite)-band 0xFF
  $events=[Collections.Generic.List[object]]::new()
  for($i=0;$i -lt $delta;$i++){
    $idx=($lastWrite+$i)-band 0xFF
    $events.Add([ordered]@{queueIndex=$idx;soundRequestId=(H32 (Read-Event $idx))})
  }
  $readAfter=Read-Index 0x80332F40
  $writeAfter=Read-Index 0x80332F44
  $after=U32 (Read-Guest 0x8032D5D4 4) 0
  return [pscustomobject][ordered]@{
    trialId=$TrialId;sequence=$seq;hostReadStartUnixMs=$started;hostReadEndUnixMs=[DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
    loopCounterBefore=$before;loopCounterAfter=$after;counterPairEqual=($before -eq $after)
    playModeS16=(S16Guest $pause 0);marioAction=(H32 (U32 $mario 0x0C));marioActionState=(U16Guest $mario 0x18)
    requestReadIndexBefore=$readBefore;requestReadIndexAfter=$readAfter;requestWriteIndexBefore=$writeBefore;requestWriteIndexAfter=$writeAfter
    requestIndicesStable=($writeBefore -eq $writeAfter);queueLagBefore=(($writeBefore-$readBefore)-band 0xFF)
    detectedRequests=$events.ToArray();detectedRequestCount=$events.Count
  }
}
$rows=[Collections.Generic.List[object]]::new();$seq=0;$lastWrite=$null;$writer=$null
try {
  $meta=[ordered]@{
  trialId=$TrialId;samplerVersion='issue50-sound-request-queue-v1';samplerScriptPath=$PSCommandPath;samplerScriptSha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash
  startedUtc=[DateTime]::UtcNow.ToString('o');processId=$TargetPid;processStartUtc=$pStart;windowHandle=('0x{0:X}' -f $TargetHwnd);executable=$exe;executableSha256=$expectedExeHash
  analysisRomSha256=$expectedRomHash;nrageIniSha256=(Get-FileHash -Algorithm SHA256 -LiteralPath $ini).Hash;hostBase=('0x{0:X}' -f $base)
  liveRomPrefix='16 bytes at guest 0x80246000; each 4-byte word reversed to canonical ROM order and compared byte-for-byte with pinned ROM[0x1000..0x1010) on every row'
  correlatedScalars='Signed play mode from 0x8033B238 (guest halfword XOR2); Mario action U32 at 0x8033B17C and action-state U16 at 0x8033B188 (the latter guest halfword XOR2). Reads are separately bracketed, not atomic.'
  access='OpenProcess(PROCESS_QUERY_INFORMATION|PROCESS_VM_READ), ReadProcessMemory only';input='No input injection; caller runs this sampler only during a named scenario'
  queue='U8 read cursor 0x80332F40, U8 write cursor 0x80332F44; 256 entries at 0x80361498, stride 8; output includes only each newly observed entry U32 sound ID and omits its position pointer'
  eventSemantics='A row proves that the target submitted a numeric sound request ID to this ring. It does not prove acceptance, mixing, playback, or audible output.'
    limitations='Queue cursors wrap modulo 256; more than 255 requests between polls or races during reads can make individual request coverage incomplete. Equal loop-counter brackets and stable cursors do not guarantee atomic reads.'
    limit=$Limit;pollMs=$PollMs
  }
  $meta|ConvertTo-Json -Compress|Set-Content -LiteralPath $metadata -Encoding UTF8
  $rows=[Collections.Generic.List[object]]::new();$seq=0;$lastWrite=$null
  $writer=[IO.StreamWriter]::new([IO.File]::Open($out,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read),[Text.UTF8Encoding]::new($false))
  Assert-Target;Assert-RomPrefix
  $lastWrite=Read-Index 0x80332F44
  while($rows.Count -lt $Limit -and -not (Test-Path -LiteralPath $stop)){
    if($rows.Count -gt 0){Start-Sleep -Milliseconds $PollMs}
    $r=Read-Row $seq $lastWrite;$rows.Add($r);$seq++
    $writer.WriteLine(($r|ConvertTo-Json -Depth 8 -Compress))
    # Advance only through the cursor that bounded this scan. If the writer
    # moved during the read, the next row will inspect that newer portion.
    $lastWrite=$r.requestWriteIndexBefore
    if(($rows.Count % 100)-eq 0){$writer.Flush();$tick=[ordered]@{trialId=$TrialId;event='progress';count=$rows.Count;last=$r;utc=[DateTime]::UtcNow.ToString('o')};($tick|ConvertTo-Json -Depth 8 -Compress)|Add-Content -LiteralPath $progress -Encoding UTF8}
    $alive=Get-Process -Id $TargetPid -ErrorAction Stop
    if($alive.StartTime.ToUniversalTime().ToString('o') -ne $pStart){throw 'Project64 process restarted during capture.'}
  }
  $writer.Flush();$writer.Dispose();$writer=$null
  $endState=if(Test-Path -LiteralPath $stop){'stopped'}else{'complete'}
  $summary=[ordered]@{trialId=$TrialId;status=$endState;count=$rows.Count;first=$rows[0];last=$rows[$rows.Count-1];trace=$out;metadata=$metadata;endedUtc=[DateTime]::UtcNow.ToString('o')}
  $summary|ConvertTo-Json -Depth 8 -Compress|Set-Content -LiteralPath $status -Encoding UTF8
  $summary|ConvertTo-Json -Depth 8 -Compress
} catch {
  if($writer){try{$writer.Flush();$writer.Dispose()}catch{}}
  if($rows -and $rows.Count -gt 0 -and -not (Test-Path -LiteralPath $out)){$jsonl=($rows|ForEach-Object {$_|ConvertTo-Json -Depth 8 -Compress}) -join "`n";[IO.File]::WriteAllText($out,$jsonl+"`n",[Text.UTF8Encoding]::new($false))}
  $summary=[ordered]@{trialId=$TrialId;status='error';count=$rows.Count;error=$_.Exception.Message;trace=$out;metadata=$metadata;endedUtc=[DateTime]::UtcNow.ToString('o')}
  $summary|ConvertTo-Json -Depth 8 -Compress|Set-Content -LiteralPath $status -Encoding UTF8
  throw
} finally {if($writer){try{$writer.Flush();$writer.Dispose()}catch{}};if($handle -ne [IntPtr]::Zero){[void][MarioCraftIssue50ReadOnly]::CloseHandle($handle)}}
