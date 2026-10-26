# Mario 64 issue 56 — interaction and pickup evidence (partial)

**Status: reviewed partial evidence.** This is a partial report, not issue completion. The generic zero-star door path has controlled runtime evidence paired with target-ROM code/data analysis. Coin healing and 1-up effects remain unobserved in a controlled collection. This report does not satisfy issue 56’s pickup acceptance criteria, and the issue should remain open.

## Target and method

The target was the USA revision 0 ROM (8 MiB; SHA-256 `17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91`) in Project64. The tested executable was the portable Project64 build with SHA-256 `C257C662D4E0B2CE1732B7F4A7D5DD4682F604CB082BBDBFCE5181470D6DBFB8`. Runtime inspection used bounded read-only process-memory sampling and the authorized input helper. No guest memory writes, cheats, ROM-derived assets, raw memory dumps, save files, screenshots, or state files are included here.

The capture used bracketed main-loop counter reads and retained accepted scalar rows only. These reads establish a stable sampled counter around each memory read; they are not atomic snapshots of game state. The counter is a main-loop count, not a physics-frame count. The door trace contains 256 rows, 255 accepted; its SHA-256 is `5AB67E85F0C2B9DC529411D18FF71DBE7F9B6952000F9778EEA6D62962124BA0`. The sampler version SHA-256 is `9E4566D55B1E8395F54D2CB176E1CDFF8CD7FFA0FAEE2A9620BD12A3B3554342`. A sanitized five-row excerpt is in [mario64-issue56-door-scalar-partial.json](mario64-issue56-door-scalar-partial.json). It excludes process and RAM addresses.

## Shared interaction path

Static analysis of the target ROM shows the coin interaction callback at guest address `0x8024DB2C` (ROM offset `0x8B2C`). For the selected coin path, the callback adds the object’s signed value at `+0x180` to Mario’s signed coin count at `+0xA8`, queues healing ticks by adding four times that value to Mario’s heal counter at `+0xB3`, and sets object interaction status at `+0x134` to `0x8000`. The consumer at `0x802F2F2C` handles that status, dispatches the effect, and clears the object’s active state. These are code-analysis results, not observed effects in the current run.

The selected red coin hitbox-data record at guest `0x80332794` has selector `0x10`, value `2`, radius `100`, height `64`, and no direct health or loot amount. The predicted counter effect for this record is +2 coins and eight healing ticks. The health update applies 64 units per tick and caps at `0x880` (2,176). With a settled pre-pickup health value of 1,152, the predicted maximum is 1,664 if no other state changes occur. **This prediction has not been confirmed by collecting the coin.**

The one-up behavior loop at `0x802F45B8` checks the one-up object’s own collision list through helper `0x802A1424`; on a Mario match it plays its sound, increments Mario’s signed lives byte at `+0xAD`, and clears object active flags at `+0x74`. This is static analysis only. No live life gain or one-up removal was observed.

For a normal generic door, selector `4` dispatches through `0x8024E420` (ROM offset `0x9420`). The zero-star door uses required-star count zero and ordinary subtype zero. Native code accepts Mario walking/decelerating actions `0x04000440` or `0x0400044A`; it does not require a jump or an A press. On activation Mario enters action `0x1320` (pull) or `0x1321` (push), while the door enters an opening action and later returns to idle. Door status values map through data at `0x80330260`. Star and warp progression branches are outside this ticket and belong to issue 15.

## Controlled door observation

The accepted trace was captured continuously before approach through the door’s reset to idle. The scene level was 6; the v5 field at `0x8032DDF4` is a save-file number, not a course identifier, and is not used here as a course claim.

At sequence 18 / loop 257301, Mario was walking (`0x04000440`) at `(-3012.6162, 205, -788.1448)`. The nearby object matched the generic door selector and zero-star/ordinary subtype gates; its door action was idle. At sequence 19 / loop 257307, Mario was in push action `0x1321`, the door was in action `2` with timer `6`, and the door appeared in Mario’s collision list. Mario’s push action continued through sequence 26 while he moved to x `-3423`. By sequence 31 / loop 257377 Mario had returned to idle while the door remained in action `2`; by sequence 32 / loop 257381 the door had returned to action `0` and timer `0`. The sequence supports one generic push-door activation and completion. It does not claim a sampled transient interaction-status flag.

## Pickup runtime attempt and limits

In a later BOB runtime capture, Mario was at `(-2446.4414, 0, 2097.4053)`, health `1,152`, lives `2`, coins `0`, and hurt/heal counters `0`. The red coin and one-up target instances were still active in the final valid sample. A guarded movement pulse ended with no meaningful displacement; the monitored nearby behavior entered the hard stop radius. No pickup contact, counter change, healing, life gain, or removal was recorded. Runtime navigation is deferred here rather than reporting a blocked movement attempt as a game effect.

An earlier capture gap coincided with a health decrease; its cause is unknown. No source family or environmental cause is attributed to that change. The current 1,152 health baseline is therefore reported as observed state, not as a controlled damage outcome.

## Bounded inventory and handoff

Evidence in this report covers the coin selector `0x10`, water-ring selector `0x10000` (healing/status queue path, not a live trial), one-up behavior, and generic door selector `4`. Recovery-heart behavior remains unverified. This is not a complete inventory of all interactive objects. Keys, caps, cannons, warps, star thresholds, course/catalog data, and progression consumers are explicitly handed off to issue 15.

## Acceptance status

1. Shared interaction gating, status/effect dispatch, and removal: **static evidence present** for the selected coin and one-up paths; live coin dispatch unverified.
2. Coin/healing numeric outcome and one-up life gain: **not met**; values above are static predictions only.
3. One generic door/switch path: **met for a zero-star generic push door**, with continuous runtime trace and static callback/data pairing.
4. Controlled collection/activation observations paired with code/data: **partial**; door is paired, pickup collection is not.
5. Bounded inventory and issue 15 handoff: **partial inventory only**, with out-of-scope progression explicitly handed off.
6. ROM/Project64 identity, sanitized scalar evidence, and unknowns: **present** in this report; raw/private captures are excluded.
7. Safe scalar/report files under `docs/research`: **reviewed partial files published**; remaining runtime acceptance checks are open.

Reviewable numeric native references are published in [mario64-issue56-native-evidence-partial.json](mario64-issue56-native-evidence-partial.json): 24 function envelopes, two data windows, and three unique 128-byte live behavior-to-ROM matches, each with address/offset, length, and SHA-256. These metadata anchors contain no ROM bytes.

The decompilation source is correspondence context only; target-ROM disassembly and runtime observations above are the direct evidence. Relevant primary-source files: [interaction.c](https://github.com/n64decomp/sm64/blob/master/src/game/interaction.c), [coin.inc.c](https://github.com/n64decomp/sm64/blob/master/src/game/behaviors/coin.inc.c), [red_coin.inc.c](https://github.com/n64decomp/sm64/blob/master/src/game/behaviors/red_coin.inc.c), [mushroom_1up.inc.c](https://github.com/n64decomp/sm64/blob/master/src/game/behaviors/mushroom_1up.inc.c), [door.inc.c](https://github.com/n64decomp/sm64/blob/master/src/game/behaviors/door.inc.c), [water_ring.inc.c](https://github.com/n64decomp/sm64/blob/master/src/game/behaviors/water_ring.inc.c), and [mario.c](https://github.com/n64decomp/sm64/blob/master/src/game/mario.c).




