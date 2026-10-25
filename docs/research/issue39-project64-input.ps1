<#
Project64-only bounded keyboard input helper for Mario64 issue #39 research. The logical Z key maps to the active N-Rage keyboard binding, Space (scan 0x39).

The helper does not choose or foreground an application. It requires the caller
to provide the currently observed Project64 PID and HWND, and refuses to send
input unless that exact process/window is the foreground window. It accepts
only configured SM64 controller keys, at most three at once, held for at most 3000 ms.
Key-up events are always sent in finally.
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][int]$ExpectedPid,
    [Parameter(Mandatory = $true)][long]$ExpectedHwnd,
    [ValidateSet('Installed', 'Issue11Portable')][string]$TargetInstance = 'Installed',
    [Parameter(Mandatory = $true)]
    [ValidateSet('Start', 'A', 'B', 'Z', 'DpadUp', 'DpadDown', 'DpadLeft', 'DpadRight', 'AnalogUp', 'AnalogDown', 'AnalogLeft', 'AnalogRight')]
    [string]$Key,
    [ValidateSet('Start', 'A', 'B', 'Z', 'DpadUp', 'DpadDown', 'DpadLeft', 'DpadRight', 'AnalogUp', 'AnalogDown', 'AnalogLeft', 'AnalogRight')]
    [string[]]$AdditionalKeys = @(),
    [ValidateRange(30, 3000)][int]$HoldMs = 120
)

$ErrorActionPreference = 'Stop'
if (-not [Environment]::Is64BitProcess) { throw 'Use 64-bit PowerShell; other process architectures are unsupported.' }

$expectedHash = 'C257C662D4E0B2CE1732B7F4A7D5DD4682F604CB082BBDBFCE5181470D6DBFB8'
$expectedPath = if ($TargetInstance -eq 'Installed') {
    [IO.Path]::GetFullPath('C:\Program Files (x86)\Project64 3.0\Project64.exe')
} else {
    [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\MarioCraft\tools\issue11-local\pj64-portable\Project64.exe'))
}
$keys = @($Key) + @($AdditionalKeys)
if ($keys.Count -gt 3 -or (@($keys | Select-Object -Unique).Count -ne $keys.Count)) {
    throw 'Refusing input: provide one to three distinct whitelisted keys.'
}
$scan = @{
    Start       = @{ Code = 0x1C; Extended = $false }
    A           = @{ Code = 0x2D; Extended = $false }
    B           = @{ Code = 0x2E; Extended = $false }
    Z           = @{ Code = 0x39; Extended = $false }
    DpadUp      = @{ Code = 0x17; Extended = $false }
    DpadDown    = @{ Code = 0x25; Extended = $false }
    DpadLeft    = @{ Code = 0x24; Extended = $false }
    DpadRight   = @{ Code = 0x26; Extended = $false }
    AnalogUp    = @{ Code = 0x48; Extended = $true }
    AnalogDown  = @{ Code = 0x50; Extended = $true }
    AnalogLeft  = @{ Code = 0x4B; Extended = $true }
    AnalogRight = @{ Code = 0x4D; Extended = $true }
}

if (-not ('Project64ScopedInputNative' -as [type])) {
Add-Type -TypeDefinition @'
using System;
using System.Runtime.InteropServices;

public static class Project64ScopedInputNative {
    [StructLayout(LayoutKind.Sequential)]
    public struct KEYBDINPUT {
        public ushort wVk;
        public ushort wScan;
        public uint dwFlags;
        public uint time;
        public UIntPtr dwExtraInfo;
    }

    [StructLayout(LayoutKind.Explicit, Size = 40)]
    public struct INPUT64 {
        [FieldOffset(0)] public uint type;
        [FieldOffset(8)] public KEYBDINPUT ki;
    }

    [DllImport("user32.dll", SetLastError = true)]
    public static extern uint SendInput(uint nInputs, INPUT64[] pInputs, int cbSize);

    [DllImport("user32.dll")]
    public static extern IntPtr GetForegroundWindow();

    [DllImport("user32.dll")]
    public static extern uint GetWindowThreadProcessId(IntPtr hWnd, out uint processId);

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    public static extern int GetWindowTextW(IntPtr hWnd, System.Text.StringBuilder text, int maxCount);

    [DllImport("user32.dll")]
    public static extern bool IsWindow(IntPtr hWnd);

    public static bool SendScan(ushort scan, bool extended, bool keyUp) {
        const uint INPUT_KEYBOARD = 1;
        const uint KEYEVENTF_EXTENDEDKEY = 0x0001;
        const uint KEYEVENTF_KEYUP = 0x0002;
        const uint KEYEVENTF_SCANCODE = 0x0008;
        uint flags = KEYEVENTF_SCANCODE;
        if (extended) flags |= KEYEVENTF_EXTENDEDKEY;
        if (keyUp) flags |= KEYEVENTF_KEYUP;
        var input = new INPUT64 {
            type = INPUT_KEYBOARD,
            ki = new KEYBDINPUT { wVk = 0, wScan = scan, dwFlags = flags, time = 0, dwExtraInfo = UIntPtr.Zero }
        };
        return SendInput(1, new INPUT64[] { input }, Marshal.SizeOf(typeof(INPUT64))) == 1;
    }
}
'@
}

$hwnd = [IntPtr]$ExpectedHwnd
$process = Get-Process -Id $ExpectedPid -ErrorAction Stop
$actualPath = [IO.Path]::GetFullPath($process.Path)
$titleBuffer = [Text.StringBuilder]::new(512)
[uint32]$windowPid = 0
[void][Project64ScopedInputNative]::GetWindowThreadProcessId($hwnd, [ref]$windowPid)
$null = [Project64ScopedInputNative]::GetWindowTextW($hwnd, $titleBuffer, $titleBuffer.Capacity)
$title = $titleBuffer.ToString()
if (-not [string]::Equals($actualPath, $expectedPath, [StringComparison]::OrdinalIgnoreCase)) {
    throw "Refusing input: PID $ExpectedPid path '$actualPath' is not the expected Project64 executable."
}
if ((Get-FileHash -LiteralPath $actualPath -Algorithm SHA256).Hash -ne $expectedHash) {
    throw "Refusing input: Project64 binary SHA-256 does not match the verified build ($expectedHash)."
}
if ($windowPid -ne $ExpectedPid -or -not [Project64ScopedInputNative]::IsWindow($hwnd)) {
    throw "Refusing input: HWND $ExpectedHwnd is not a live window owned by Project64 PID $ExpectedPid."
}
if ($title -notmatch '^Super Mario 64 \(U\) - Project64 3\.0\.1\.5664-') {
    throw "Refusing input: unexpected Project64 window title '$title'."
}

function Assert-TargetForeground {
    $foreground = [Project64ScopedInputNative]::GetForegroundWindow()
    [uint32]$foregroundPid = 0
    [void][Project64ScopedInputNative]::GetWindowThreadProcessId($foreground, [ref]$foregroundPid)
    if ($foreground -ne $hwnd -or $foregroundPid -ne $ExpectedPid) {
        throw "Focus changed: expected Project64 HWND $ExpectedHwnd / PID $ExpectedPid; foreground HWND $($foreground.ToInt64()) / PID $foregroundPid."
    }
}

$logRelativePath = if ($TargetInstance -eq 'Installed') { 'MarioCraft\tools\issue10-local\project64-input-log.jsonl' } else { 'MarioCraft\tools\issue11-local\project64-input-log.jsonl' }
$logPath = Join-Path (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path $logRelativePath
$null = [IO.Directory]::CreateDirectory((Split-Path -Parent $logPath))
$record = [ordered]@{
    startedUtc = [DateTime]::UtcNow.ToString('o')
    pid = $ExpectedPid
    hwnd = $ExpectedHwnd
    windowTitle = $title
    executable = $actualPath
    executableSha256 = $expectedHash
    targetInstance = $TargetInstance
    keys = $keys
    scanCodes = @($keys | ForEach-Object { '0x{0:X2}' -f $scan[$_].Code })
    holdMsRequested = $HoldMs
    keyDownUtc = $null
    keyUpUtc = $null
    inputEvents = [Collections.Generic.List[object]]::new()
    elapsedMs = $null
    outcome = 'not-started'
    error = $null
}
$downSent = [Collections.Generic.List[string]]::new()
$stopwatch = [Diagnostics.Stopwatch]::new()
try {
    Assert-TargetForeground
    foreach ($currentKey in $keys) {
        if (-not [Project64ScopedInputNative]::SendScan([UInt16]$scan[$currentKey].Code, [bool]$scan[$currentKey].Extended, $false)) {
            throw "SendInput key-down for $currentKey failed with Win32 error $([Runtime.InteropServices.Marshal]::GetLastWin32Error())."
        }
$downSent.Add($currentKey)
        $record.inputEvents.Add([ordered]@{ event = 'keydown'; key = $currentKey; utc = [DateTime]::UtcNow.ToString('o') })
    }
    $record.keyDownUtc = [DateTime]::UtcNow.ToString('o')
    $record.outcome = 'held'
    $stopwatch.Start()
    while ($stopwatch.ElapsedMilliseconds -lt $HoldMs) {
        Assert-TargetForeground
        Start-Sleep -Milliseconds ([Math]::Min(10, [Math]::Max(1, $HoldMs - [int]$stopwatch.ElapsedMilliseconds)))
    }
    Assert-TargetForeground
    $record.outcome = 'completed'
}
catch {
    $record.outcome = 'aborted'
    $record.error = $_.Exception.Message
    throw
}
finally {
    $stopwatch.Stop()
    for ($i = $downSent.Count - 1; $i -ge 0; $i--) {
        $currentKey = $downSent[$i]
        $released = [Project64ScopedInputNative]::SendScan([UInt16]$scan[$currentKey].Code, [bool]$scan[$currentKey].Extended, $true)
$record.keyUpUtc = [DateTime]::UtcNow.ToString('o')
        $record.inputEvents.Add([ordered]@{ event = 'keyup'; key = $currentKey; utc = $record.keyUpUtc })
        $record.elapsedMs = [Math]::Round($stopwatch.Elapsed.TotalMilliseconds, 1)
        if (-not $released) {
            $record.outcome = 'release-failed'
            $record.error = "SendInput key-up for $currentKey failed with Win32 error $([Runtime.InteropServices.Marshal]::GetLastWin32Error())."
        }
    }
    $record.finishedUtc = [DateTime]::UtcNow.ToString('o')
    $record | ConvertTo-Json -Compress | Add-Content -LiteralPath $logPath -Encoding UTF8
}

if ($record.outcome -ne 'completed') { throw "Input helper ended with outcome '$($record.outcome)'" }
$record | ConvertTo-Json -Compress





