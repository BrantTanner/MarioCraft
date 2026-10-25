# Project64 safe runtime sampler checkpoint for issue #35

Status: bounded read-only capture and input correlation demonstrated; exact emulator-frame timing and gameplay field semantics remain unverified.

## Build and safety boundary

Tests used the isolated official Project64 3.0.1.5664-2df3434 portable clone, executable SHA-256 `C257C662D4E0B2CE1732B7F4A7D5DD4682F604CB082BBDBFCE5181470D6DBFB8`. The ROM was Super Mario 64 (USA), 8 MiB, revision 0, SHA-256 `17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91`; the emulator opened the analysis copy and the original stayed untouched. The clone used `Force Interpreter CPU=1`, pause-on-inactive disabled, Project64 audio/input/RSP plugins `3.0.1.5664-2df3434`, and GLideN64 revision `c36ffa9`. Controller 1 was plugged in, bound to Keyboard, with A mapped to X; the analog directions were keyboard arrow keys. No full memory dump, ROM image, savestate, crash dump, process identifier, window handle, or machine path is included with this report or its sanitized sample file.

The sampler targets the pinned old-build API source at [Project64 `API.js`](https://github.com/project64/project64/blob/2df34341d95d28c536f78ea8174a8fdda066e9cf/Source/Project64/UserInterface/API.js), commit `2df34341d95d28c536f78ea8174a8fdda066e9cf`, SHA-256 `8f08e4abd103f432c72ce445c75bd51b6688f9ead33a91541d1cb0c39c8554af`. It uses `events.ondraw`, `events.remove`, `mem.u32`, `mem.u16`, `mem.float`, and `fs.writeFile(path, buffer)`. It reads candidate values in one callback, checks the candidate counter before and after each field group, caps each run at 1,200 callback invocations, writes the bounded JSONL once, and removes its callback at completion. The 1,200-row output completed in both repeated runs. Callback registration is bounded; this build's UI retained a stale “Running” label until the Stop button was clicked, but output did not continue after completion.

The event source is a graphics draw callback. `hostUnixMs` is the host wall clock sampled inside the callback. Draw callbacks are not emulated frames: observed intervals varied, duplicate rows occurred, and this experiment does not establish frame-exact timestamps. The counter at `0x8032D5D4` is a candidate runtime counter. Under the byte-verified mapping ROM offset `0x1000` → RDRAM `0x80246000`, static ROM offsets `0x2FB0`–`0x2FC0` map to runtime PC addresses `0x80247FB0`–`0x80247FC0` and contain a load/increment/store sequence targeting `0x8032D5D4`. It advanced by 599 during each roughly 20-second capture, but this does not prove a relationship to emulated frames or game time.

## Repeatable input trial

The baseline was created after starting a new file, dismissing the two introductory text prompts with A, and waiting until Mario stood idle in the courtyard with no dialog. F5 saved that state; F7 restored it before each trial. The screen showed Mario at the spawn point, lives ×4 and stars ×0. The first draw sample in both trials had candidate input `32`, action `0x0c000203`, action timer `0`, position `[-1328, 260, 4354]`, and velocity `[0, 0, 0]`. The visuals and candidate initial state matched between restores; the runtime counter differed by nine because the first callback occurred at a different host delay after loading. This is a restored baseline for a repeated timed-input test, not a guarantee of identical internal frame phase.

Each trial ran this guarded helper sequence against the exact Project64 clone: A held 160 ms; wait 2,000 ms; A held 160 ms; wait 400 ms; A held 160 ms. The helper checked the allowlisted executable hash, PID, HWND, foreground window, and title before input and during each hold; it released every key in `finally`. Each logged hold completed and emitted keyup.

Both 1,200-row captures ran for about 20.01 seconds. Candidate-counter reads matched before and after every row (1,200/1,200 per trial), and each counter advanced by 599. In trial 1, candidate Y rose from 260 to 524 and returned to 260; the first above-ground observation was at callback 638 and landing was observed at callback 712. In trial 2, candidate Y rose from 260 to 502 and returned to 260; ascent was first observed at callback 642 and landing at callback 684. X/Z stayed at `-1328`/`4354`. The first above-ground samples occurred 53 ms and 34 ms after the second helper keydown, respectively, using host timestamps. The measured peak and number of above-ground samples differed between runs, so these are timed host-input trials with correlated numeric response, not frame-identical replays. The paired counter reads detect only a counter change between those two reads; they do not prove that all fields were sampled atomically, and an intervening field change can go undetected if the candidate counter does not change.


## Reproduction steps

1. Launch the pinned portable clone with the configuration above and open the verified USA analysis copy. Select a new file, dismiss the two intro prompts with A, and wait for Mario to stand in the courtyard with no dialog.
2. Press F5 to create the local baseline state. Keep that state archive local; its tested copy was 1,387,126 bytes with SHA-256 `6E2C8265A8A9A19761BCC6CE29FA20694FAD5444B325B1F22CA987776A2D6DFE`.
3. Copy the sampler into the Project64 clone Scripts directory and set its local output path and `trialId`. Open Debugger → Scripts, select the sampler, and click Run. Return focus to the Project64 game window immediately. Run the existing helper with the current verified PID and main-window handle (the angle-bracket values below are placeholders):

   ```powershell
   & '.\\MarioCraft\\docs\\research\\project64-input.ps1' -ExpectedPid <current-pid> -ExpectedHwnd <current-game-hwnd> -TargetInstance Issue11Portable -Key A -HoldMs 160
   Start-Sleep -Milliseconds 2000
   & '.\\MarioCraft\\docs\\research\\project64-input.ps1' -ExpectedPid <current-pid> -ExpectedHwnd <current-game-hwnd> -TargetInstance Issue11Portable -Key A -HoldMs 160
   Start-Sleep -Milliseconds 400
   & '.\\MarioCraft\\docs\\research\\project64-input.ps1' -ExpectedPid <current-pid> -ExpectedHwnd <current-game-hwnd> -TargetInstance Issue11Portable -Key A -HoldMs 160
   ```

   The helper aborts unless the exact guarded Project64 instance is foreground.
4. Allow the 1,200 callback capture to complete (about 20 seconds). Confirm `issue35_capture_complete` reports 1,200 rows and a successful write. If the Scripts dialog still shows a stale Running indicator, click Stop after completion.
5. Press F7 to restore the baseline and repeat steps 3–4 with a new `trialId` and output path. Preserve only the sanitized JSONL with relative host timing; keep raw traces, the savestate, and emulator logs local.

## Candidate address map

| Candidate field | Address(es) | Read type |
| --- | --- | --- |
| Runtime counter | `0x8032D5D4` | `u32` before and after the row |
| Input word | `0x8033B172` (`Mario + 0x02`) | `u16` |
| Action word | `0x8033B17C` (`Mario + 0x0C`) | `u32` |
| Action timer | `0x8033B18A` (`Mario + 0x1A`) | `u16` |
| Position candidate X/Y/Z | `0x8033B1AC`, `0x8033B1B0`, `0x8033B1B4` (`Mario + 0x3C/+0x40/+0x44`) | `float` |
| Velocity candidate X/Y/Z | `0x8033B1B8`, `0x8033B1BC`, `0x8033B1C0` (`Mario + 0x48/+0x4C/+0x50`) | `float` |

The fields and addresses remain candidates: the sampled input word, action word, action timer, position, velocity, and counter were not fully mapped to ROM semantics. The position candidate is based at `0x8033B1AC`; the observed Y response is consistent with the visible jump, but no general structure layout or movement model is claimed. No values here establish a game-frame counter.

## Sanitized data

[`project64-runtime-sampler.js`](./project64-runtime-sampler.js) is the bounded read-only sampler. Before using it, set its `tracePath` to a local writable path, and set `trialId` for each run. [`project64-runtime-samples-sanitized.jsonl`](./project64-runtime-samples-sanitized.jsonl) contains the two repeated trials with timestamps relative to each capture start, helper keydown/up events on the same relative host-time axis, sparse baseline context, helper-edge samples, and one representative row per distinct candidate counter from the second press through landing. The full 1,200-row captures remain local. Raw traces and emulator files were kept out of the published artifacts.
