# Mario course and progression research plan (#15)

**Status:** #15 remains open, with its original acceptance criteria unchanged. This plan decomposes the broad source, ROM, and runtime investigation into three sequential children. Runtime-dependent criteria remain open until directly observed.

## Sequence

| Order | Issue | Scope | Dependencies |
| --- | --- | --- | --- |
| 1 | [#57 Inventory USA courses, levels, areas, and level-script families](https://github.com/BrantTanner/MarioCraft/issues/57) | Complete source-pinned census of every level/course definition and level script/area family; bounded canonical-ROM cross-checks where exact safe mappings exist. This child does not require a new Project64 observation. | [#10](https://github.com/BrantTanner/MarioCraft/issues/10) |
| 2 | [#58 Map course asset segments, compression, and cartridge loading](https://github.com/BrantTanner/MarioCraft/issues/58) | Exact ROM spans, compressed/raw asset containers, source-to-ROM/runtime segment mappings, and content-load lifetime. Finish static work if a Project64 step is blocked; keep runtime criteria open. | #10, #57 |
| 3 | [#59 Trace course objectives, caps, warps, and castle progression](https://github.com/BrantTanner/MarioCraft/issues/59) | Finite objective and transition matrix across all main courses, secrets, castle stages, cap/key/warp/cannon and ending progression, with source/data anchors and runtime status. | #14, #57, #58 |

The child issues link back to #15 and list their predecessor URLs. Use those reciprocal links and this plan as the dependency record. Do not close #15 from source inventory alone: its Project64 observations and dynamic ROM/RAM correspondence remain requirements.

## Scope corrections

- The 15 main courses each expose six act-selected stars plus a seventh 100-coin star; the red-coin objective is one of the six act-selected stars.
- Cap coverage includes worn, stolen, dropped/lost, and returned/default-location states as well as the three switch power-ups.
- #16 owns broader runtime, DMA, hardware, and EEPROM/save-format research. #59 cross-references its persistence boundary without depending on #16 closure or duplicating its format analysis.
- Runtime observations use the documented foreground and identity guards; no UI refocus workaround is authorized.
- Safe publication is limited to prose, source links, numeric offsets/lengths, hashes, and reproducibility helpers. Do not publish ROM bytes, RAM, derived assets, screenshots, save states, credentials, or local captures.

## Source anchor

Use the USA decomp navigation source only as a source map: [n64decomp/sm64 at 9921382a68bb0c865e5e45eb594d9c64db59b1af](https://github.com/n64decomp/sm64/tree/9921382a68bb0c865e5e45eb594d9c64db59b1af). Cartridge/runtime claims still require evidence from the canonical USA revision 0 ROM (8 MiB; SHA-256 `17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91`).

## Current results and outstanding work

[#57](https://github.com/BrantTanner/MarioCraft/issues/57) and [#58](https://github.com/BrantTanner/MarioCraft/issues/58) are closed. #58's [asset report](MARIO64-ISSUE58-COURSE-ASSET-LOADING.md), [manifest](MARIO64-ISSUE58-ASSET-LOAD-MANIFEST.json), and [runtime evidence](MARIO64-ISSUE58-RUNTIME-EVIDENCE.json) include a BOB-to-castle transition and selected segment replacements; exact release timing remains unknown. [#59](https://github.com/BrantTanner/MarioCraft/issues/59) remains open for its remaining reachable progression-family observations. The [aggregate audit](MARIO64-ISSUE15-AGGREGATE-AUDIT.md) maps the unchanged parent criteria to these results.
