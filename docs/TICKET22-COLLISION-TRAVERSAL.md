# Voxel collision traversal — issue 22

Mario traverses real Minecraft collision boxes through the pinned libsm64 action
and quarter-step handlers. Java supplies geometry and resolves changing-world
ownership; it does not select actions, set jump velocity or replace native movement.
The scale remains 100 native units per block for surfaces, motion, mob-coordinate
submission and rendering. Both client and server disable vanilla step-up under
native ownership; the passing actual-world result records `stepHeight=0`.

## Geometry and changing-world policy

`TerrainMesh` gathers real full/partial block collision boxes in the existing
bounded region (9 blocks horizontally, 8 below and 12 above Mario). `BoxSurfaces`
subtracts covered face rectangles, retains exposed partial faces and merges
coplanar rectangles. Indexed face planes and block-sized UV buckets bound the
occluder candidates. Outward winding preserves native floor, wall and ceiling
classification; integer surface vertices round by at most 0.005 block.

A discovered integration fault affected thin solids: native wall queries accept
negative plane distances within the query radius. Submitting both opposing faces
of a thin box could sum their projections and push a long jump through it.
`SurfaceBoundary` selects exterior-facing vertical faces from Mario's position
before each native step. Floor/ceiling faces and all surface metadata are retained.
The selected native array is reused until sidedness changes. This adapts closed
voxel volumes to native wall queries while leaving action gates, collision radii,
four quarter-steps, velocity, wall kicks and ledge logic in libsm64. The failing
thin-wall case now stops and bonks; regressions cover widths 1, 13 (rounded pane)
and 25 (fence) native units at a long-jump approach speed of 45.541267.

Bounded local world notifications invalidate the mesh before the next native
step. A per-step loaded-neighbor mask also catches chunk arrival/removal. Missing
chunks supply temporary solid barriers instead of missing collision. Meshes
otherwise rebuild after movement of at least two blocks, rather than on an
unconditional ten-step timer. Coordinate rebasing at 5,000 local units is retained.
Listeners are removed on session teardown, including partial initialization failure.

If an updated solid contains Mario's vertical probe center, `VoxelRecovery`
selects the nearest clear, supported exit within 160 native units. It changes
position only; native action/velocity remain intact and the subsequent native
pose is published through the existing client/server bridge. If no such exit
exists, native steps freeze until geometry changes rather than jumping through
distant solids. This is a conservative changing-volume policy, not native crush
damage, piston animation equivalence or a general teleport escape mechanic.

## Rules and geometry limits

Native wall-hit/soft-bonk timing and fresh-A wall-kick gates remain unchanged.
Ledge grabs retain descending-motion, displaced-against-velocity and behind-wall
floor-search conditions. The 100-unit lower rejection and 160-unit search offset
are preserved; natural three-block banks can supply valid grabs and climbs.

Crouching/crawling shortens Mario's object hitbox to 100 units, but native ground
collision still requires floor/ceiling clearance greater than 160 units. It does
not permit crawling through a 1.2-block tunnel. The synthetic 120-unit tunnel
correctly stops crawling and standing release. Likewise, a 50-unit partial-box
step can catch the lower wall probe and require jumping. Vanilla auto-step is
not added to bypass either rule. Flat voxel tops provide steps rather than
continuous inclines; slope-specific slides and hangable-ceiling actions are not
invented by marking ordinary blocks as special surfaces or editing terrain.

## Observable evidence

The final Forge client ran in a fresh normally generated seed-20261026 Overworld.
A bounded setup search selected an existing three-block bank at
`(-195,45,112)`, facing +Z. No blocks were placed, removed or rewritten and no
obstacle-course world was created. Setup-only relocation preceded native ownership
and each recipe, followed by 40 neutral steps. The wall-kick recipe sends its
initial A edge when the measured bank distance is at most 1.1 blocks, releases A,
then sends a fresh edge on observed soft bonk. The ledge recipe holds A during
steps 45–64. Both use real `KeyBinding` state and `MarioBindings.sample`.

The 360 consecutive recorded steps show wall hit → soft bonk → wall kick and a
separate ledge grab → slow climb 1 → slow climb 2 → grounded movement. First
post-update wall-kick vY was 58, matching the existing Project64 #41 representative;
this is a transition comparison, not whole-trajectory equivalence. Climb animation
advanced. All poses were finite, the vertical probe center remained clear of
actual world boxes, and the final pose was grounded. The stationary server pose
sample at frame 340 had zero error. Native teardown, normal save/unload and the
wrapper's fresh-result check completed. No moved-wrongly/moved-too-quickly warning
occurred. Prior unsuccessful search/input recipes remain private and are not
passing evidence.

[Actual-world result](validation/TICKET22-RESULT.json) and the independent
[trace checks](validation/TICKET22-TRAVERSAL-CHECKS.json) record these observations.
Existing research #42 never captured a successful Project64 ledge grab or isolated
ceiling contact. Those reference conditions remain code-derived; this ticket adds
observable native/Minecraft cases without inventing missing retail evidence.

Thirteen separate [native scenarios](validation/TICKET22-NATIVE-CHECKS.json) use
the same box/sidedness adapter with purpose-built synthetic geometry. They cover
wall kick, grab/climb, 250-unit ceiling response (peak 89.843765), 120-unit crouch
clearance, adjacent voxel seams, a wall junction, jumping onto a 50-unit partial
box, a 200-unit drop to freefall/landing, wall removal, a newly containing solid
with bounded recovery, and the three thin-wall widths. These are native adapter
checks, not actual-world pane/fence, cave-ceiling, piston or chunk-unload gameplay
proof. Eight added unit tests cover union area, internal-face removal, partial
contact, winding, empty geometry, exterior selection/metadata/cache, supported
recovery and no-exit freeze. All 17 unit tests and final build/reobfuscation pass.

## Cost, reproduction and boundaries

The passing world run was captured before the final UV candidate-index
optimization. Its six rebuilds used 516–776 selected triangles and took
122.064–186.4709 ms (median 140.3153), including gathering/meshing/native load.
These are material hitches: a full-speed run can cross the two-block refresh
threshold about every seven native steps. The bank's stationary intervals do
not establish a general frame-time rate.

A subsequent non-gameplay profile used 2,892 synthetic voxels. It isolated warmed
geometry CPU near 20–22 ms, with much smaller native load, and showed large cold/
JIT spikes. UV bucketing then produced the exact same 88 outward triangles
(compared by complete sorted vertices/winding, not count alone). The optimized
synthetic builds took 17.5243–29.3377 ms, sidedness 0.791–5.5117 ms and native load
0.1916–0.8309 ms. Cold initialization and live terrain costs are not bounded by
that benchmark. The [cost record](validation/TICKET22-MESH-CHECKS.json) preserves
the pre-optimization world measurements and labels the synthetic comparison.
The final optimization passed the geometry comparison, unit tests and all native
regressions; no duplicate whole-world replay was used to imply new timing proof.

```powershell
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task build
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task traversalSmoke
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task client -TraversalProbe
python scripts/verify-traversal-trace.py .local/run-client/traversal-trace.jsonl .local/traversal-checks.json
```

The bounded normal-terrain search can fail if no bank is found; it never edits
terrain to manufacture a pass. The probe archives earlier result/trace/log files
and requires fresh passing JSON. Raw traces, worlds, logs, captures, ROM/native
resources and generated geometry remain private under ignored `.local/`.

This is representative ordinary traversal coverage, not an exhaustive shape,
speed/glitch or terrain-update matrix. Actual-world partial-block and live chunk
arrival/removal traversal, moving piston animation, tightly enclosed crushing and
worst-case terrain performance are not runtime-proven here. Loaded barriers and
bounded recovery are explicit adapter policies; incoming damage/crushing remains
#25. Full camera behavior remains #23, mob rules #24 and water #26. No new emulator
session, broad research or excluded progression mechanics were added.
