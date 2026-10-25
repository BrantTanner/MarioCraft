# Mario 64 USA wall-kick timing and runtime evidence (issue #41)

Runtime representatives cover a late wall kick from the soft-bonk window and a predictive early wall kick on the wall-hit frame. The raw captures and save states remain local; the published sample file contains only selected scalar rows and input-edge metadata.

## Identity and method

- Target: Super Mario 64 USA retail ROM, SHA-256 `17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91`. The original ROM was read-only; gameplay used its byte-identical local analysis copy.
- Runtime: Project64 3.0.1.5664-2df3434, executable SHA-256 `C257C662D4E0B2CE1732B7F4A7D5DD4682F604CB082BBDBFCE5181470D6DBFB8`; N-Rage controller plugin SHA-256 `8B06D0BC29DEC6233640400E15051B945923BB6283E290B40AF64016992E6C20`. Controller 1 was configured for Real N64 Range, stick range 100, deadzone 5. The active N-Rage keyboard mapping sends A through X and the Z trigger through Space; the issue-specific input helper maps logical Z to that Space scan code.
- The on-draw callback sampler repeatedly faulted in Project64's embedded script path. Runtime evidence here instead came from a bounded external **read-only** process-memory sampler (query/read access only). It checked the exact portable emulator process and executable hash, validated the ROM/RDRAM correspondence and Mario/controller/surface structures, and bracketed each row with the gameplay counter. Equal before/after counter values are retained as a consistency check, not treated as proof of an atomic snapshot. Host polling rows can duplicate a game counter.
- The input helper was restricted to the verified Project64 process/window, a small key whitelist, short bounded holds, foreground checks, and `finally` key release. The research tools did not write process memory or alter the ROM; ordinary emulation and loading a save state still update emulator memory as part of normal play.
- The reproducible local runway seed is `issue41-wide-platform-runway-x169-z2679-range20.pj.zip` (SHA-256 `F304656719C24ADE5F7E7F9F8CB9D0B44C917972E4AB5BB0D85358585F459C38`). Restoring it through Project64's Load State menu returns Mario to the broad flat platform at approximately X=169.68, Y=803, Z=−2679.37, floor type 0 and normal-Y 1.0. The save is a local-only artifact and is not included in this report package.

## Exact-ROM control flow

The address relation used here is `PC = 0x80245000 + ROM file offset`, for offsets `0x1000..0xE855F`. All ranges below were hashed from the exact USA ROM named above.

- `FUN_8026D988`, the airborne wall-hit handler, is ROM `[0x28988,0x28B54)` (460 bytes; SHA-256 `8FF2C234A346EC918B2B76D3728CF3A1E067619B5FA8F1418CF6CE14D3D4AF59`). It increments the action timer before the early A-pressed check. The post-increment timer must be less than 3 and A-pressed bit `0x0002` must be set. That path writes vertical speed 52, adds `0x8000` to facing yaw, then calls the common action setter at PC `0x80252CF4` for action `0x03000886`.
- The action dispatcher is ROM `[0x2AB04,0x2B104)` (1536 bytes; SHA-256 `E366613697BA39353876676AEB6C187F108610B8553D3241E2C32F1ECD30F680`). Its unique mapped JAL to `FUN_8026D988` is at ROM offset `0x2AF50` / PC `0x8026FF50`. The handler's return value is stored and returned by the dispatcher.
- The enclosing action-update caller is ROM `[0x0F830,0x0FB20)` (752 bytes; SHA-256 `8881CEDD4B56C6E555C21208D5BDC83C768E6955BBA75DB02A0175C5903FD790`). It calls the dispatcher at PC `0x80254970`; when the result is nonzero, its branch at PC `0x802549E8` returns to the action-selection path at PC `0x802548D0`. Thus a handler return can cause another action dispatch in the same update. In the no-A wall-hit path the handler's animation-helper result is not overwritten before return. The pinned source navigation note at [mario_actions_airborne.c, commit 9921382](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/mario_actions_airborne.c) explains the animation-result redispatch; that source is navigation evidence, while the retail-ROM ranges above establish the shipped control flow. This supports treating early timing as a narrow firsties opportunity, not as a guaranteed two-frame input window.
- The late soft-bonk gate at PC `0x8026D33C` is ROM `[0x2833C,0x283C4)` (136 bytes; SHA-256 `D119092ADE9BD5059F353F377DE08EBF75E849E8268EE4BE8E34286DCCCFDA00`). It requires A-pressed bit `0x0002`, a nonzero wall-kick timer byte at Mario+`0x2A`, and previous action `0x000008A7`; it adds `0x8000` to facing yaw and calls the common action setter at PC `0x80252CF4` for `0x03000886`.
- The common action setter at PC `0x80252CF4` is ROM `[0x0DCF4,0x0DE58)` (356 bytes; SHA-256 `1E210F62F404D4BE0E0CD008271C03D47D1166BDF85FE25D50B0B3242918F0BC`). Both A branches call it; its airborne group routes to transition setup PC `0x80252460`, ROM `[0x0D460,0x0D9E4)` (1412 bytes; SHA-256 `2F4D27121501375336334575B07ED17E1C42BDAD2DB3409C849318488711E84A`). For `0x03000886`, that setup writes vertical speed 62, enforces minimum forward speed 24, and clears the wall-kick timer. This ordering means the early branch's earlier 52 write is superseded by the shared transition setup before the runtime kick state.

## Runtime results

The compact sanitized rows and native key-edge events are in [MARIO64-ISSUE41-WALL-KICK-TRACE-SAMPLES.json](MARIO64-ISSUE41-WALL-KICK-TRACE-SAMPLES.json). The companion [evidence manifest](MARIO64-ISSUE41-WALL-KICK-EVIDENCE-MANIFEST.json) records hashes and byte lengths for the public report/scripts and local evidence files; full captures and save-state bytes are deliberately excluded.

**Late-window kick, trial 38.** After a normal-jump wall collision (`0x000008A7`) on a vertical surface with normal +X, Mario entered soft bonk `0x010208B6`. The timer counted down from 5. Native A keydown at 07:44:36.056Z fell between valid timer-3 (seq 823, counter 53847, 07:44:36.052Z) and timer-2 (seq 824, counter 53848, 07:44:36.067Z) rows. The first sampled A-pressed row was seq 826, counter 53849, already in action `0x03000886`, with previous action `0x010208B6`, timer 0, Y=1053.5015, and vY=+58. Sampled apex was seq 842/counter 53856 at Y=1284.0015; jump-land action `0x04000470` began at seq 874/counter 53872 and was stable at seq 881/counter 53875. Native A keydown-to-keyup was about 144 ms by host timestamps (longer than nominal script hold), not a game-frame duration.

**Early-window kick, trial 39.** From the runway seed, analog movement built speed before a normal jump (`0x03000880`) toward the +X-normal wall face. The driver selected the predictive A edge from its seq 1009/counter 53695 snapshot; the native A keydown occurred at 07:49:23.4616734Z before the expected contact step. The next rows showed wall-hit `0x000008A7` at seq 1011/counter 53696, previous action normal jump, wall pointer with normal +X; at seq 1012/counter 53697 A-pressed was `0x8000`, action `0x03000886`, previous action `0x000008A7`, Y=1080.9573, vY=+58. This is direct early-branch evidence, distinct from a kick after soft bonk. Sampled apex was seq 1026/counter 53704 at Y=1311.4573; jump-land action `0x04000470` began at seq 1044/counter 53721 and was stable at seq 1049/counter 53724. Native A keydown-to-keyup was about 105 ms by host timestamps.

These two peak heights are not a controlled comparison: the takeoff states, contact timing, and steering/release timing differed. They establish the two transition paths, not which path produces a higher jump under matched conditions. A complete contact-only control (trial 36, 1,033 host polls from sequence 0 through 1032) shows a wall hit at seq 549/counter 53616 and soft bonk at seq 550/counter 53617 without another A after the initial jump press. Selected rows and key events are included in the compact JSON; the full control trace remains local. An earlier reactive late attempt delivered A one to two counters after the useful event; that miss motivated sending the native A keydown before logging or other per-row work. It is excluded from the successful representatives.

## Reproduction outline

1. The legitimate seed path began with the original courtyard save `issue35-mario-gameplay-baseline.pj.zip` (1,387,126 bytes; SHA-256 `6E2C8265A8A9A19761BCC6CE29FA20694FAD5444B325B1F22CA987776A2D6DFE`). Ordinary game movement through the castle-grounds route and Lakitu bridge tutorial reached the lower Y=803 platform. The lower-platform archive `issue41-lower-platform-y803-z2670.pj.zip` is 1,434,741 bytes, SHA-256 `E3AF591D10EE312438A88F57351ADA6CB35D1B0A8BE80128B38D7242429DE616`. Further ordinary movement along that platform produced the runway archive at X=169.68, Y=803, Z=−2679.37: `issue41-wide-platform-runway-x169-z2679-range20.pj.zip`, 1,413,017 bytes, SHA-256 `F304656719C24ADE5F7E7F9F8CB9D0B44C917972E4AB5BB0D85358585F459C38`. Each state was made through Project64 F5 after normal movement. No state bytes were written to RAM; archives stay local.
2. Keep the original ROM unchanged. Use the verified Project64/N-Rage identity above, restore the runway save through Project64’s Load State menu, and confirm X/Y/Z plus flat floor type/normal before capture. Ignore title/attract input and do not infer a valid seed from it.
3. The report directory contains portable, parameterized [RDRAM locator](mario64-issue41-find-rdram-base.ps1), [late and early samplers](mario64-issue41-external-capture-38.ps1), [early driver](mario64-issue41-early-wall-kick-39.ps1), and [late driver](mario64-issue41-late-wall-kick-38.ps1), plus the shared guarded [input helper](issue39-project64-input.ps1). They take explicit executable, ROM, output directory, PID, HWND, profile, and process-specific base arguments; the sampler opens the exact process with query/read access only. The scanner can show decoy signature matches: accept exactly one candidate whose `fieldsValid` is true, guest pointers are valid RDRAM, and X/Y/Z match the restored seed. Never reuse the host base after restarting Project64.
4. Example PowerShell setup from `docs/research` (choose a new empty `$trace` for each attempt):

   ```powershell
   $exe = (Resolve-Path 'C:\path\to\Project64.exe').Path
   $rom = (Resolve-Path 'C:\path\to\Super Mario 64 (USA).z64').Path
   $game = Get-Process -Name Project64 | Where-Object { $_.Path -eq $exe } | Select-Object -First 1
   $targetPid = $game.Id
   $targetHwnd = [long]$game.MainWindowHandle
   # Restore the archived runway seed in Project64, then check its XYZ/floor telemetry.
   $scan = .\mario64-issue41-find-rdram-base.ps1 -TargetPid $targetPid -Project64Path $exe -AnalysisRomPath $rom | ConvertFrom-Json
   $valid = @($scan.candidates | Where-Object { $_.fieldsValid -and [math]::Abs($_.x-169.67961) -lt 5 -and [math]::Abs($_.y-803) -lt 5 -and [math]::Abs($_.z+2679.3728) -lt 5 })
   if ($valid.Count -ne 1) { throw 'No unique RDRAM candidate matches the restored seed.' }
   $base = $valid[0].hostBase
   $trace = Join-Path $env:TEMP 'mario64-issue41-run-unique'
   # These background invocations assume Project64 is already foreground; hidden PowerShell alone does not activate it.
   # Keep the emulator foreground while the sampler and guarded driver run.
   $pwsh = Join-Path $PSHOME 'pwsh.exe'
   $sampler = Join-Path $PWD 'mario64-issue41-external-capture-39.ps1'
   $capArgs = '-NoProfile -File "{0}" -TargetPid {1} -TargetHwnd {2} -HostBaseHex {3} -Project64Path "{4}" -AnalysisRomPath "{5}" -TraceDirectory "{6}" -StickRange 100' -f $sampler,$targetPid,$targetHwnd,$base,$exe,$rom,$trace
   Start-Process -WindowStyle Hidden -FilePath $pwsh -ArgumentList $capArgs
   $progress = Join-Path $trace 'issue41-early-wall-kick-39.progress.jsonl'
   $deadline = [datetime]::UtcNow.AddSeconds(10)
   while (-not (Test-Path $progress) -and [datetime]::UtcNow -lt $deadline) { Start-Sleep -Milliseconds 50 }
   if (-not (Test-Path $progress)) { throw 'Sampler did not create a fresh progress file.' }
   $driver = Join-Path $PWD 'mario64-issue41-early-wall-kick-39.ps1'
   $driverArgs = '-NoProfile -File "{0}" -ExpectedPid {1} -ExpectedHwnd {2} -Project64Path "{3}" -TraceDirectory "{4}"' -f $driver,$targetPid,$targetHwnd,$exe,$trace
   Start-Process -WindowStyle Hidden -FilePath $pwsh -ArgumentList $driverArgs
   ```

   For the late route, substitute `mario64-issue41-external-capture-38.ps1` and `mario64-issue41-late-wall-kick-38.ps1`, and wait on `issue41-late-wall-kick-38.progress.jsonl`. Both drivers enforce exact process/window/foreground checks, a small scan-code whitelist, one additional A edge, bounded position/time, and final key-up. The sampler verifies configured N-Rage settings and records action, previous action, timers, position, velocity, controller masks, floor/wall fields, and bracketed gameplay-counter values. Keep full captures local; use the selected JSON rows for review.

## Limits and artifacts

- These are representative runtime trials, not a statistical timing sweep. They establish one direct early and one direct late route; they do not quantify every possible input delay or compare arc heights under matched takeoffs.
- The global counter is a gameplay timing aid, not a universal physics-frame clock. Host sampler polls can repeat a counter, and equal counter brackets do not guarantee an atomic multi-field read.
- Embedded Project64 callback sampling produced recurrent host memory exceptions in this setup. The external read-only sampler was used successfully; the underlying embedded callback fault cause is not established.
- Complete RAM traces, helper logs, ROM bytes, geometry exports, and save-state contents remain local. The report's compact JSON contains selected scalar snapshots and native edge metadata only.

