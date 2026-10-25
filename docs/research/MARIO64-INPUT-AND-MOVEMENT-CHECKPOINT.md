# Mario 64 input, action, and movement research checkpoint

Status: **partial evidence; issue #11 remains open.** This report records only the input and movement evidence verified so far. No issue #11 acceptance criterion is marked complete.

## Test identity and method

The target is the USA revision 0 cartridge, 8 MiB, SHA-256 `17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91`, previously validated in [the issue #10 tooling report](MARIO64-ROM-TOOLING-AND-SCOPE.md). Static addresses below use the verified matching prefix mapping ROM offset `0x1000` → RDRAM `0x80246000`; the mapping is only valid through ROM offset `0xE855F`, before the documented first mismatch.

Runtime captures used the isolated Project64 portable clone, version `3.0.1.5664-2df3434`, executable SHA-256 `C257C662D4E0B2CE1732B7F4A7D5DD4682F604CB082BBDBFCE5181470D6DBFB8`. Its old API source is pinned to Project64 commit `2df34341d95d28c536f78ea8174a8fdda066e9cf`; the working sampler uses only `events.ondraw`, `mem.*`, `Date.now()`, and `fs.writeFile` with the API-supported `new Buffer(length)`. A write-hook experiment on this build crashed, so no write hooks are used. The executable was configured for interpreter CPU and pause-on-inactive disabled. Exact ROM, emulator, script, and save-state identities are recorded in local ignored artifacts; no ROM, RAM dump, or state archive is published.

The sampler captures 3,600 draw-callback records over approximately 60 seconds. Every record brackets reads with the candidate word at `0x8032D5D4` and retains the row only as a bounded sample. Draw callbacks are not simulation ticks. The candidate word advanced by 1,799 between 5,607 and 7,406 across 60.082 host seconds (1,800 distinct observed counter values); static code shows a recurring increment after queue waits and a buffer-swap call, but this does not yet prove the value is the gameplay simulation clock. Position, velocity, action, and controller fields are read from candidate Mario/controller structures; fields confirmed below are identified as such, while the remaining Mario offsets await independent static/runtime validation.

The baseline is local `issue35-mario-gameplay-baseline.pj.zip`, SHA-256 `6E2C8265A8A9A19761BCC6CE29FA20694FAD5444B325B1F22CA987776A2D6DFE`. It restores a courtyard position candidate `[-1328, 260, 4354]`, four lives, zero stars. A new restore was visually checked before batch 04. The Project64-only guarded helper logs key-down/up UTC timestamps, exact executable identity, PID/HWND, focus validation, and hold duration. Helper log and full traces remain under ignored `tools/issue11-local/`.

## Input sampling confirmed in target ROM

The exact-ROM routine at ROM `0x3638..0x3700` (runtime `0x80248638..0x80248700`) walks controller records at stride `0x1C`. It loads the controller-data pointer from controller offset `+0x18`, copies signed raw axis bytes from data offsets `+2` and `+3` to controller halfwords `+0` and `+2`, and stores them as the raw X/Y candidates. It computes newly pressed buttons as `(current & (current ^ old))`, stores that halfword at controller `+0x12`, and stores current buttons at `+0x10`. This matches runtime captures: helper A gives down and pressed mask `0x8000`; helper Z gives `0x2000`; helper B gives `0x4000`.

The exact-ROM adjustment routine at ROM `0x3304..0x3490` (runtime `0x80248304..0x80248490`) resets processed X/Y and magnitude to zero. For each raw axis, the branch comparisons make values from -7 through +7 zero; values at or below -8 receive +6, and values at or above +8 receive -6. The resulting components are converted to floats. The routine computes Euclidean magnitude, and if magnitude exceeds `64.0`, scales both components by `64/magnitude` and stores magnitude 64. Runtime keyboard-stick captures confirmed the endpoints: raw X/Y ±127 produce processed component approximately ±63.999996 and magnitude 64; releasing returns all three to zero.

This proves the static deadzone transform and radial cap, but dynamic raw values between -8 and +8, intermediate magnitudes, diagonal vectors, and physical-controller sampling have not yet been captured. Keyboard emulation currently supplies only endpoint values.

## Reproducible runtime movement observations — batch 04

Batch 04 is local `issue11-movement-batch-04.jsonl` (3,600 rows, 60.082 s; sampler script is `issue11_movement_batch_04.js`). It began from the saved courtyard state and used this guarded input order; all holds were accepted by the helper:

| UTC start | Input | Hold |
| --- | --- | ---: |
| 2026-10-25 00:50:28.6909028Z | AnalogUp | 700 ms |
| 2026-10-25 00:50:29.9831208Z | AnalogUp | 700 ms |
| 2026-10-25 00:50:30.9989239Z | A | 160 ms |
| 2026-10-25 00:50:31.5114596Z | Z | 160 ms |
| 2026-10-25 00:50:33.7018766Z | Z | 500 ms |
| 2026-10-25 00:50:34.6617960Z | AnalogUp + Z | 700 ms |
| 2026-10-25 00:50:35.7156709Z | AnalogUp | 700 ms |
| 2026-10-25 00:50:36.7169687Z | AnalogUp + A + Z | 160 ms |
| 2026-10-25 00:50:38.7231574Z | AnalogUp | 700 ms |
| 2026-10-25 00:50:39.6761433Z | B | 160 ms |
| 2026-10-25 00:50:41.3748041Z | B | 160 ms |

Host-aligned to the capture start (`1792889425392` Unix ms), the first held direction was observed at row 200 / 3.340 s / candidate counter 5,707. Action candidate changed `0x0c000203` → `0x0c000204`; by row 240 / 4.003 s it changed to `0x04000440`, with forward-speed candidate 8.91 and Z position 4,345.09. At row 320 / 5.341 s the speed candidate was 19.32 and Z was 3,922.98. After release, action candidate `0x0c00023d` appeared at row 330 / 5.503 s and sampled speed was zero. A later held direction produced a maximum sampled forward-speed candidate of 23.323; exact speed units and simulation-tick duration remain uncalibrated.

At row 340 / 5.671 s, A had raw down and pressed mask `0x8000`, Mario input candidate `0x82`, and action candidate `0x03000880`. The vertical-position candidate rose from 260 to 302, then reached 467.5 before returning to 260 by row 406 / 6.771 s. The ROM branch at ROM `0x22790..0x227B8` tests the input halfword at argument offset `+2` for bit `0x0002`, then passes `0x03000880` to runtime routine `0x802530A0`, corroborating the key-trigger/action relation. The observed A-triggered vertical arc is consistent with a jump; the full action dispatch chain and jump constants are still untraced.

Z presses reported raw down `0x2000`. The observed action candidate changed to `0x008008a9` at row 370 / 6.171 s and to `0x0080023c` at row 406 / 6.771 s. The same action sequence occurred during a later combined input. The exact-ROM caller at ROM `0x26C18..0x26C88` (runtime `0x8026BC18..0x8026BC88`) loads the input halfword at argument offset `+2`, tests bit `0x8000`, and passes action word `0x008008A9` to the action setter at `0x80253178`; a neighboring branch tests bit `0x2000` and passes `0x830008AB`. Two parallel blocks at ROM `0x26CC0..0x26D30` and `0x26DDC..0x26E3C` repeat the `0x8000` → `0x008008A9` assignment. The captured Z event has raw controller mask `0x2000` and Mario-input candidate high bits `0xC000`, which includes the statically tested `0x8000`; its sampled action transitions to `0x008008A9`. This supports a Z-triggered action transition in this airborne context. The wrapper at ROM `0xE178..0xE1A4` (runtime `0x80253178..0x802531A4`) calls the common action routine at runtime `0x80252CF4`. That common routine’s exact ROM body at `0xDCF4..0xDE58` (runtime `0x80252CF4..0x80252E58`) stores the prior action from state `+0x0C` into `+0x10`, writes the new action at `+0x0C`, stores the argument at `+0x1C`, and clears action state/timer at `+0x18/+0x1A`. This proves the passed word is a current-action transition. The public reconstruction default-branch header names `0x008008A9` `ACT_GROUND_POUND`; that label remains navigation-only until target-ROM action dispatch/callers are fully mapped, so this report keeps the numeric ID.

A later button event with raw mask `0x4000` coincided at row 858 / 14.333 s with action candidate `0x00800380`, action-state candidate 1, and position change from `[-1328, 311.32, 3234.97]` to `[-1328, 313.54, 3225.27]`. This is a B-key event and an observed action transition, not yet a named behavior. Batch ended with position candidate `[-1328, 325.18, 3178.71]`; the intervening state included multiple inputs and cannot be assigned to one action.

The raw traces include `previousActionCandidate`, action state/timer candidates, button edges, intended magnitude/yaw, candidate positions and velocities, and both counter reads. Candidate fields other than the controller fields above have not all been proven structurally or temporally. Counter before/after equality is a read guard, not an atomic snapshot guarantee.

The sanitized, numeric-only trace is [batch 04 JSONL](mario64-issue11-trial04-sanitized.jsonl), SHA-256 `bf6caf471b97e2a3af9b4ac32e0127a6dd2769db75fc5256e693018c491d1610`. It contains 45 selected numeric samples from the 3,600-row draw-callback capture at movement/action transition points, plus 11 input-event records. No process IDs, window handles, local paths, ROM bytes, or raw memory dumps are included.

## Reproducibility hashes and commands

The local source ROM used by the disassembly script is `C:\Users\brant\Downloads\Super Mario 64\Super Mario 64 (USA).z64`, 8,388,608 bytes, SHA-256 `17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91`. The MIPS listing can be regenerated with `python MarioCraft/tools/issue11-local/disasm_range.py 0x3304 0x3490` (and the corresponding bounds below); the script reads the original ROM and reports offset, big-endian word, mapped runtime PC, and decoded common instruction. Range SHA-256 values are over exact half-open ROM byte intervals:

| ROM interval | Purpose | Range SHA-256 |
| --- | --- | --- |
| `0x3304..0x3490` | input axis deadzone, adjustment, magnitude and cap | `838339138778394a85c54ba7d81bb34013cc6df45f036a84cafd0c9994f56108` |
| `0x3638..0x3700` | controller data reads and pressed-edge mask | `d98898002959a7bfdce08cea2b91553b0c945e2e7c28c3057f52a540ec358980` |
| `0x22790..0x227B8` | input-bit test and action assignment | `817d3563820c779728f1dd8ec711f9bff9823505a5c744910563db2f5f4cfeb9` |
| `0x31B8..0x31C4` | candidate recurring counter increment | `0277ef4a1781e84723d13f7aa41548528a1443605e3a40eefe43c8ea97a9466e` |
| `0x26C18..0x26C88` | input-bit test and action-word assignment | `fb4a8ebbde4b69c37446740a4e9bc794c3985e198f5afed23f2f121a7ba02a6f` |
| `0x26CC0..0x26D30` | repeated input-bit test/action assignment | `5d1ed162e6419af531de6cc97a0756fb050783fe009f4342752e06ab5e617ee7` |
| `0x26DDC..0x26E3C` | repeated input-bit test/action assignment | `041d5dcb216a0d1f80cbef6c49b7eb0c1646630daec996f31d80a904290356ff` |
| `0xDCF4..0xDE58` | common action state update routine | `1e210f62f404d4be0e0cd008271c03d47d1166bdf85fe25d50b0b3242918f0bc` |
| `0xE178..0xE1A4` | wrapper calling common action update | `0fbb84f63084069aa3a1d5128c4b505a5ae079c34df053136535da7310758837` |

The batch 04 sampler script SHA-256 is `2CA9EE1B7D0CE717B54A2D1DA5D9B8C01876DE740FE41B8EFD4255F19514DFD2`; its local trace SHA-256 is `C2F81E4C3E6963891643B14A7D0F2C1CCB12FA76EC01C1641DE9E706A337FA33`. The Project64 input helper SHA-256 is `2F6D443902FE626CB12B1799BBEDA562275A235D8DCEE043CC940154E1374DC3`. These hashes identify reproductions without publishing raw game data.

## Static timing candidate

The ROM code at offset `0x31B8..0x31C4` (runtime `0x802481B8..0x802481C4`) loads candidate counter `0x8032D5D4`, increments by one, and stores it after two calls to a blocking queue receive routine; another queue receive and a framebuffer-address call occur in the surrounding loop. A separate candidate increment sequence occurs at ROM `0x2FB0..0x2FC0`, but its full call flow has not been established; a post-store execution breakpoint at `0x80247FC4` received no hits during its bounded run. Neither site is yet proven to be the active runtime writer. The recurring loop site is a static candidate only, and no claim of exact gameplay-frame timing is made. Host timestamps plus the counter can bound input/capture order, not establish N64 frame duration.

## Gaps required before issue completion

- Capture diagonals, both directions, release and reversal from restored states, including positions and speed history; isolate acceleration, turning and braking.
- Calibrate jump takeoff, hold/release effects, apex, landing, and air control with repeated baselines. Trace action transitions in target ROM before naming action IDs.
- Measure crouch, slide, dive, ground pound, long jump, backflip, side jump, wall kick, triple jump, and relevant combinations from safe repeated seeds. No named Z/B behavior is accepted from the current trace alone.
- Obtain intermediate stick values and a sweep around the deadzone, including diagonal radial normalization; identify a legitimate physical/controller or emulator method that supplies reproducible non-endpoint values.
- Inventory all sampled controller bits and action state/transition paths. Corroborate every retained structure offset and movement constant in the USA ROM. Separate supported constants from unresolved branches.
- Validate the recurring counter’s relation to gameplay update/cadence; until then preserve host timing and candidate counter as separate columns.
- Produce sanitized numeric traces with provenance and a human-readable behavior specification for each verified move. The complete raw trace and helper log remain local; the published 45-row subset preserves key transition points and the measured input order.

Do not check any issue #11 acceptance criteria yet. The current artifacts are useful partial evidence only. Issue #11 remains open while its bounded gaps are worked and linked.


