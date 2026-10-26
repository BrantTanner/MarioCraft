# Mario 64 course asset loading map (#58)

**Status:** Static mapping is prepared; issue #58 remains open. Project64 transition observations are still required, and several source data ranges do not yet have exact content correspondence.

Tracker links: [#15 parent issue](https://github.com/BrantTanner/MarioCraft/issues/15), [#57 source inventory](https://github.com/BrantTanner/MarioCraft/issues/57), and [#58 this loading map](https://github.com/BrantTanner/MarioCraft/issues/58).

## Target and evidence boundary

The ROM target is the supplied 8 MiB big-endian USA revision 0 image, SHA-256 `17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91`. The source reference is [`n64decomp/sm64@9921382a68bb0c865e5e45eb594d9c64db59b1af`](https://github.com/n64decomp/sm64/tree/9921382a68bb0c865e5e45eb594d9c64db59b1af), with `VERSION_US` selected. The source files describe linker and script intent. Native-code details below come from the canonical ROM's independently hash-checked code blocks; they do not show that a course transition was executed.

The finite manifest [MARIO64-ISSUE58-ASSET-LOAD-MANIFEST.json](MARIO64-ISSUE58-ASSET-LOAD-MANIFEST.json) contains ROM offsets, lengths, hashes, source paths and line numbers, linker-order candidates, and bounded unknowns. Its helper [issue58-build-manifest.py](../../tools/issue58-build-manifest.py) reads the ROM and pinned source checkout, decodes MIO0 to transient memory, and writes only numeric and hash metadata.

## Compressed containers

A full 4-byte-aligned scan found 79 MIO0 markers in ROM range `[0x108A40,0x4EB1E6)`. The pinned `sm64.ld` expands to 79 compressed linker slots: the initial segment, actor and common groups, intro/menu data, skyboxes, texture bins, and level-data segments. Pairing those sequences by ordinal gives one bounded candidate for every source slot. All 79 streams decoded to their declared output size; all output SHA-256 values are distinct. No read crossed the next marker, no stream ended with a truncated back-reference, and the decoded content was kept in memory only. The decoded outputs total 7,463,840 bytes; consumed input envelopes total 3,438,897 bytes. The consumed envelope excludes possible linker alignment padding.

The ordinal relationship is a linker-order candidate. It is not independent byte equality against assets rebuilt from source, and a structurally valid MIO0 stream does not establish its identity or runtime use. Every stream row therefore preserves its evidence level and does not claim exact source-build correspondence. Existing exact BOB evidence remains separately bounded: the BOB level-data stream candidate begins at `0x3FC2B0`, the BOB raw script/geometry bank candidate spans `[0x405A60,0x405FB0)`, and its source `EXECUTE` entry pointer resolves to the unique BOB command body at `0x405CC4`. These are not a general segmented-pointer-to-ROM rule.

## Source load inventory and grouping

The manifest contains 290 active source load/execute uses from the 33 level `script.c` files, the global `levels/scripts.c` entry and transitions, and the 31 `DEFINE_LEVEL` expansions. It records 141 MIO0 loads, 31 texture-MIO0 loads, 80 raw loads, and 38 raw script/geometry `EXECUTE` rows: 31 generated course rows plus seven explicit top-level `EXECUTE`/`EXIT_AND_EXECUTE` rows. MIO0 slots divide into 31 course level-data containers plus intro/menu containers; 18 actor groups; two common actor groups; 10 skyboxes; 13 texture bins; and three system/menu streams. Source references show repeated use of actor groups, common assets, skyboxes, and textures across course scripts. The manifest records per-symbol reference counts and source files.

A unique 66-byte aligned ROM prefix at `0x2ABCA0` matches the category/segment sequence for the first five loads in `levels/scripts.c`'s `level_main_scripts_entry`, followed by `ALLOC_LEVEL_POOL` and the first model-geo header. This recovers numeric source-symbol bounds for `group0_geo` `[0x1279B0,0x12A7E0)`, `common1_geo` `[0x218DA0,0x219E00)`, and `behavior` `[0x219E00,0x21F4C0)`. The two raw intervals are bounded by encoded operands rather than guessed. The prefix does not prove full entry-array identity or that each declaration executed.

The source-enum dispatcher table at `[0x2ABF18,0x2AC094)` (380 bytes, SHA-256 `FAD1544765396E6E7F3E468E1C8E33B1AA6E5913883155FF65D78372D9CC3E51`) uniquely matches all 31 defined numeric level IDs. Its 31 generated `EXECUTE`/`RETURN` wrappers at `[0x2AC094,0x2AC300)` resolve through segment 21 base `0x2ABCA0` to each course's numeric script-bank start/end and entry pointer. Every source entry's opcode/command-size sequence matches the canonical ROM, and 235 load commands have their encoded segment and numeric ROM operands recorded in the manifest. This is source-encoded correspondence, not full rebuilt-asset byte equivalence or runtime execution. The TTM bank is directly bounded at `[0x4EB1F0,0x4EC000)` (3,600 bytes; SHA-256 `B4B4C6F2AD17F34C7AC1464D3D4E10CB031155936E896758DF29AEBD80A58910`); its entry begins at `0x4EB584`, spans 892 bytes, and has SHA-256 `E80F36EFBF30209EC1C37FF39EA4E6F8F67BDEB7E4D4D49685E2ACA01389B1C5`.

For the 31 course and bonus level scripts, `LOAD_MIO0(0x07, ...)` loads the level-data segment. Its source declarations connect that segment to the course collision, macro-object, room, and related level data. The course `EXECUTE(0x0E, ...)` selects the raw script/geometry bank that contains the level entry and area commands. `LOAD_MIO0` also supplies shared actor-group, common, and skybox data; `LOAD_RAW` supplies their geometry companions; `LOAD_MIO0_TEXTURE` supplies shared texture bins. The exact segment IDs, source symbols, call sites, and ROM candidate rows are in the manifest.

Adjacent linker-section boundaries remain candidates for shared actor-group geometry spans not included in the source enum table. The 31 course script/geometry banks now have direct numeric bounds from their generated wrappers and source entry patterns; TTM is no longer an unresolved final linker interval. Six intro/menu script transitions remain without full raw bounds. Other adjacent shared spans and ordinal MIO0 associations remain bounded candidates pending independent source-build byte comparison.

Area source relationships follow the #57 inventory: geometry and area script commands are in the level script/geometry segment, while terrain/collision and macro-object tables are in level data. Native handlers write the resolved terrain and macro pointers into the active area state. The BOB collision header and vertices have the separate exact correspondence recorded in #57; this does not establish equivalent correspondence for every course asset.

## Passive Project64 current-state sample

A read-only sample on 2026-10-26 at 05:24:53 UTC identified the foreground Project64 process, matched its live ROM prefix to the canonical image, and observed level 9/course 1/save slot 1 (BOB). All 15 block reads had equal state-counter brackets and stable target segment bases. Fourteen full-block hashes match canonical raw or decoded spans; BOB segment 7 differs by one byte as described below. No guest input or writes occurred. These data verify a loaded snapshot, not a course transition.

| Segment | Guest virtual base | Expected source symbol | ROM span | Compared bytes | Full block result |
| ---: | ---: | --- | --- | ---: | --- |
| `0x07` | `0x800FAF90` | `bob_segment_7` | `[0x3FC2B0,0x405A60)` | 71,618 | One byte differs; live hash `EBE6901CDCFAC197E4FDCD1CF93AE5C07AEE61A4D1DB9A0CB8CFB11C1296928C` vs candidate `CACAC9B811A9231002D7C22EA3D242CCAED283D04767D64D9C55E4FC968DC0F4` |
| `0x09` | `0x801C1000` | `generic_mio0` | `[0x32D070,0x334B30)` | 47,104 | Exact hash match |
| `0x0A` | `0x8010C770` | `water_skybox_mio0` | `[0x2AC6B0,0x2B8F10)` | 131,392 | Exact hash match |
| `0x05` | `0x8012C8C0` | `group3_mio0` | `[0x134D20,0x13B5D0)` | 69,792 | Exact hash match |
| `0x0C` | `0x8013D970` | `group3_geo` | `[0x13B5D0,0x13B910)` | 832 | Exact hash match |
| `0x06` | `0x8013DCC0` | `group14_mio0` | `[0x1C4230,0x1D7C90)` | 151,944 | Exact hash match |
| `0x0D` | `0x80162E60` | `group14_geo` | `[0x1D7C90,0x1D8310)` | 1,664 | Exact hash match |
| `0x08` | `0x801634F0` | `common0_mio0` | `[0x1F2200,0x2008D0)` | 166,896 | Exact hash match |
| `0x0F` | `0x8018C0F0` | `common0_geo` | `[0x2008D0,0x201410)` | 2,880 | Exact hash match |
| `0x0E` | `0x800FA1F0` | BOB raw script/geometry bank | `[0x405A60,0x405FB0)` | 1,360 | Exact hash match |
| `0x04` | `0x8007EC20` | `group0_mio0` | `[0x114750,0x1279B0)` | 217,976 | Exact hash match |
| `0x03` | `0x800B3FB0` | `common1_mio0` | `[0x201410,0x218DA0)` | 209,672 | Exact hash match |
| `0x17` | `0x800E72D0` | `group0_geo` | `[0x1279B0,0x12A7E0)` | 11,824 | Exact hash match |
| `0x16` | `0x800EA110` | `common1_geo` | `[0x218DA0,0x219E00)` | 4,192 | Exact hash match |
| `0x13` | `0x800EB180` | `behavior` | `[0x219E00,0x21F4C0)` | 22,208 | Exact hash match |

The BOB level-data block differs at decoded offset `0x11234`, which is byte `0x1104C + 48*10 + 8` in the macro-object table. Pinned source record 48 is `macro_bobomb` at `(-1400, 0, -950)`; this is a positional source-encoding association and does not establish what changed the live byte. The collision header and 570 vertices at offset `0xE958` match exactly across 3,424 bytes (SHA-256 `4B035BDA7254839FACFC4B3B9774A6C5250CCF130B5090FF9BB2F8B063667E0B`). Across the other 14 blocks, captured content hashes match the corresponding canonical ROM raw or decoded spans. The block checks corroborate current BOB segment contents and several source/linker mappings; they do not show allocation, replacement, retention, or release during a transition.

## Native loading and lifetime

The canonical ROM contains a 61-entry opcode table at PC `0x8038B8B8`, ROM `0x108638`, length 244 bytes, SHA-256 `6D7665B139BE014D50B0FA3E245DF4F50CF58552163BC165111BB88311F8A868`. The table and relevant handlers have unique exact ROM code matches. Their PC and ROM offsets are:

| Command | Handler PC | Exact ROM offset |
| --- | ---: | ---: |
| Execute `0x00` | `0x8037E2C4` | `0x0FB044` |
| Exit and execute `0x01` | `0x8037E388` | `0x0FB108` |
| Exit `0x02` | `0x8037E404` | `0x0FB184` |
| Jump link `0x06` | `0x8037E5B8` | `0x0FB338` |
| Return `0x07` | `0x8037E620` | `0x0FB3A0` |
| Raw load `0x17` | `0x8037ECA4` | `0x0FBA24` |
| MIO0 load `0x18` | `0x8037ECF8` | `0x0FBA78` |
| Texture MIO0 `0x1A` | `0x8037EDF8` | `0x0FBB78` |
| Init level `0x1B` | `0x8037EE48` | `0x0FBBC8` |
| Clear level `0x1C` | `0x8037EEA8` | `0x0FBC28` |
| Allocate level pool `0x1D` | `0x8037EF00` | `0x0FBC80` |
| Free level pool `0x1E` | `0x8037EF70` | `0x0FBCF0` |
| Area `0x1F` | `0x8037F010` | `0x0FBD90` |
| End area `0x20` | `0x8037F130` | `0x0FBEB0` |
| Object `0x24` | `0x8037F45C` | `0x0FC1DC` |
| Warp `0x26` | `0x8037F67C` | `0x0FC3FC` |
| Mario position `0x2B` | `0x803800BC` | `0x0FCE3C` |
| Terrain `0x2E` | `0x8037FE94` | `0x0FCC14` |
| Macro objects `0x39` | `0x8037FF94` | `0x0FCD14` |

The VM loop is PC `0x803805C8`, ROM `0x0FD348`, length 188 bytes, SHA-256 `4C9960F45C1C124445D650355ED8391E13504B5C0C42B99C34E78F6E403DFD9D`. All listed high-address code windows were uniquely matched as whole byte ranges in the canonical ROM; the main-code loader helpers use the verified #10 mapping [MARIO64-ROM-TOOLING-AND-SCOPE.md](MARIO64-ROM-TOOLING-AND-SCOPE.md). The command categories map to the handlers above.

| Loader helper | PC | ROM offset | Length | SHA-256 |
| --- | ---: | ---: | ---: | --- |
| Segment pointer resolver | `0x80277F50` | `0x32F50` | 88 | `07345C27FC6AD056FDE87B2659CAC872075744C98203C0040C13E09CC2B70CD2` |
| Segment base setter | `0x80277EE0` | `0x32EE0` | 48 | `04D83E28A005FCB0A1E443684080DE5ABAB2EE37867990F84E7376200565081E` |
| Raw load wrapper | `0x8027868C` | `0x3368C` | 100 | `AA1889D3F26FBAB51093E9A87019FC490A9E4B079F6C490C8B43DB12E1E0730F` |
| MIO0 load and install | `0x802787D8` | `0x337D8` | 220 | `239150A9914575850464122EED68FC7A38B5D93FD1128D7F816A573F43F0B634` |
| Texture MIO0 load and install | `0x802788B4` | `0x338B4` | 192 | `6848B5907C1AA9F3B62D323415CA0CE300CAE9FDE9CB684BB14B45F327C3E419` |
| Segment copy/DMA wrapper | `0x80278504` | `0x33504` | 268 | `31CB57645FC32F9AE7513B553E953A1C5F2C633BB09F9F0EB51163A3168A732E` |
| MIO0 decoder | `0x8027F4E0` | `0x3A4E0` | 164 | `99F254304F29A1E91384387E7172240011813076DEF5C2B35485A1B36020711F` |

The helper and VM ranges were independently checked against canonical ROM bytes. The ROM-to-PC relationship above is limited to each exact listed range and the verified #10 initial main-code mapping; it is not a general address conversion rule.

Static native code establishes these behaviors:

- Segment resolution indexes the base table at `0x8033B400` by the segmented pointer's high byte, adds its low 24-bit offset, then applies the cached-address bit. A segmented address is never treated as a ROM offset.
- Raw loads copy the declared ROM interval, then set the selected segment base. MIO0 loads copy the encoded bytes into a temporary pool allocation, read the declared decoded size from the header, allocate that output from the opposite pool end, decode, install the segment base, and free the temporary allocation on the successful path. Texture-MIO0 uses a fixed working destination and returns its fixed pointer.
- The copy helper aligns length to 16 bytes and sends chunks of at most `0x1000` to a blocking DMA queue. Generic DMA and scheduler/hardware behavior remain #16 scope.
- `EXECUTE` saves the caller continuation, pushes pool state, loads the requested raw script bank, resolves the entry pointer, and switches the current script pointer. `EXIT_AND_EXECUTE` restores/pops then pushes pool state around its replacement raw script load; `EXIT` restores the caller state. `RETURN` follows the saved continuation.
- `INIT_LEVEL` resets level/object/area state and pushes pool state. `ALLOC_LEVEL_POOL` allocates from currently free level-pool capacity. `FREE_LEVEL_POOL` calls the pool resize helper with the pool's used size, clears the active pool handle, scans eight areas, and calls `0x80383340` when an area has terrain. Its name does not prove that every course asset is immediately freed. `CLEAR_LEVEL` resets objects and areas and pops pool state.
- `AREA` and `END_AREA` maintain the current area state; terrain and macro commands resolve pointers into the active area. `FREE_LEVEL_POOL` scans eight areas and calls `0x80383340` when any area has terrain; the callee's effect is not established here. These facts describe static code paths, not observed per-course transitions or retention.

The main pool allocator aligns allocations to 16 bytes and uses a 16-byte header. The allocation-only pool helpers round requests and capacities to four bytes; the allocation helper advances the active pool's current pointer while ensuring usage stays within capacity. The free-level handler asks its resize helper to resize the reservation to the used size, then clears the global handle. No base movement is established. The exact content lifetime across a real course transition remains to be corroborated in Project64.

## Acceptance status

| Criterion | Status |
| --- | --- |
| Bounded ROM scan and source-declared load categories | Enumerated across the full 8 MiB target and pinned source families; all 79 compressed slots and 290 source uses are represented. All 31 generated course banks are linked to source enum IDs, and 235 in-entry load operands are recorded. |
| Every candidate's compression, decoded size/hash, and consumed span | Complete for all 79 MIO0 candidates. The 31 course script-bank spans are directly encoded in their generated wrappers; six intro/menu transition spans remain unresolved. Other raw spans are candidate or unresolved as marked. |
| Shared/course-specific groups and geometry/collision/script relationships | Static source and native relationships mapped; exact asset identity is strongest for the bounded BOB examples and remains incomplete for other data. |
| Project64 load-transition corroboration | Partially corroborated: a passive, stable BOB snapshot matched 14 of 15 full blocks and matched BOB collision data within the differing block. A transition observation remains open. |
| Sanitized report and finite manifest | Prepared. Contains no ROM, decoded asset, RAM, or capture bytes. |

The source inventory's bounds and 79-candidate scan are finite. Six intro/menu transition spans remain unresolved; ordinal MIO0 associations and most other raw source/data candidates still lack independent exact source-build byte correspondence. The passive sample does not show a course load/unload transition. Issue #58 and parent #15 should remain open until those gaps are addressed.

## Reproduction and references

Run from the repository root with the supplied ROM and the pinned source checkout:

```powershell
python tools/issue58-build-manifest.py `
  --rom <path-to-canonical-USA-rev0-ROM> `
  --source-root <path-to-sm64-at-9921382a68bb0c865e5e45eb594d9c64db59b1af> `
  --course-manifest docs/research/MARIO64-ISSUE57-COURSE-LEVEL-MANIFEST.json `
  --output docs/research/MARIO64-ISSUE58-ASSET-LOAD-MANIFEST.json
```

The helper requires the exact ROM SHA-256 and source commit above, and requires the 79-to-79 census counts. Primary source anchors: [`sm64.ld`](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/sm64.ld), [`levels/scripts.c`](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/levels/scripts.c), [`levels/level_defines.h`](https://github.com/n64decomp/sm64/blob/9921382a68bb0c865e5e45eb594d9c64db59b1af/levels/level_defines.h), and [the #57 source-pinned level inventory](MARIO64-ISSUE57-COURSE-LEVELS.md). Native evidence metadata and decompiler logs remain private under `tools/issue11-local/`; no pseudocode or raw code dump is published.
