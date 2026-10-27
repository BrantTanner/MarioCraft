# Mario 64 warp destinations and transition state (#59)

**Static evidence covers the bounded declarations below; two course-transition observations are carried forward.** This report supports [#59](https://github.com/BrantTanner/MarioCraft/issues/59), which remains open. The target is USA revision 0, SHA-256 `17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91`.

## Finite destination inventory

The [warp inventory](MARIO64-ISSUE59-WARP-INVENTORY.json) accounts for 311 active USA source declarations: 258 ordinary warp nodes, 44 painting nodes, and 9 instant warps. All declared destination areas resolve in the source area inventory; all ordinary/painting destination node IDs have corresponding ordinary node declarations. This does not prove spawn-object availability or gameplay reachability.

Each declaration's complete encoded command matches the cartridge inside its course's independently bounded raw script bank. Of these, 187 commands match their source-corresponding entry positions, using #58's entry opcode/length correspondence; 124 local-array commands have a unique matching encoding within the appropriate bank. No command is unmatched or has multiple bank candidates. The local-array matches establish command-data correspondence, not a complete rebuilt script identity or observed execution.

The input inventory and bank bounds come from [#57](MARIO64-ISSUE57-COURSE-LEVEL-MANIFEST.json) and [#58](MARIO64-ISSUE58-ASSET-LOAD-MANIFEST.json). The pinned encodings are in [level_commands.h](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/include/level_commands.h): ordinary and painting commands are 8 bytes, instant commands 12 bytes. The helper exports offsets, lengths, hashes and interpreted numeric fields; it exports no command bytes.

Reproduce from the repository root:

```text
python tools/issue59-build-warp-inventory.py --course-manifest docs/research/MARIO64-ISSUE57-COURSE-LEVEL-MANIFEST.json --asset-manifest docs/research/MARIO64-ISSUE58-ASSET-LOAD-MANIFEST.json --rom <authorized-local-USA-ROM> --output <numeric-output.json>
```

The helper verifies ROM identity, source pins, complete bank hashes, level enums, entry counts and spans, and every compared warp operand. Matching digests bind the numeric evidence without publishing ROM contents.

## Engine state and native evidence

[Native envelope metadata](MARIO64-ISSUE59-WARP-NATIVE-MANIFEST.json) records 35 inspected function windows. Every window's complete canonical-ROM hash was checked through the already verified initial copy relation: PC `[0x80246000,0x8032D560)` corresponds to ROM `[0x1000,0xE8560)`, with offset `PC - 0x80245000`. This relation is not extended to other addresses. Source correspondence is pinned to [level_update.c](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/level_update.c) and [level_update.h](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/level_update.h).

| Guest field | Native/source interpretation |
| --- | --- |
| `0x8032DDF4`, signed 16-bit | Save-file number; it is not the course number. |
| `0x8032DDF8`, signed 16-bit | Current level number. |
| `0x8033BAC6`, signed 16-bit | Current course; `0x8024BE14` sets it using the level-to-course table. |
| `0x8033BAC8`, signed 16-bit | Current act; the credits-next path also changes it from a credits entry. |
| `0x8033B238`, signed 16-bit | Play mode: normal 0, paused 2, area transition 3, level transition 4, frame advance 5. |
| `0x8033B23C`, signed 16-bit; `0x8033B240`, pointer | Transition countdown and optional update callback. |
| `0x8033B248..24B`, four bytes; `0x8033B24C`, 32-bit | Warp destination type, level, area, node, then argument. Type 0 means none, 1 change-level, 2 change-area, 3 same-area. |
| `0x8033B252`, `254`, `256`, signed 16-bit; `0x8033B258`, 32-bit | Delayed operation, countdown, source-node ID, argument. |

These are code-established state roles, not values sampled in a #59 runtime trial. Level/course/act and save-slot identity remain separate dimensions.

## Trigger → state → result

| Family | Static gate and state change | Native result path and limit |
| --- | --- | --- |
| Destination selection | `0x8024A700`: node `>=0xF8` or a different level selects type 1; otherwise a different area selects 2; otherwise 3. Writes destination level/area/node/argument. | The pending destination is not itself a completed transition. |
| Ordinary warp door, object, teleport | `0x8024A9CC` operations 3/4/5 select source node from used-object parameter bits 16–23, countdown 20. Door also preserves Mario's action argument. | `0x8024AEDC` consumes the countdown, looks up the source node, strips destination-level checkpoint bit `0x80`, then calls destination selection and checkpoint handling. |
| Star return | Operation `0x11`: countdown 32, source `0xF0`, clears saved-course selection. | Destination follows that area's success-node declaration. Collection and no-exit variants are covered by the companion objective report. |
| Death and warp floor | Operation `0x12`: countdown 48, source `0xF1`; zero remaining lives changes the operation to game over `0x14`. Operation `0x13` uses source `0xF3`, countdown 20, falling back to death/game-over rules if absent. | The delayed executor reloads save state for game over and dispatches a special level return. This does not establish EEPROM transport semantics, owned by #16. |
| Painting | `0x8024A7B4` indexes floor types `0xD3..0xFF` as indices 0..44; the last three require height above floor `<80`. `0x8024A85C` rejects intangible Mario, checks the node, processes checkpoints, sets destination, transition countdown 74 and disappeared action `0x1300`. | A matched painting declaration is not proof Mario crossed its floor gate. The source stores 44 painting declarations; the engine's index range has 45 possible slots. |
| Instant warp | `0x8024A374` indexes floor types `0x1B..0x1E`, requires a nonzero instant record, adds signed displacement to Mario and his object, changes area, translates camera, preserves camera yaw. | Castle instant warps are bypassed once the total-star check reaches 70. This explains the bounded endless-stairs displacement path without observing it. |
| Pause exit | `0x8024B5D4`, normal non-debug exit branch: destination castle level 6, area 1, node `0x1F`, special fade/transition, clears saved-course selection. | The later #58 controlled pause-exit observation reached level 6/course 0 from level 9/course 1. The intermediate node and every transition update were not directly sampled. |
| Area and level loading | Normal loop `0x8024B3E4` dispatches area/instant updates, object/camera updates, painting and delayed warps, then selects a transition play mode. `0x8024A124` handles area/same-area pending state; `0x8024A18C` handles the destination level. | Post-warp `0x80249CD8` resolves a spawn node/object, copies spawn position/angles, initializes Mario/action and camera, and clears pending warp/delayed-op state. Node declarations alone do not prove a matching spawn object exists. |
| Credits | Operation `0x17` schedules the first credits entry using node `0xF8`; `0x18` advances entries and uses `0xF9` or final `0xFA`. `0x8024A1D8` chooses ending, credits, or waving Mario actions from these nodes. | Final credits-end operation `0x15` dispatches the special ending return. The ending/cake source branch is covered by the companion objective report; none is observed here. |

The painting gate's inclusive floor upper bound is `0xFF` because `0xD3 + 44 = 0xFF`; the source macro names do not substitute for this arithmetic.

## Coverage and deferred observations

The [#58 runtime evidence](MARIO64-ISSUE58-RUNTIME-EVIDENCE.json) records a guarded pause-exit sequence: initial BOB level 9/course 1/save 1, Start then AnalogDown then A with logged releases, and a resulting castle level 6/course 0/save 1. The samples bracket a real level change; they do not independently identify the intermediate node or exact allocation/free timing. Its BOB paused before-state counter was 2589567; brackets do not make the reads atomic or count physics frames.

A separate guarded normal-entry sequence sampled castle level 6/course 0 at approximately (-5171, 307, -223), main-loop counter 2618557 at 2026-10-27 02:08:21.497 UTC, then recorded a jump toward the painting and a later A selection. The following sample at 02:08:48.708 UTC, counter 2619354, was BOB level 9/course 1 at approximately (-6525, 0, 6431), play mode 3. The input and before/after samples support the bounded castle-to-BOB entry; they did not capture every transition stage, the exact floor node, or an uninterrupted object/segment lifetime. The source painting gate and declarations above explain the candidate path without converting those unobserved stages into measurements. The ROM and emulator identities match the [#58 runtime evidence](MARIO64-ISSUE58-RUNTIME-EVIDENCE.json) and [#56 procedure](MARIO64-ISSUE56-INTERACTION-EVIDENCE-PARTIAL.md); original recordings remain private.

These observations do not upgrade every row in the static inventory: its `runtimeStatus: unobserved` values denote individual declarations, not a claim that no transition family was sampled. Instant, door/object, star-return, death/return, and credits-family observations remain open where reachable, as do exact segment allocation-lifetime correlations. No main-loop count is treated as a physics frame, and no unrecorded health change is assigned a warp cause. #59 stays open.
