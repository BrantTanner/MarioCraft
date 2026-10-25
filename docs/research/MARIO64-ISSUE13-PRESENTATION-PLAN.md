# Mario 64 presentation research plan (#13)

**Status:** all five planned children #47–#51 have been independently reviewed, published, and closed. Root independently reviewed and passed the aggregate evidence audit in [MARIO64-ISSUE13-AGGREGATE-AUDIT.md](MARIO64-ISSUE13-AGGREGATE-AUDIT.md): all five original criteria are supported at the explicitly bounded representative scope, with residual limits retained. This plan update and audit have been published. Parent issue #13 remains open with its original checkboxes unchanged for the root's final update.

## Sequence and dependency graph

Run one research ticket at a time, in order, with one Luna agent at a time. All five are research-only. Finish #47 → #48 → #49 → #50 → #51, then perform the #13 aggregate acceptance audit. The sequence remains after #10 ROM/scope validation and before the #17 Mario dossier audit and #18 readiness review. Do not start implementation or gameplay prototypes during this sequence.

| Order | Ticket | Bounded scope | Dependencies |
| --- | --- | --- | --- |
| 1 | [#47 Camera modes, controls, following, and obstruction](https://github.com/BrantTanner/MarioCraft/issues/47) | Finite static inventory of all camera modes and controls; follow/position/angle/timing/obstruction code and data; repeatable ordinary-action and water-transition runtime scenarios. Include title/menu/cutscene/special mode inventory without a separate ticket for every mode. | #10; reuse #11, #12, #45, #46 |
| 2 | [#48 Model, skeleton, animation, and rendering pipeline](https://github.com/BrantTanner/MarioCraft/issues/48) | Player geometry/material/texture formats and linkage; representative skeleton/animation transitions; rendering/display-list/task path; RSP task/microcode interface inventory. Full hardware/microcode study belongs to #16. | #10, #47 |
| 3 | [#49 HUD, text, menus, and presentation UI](https://github.com/BrantTanner/MarioCraft/issues/49) | Overlay and UI inventory plus representative code/data/runtime evidence; distinguish game HUD from menu/screen systems; cite rather than duplicate #12/#44 health projection evidence. | #10, #12, #48 |
| 4 | [#50 Sound effects, voices, music, and sequencing](https://github.com/BrantTanner/MarioCraft/issues/50) | Audio engine/sequencer, representative formats/data and event triggers, Project64 runtime scenarios and explicit waveform/event evidence limits. | #10 |
| 5 | [#51 Private local decoder and asset-loading workflow](https://github.com/BrantTanner/MarioCraft/issues/51) | Reproducible local-only decoder/loading proof for representative texture, geometry, skeleton/animation, and audio resources, with independent byte validation. All derived outputs remain private/ignored. | #48, #50 |

The presentation sequence owns only formats and representative assets needed to understand and validate Mario's presentation. Later #15 owns the complete course, script, asset, and progression catalog; #15 is not a dependency for these tickets.

## Prior evidence anchors

- #11 establishes controller/action and representative movement timing, which camera scenarios can reuse.
- #12/#44 establish health state and HUD health projection; #49 should cite these findings.
- #45 traces current/previous camera floor-geometry fields and camera surface consumers. It does not establish camera control modes, follow behavior, collision/obstruction handling, or water transitions.
- #46 provides a bounded water entry/swimming/lava runtime route, reusable for camera observations.
- #10 validates the ROM/runtime tooling and assigns camera, character presentation, HUD, and audio to #13; it identifies candidate asset regions, not validated presentation decoders.

## Aggregate coverage checklist

The original acceptance wording remains unchanged. The status below records the local audit decision; the GitHub parent checkboxes remain unchecked until the root reviews the audit and authorizes the final issue update.

| Original #13 criterion | Planned coverage | Current status |
| --- | --- | --- |
| Trace camera modes, controls, following, collision/obstruction, timing across ordinary actions and water transitions. | #47; reuse #11/#12/#45/#46; title/menu camera ownership reconciled in #49 | Audited complete at stated scope; obstruction contact trial remains unperformed |
| Inventory model/texture formats, skeleton/animation state, rendering, HUD, text/UI, SFX, voices, and music systems. | #48, #49, #50; representative decoder validation in #51 | Audited complete as finite representative inventory; exhaustive catalog belongs to #15 and full hardware/microcode to #16 |
| Record representative Project64 camera/animation/audio scenarios tied to code and data. | #47, #48, #50 | Audited complete; numeric audio request/sequence state is not proof of audible output |
| Document reproducible local asset extraction/loading without publishing ROM-derived assets. | #48 defines representative formats; #50 defines audio resources; #51 validates local workflow | Audited complete for declared subset; outputs guarded to ignored local directory; one sample boundary and unsupported formats documented |
| Research all presentation behavior including non-MVP modes and identify coverage limits. | Finite inventories across #47–#50 and decoder limits in #51 | Audited complete under explicit finite-inventory contract; untested states/resources remain listed in dossiers and aggregate audit |

## Publication and evidence boundary

Publish human-readable dossiers, reproducibility scripts, safe manifests, and sanitized numeric observations only. Keep the ROM, derived assets, decoded geometry/images/audio, emulator states, raw RAM, and sensitive/local captures outside GitHub. Each child must distinguish target-byte evidence, runtime observations, inference, and unknowns; list untested branches and finite inventory boundaries. The #13 aggregate audit should close only after reviewing all child evidence and reconciling every original criterion and remaining gap.
