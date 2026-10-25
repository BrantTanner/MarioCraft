# Mario 64 issue 47: camera modes, controls, follow, and obstruction

Status: bounded target and runtime research complete; awaiting independent review. This covers a finite mode inventory and representative ground/water trials, not every mode placement or every obstruction geometry.

## Target and reproduction

Target ROM: USA rev 0, 8 MiB, SHA-256 `17CE077343C6133F8C9F2D6D6D9A4AB62C8CD2AA57C40AEA1F490B4C8BB21D91`. Project64 3.0.1.5664-2df3434 SHA-256 `C257C662D4E0B2CE1732B7F4A7D5DD4682F604CB082BBDBFCE5181470D6DBFB8`. Runtime reads used read-only `OpenProcess(0x0410)`/`ReadProcessMemory`; no RAM writes, cheats, physics changes, action forcing, or code patches were used. The known seed `issue35-mario-gameplay-baseline.pj.zip` (SHA-256 `6E2C8265A8A9A19761BCC6CE29FA20694FAD5444B325B1F22CA987776A2D6DFE`) was loaded for ground and water trials; a separate wake pulse was used only when the observed action was sleep. The unchanged N-Rage profile is SHA-256 `7A05EC7DFD8DDB939CB45A30B03B502544E1DD8892D9B48529670A77FC8D3E73`.

The supplied PowerShell sampler reads selected guest addresses after validating the Project64 process and brackets reads with the main-loop counter. Counter equality is not an atomic snapshot guarantee. The input helper validates process, HWND, path/hash and foreground state, releases keys in `finally`, and logs UTC edges. Reproduce with the three published scripts next to this report; route-driver early termination is described below. Every pulse is bounded; no claim is made about an unobserved placement or universal timing.

## Mode inventory and target path

The mode dispatch table at guest `8032DFA8..8032DFF0` (72 bytes) was independently read and uniquely byte-matched as one whole block to ROM `[0xE8FA8,0xE8FF0)`, SHA-256 `58ABF2D20CAD1918E9F3DE5CC2F167FC8154163F5C59B3D9022507D0405C7517`. This is a per-block match outside the validated linear map, not a global address delta. Zero-based entries are:

| Mode | Target handler | Bounded identification |
|---:|---:|---|
| 0 | null | no handler; setter normalizes requested 0 to 4 |
| 1 | `80280810` | pinned navigation enum: radial |
| 2 | `802816A0` | pinned navigation enum: outward radial |
| 3 | `80282D78` | pinned navigation enum: behind Mario |
| 4 | `80283A68` | pinned navigation enum: close |
| 5 | `80282C0C` | numeric only; no pinned enum label |
| 6 | `80285808` | pinned navigation enum: C-up / first-person |
| 7 | `80283A68` | numeric alias of mode 4 handler; no separate pinned enum label |
| 8 | `80283A18` | pinned navigation enum: water surface |
| 9 | `802850EC` | pinned navigation enum: slide/hoot |
| 10 | `80285ED8` | pinned navigation enum: cannon |
| 11 | `802826A0` | pinned navigation enum: boss fight |
| 12 | `80281904` | pinned navigation enum: parallel tracking |
| 13 | `80282280` | pinned navigation enum: fixed |
| 14 | `80280970` | pinned navigation enum: 8 directions |
| 15 | `802850EC` | numeric alias of mode 9 handler; no separate pinned enum label |
| 16 | `80283A68` | pinned navigation enum: free roam; shares handler with 4 and 7 |
| 17 | `80284D74` | pinned navigation enum: spiral stairs |

Names above are navigation aids from pinned `n64decomp/sm64` commit `9921382a68bb0c865e5e45eb594d9c64db59b1af`; the target proves numeric table entries and handlers. Target `FUN_802868F8` ordinary update switch has cases 1–4, 6, 8–14, 16, 17. Table presence does not imply all modes are dispatched by that ordinary switch: 0, 5, 7, and 15 are omitted there. When Area Camera cutscene byte `+0x30` is nonzero, the update bypasses ordinary mode dispatch and calls scripted path `FUN_802994E8`. The 18-entry table and ordinary switch describe the per-area world-camera path; they are not an inventory of every camera system in the application. Target evidence establishes the Area Camera update and its scripted branch, but I did not establish a title/menu camera caller or whether title/menu rendering shares these camera vectors or uses separate camera state. Classify title/menu camera ownership as unresolved, not absent and not covered by this table. Scripted scenes were not dynamically exercised. Exact handler names for numeric-only/aliased entries remain intentionally unassigned.

The mode setter `FUN_80286188` occupies PC `[0x80286188,0x80286420)` / ROM `[0x41188,0x41420)`, 664 bytes, SHA-256 `9CBE83C38D7774C900441D7E1DEFAE7A06A1045897E053FA550198F28E32CDC6`. It normalizes 0 to 4 and `0xFFFF` to the previous mode, stores the selected mode, updates transition state, and indexes the verified mode table. The ordinary update `FUN_802868F8` is PC `[0x802868F8,0x80286F68)` / ROM `[0x418F8,0x41F68)`, 1648 bytes, SHA-256 `EC248194AFBAC4C5FE56C064E2771017062A35C45C0CE9F829DF3B6EC81B4E72`.

## Camera follow, angle, obstruction

`FUN_80286420` (PC `[0x80286420,0x802868F8)`, ROM `[0x41420,0x418F8)`, 1240 bytes, SHA-256 `FCD6A5C781B2C400F22C18620A6D112449B5F480A17BCB8F155C14C31912410C`) transfers Area Camera focus/position goals into the Lakitu state, approaches current focus/position using separate horizontal/vertical speed fields, then copies those current vectors to render focus/position globals before later camera transforms. Default speed constants decoded in the target body are 0.8 and 0.3 with 0.05 approach step. These are target constants, not a guarantee that every mode uses the same values.

The sampler distinguished Area Camera control yaw at `+0x02` from the Lakitu current/goal/render vectors. It does not call control yaw the rendered view angle. In the sampled mode-16 ground C-right pulse, mask `0x0001` was present while held; yaw changed across signed wrap and continued settling after key-up, returning near its prior value in about 1.3 seconds in this trial. This is an observed window, not a universal turn duration or frame count. C-left showed opposite-direction response. C-up changed Area Camera mode 16→6 and set mode flag `0x2000`; C-down returned 6→16. In the sampled 250 ms R and L pulses, masks `0x0010` and `0x0020` were observed but no mode/default change followed in the captured idle window; this narrow negative does not establish behavior in other scenes/modes.

Ground locomotion-follow capture showed analog-up/right input followed by braking, jump-land, jump-land-stop, and idle actions. Mario changed from `(0,872.839,-1710)` to approximately `(247.25,803,-2150.27)`; camera render position changed from `(-2.48,1096,-1440.14)` to `(17.74,993,-2243.28)` while render focus tracked Mario from `(0,972.88,-1710)` to `(246.44,902.71,-2148.55)`. This directly demonstrates follow on the tested mode-16 ground route, not every camera mode.

Target `FUN_8028F914` has a Ghidra body of 896 instruction bytes at PC `[0x8028F914,0x8028FC9B]`. Its full contiguous envelope is PC `[0x8028F914,0x8028FC9C)` / ROM `[0x48914,0x48C9C)`, 904 bytes including an 8-byte gap between body blocks, SHA-256 `7373646B95271997554EFD96C19BBBBEBC05DF6F3A9EB7D2040EDB2B03660358`. It samples eight points at 0.125 increments along the segment and calls geometry query `FUN_80380E8C`; a first-query collision after sample 4 sets candidate flag `0x20` in `0x8033C84A` and computes a candidate yaw. A second query uses different extents; additional target predicates gate a bounded yaw adjustment and return value 3. This establishes the target obstruction-adjustment path, not that every collision changes the camera. The update's geometry buffer and current/previous floor-type dataflow are also described in [issue 45](MARIO64-ISSUE45-SPECIAL-SURFACE-CENSUS.md). Runtime trials here did not isolate a reproducible obstruction contact, so the obstruction mechanism is static target evidence, not a runtime-validated collision outcome. The skybox consumer alone is not treated as proof of the complete view-matrix path.

## Water runtime and timing observations

The route began from the known courtyard seed and reached water. The route driver's reverse-progress guard stopped it on the same bounded pulse that crossed the water threshold, so that trial is a nonzero/partial driver result, not a successful route completion. The trace nevertheless captures water threshold crossing and Area Camera 16→8. A separate stationary water sample settled in mode 8 at Y `-161`, with water level `-81`; a later A pulse produced a swim stroke and moved Mario `(2376.38,-161,3378.39)`→`(2517.04,-161,3485.41)` while render focus and position followed. Separate analog-turn pulses changed Mario facing yaw with little/no displacement; this is evidence of turning in place in the tested water state, not a general angular-speed law.

A bounded shoreline trial began in mode 8 and ended in mode 16 after action changed to `0x0400044A` and then idle `0x0C400201`; Mario rose from Y `-161` to `-136.59` at the bank. The sampled water-level field remained `-81` after exit; that unchanged value is not sufficient to classify the Mario state as underwater or on land. The mode/action transition and grounded floor/action state are the exit indicators. A UI view corroborated Mario at the shoreline but no screenshot is published.

Project64 main-loop observations are sampled at roughly 30 Hz, not per physics step. Host UTC key-down/up edges are in the local helper log and summarized in the companion manifest. A trace row can bracket several simulator updates; repeated counter values mean duration/sample count must not be interpreted as frame count. The unaligned sampler-v1 flag field was invalid and excluded from conclusions; only sampler v2 aligned reads are used here. Earlier controls-only probes that occurred during dialog/cutscene gating are excluded as behavior evidence.

## Evidence and limits

Exact ROM ranges, hashes, trial IDs, input timestamps, raw local trace hashes, and the executed helper hashes are in [the sanitized evidence manifest](mario64-issue47-runtime-evidence.json). Reproducible helpers: [read-only sampler v2](mario64-issue47-external-capture-v2.ps1), [guarded Project64 input helper](mario64-issue47-project64-input.ps1), and [bounded water-route driver](mario64-issue47-water-route-driver.ps1). Full raw traces and emulator-state files remain local and are not published.

This finite study covers the target mode table and ordinary update switch plus representative mode-16 ground and mode-8 water runtime. It does not exhaustively test each mode, title/menu scenes, all scripted transitions, all camera placements, or an obstruction collision. The water-route error is retained as a limitation rather than hidden. These camera findings complement [issue 12's aggregate audit](MARIO64-ISSUE12-AGGREGATE-AUDIT.md) and inform the parent research plan [issue 13](https://github.com/BrantTanner/MarioCraft/issues/13); they do not complete issue 13 or authorize implementation.
