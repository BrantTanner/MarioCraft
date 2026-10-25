# Mario 64 crouch, slide, dive, and ground-pound behavior (issue #40)

This dossier records bounded runtime representatives and exact-US-ROM checks for crouch/crawl, moving Z-slide and release, slide-kick speed gating, grounded dive, and ground pound. It is an evidence report for the original USA retail ROM; it does not implement gameplay code.

## Identity and method

The tested ROM is the 8 MiB USA image, SHA-256 `17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91`. The original image was read-only; Project64 loaded a byte-identical local analysis copy. Runtime was isolated Project64 3.0.1.5664-2df3434 (SHA-256 `C257C662D4E0B2CE1732B7F4A7D5DD4682F604CB082BBDBFCE5181470D6DBFB8`) with N-Rage (SHA-256 `8B06D0BC29DEC6233640400E15051B945923BB6283E290B40AF64016992E6C20`), Real N64 Range enabled, analog range 100, deadzone 5. No game memory was written.

Runtime rows came from [mario64-issue40-external-capture.ps1](mario64-issue40-external-capture.ps1), which reads the selected process using `OpenProcess(QUERY_INFORMATION|VM_READ)` and `ReadProcessMemory`, validates the ROM signature-derived RDRAM base and exact target identity, and has bounded capture/stop controls. It reads Mario/controller fields and brackets them with the global loop counter at `0x8032D5D4`. A counter pair mismatch marks a row as transient; equal pairs are still not an atomic snapshot. Counter units are used only for within-trial ordering and elapsed bounded timing, not labeled physics frames or VI frames. Poll intervals were host-side and varied with Project64 scheduling.

Clean representatives restored the archived courtyard baseline (SHA-256 `6E2C8265A8A9A19761BCC6CE29FA20694FAD5444B325B1F22CA987776A2D6DFE`): lives 4, stars 0, position `(-1328,260,4354)`, floor type 0, normal-Y 1.0. Baseline restores were verified numerically before inputs. The N-Rage mapping was A=X/scan `0x2D`/mask `0x8000`, B=C/scan `0x2E`/mask `0x4000`, Z=Space/scan `0x39`/mask `0x2000`; Up is the analog stick. The bounded helper is [mario64-issue40-project64-input.ps1](mario64-issue40-project64-input.ps1). Its final reviewed SHA-256 is `C922F6CE5CCCFFA3648EC8586E8E2069040CE998935908C3FD1DC305F6B21249` and it checks process path/hash, PID, HWND, title and foreground, restricts keys and duration, and releases held keys in `finally`. The file named `issue40-project64-input-reviewed-three-phase-20261025.ps1` is byte-identical to that final helper; despite its historical “pre-z-b” label in working notes, it is not an earlier revision. The earliest legacy-capture helper bytes were not preserved, so no per-trial source hash is claimed for those earlier captures; their scan codes, target checks, hold bounds and release behavior were unchanged.

Exact ROM offsets map to PCs as `0x80245000 + ROM offset` only for offsets `0x1000..0xE855F`; do not extrapolate beyond that verified linear span. Decomp source commit `9921382a68bb0c865e5e45eb594d9c64db59b1af` supplies navigation labels only. Exact-ROM bytes and observed numeric action IDs are the evidence; source names are used as convenient labels.

## Observed behavior

- **Stationary crouch and release:** From the F7-restored flat-courtyard idle baseline, a 350 ms Z hold transitioned `0C400201` → `0C008221` (crouch-start) → `0C008220` (held crouch). Releasing Z passed through `0C008222` (stop crouching) back to idle. Position and forward speed remained fixed. IDs are numeric; source labels are navigation.
- **Crawl:** A simultaneous Z+Up hold entered crawl-start `0C008223`, then crawl `04008448`. Over the sampled hold Mario moved about 53 units along Z (4344.81→4291.79). Releasing both reached crawl-stop `0C008224` and then settled at zero speed on the flat floor. This trial was restored from the courtyard baseline.
- **Moving Z-slide and release:** Up was held for 800 ms, followed by a Z+Up segment for 350 ms. In the second segment the helper necessarily released/re-pressed Up; the input log records that edge. The walk-to-slide branch entered `04808459` at forward speed 20.266. While Z+Up remained held speed fell to roughly 10; after release it continued to decay, passed crouch/stop-crouch, then settled to idle. No A or B was pressed. This documents one slope-free entry/release path, not all surface behavior.
- **Slide-kick speed gate:** The fast continuous Up→Z→B trial had `04808459` already active before B, at speed 20.73 and input magnitude 64. B entered `018008AA` at counter 4671, then slide-kick slide `0080045A` at 4687, stop `08000225` at 4699, crouch `0C008220` at 4713, stop-crouch at 4714, and stable idle by 4723. The settled endpoint remained on floor type 41 (normal-Y 0.9702), Y≈300.786, vertical velocity 0, forward speed 0. It is a type-41 slope endpoint, not the flat courtyard surface.
- The slow control verified `04808459` before B at speed 8.365 (input magnitude was 64). At the B edge the sampled speed was 4.771; B selected `00800457` (move-punch branch), not `018008AA`. The ROM gate is forward speed ≥10, not stick magnitude. The character then settled through crouch/idle. This brackets the observed branch once on each side; it is not a statistical threshold sweep.
- **Grounded dive:** A full-range Up run reached normal walking speed 31.22 with input magnitude 64 before B. The B edge selected `0188088A` with argument 1, then transitioned into `00880456` dive-slide while descending. The highest airborne sample was Y=320; subsequent Y rose from 319.73 to 368.47 while traversing the rising type-41 floor. It decelerated to zero, transitioned to `00000386` stomach-slide-stop, and stayed physically settled there for eight equal-counter rows (Y≈368.47, vertical velocity 0, forward speed 0). This endpoint is a stomach-slide stop, not generic idle. A low-speed airborne B control selected `018008AC` jump-kick, not dive.
- **Ground pound:** From baseline, A+Up initiated `03000880` normal jump, then a fresh Z+Up press selected `008008A9`. During action state 0, Y rose from 504 to 594 and remained at that level as the action timer advanced to 15; state 1 then began the descent (Y=544, vertical velocity −54). The flat-floor impact at Y=260 transitioned to `0080023C` ground-pound-land, then `0C00023E` butt-slide-stop, and ultimately idle. Floor type 0 and normal-Y 1.0 remained constant. Exact ROM shows the initial Y behavior is action-state/timer driven; the free-fall air-step is deferred until state 1.
- **Combination reference:** The accepted #39 trace [MARIO64-ISSUE39-JUMP-TRACE-SAMPLES.jsonl](MARIO64-ISSUE39-JUMP-TRACE-SAMPLES.jsonl) records crouch-slide long-jump entry. The selected #40 trace includes its relevant rows for comparison. In that path A requires timer≤29 and forward speed>10. A long jump is not treated as a B-dive path.

Selected numeric scalar rows are in [MARIO64-ISSUE40-CROUCH-SLIDE-DIVE-GROUND-POUND-TRACE-SAMPLES.jsonl](MARIO64-ISSUE40-CROUCH-SLIDE-DIVE-GROUND-POUND-TRACE-SAMPLES.jsonl). They omit pointers, raw memory, save-state bytes, and screenshots. Each row retains source sequence, counter bracket/equality, host read-start time, raw action identifiers, selected input scalars, position/velocity, speed, and floor information. Full local captures remain in the ignored Project64 working directory and are not published.

## Exact-ROM checks

| Behavior | ROM offsets (inclusive/exclusive) | Verified PC span | SHA-256 of exact bytes | Runtime-relevant condition/write |
|---|---:|---|---|---|
| Shared action setter | `0xDCF4..0xDE58` | `0x80252CF4..0x80252E58` | `1e210f62f404d4be0e0cd008271c03d47d1166bdf85fe25d50b0b3242918f0bc` | Common action/state/timer initialization; observed transitions are setter effects. |
| Walking→Z slide | `0x2146C..0x21494` | `0x8026646C..0x80266494` | `e28a84eb3dc0728b4e4eb0b5f95d823bcb82c53135be6bb1e85dbaa049b4da7e` | Derived-input Z bit `0x8000` invokes action `0x04808459`. |
| Crouch-slide handler and B gate | `0x23168..0x23338` | `0x80268168..0x80268338` | `FA7358A2BC1623CC148462AF90E6CBB4A3E82CBF1662E6ABA7502217C9CD4B1B` | B bit `0x2000`; forward speed ≥10.0 selects `0x018008AA`, otherwise `0x00800457` argument 9. Its A path includes the #39 long-jump gate (timer≤29 and speed>10). |
| Ground dive-or-punch branch | `0x20558..0x20620` | `0x80265558..0x80265620` | `4700f83637e0fc186097c1b40d98d6d7191c488f64abcad790967862da95c353` | B bit `0x2000`, forward speed ≥29.0 and stick magnitude >48.0 sets vertical velocity 20 and action `0x0188088A` argument 1; otherwise `0x00800457` argument 0. The recorded pre-edge row had speed 31.22 and magnitude 64. |
| Dive handler | `0x271E0..0x274B4` | `0x8026C1E0..0x8026C4B4` | `51adde4c7223e5a2b4bbbb458a2dc9b206c7691aae99870c0c9c39cb98039d40` | Dive air-step and landed transition write `0x00880456`; runtime then decelerated into `0x00000386`. |
| Ground-pound handler | `0x279FC..0x27D08` | `0x8026C9FC..0x8026CD08` | `344a3c1ffae1f6740237007cb4f80071b3ec6d5115cc5e50aa69c6303bcb945e` | State 0 applies Y offset `20−2*timer` while timer<10, sets vertical velocity −50 and forward speed 0; air-step begins only at state 1. Landing setter writes `0x0080023C`. |
| Ground-pound stop | `0x1E5B4..0x1E5C8` | `0x802635B4..0x802635C8` | `ea491c77d0c5ac9bf94c2d408a9c09de66c618f6fc6ad653f847ffdef15afd9a` | `0x0080023C` proceeds to `0x0C00023E` butt-slide-stop. |
| Slide-kick slide | `0x2975C..0x29770` | `0x8026E75C..0x8026E770` | `58b072f45cd3550c58193dd9196b3b49630e7b42dc20cc2368f50dcfb84bb66b` | `0x018008AA` proceeds to `0x0080045A`. |
| Slide-kick stop | `0x233B4..0x233C8` | `0x802683B4..0x802683C8` | `b104840d1c049d19434ce0ed1e5fc25e4f81df3e891e2bad30806279a07a1d72` | `0x0080045A` stop path writes `0x08000225`. |

These byte-window hashes are over the original ROM bytes at the listed offsets, not source files. The independently verified ROM-to-PC linear mapping is valid only over offsets `0x1000..0xE855F`. Crouch and crawl action IDs are corroborated by clean runtime transitions, but this dossier does not claim separate exact-ROM handler windows for those stationary/crawl transitions.

## Reproduction and limits

Use the scripts from 64-bit Windows PowerShell in a clean isolated Project64 clone with the exact ROM copy and N-Rage configuration. First locate the current process-specific RDRAM base with the already-published [issue #41 read-only locator](mario64-issue41-find-rdram-base.ps1), then launch the bounded #40 capture with those validated candidate fields. This compact example shows the actual parameter names and how the continuous fast slide-kick chord is sent; replace only the two example paths and use the freshly observed PID/HWND. The capture runs as a separate read-only job while the input call stays in the current PowerShell process.

```powershell
$projectRoot = 'C:\path\to\MarioCraft'
$romCopy = 'C:\path\to\MarioUSA-local-analysis-copy.z64'
$exe = Join-Path $projectRoot 'tools\issue11-local\pj64-portable\Project64.exe'
$pid64 = 26696 # replace with the current Project64 clone PID
$hwnd = 13962904 # replace with its current MainWindowHandle
$locator = Join-Path $projectRoot 'docs\research\mario64-issue41-find-rdram-base.ps1'
$probe = & $locator -TargetPid $pid64 -Project64Path $exe -AnalysisRomPath $romCopy | ConvertFrom-Json
$valid = @($probe.scan.candidates | Where-Object { $_.fieldsValid -and $_.romPrefixMatch })
if ($valid.Count -ne 1) { throw "Expected one validated RDRAM candidate; found $($valid.Count)." }
$trial = 'issue40-slidekick-fast-example'
$capture = Join-Path $projectRoot 'docs\research\mario64-issue40-external-capture.ps1'
$captureJob = Start-Job -ArgumentList $capture,$trial,$pid64,$hwnd,$valid[0].hostBase,$projectRoot,$romCopy -ScriptBlock {
    param($script,$id,$processId,$windowHandle,$base,$root,$rom)
    & $script -TrialId $id -TargetPid $processId -TargetHwnd $windowHandle -HostBaseHex $base -ProjectRoot $root -AnalysisRomPath $rom -Limit 7200 -PollMs 5
}
$out = Join-Path $projectRoot "tools\issue11-local\pj64-portable\$trial.jsonl"
$progress = Join-Path $projectRoot "tools\issue11-local\pj64-portable\$trial.progress.jsonl"
$stop = Join-Path $projectRoot "tools\issue11-local\pj64-portable\$trial.stop"
$readyBy = [DateTime]::UtcNow.AddSeconds(10)
while (-not (Test-Path -LiteralPath $progress)) {
    if ($captureJob.State -in @('Failed','Completed','Stopped')) { throw "Capture job ended before progress appeared: $($captureJob.State)." }
    if ([DateTime]::UtcNow -gt $readyBy) { throw 'Capture progress file did not appear within 10 seconds.' }
    Start-Sleep -Milliseconds 25
}
$input = Join-Path $projectRoot 'docs\research\mario64-issue40-project64-input.ps1'
& $input -ExpectedPid $pid64 -ExpectedHwnd $hwnd -TargetInstance Issue11Portable -Key AnalogUp -AdditionalKeys Z -AdditionalDelayMs 800 -ThirdKey B -ThirdDelayMs 900 -HoldMs 1000
# Wait until the selected endpoint is visible in the progress file, then stop and flush:
Read-Host 'After observing the settled endpoint, press Enter to stop and flush this capture'
New-Item -ItemType File -Path $stop | Out-Null
Receive-Job -Job $captureJob -Wait -AutoRemoveJob
```

For routine trials first restore F7 to the verified baseline with the sampler stopped, send the wake pulse (`AnalogUp`, 30 ms), then settle 2 seconds before arming capture. Never press F7 during capture. The accepted representative recipes were: stationary crouch, `Z` 350 ms; crawl, `Z+AnalogUp` 900 ms; moving Z-slide, `AnalogUp` 800 ms, release, then `Z+AnalogUp` 350 ms (the second call creates a new Up edge); fast slide kick, one continuous 1,000 ms call, `AnalogUp` at start, `Z` at +800 ms, `B` at +900 ms; slow gate control, one continuous 600 ms call, `AnalogUp` at start, `Z` at +250 ms, `B` at +500 ms; grounded dive, one continuous 1,650 ms call, `AnalogUp` at start, `B` at +1,500 ms; ground pound, `A+AnalogUp` 250 ms, release, then fresh `Z+AnalogUp` 150 ms. For the dive and ground-pound trials, the sampler was armed before starting the sequence. These durations are one reproducible successful input recipe per trial, not universal timing guarantees. The helper’s maximum active hold is 3 seconds and its key whitelist is intentionally narrow. Captures are bounded at 7,200 rows and stop markers are written only after the selected endpoint is reached.

This is a representative behavior dossier, not a frame-accurate timing specification or an exhaustive surface/action matrix. Host polling, counter pairing and game loop scheduling constrain timing precision. Crawl is documented for one flat baseline and moving Z-slide for one controlled sequence; the slide-kick control brackets the observed 10-unit gate only once per side. The grounded dive endpoint uses a type-41 rising slope. No dedicated A/B rollout-from-dive-slide trial was completed for this package, so those exits remain untested here. Other untested branches include water/quicksand, moving platforms, edge falls, wall interactions, and alternate controller hardware/configurations. Failed or drift-origin attempts are excluded from positive claims. No raw RAM, ROM, Project64 save-state, or game assets are included.




