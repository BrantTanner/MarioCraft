<#
Project64-only bounded keyboard input helper for Mario64 issue #47 research. The logical Z key maps to the active N-Rage keyboard binding, Space (scan 0x39). LoadState remains the Project64 F7 command (scan 0x41), scoped to the same verified Project64 window. C buttons and L/R use the active N-Rage keyboard bindings validated for this controller profile.

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
    [ValidateSet('Start', 'A', 'B', 'Z', 'DpadUp', 'DpadDown', 'DpadLeft', 'DpadRight', 'AnalogUp', 'AnalogDown', 'AnalogLeft', 'AnalogRight', 'CUp', 'CDown', 'CLeft', 'CRight', 'R', 'L', 'LoadState')]
    [string]$Key,
    [ValidateSet('Start', 'A', 'B', 'Z', 'DpadUp', 'DpadDown', 'DpadLeft', 'DpadRight', 'AnalogUp', 'AnalogDown', 'AnalogLeft', 'AnalogRight', 'CUp', 'CDown', 'CLeft', 'CRight', 'R', 'L', 'LoadState')]
    [string[]]$AdditionalKeys = @(),
    [ValidateRange(0, 2000)][int]$AdditionalDelayMs = 0,
    [ValidateSet('B')][string]$ThirdKey,
    [ValidateRange(0, 2000)][int]$ThirdDelayMs = 0,
    [ValidateRange(30, 3000)][int]$HoldMs = 120
)

$ErrorActionPreference = 'Stop'
if (-not [Environment]::Is64BitProcess) { throw 'Use 64-bit PowerShell; other process architectures are unsupported.' }

$expectedHash = 'C257C662D4E0B2CE1732B7F4A7D5DD4682F604CB082BBDBFCE5181470D6DBFB8'
$expectedPath = if ($TargetInstance -eq 'Installed') {
    [IO.Path]::GetFullPath('C:\Program Files (x86)\Project64 3.0\Project64.exe')
} else {
    [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..\tools\issue11-local\pj64-portable\Project64.exe'))
}
$keys = @($Key) + @($AdditionalKeys)
if ($PSBoundParameters.ContainsKey('ThirdKey')) { $keys += $ThirdKey }
if ($keys.Count -gt 3 -or (@($keys | Select-Object -Unique).Count -ne $keys.Count)) {
    throw 'Refusing input: provide one to three distinct whitelisted keys.'
}
if ($keys -contains 'LoadState' -and ($keys.Count -ne 1 -or $HoldMs -lt 30 -or $HoldMs -gt 150)) {
    throw 'Refusing LoadState: F7 must be the only key and held for 30–150 ms.'
}
if ($AdditionalDelayMs -gt 0 -and -not $PSBoundParameters.ContainsKey('ThirdKey') -and ($Key -ne 'AnalogUp' -or $AdditionalKeys.Count -ne 1 -or $AdditionalKeys[0] -ne 'B' -or $AdditionalDelayMs -lt 100 -or $AdditionalDelayMs -gt 2000 -or ($HoldMs - $AdditionalDelayMs) -lt 30)) {
    throw 'Refusing delayed input: only AnalogUp then B is supported, with a 100–2000 ms delay and at least 30 ms of HoldMs remaining.'
}
if ($PSBoundParameters.ContainsKey('ThirdKey') -or $ThirdDelayMs -gt 0) {
    if ($Key -ne 'AnalogUp' -or $AdditionalKeys.Count -ne 1 -or $AdditionalKeys[0] -ne 'Z' -or $ThirdKey -ne 'B' -or $AdditionalDelayMs -lt 30 -or $AdditionalDelayMs -ge $ThirdDelayMs -or ($ThirdDelayMs - $AdditionalDelayMs) -lt 30 -or ($HoldMs - $ThirdDelayMs) -lt 30) {
        throw 'Refusing three-phase input: only AnalogUp→Z→B is supported, with each phase separated by at least 30 ms and 30 ms remaining before release.'
    }
} elseif ($ThirdDelayMs -ne 0) {
    throw 'Refusing third delay without the exact AnalogUp→Z→B key sequence.'
}
if (-not $PSBoundParameters.ContainsKey('ThirdKey') -and ($PSBoundParameters.ContainsKey('ThirdDelayMs') -or $AdditionalDelayMs -gt 0 -and ($Key -ne 'AnalogUp' -or $AdditionalKeys.Count -ne 1 -or $AdditionalKeys[0] -ne 'B'))) {
    throw 'Refusing unsupported delayed key combination.'
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
    CRight      = @{ Code = 0x20; Extended = $false }
    CLeft       = @{ Code = 0x1E; Extended = $false }
    CDown       = @{ Code = 0x1F; Extended = $false }
    CUp         = @{ Code = 0x11; Extended = $false }
    R           = @{ Code = 0x12; Extended = $false }
    L           = @{ Code = 0x10; Extended = $false }
    LoadState   = @{ Code = 0x41; Extended = $false }
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

$logRelativePath = if ($TargetInstance -eq 'Installed') { 'tools\issue10-local\project64-input-log.jsonl' } else { 'tools\issue11-local\issue40-project64-input-log.jsonl' }
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
    additionalDelayMs = $AdditionalDelayMs
    thirdKey = $ThirdKey
    thirdDelayMs = $ThirdDelayMs
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
    $initialKeys = if ($AdditionalDelayMs -gt 0 -or $PSBoundParameters.ContainsKey('ThirdKey')) { @($Key) } else { $keys }
    foreach ($currentKey in $initialKeys) {
        if (-not [Project64ScopedInputNative]::SendScan([UInt16]$scan[$currentKey].Code, [bool]$scan[$currentKey].Extended, $false)) {
            throw "SendInput key-down for $currentKey failed with Win32 error $([Runtime.InteropServices.Marshal]::GetLastWin32Error())."
        }
$downSent.Add($currentKey)
        $record.inputEvents.Add([ordered]@{ event = 'keydown'; key = $currentKey; utc = [DateTime]::UtcNow.ToString('o') })
    }
    $record.keyDownUtc = [DateTime]::UtcNow.ToString('o')
    $record.outcome = 'held'
    $stopwatch.Start()
    if ($AdditionalDelayMs -gt 0) {
        while ($stopwatch.ElapsedMilliseconds -lt $AdditionalDelayMs) {
            Assert-TargetForeground
            Start-Sleep -Milliseconds ([Math]::Min(10, [Math]::Max(1, $AdditionalDelayMs - [int]$stopwatch.ElapsedMilliseconds)))
        }
        if (($HoldMs - [int]$stopwatch.ElapsedMilliseconds) -lt 30 -and -not $PSBoundParameters.ContainsKey('ThirdKey')) {
            throw 'Refusing delayed B: fewer than 30 ms remain in the bounded hold.'
        }
        foreach ($currentKey in $AdditionalKeys) {
            Assert-TargetForeground
            if (-not [Project64ScopedInputNative]::SendScan([UInt16]$scan[$currentKey].Code, [bool]$scan[$currentKey].Extended, $false)) {
                throw "SendInput key-down for $currentKey failed with Win32 error $([Runtime.InteropServices.Marshal]::GetLastWin32Error())."
            }
            $downSent.Add($currentKey)
            $record.inputEvents.Add([ordered]@{ event = 'keydown'; key = $currentKey; utc = [DateTime]::UtcNow.ToString('o') })
        }
    }
    if ($PSBoundParameters.ContainsKey('ThirdKey')) {
        while ($stopwatch.ElapsedMilliseconds -lt $ThirdDelayMs) {
            Assert-TargetForeground
            Start-Sleep -Milliseconds ([Math]::Min(10, [Math]::Max(1, $ThirdDelayMs - [int]$stopwatch.ElapsedMilliseconds)))
        }
        if (($HoldMs - [int]$stopwatch.ElapsedMilliseconds) -lt 30) { throw 'Refusing third-phase B: fewer than 30 ms remain in the bounded hold.' }
        Assert-TargetForeground
        if (-not [Project64ScopedInputNative]::SendScan([UInt16]$scan[$ThirdKey].Code, [bool]$scan[$ThirdKey].Extended, $false)) {
            throw "SendInput key-down for $ThirdKey failed with Win32 error $([Runtime.InteropServices.Marshal]::GetLastWin32Error())."
        }
        $downSent.Add($ThirdKey)
        $record.inputEvents.Add([ordered]@{ event = 'keydown'; key = $ThirdKey; utc = [DateTime]::UtcNow.ToString('o') })
    }
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





