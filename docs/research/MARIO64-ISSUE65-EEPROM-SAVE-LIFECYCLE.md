# Issue 65 — USA save and file lifecycle (static research)

Status: **partial; runtime acceptance remains open**. The source evidence below is pinned to [n64decomp/sm64 at 9921382a68bb0c865e5e45eb594d9c64db59b1af](https://github.com/n64decomp/sm64/tree/9921382a68bb0c865e5e45eb594d9c64db59b1af). This is source-derived behavior, not a claim of execution or completed EEPROM experiment. USA rev 0 target identity: SHA-256 `17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91`. [#10](https://github.com/BrantTanner/MarioCraft/issues/10) observed a 512-byte storage extent; that observation alone does not establish this internal layout.

## USA 512-byte layout

Offsets are byte offsets in the buffer and 16/32-bit values use target big-endian representation. The layout follows [save_file.h](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/save_file.h) and [course_table.h](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/include/course_table.h); `sizeof(SaveBuffer) == 0x200` is statically asserted in [save_file.c](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/save_file.c). The following offsets and constituent sizes are calculated from those source definitions and the USA target ABI, not independently observed save bytes. `COURSE_COUNT` is 25 stored entries: the enumerated NONE is excluded; the 15 stage scores are a separate count.

| Buffer offset | Size | Meaning |
|---|---:|---|
| `0x000–0x1bf` | `0x1c0` | Four files A–D, each with two `0x38` copies, interleaved: A `000/038`, B `070/0a8`, C `0e0/118`, D `150/188`. |
| `0x1c0`, `0x1e0` | `0x20` each | Main menu copies 0 and 1. |

| Relative file offset | Size | Meaning |
|---|---:|---|
| `0x00`, `0x01` | 1 each | Lost-cap level and area. |
| `0x02` | 6 | Three signed 16-bit cap coordinates. Source notes those saved coordinates are not actually used for locating the cap in normal play. |
| `0x08` | 4 | Flags: file existence, caps, keys, doors and other progress; bits 24–30 also encode castle secret-star flags. |
| `0x0c` | 25 | Course star bytes; low seven bits encode stars. The cannon bit for a course is in bit 7 of the following course byte, indexed by the course number. |
| `0x25` | 15 | One 8-bit high coin score per main stage. |
| `0x34`, `0x36` | 2 each | Signature magic `0x4441`, then checksum. |

| Relative menu offset | Size | Meaning |
|---|---:|---|
| `0x00` | 16 | Four 32-bit score-age bitfields, 2 bits per main course per file. |
| `0x10` | 2 | Sound mode. |
| `0x12` | 10 | USA/JP filler. EU instead has 2-byte language at `0x12` and 8-byte filler at `0x14`. |
| `0x1c`, `0x1e` | 2 each | Signature magic `0x4849`, then checksum. |

Signature verification requires the proper magic and a 16-bit sum of all bytes except the last two checksum bytes; thus magic is included and addition wraps at 16 bits. There is no generation counter or preference test between two valid but differing copies: the load switch has no case for both valid and later reads use slot 0. Source: [signature, initialization, save and recovery](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/save_file.c#L101-L348).

## Operations and boundaries

| Operation | Source-derived state and I/O | Result and limitation |
|---|---|---|
| Startup | `thread5_game_loop` calls load after controller initialization; load clears modification flags, zeros buffer, then requests a full `0x200` read. | No probe: read wrapper returns zero without reading. Read status is ignored by caller; zeroed/partially read data is still validated. [Startup](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/game_init.c#L640-L669), [wrappers/load](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/save_file.c#L51-L98). |
| Validate/recover | For each menu block and each file, independently test both signatures; one valid copy is signed/copied to its peer and the peer is written; neither valid wipes menu or erases that file and writes both copies. | Both valid leave each unchanged. Initialization itself may write. Menu wipe initializes age words to `3fffffff`, `2aaaaaaa`, `15555555`, `00000000`. |
| Save | If file dirty, sign slot 0, copy it to slot 1 and write both as one `0x70` request; separately save dirty menu by signing/copying and writing `0x40`. | Write wrapper retries at most four times if probe succeeds and returns 1 without a probe. Callers ignore status and clear dirty flags even on failure. In-memory copies do not prove durable storage. |
| Collect star/key | Interaction calls collect with current coin count and star index. It changes slot 0 and dirty flags, updates 8-bit score when its 16-bit comparison wins, and tracks score ages; Bowser 1/2 set corresponding key flags subject to existing unlocked state. | Collection is an in-memory mutation; it is not by itself a persistent write. Score truncation after comparison can lower a stored high score. [Collection](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/save_file.c#L357-L413), [interaction](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/interaction.c#L800-L815); see [#59](https://github.com/BrantTanner/MarioCraft/issues/59). |
| Save choice | Star cutscene save-and-continue or save-and-quit invokes save; save-and-quit then initiates special warp. Cap switch and a separate affirmative dialog also call save. | The continue-without-saving option does not call save at that point; other paths must not be assumed to save. [Cutscene](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/mario_actions_cutscene.c#L249-L264), [cap switch](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/camera.c#L6956-L6959). |
| Select/copy/erase | File menu permits an existing copy source and an empty destination; it calls copy on destination selection. Erase calls erase on YES. Copy touches destination score ages and copies source slot 0 to destination slot 0, then saves destination. Erase touches ages, zeros slot 0, then saves. | Menu visual completion and dirty-flag clearing do not report write success. [Menu copy](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/menu/file_select.c#L770-L817), [erase confirmation](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/menu/file_select.c#L2370-L2385), [operations](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/save_file.c#L252-L288). Current file numbers are one-based; array indices are zero-based. |
| Game over | A death warp changes to game over when lives are zero; `WARP_OP_GAME_OVER` invokes `save_file_reload`, then special warp. | Reload copies the current file and menu slot 1 into slot 0 and clears both dirty flags; it does **not** read EEPROM. Failed earlier writes can leave the cached backup different from actual durable state. [Game over](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/level_update.c#L832-L839), [reload](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/save_file.c#L334-L350). |
| Lives/health and reset | `SaveFile` contains neither lives nor health; Mario initialization sets lives to 4 and health to `0x880`, while deriving star count from save. Course entry resets current coins on the indicated path. | This is a source-level distinction, not an observed reset experiment. Death, game over, save-and-quit, physical reset and emulator state restoration have distinct paths; do not infer identical persistence. [Mario initialization](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/mario.c#L1878-L1887), [course entry](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/src/game/level_update.c#L1284-L1289); see [#56](https://github.com/BrantTanner/MarioCraft/issues/56) and [#61](https://github.com/BrantTanner/MarioCraft/issues/61). |

## Future isolated experiment — not performed

Runtime access is deferred. Do not operate or overwrite canonical EEPROM, savestates, or existing files. Once access is authorized and functional, configure a separately named emulator profile and storage directory, establish by inspection that all save and state paths resolve there before launch, and preserve a read-only baseline with length and SHA-256. Start with a disposable isolated EEPROM copy; note its initial hash and scalar summary privately, not its contents publicly. Select a file, obtain a star, choose save, close and restart against the same isolated EEPROM, and record before/after file hashes and visible file/star scalars to demonstrate persistence. Then copy that file to an empty slot (or confirm erase on an isolated slot), close and restart again; record hashes and source/destination existence/star scalars at each boundary. Capture setup, exact inputs and launch/configuration identity sufficient to reproduce; confirm canonical files' hashes unchanged. To test reset/lives, separately specify reset type and observe lives and persisted stars before/after; a savestate restore is not equivalent to cold boot. Do not report source-derived predictions or synthetic fixtures as observations. This procedure and results remain unverified until executed; current access denial and the previous helper failure of unknown cause are blockers, not evidence of game behavior.


## Selected exact target code corroboration

Ghidra headless 12.1.4, read-only/no-analysis, inspected existing target program functions. For each window below, the complete contiguous function envelope hash was compared against the same USA ROM window at `ROM offset = PC - 0x80245000` within PC `[0x80246000, 0x8032d560)` / ROM `[0x1000, 0xe8560)`; each matched. Labels identify source-aligned interpretations of static code, not runtime observation. These windows confirm buffer strides/sizes, magic, validation/recovery branches, retry defaults, save/copy/erase and memory-only reload. They do not establish UI execution or successful writes.

| Source-aligned function | Entry | Length | SHA-256 |
|---|---:|---:|---|
| read wrapper | `80279174` | 164 | `C1736A2B1C08A400B4B5C5786BEC3F80C21B6C41AB6CDF73D38272F7BA59F3B7` |
| write wrapper | `80279218` | 168 | `C06D296AD38EB449DA45FB59D5D459FFD2CE590141F59497D9457750D3D1CCBA` |
| checksum | `802792c0` | 84 | `AF53C411CABA9152955640E6760D21D1D90588B0553A10CCF99943F1245B825E` |
| verify | `80279314` | 136 | `095912362D6C62330C02F43D974EB8B6A0B8C4F9E910728CD81027684F58FDB0` |
| sign | `8027939c` | 96 | `37000D179D875F2F7F03967851930C16BDEA156FF3DA72FB6F76597C1EE6CA42` |
| menu restore | `802793fc` | 164 | `F55074E9DAB2CC7BB5686C431235D36979AE04526382990B3B5E561169DCCFD1` |
| menu save | `802794a0` | 124 | `C4B4191F897EF3909EC76CC220FE441468711BF56639B6C83CFBADBEE296B794` |
| menu wipe | `8027951c` | 132 | `9578C70CCF8173E4741CF6EB2287EDC6D217F71BD2A73401A812B07D031A6297` |
| file restore | `80279748` | 248 | `A3721A30BE33B9E80DE4ECB7FF0DC5D6604DAC3D074672A917D8EB5E15F91F91` |
| file save | `80279840` | 188 | `846BB2F92DC8558FF50CE146959175EEE11F744F19D4B1FE9B4B0BDEADBA73FE` |
| erase | `802798fc` | 100 | `89DA7AE8CDA745DC257AA6D2218DF0EE294844755BF0C329F435BA2960064E56` |
| copy | `80279960` | 124 | `9DDF989AA5F5B530779D95D044FDB0BB722E481449C19EAA156B1040FCCFA780` |
| load all | `802799dc` | 492 | `BB3BCCB991B6BDBBDED0E82B4174A6ED7FFE38FC395B4169C46DF72BC814A62E` |
| cached reload | `80279bc8` | 124 | `12138D761CDD72EE7B805F5CEEABDD394BC00A9CA11EB73861766AD0027EDBA6` |
| collect star/key | `80279c44` | 512 | `0E8F1E0AA6E5E3238126533DDECB0347770F7F3EC0C2F688A5FFD87BFF9DE509` |

Native wrapper calls reach long read `80324690` and long write `803247d0`. Target code analysis is static; routine names are matched by behavior and call relationships, and no claim is made that every possible path executed.

## EEPROM transport boundary

The pinned [probe](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/lib/src/osEepromProbe.c), [read](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/lib/src/osEepromRead.c), [write](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/lib/src/osEepromWrite.c), [long read](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/lib/src/osEepromLongRead.c) and [long write](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/lib/src/osEepromLongWrite.c) describe 8-byte blocks. USA probe accepts a successful status whose type has the `0x8000` bit; USA read/write require exact type `0x8000`. The single-block address guard rejects starts above 64 (the source check is `> 64`, not `>= 64`); the long routines test only the initial start, advance in 8-byte blocks and return the first error. They wait on a 12 ms timer after each successful block including the last. These guards are not a total-transfer bounds check. Four application attempts are not a wall-clock timeout: queue, status and busy waits may block.

Static USA source and selected native read/write code both show an early status/type error return after SI acquisition without the normal release path. This is a possible SI lock leak on that path, not an observed failure; no connection to the previous local helper failure is established. The inspected native read/write also show address gating, busy-status rechecks and channel-error handling. Probe, write and read analysis was independently matched to complete ROM windows (same PC-to-ROM mapping as above):

| Static target function | Entry | Length | SHA-256 |
|---|---:|---:|---|
| probe | `80324080` | 108 | `D6E586D11E0DAC0F556F458F273EFAF9A8BFA530FC7D439AB8528C22C560D025` |
| long read | `80324690` | 316 | `E0CA12083EA114CBF78C76CCCAD16AF6B1CA8FA2A807455A70179489AB17BAB9` |
| long write | `803247d0` | 316 | `D9A7565B2763F821D9048EC63E9D7428F521CCDB80DE7EC798CC613E95124CAC` |
| write | `80328af0` | 432 | `AC230C8F6E4F1A13E61C64BF351B2A53E9A93E4777706D8FC85C1C2457AAF0C7` |
| status | `80328dac` | 548 | `2451E84A2A68A3A1AAC284E04D09673FF74EE546106DF95143DF7FE77A3214F9` |
| read | `80329150` | 496 | `00CADFFCE4BFA7F17395CE7B14047ADC26FCDAF10D9CE13781AB6DDFD8070E81` |

The selected long-read body also corroborates the 8-byte loop, first-error return and 12 ms timer after each successful block.


