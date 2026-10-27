# Mario 64 issue 56 - interaction and pickup evidence

**Status: bounded interaction evidence.** Controlled samples cover a yellow coin and its observed healing response, a hidden one-up life gain, and a zero-star generic door. Unobserved categories and the remaining queued healing are identified below.

## Target and method

The target was the USA revision 0 ROM (8 MiB; SHA-256 `17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91`) in Project64. The tested executable was the portable Project64 build with SHA-256 `C257C662D4E0B2CE1732B7F4A7D5DD4682F604CB082BBDBFCE5181470D6DBFB8`. Runtime inspection used bounded read-only process-memory sampling and the authorized input helper. No guest memory writes, cheats, ROM-derived assets, raw memory dumps, save files, screenshots, or state files are included here.

The capture used bracketed main-loop counter reads and retained accepted scalar rows only. These reads establish a stable sampled counter around each memory read; they are not atomic snapshots of game state. The counter is a main-loop count, not a physics-frame count. The door trace contains 256 rows, 255 accepted; its SHA-256 is `5AB67E85F0C2B9DC529411D18FF71DBE7F9B6952000F9778EEA6D62962124BA0`. The sampler version SHA-256 is `9E4566D55B1E8395F54D2CB176E1CDFF8CD7FFA0FAEE2A9620BD12A3B3554342`. A sanitized five-row excerpt is in [mario64-issue56-door-scalar-partial.json](mario64-issue56-door-scalar-partial.json). It excludes process and RAM addresses.

## Shared interaction path

Static analysis of the target ROM shows the coin interaction callback at guest address `0x8024DB2C` (ROM offset `0x8B2C`). For the selected coin path, the callback adds the object's signed value at `+0x180` to Mario's signed coin count at `+0xA8`, queues healing ticks by adding four times that value to Mario's heal counter at `+0xB3`, and sets object interaction status at `+0x134` to `0x8000`. The consumer at `0x802F2F2C` handles that status, dispatches the effect, and clears the object's active state. These code-analysis results are paired with selected runtime observations below.

The selected red coin hitbox-data record at guest `0x80332794` has selector `0x10`, value `2`, radius `100`, height `64`, and no direct health or loot amount. The predicted counter effect for this record is +2 coins and eight healing ticks. The health update applies 64 units per tick and caps at `0x880` (2,176). With a settled pre-pickup health value of 1,152, the predicted maximum is 1,664 if no other state changes occur. **This prediction has not been confirmed by collecting the coin.**

The stationary one-up loop at `0x802F45B8` delegates to the shared collector at `0x802F3C54`. That collector checks the one-up object collision list through helper `0x802A1424`; on a Mario match it plays its sound, increments Mario lives at `+0xAD`, and clears object active flags at `+0x74`. This describes static analysis of the stationary variant; the separately observed hidden-pole variant and its shared collector are documented below.

For a normal generic door, selector `4` dispatches through `0x8024E420` (ROM offset `0x9420`). The zero-star door uses required-star count zero and ordinary subtype zero. Native code accepts Mario walking/decelerating actions `0x04000440` or `0x0400044A`; it does not require a jump or an A press. On activation Mario enters action `0x1320` (pull) or `0x1321` (push), while the door enters an opening action and later returns to idle. Door status values map through data at `0x80330260`. Star and warp progression branches are outside this ticket and belong to issue 15.

## Controlled door observation

The accepted trace was captured continuously before approach through the door's reset to idle. The scene level was 6; the v5 field at `0x8032DDF4` is a save-file number, not a course identifier, and is not used here as a course claim.

At sequence 18 / loop 257301, Mario was walking (`0x04000440`) at `(-3012.6162, 205, -788.1448)`. The nearby object matched the generic door selector and zero-star/ordinary subtype gates; its door action was idle. At sequence 19 / loop 257307, Mario was in push action `0x1321`, the door was in action `2` with timer `6`, and the door appeared in Mario's collision list. Mario's push action continued through sequence 26 while he moved to x `-3423`. By sequence 31 / loop 257377 Mario had returned to idle while the door remained in action `2`; by sequence 32 / loop 257381 the door had returned to action `0` and timer `0`. The sequence supports one generic push-door activation and completion. It does not claim a sampled transient interaction-status flag.

## Pickup runtime attempt and limits

In a later BOB runtime capture, Mario was at `(-2446.4414, 0, 2097.4053)`, health `1,152`, lives `2`, coins `0`, and hurt/heal counters `0`. The red coin and one-up target instances were still active in the final valid sample. A guarded movement pulse ended with no meaningful displacement; the monitored nearby behavior entered the hard stop radius. No pickup contact, counter change, healing, life gain, or removal was recorded. Runtime navigation is deferred here rather than reporting a blocked movement attempt as a game effect.

An earlier capture gap coincided with a health decrease; its cause is unknown. No source family or environmental cause is attributed to that change. That earlier 1,152 health baseline is therefore reported as observed state, not as a controlled damage outcome.

## Bounded inventory and handoff

Evidence in this report covers the coin selector `0x10`, water-ring selector `0x10000` (healing/status queue path, not a live trial), one-up behavior, and generic door selector `4`. Recovery-heart behavior remains unverified. This is not a complete inventory of all interactive objects. Keys, caps, cannons, warps, star thresholds, course/catalog data, and progression consumers are explicitly handed off to issue 15.

## Yellow coin and observed healing response

In a guarded Bob-omb Battlefield session (level 9, course 1), a selected ordinary Goomba body contact established a controlled health deficit: health stepped from 2,176 to 1,920 while the hurt counter drained from 3 to 0. Mario's interaction object and collision list identified the selected Goomba, and its sampled action changed from 0 through 1 to 2. After knockback, a paused baseline had health 1,920, hurt/heal counters 0, coins 0, and lives 2. The independently selected yellow coin at `(-449, 0, 5668)` was active, value 1, with selector `0x10` and behavior `0x800EBA9C`.

At the paused baseline Mario was at `(-871, 0, 5869)` and the selected Goomba was near `(-833, 0, 6010)`; the selected coin was at the position above. The guarded helper resumed the game, then sent seven 100 ms directional pulses toward the coin and paused. Actual keys were Up+Right, Right, Up+Right, Up+Right, Right, Right, Up+Right, with key release confirmed. During this bounded collection, the stable sample at loop counter 2,773,792 (2026-10-27 03:34:50.708 UTC) showed the baseline and active coin. At counter 2,773,861 (03:34:53.033 UTC), Mario's coin count was 1, health was **1,984** and the heal counter was **3**; the selected coin was in Mario's collision list, its status was `0x8000`, and its active flags were zero. Thus one coin and a **64-unit health increase** were actually observed. Three healing ticks remained queued at pause; further healing and a return to full health were not observed. These sampled counter brackets are non-atomic and do not identify the exact collision frame. The game remained paused because the Goomba was still close and the sampled invincibility timer was nearly exhausted.

The selected live yellow-coin behavior was separately double-read and matched uniquely to ROM offset `0x21A71C` for 128 bytes, SHA-256 `220EEA4387B12BAC714EF319CCC4E7948D5D487574F1A87A69BC0A6E0192EC22`. The native coin callback `0x8024DB2C` updates the coin count and heal queue and sets interaction status; the selected yellow-coin hitbox record at `0x803301F4` has selector 16, value 1, radius 100 and height 64. The consumer/effect and health-update paths are in the native-reference manifest. The guarded setup, result scalars, hashes and provenance are in [mario64-issue56-coin-scalar.json](mario64-issue56-coin-scalar.json). This was one observed run with a documented setup; an independent replay has not been demonstrated.

## Castle grounds hidden one-up: runtime observation

A guarded Project64 session in castle grounds (level 16, course 0) started this trial paused with health 2,176 and one life. Mario reached the tree at `(-6269, 402, -2145)`, attached to the observed pole object, and climbed using bounded AnalogUp inputs. The selected spawner had produced a one-up at `(-6270, 1025, -2145)` and two trigger objects at heights 775 and 975. At loop counter 2,709,069 (2026-10-27 02:58:47.665 UTC) the selected one-up was observed in action 3; at 2,709,106 (02:58:48.900 UTC) it was in action 1 with one life. At 2,709,112 (02:58:49.089 UTC) the selected object was absent and lives were two. Health remained 2,176. The session was paused after collection. The guarded helper sent AnalogUp for each climb pulse; a private driver progress field logged an unused navigation choice instead of the actual key and must not be used to reconstruct inputs. These are sampled, non-atomic observations; they bracket the result but do not establish an exact collision frame. Pool-slot reuse must not be interpreted as persistence of the original object.

The selected live behavior at `0x800EF2C8` was independently read twice while paused, canonicalized, and matched uniquely to ROM offset `0x21DF48` over 56 bytes (SHA-256 `8EC6059AB4B87D5FFCB9C8A9B131FA8BC792043469C18D9D6C6D44BEB7F2EB91`). Native pole loop `0x802F496C` calls `0x802F3EA8`, which calls the shared collector `0x802F3C54`; the latter invokes the collision helper and increments the life field before deactivating the object. Function envelope hashes and bounded observations are recorded in [mario64-issue56-pole-scalar-partial.json](mario64-issue56-pole-scalar-partial.json). The source correspondence for the trigger and one-up behavior is [mushroom_1up.inc.c](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/behaviors/mushroom_1up.inc.c). The target ROM SHA-256 was `17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91`, Project64 executable SHA-256 `C257C662D4E0B2CE1732B7F4A7D5DD4682F604CB082BBDBFCE5181470D6DBFB8`, and input helper SHA-256 `2E78A7B6685C010C8FD2DDAD9D92815A77E9FB87202EAE6DA640A8579B0F9463`. The behavior match was captured separately after collection and does not prove a continuous lifetime by itself.

## Reproduction protocol and limits

Use the verified USA revision 0 ROM and Project64 executable identified above. Start with ordinary gameplay and fresh foreground, process and ROM checks; no memory writes or saved-state shortcuts are needed. Sample Mario and the selected live objects with the main-loop brackets, preserve the seed and input log, and stop if the scene, object identity or health differs from the intended baseline. Behavior scripts and actor positions must be selected from the current session because slots and dynamic positions can change.

For the generic door, enter the castle and approach the ordinary zero-star door near `(-3013, 205, -788)` while walking, then observe the door action, Mario push/pull action and return to idle. For the one-up, enter castle grounds normally, approach the tree near `(-6269, 402, -2145)`, verify the hidden one-up and both trigger children are present, attach to that tree with a bounded jump, then climb with AnalogUp while sampling the child, collision and lives fields. For the coin, enter Bob-omb Battlefield normally, freshly identify a value-one yellow coin and nearby ordinary Goomba, and use a controlled body contact to establish a settled health deficit with no pending hurt or healing. Pause, record the active coin and Mario baseline, then use short guarded directional pulses toward that coin while sampling until count, health, heal counter, collision and object status change. Dynamic enemy movement and geometry vary: abort or re-plan if a safe matching baseline cannot be established, rather than forcing the seed or ignoring a hazard.

The reported door, one-up and coin observations are each one documented run. Their seeds, relevant actions, identities and sample brackets support a conditional reproduction attempt; an independent replay has not been demonstrated. Samples can miss transient states and do not establish atomic event timing.

## Acceptance status

1. Shared interaction gating, status/effect dispatch, and removal: **mapped** in target code/data; selected coin status and removal were observed.
2. Coin/healing numeric outcome and one-up life gain: **observed** for the selected coin (+1 coin, +64 health with three queued ticks at pause) and one-up (+1 life). Remaining queued healing was not observed.
3. One generic door/switch path: **met for a zero-star generic push door**, with continuous runtime trace and static callback/data pairing.
4. Controlled collection/activation observations paired with code/data: **documented** for door, one-up and coin, with setup and sample provenance. Each pickup was observed once; independent replay remains untested.
5. Bounded inventory and issue 15 handoff: **bounded**, with untested recovery-heart and live water-ring outcomes identified and out-of-scope progression handed off.
6. ROM/Project64 identity, sanitized scalar evidence, and unknowns: **present** in this report; raw/private captures are excluded.
7. Safe scalar/report files under `docs/research`: **present**, including selected outcome scalars and native metadata.

Reviewable numeric native references are published in [mario64-issue56-native-evidence-partial.json](mario64-issue56-native-evidence-partial.json): 24 function envelopes, two data windows, and three unique 128-byte live behavior-to-ROM matches, each with address/offset, length, and SHA-256. These metadata anchors contain no ROM bytes.

The decompilation source is correspondence context only; target-ROM disassembly and runtime observations above are the direct evidence. Relevant primary-source files: [interaction.c](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/interaction.c), [coin.inc.c](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/behaviors/coin.inc.c), [red_coin.inc.c](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/behaviors/red_coin.inc.c), [mushroom_1up.inc.c](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/behaviors/mushroom_1up.inc.c), [door.inc.c](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/behaviors/door.inc.c), [water_ring.inc.c](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/behaviors/water_ring.inc.c), and [mario.c](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/mario.c).







