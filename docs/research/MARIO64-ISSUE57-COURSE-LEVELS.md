# Issue #57: source-pinned course and level inventory

**Status:** Reviewed research result; source census and representative BOB cartridge correspondence are complete. Issue #57 remains open pending root closure.

## Result

The manifest covers the pinned USA decomp course and level tables and every `LevelScript` array in all 33 `script.c` files. It retains all 7 stub IDs and `COURSE_NONE`. It contains all 52 source-declared areas, with geometry and terrain symbols resolved to their defining source arrays (52/52 geometry, 47/47 terrain references). The five areas without terrain declarations belong to intro/ending cinematics.

For each area, the manifest records its source line and script array, active USA commands, linked local-script references and recursively expanded commands, terrain data/type, object placements, macro table reference, warp nodes, painting warps, instant warps, and related script calls. Cyclic or unresolved local references are listed explicitly. It retains inactive conditional alternatives with `usa_active: false`; active declarations and counts use the USA flag selection below.

The macro-object census resolves every `MACRO_OBJECTS` table reference: 42 defined tables with 1,528 placement entries plus 42 separately recorded `MACRO_OBJECT_END` terminators, with zero unresolved references. All 26 course rows, 38 level rows, and 143 `LevelScript` arrays were parsed; unparsed row/file counts are zero. Geometry references resolve to their actual `GeoLayout` declarations and terrain references to their actual `Collision` declarations, not just candidate paths. Each table entry retains its source macro name and source arguments, including the symbolic preset, yaw, position, and behavior parameter where present. Preset names are not expanded into actor behavior identities.

USA selection defines `VERSION_US` and leaves `VERSION_JP`, `VERSION_SH`, `VERSION_EU`, `VERSION_CN`, and `NO_SEGMENTED_MEMORY` undefined. The level table's `VAL_DIFF` therefore resolves to 60000. CCM's `#ifndef VERSION_JP` branch is active; JRB's `VERSION_JP || VERSION_SH` branch is inactive and its `else` branch active. `NO_SEGMENTED_MEMORY` is an independent build flag and its alternative is retained with the cartridge selection marked false.

The source scope is pinned to [`n64decomp/sm64@9921382a68bb0c865e5e45eb594d9c64db59b1af`](https://github.com/n64decomp/sm64/tree/9921382a68bb0c865e5e45eb594d9c64db59b1af). The 26 course rows comprise 15 playable main courses, `COURSE_NONE`, and 10 bonus entries. The 38 level rows comprise 31 definitions and 7 stubs. The 33 `script.c` files comprise all 31 defined folders plus `intro` and `menu`; `levels/entry.c` and `levels/scripts.c` are included as entry/dispatcher sources. All 18 shared `script_func_global_*` families and actor-group header imports are cross-referenced.

## Canonical USA ROM cross-check

The canonical USA revision 0 ROM is 8 MiB with SHA-256 `17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91`. A 4-byte-aligned whole-ROM scan of BOB's source command opcode/length sequence (26 entry-body commands, 20 area-body commands, plus `AREA` and `END_AREA`) found one candidate at ROM `[0x405CC4,0x405E9C)`, 472 bytes, SHA-256 `BC942670A2A6B580713467E425913277B895CC2FCDB7336F4F3F3652BF1A27EB`.

The candidate's numeric operands agree with the coordinate/angle tuples for five direct object placements and 25 placements reached through three local-script references, seven warp destinations, `MARIO_POS` (area 1, yaw 135, `(-6558,0,6464)`), and all nine segment IDs. The five direct placements are source lines 81–85 of `levels/bob/script.c`; the three linked local arrays contain 5, 15, and 5 placements.

A unique aligned BOB `EXECUTE` header at ROM `0x2AC0F8` declares segment `0x0E`, bank `[0x405A60,0x405FB0)`, and entry `0x0E000264`. The bank offset `0x264` lands at `0x405CC4`. Local-script pointers `0x0E000000`, `0x0E00007C`, and `0x0E0001E8` agree with the source array placement. This is a bounded, self-consistent source-encoded relationship in cartridge data. It does not prove observed loader execution or native VM handler semantics, and it does not establish a general segmented-pointer-to-ROM mapping. The separate #10 linear mapping applies only to PC `[0x80246000,0x8032D560)` ↔ ROM `[0x1000,0xE8560)`.

## Files and reproduction

- [Finite JSON manifest](MARIO64-ISSUE57-COURSE-LEVEL-MANIFEST.json): 1.58 MB, containing all source declarations, exact line references, USA guard outcomes, area and local-script data, macro-object entries, shared functions, and bounded ROM evidence.
- Generator: `tools/issue57-build-manifest.py`. Clone `n64decomp/sm64`, check out commit `9921382a68bb0c865e5e45eb594d9c64db59b1af`, then run `python tools/issue57-build-manifest.py --source-root <sm64-checkout> --output <manifest.json>`. The helper verifies the source HEAD before parsing and accepts explicit source/output paths.
- Pinned local source checkout: `tools/issue57-source-pinned`, detached at the pinned commit; it is a research input, not report content.

The scanner masks comments with equal-length spaces to preserve source offsets and line numbers, scans only outer macro calls so nested argument helpers are not counted as commands, and bounds every `LevelScript` array independently—including all intro and menu entry arrays. It expands referenced local functions recursively, recording cycles and unresolved names. The generator anchor checks the 48-command BOB sequence, 5 direct plus 25 linked placements, 7 warps, source lines 81–85, and CCM/JRB USA guard outcomes. Shared global functions are inventoried separately. The generator contains anchor assertions for the BOB 48-command source sequence, object and warp counts, and USA CCM/JRB guard outcomes.

## Coverage and bounded follow-ups

| Census item | Coverage |
| --- | ---: |
| Course rows | 26 (15 playable main, 1 sentinel, 10 bonus) |
| Level rows | 38 (31 definitions, 7 stubs) |
| `script.c` files | 33 (all 31 defined folders, intro, menu) |
| Areas | 52; all geometry references resolve |
| Terrain references | 47 resolved; five cinematic areas have none |
| Macro-object tables and placements | 42 tables; 1,528 placements + 42 terminators; zero unresolved references |
| Shared script families | 18 global families, 8 entry/dispatcher arrays, all level-local definitions |
| Direct ROM correspondence | BOB, one unique candidate; no runtime observation |

Follow-up work under #58 owns broader source-to-ROM/segment mapping and runtime loader observations when the Project64 boundary permits them. Course objective and transition behavior belongs to #59. Expanding symbolic macro presets into actor behavior identities is outside this finite placement inventory. This child adds no Project64 interaction requirement.

