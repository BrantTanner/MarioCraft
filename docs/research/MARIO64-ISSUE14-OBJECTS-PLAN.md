# Mario 64 objects, enemies, and interactions plan (#14)

**Status:** #14 remains open with its original criteria unchanged. #52–#56 are closed. #60 has a finite static classification and one observed Bob-omb AI, contact and self-detonation sequence; its separate projectile contact with Mario remains open. #17 follows actual acceptance of #14, #15 and #16, and #18 follows #17.

## Sequence and dependency graph

| Order | Ticket | Bounded scope | Dependencies |
| --- | --- | --- | --- |
| 1 | [#52 Object runtime, structures, behavior dispatch, and lifecycle](https://github.com/BrantTanner/MarioCraft/issues/52) | Map only target-evidenced object fields, behavior/list processing, spawn/despawn, behavior-script/list data representation, and selected lifecycle examples. Do not claim an exhaustive per-course script catalog or enemy-family behavior. | #10 |
| 2 | [#53 Attack contact resolution and target outcomes](https://github.com/BrantTanner/MarioCraft/issues/53) | Trace punch, kick, stomp, dive, and ground pound through exact target contact gates, target outcome and Mario response code/data. Capture controlled positive plus rejected/immune outcomes with paired target result and Mario response. Mark untested action-by-target rows; no exhaustive runtime cross-product. | #52 |
| 3 | [#54 Enemy families, AI, contact, defeat, and projectiles](https://github.com/BrantTanner/MarioCraft/issues/54) | Build a finite family/specimen inventory and trace representative AI, body contact, defeat/removal, and at least one projectile lifecycle where present. Cross-reference #53 instead of repeating its five-action matrix for every family. | #52, #53 |
| 4 | [#55 Dynamic objects, moving platforms, and rider carry](https://github.com/BrantTanner/MarioCraft/issues/55) | Trace selected object transforms, geometry/floor handoff, carry and detachment with a controlled trial. Reuse #12 collision and surface results without duplicating them. | #52, #54 |
| 5 | [#56 Pickup and generic interactive effects](https://github.com/BrantTanner/MarioCraft/issues/56) | Trace controlled coin, healing, one-up and one generic door/switch interaction to object and Mario state outcomes. Stars, keys, caps, cannons, warps, course placement and progression remain #15. | #52, #55 |
| Follow-up | [#60 Enemy-family AI and projectile-contact coverage](https://github.com/BrantTanner/MarioCraft/issues/60) | Static classification of all 17 #54 candidates is published, including three unresolved structural names; select reachable family-specific AI/contact/defeat examples beyond #54 and check Mario projectile contact where reachable. Preserve remaining families and unavailable cases as untested. | #52, #53, #54 |

Each child links back to #14 and names its predecessors. The GitHub connector used for these issue updates does not expose a native sub-issue relationship operation, so reciprocal parent and dependency URLs plus this plan provide the relationship record.

## Prior evidence anchors and boundaries

- [#10 ROM and tooling report](https://github.com/BrantTanner/MarioCraft/blob/main/docs/research/MARIO64-ROM-TOOLING-AND-SCOPE.md) establishes the USA revision 0 ROM and Project64 workflow. The initial static correspondence is limited to the verified prefix; outside it, require a unique whole-block match against ROM bytes or corroborating runtime code.
- #11 supplies reproducible courtyard movement and action traces, but the courtyard has no nearby enemy. Its B-triggered `0x00800380` transition establishes an action change, not an enemy outcome.
- #12/#44 inventory health writers and static coin/recovery healing paths, and later #54/#60 enemy contact and #56 controlled pickup observations provide bounded runtime examples. #12 assigns moving-platform transforms to #14 and separates them from surface-force classification.
- #15 owns course placement and progression, including #59’s published static objective/cap and warp/state reports and their open runtime gates. #16 owns scheduling, memory, DMA, RSP/microcode, hardware, and saves; its #61–#65 evidence is published, with remaining requirements recorded per child. #17 audits the full Mario dossier only after #14, #15 and #16 actually meet their acceptance criteria; #18 is blocked by #17 and performs architecture and readiness review.

## Evidence and publication rules

Start runtime trials from legitimate reachable course entry with new private backups as needed. Preserve the canonical EEPROM and existing emulator states; do not force guest memory, cheats, or actions. Record Project64/ROM identity, seed provenance, exact target-code/data bounds, sanitized scalar observations, and known sampling limits. Keep ROM bytes, derived assets, raw RAM, save states, screenshots, local captures, emulator backups, and credentials outside GitHub. Publish only safe research prose, source/data references, sanitized numeric traces/manifests, and reproducible helper code that does not contain private capture data.

## Parent acceptance audit

After #52–#56 and #60, reconcile each original #14 criterion against the child dossiers: object/behavior inventory and lifecycle; all five attack/contact paths and Mario responses; representative Project64 scenarios corroborated by target code/data; dynamic geometry and action-specific vulnerability/immunity; and the subsystem map with explicit residual object/AI coverage. Keep finite inventory limits and untested families/actions visible. Do not mark the parent complete while required criteria or child work remains open.


