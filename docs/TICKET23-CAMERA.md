# Overworld camera - issue 23

Java now owns an explicit camera rather than rotating Minecraft's provisional
third-person view. The pinned native API has no camera entry point. The real
Minecraft player continues to own identity, targeting and server position; a
render-only entity supplies the viewpoint and is never spawned or ticked.
Minecraft's first-person 0.05-block translation is canceled at CameraSetup,
after verifying the mapped EntityRenderer transform order. Hand rendering is
suppressed. Native Mario mesh translation and camera vectors interpolate using
the same 30 Hz clock fraction; inspection hides the native mesh.

## Applicable contract

- Mode 16 follows Mario. C-left/right request a temporary orbit and settle after
  release; opposite simultaneous buttons cancel. Control yaw is separate from
  the yaw derived from rendered eye/focus vectors. Native camera-relative input
  uses the current control basis, never a display-frame interpolation.
- A fresh C-up edge while stationary and grounded enters mode 6 inspection.
  The movement stick and C-left/right turn the inspection view, while native
  stick movement is zero. C-down, A/B, airborne action or swimming exits it.
- Native ACT_FLAG_SWIMMING selects mode 8; leaving that action family restores
  mode 16. Water level alone does not classify the mode. This is a synthetic
  action contract until issue 26 supplies actual-world water integration.
- L/R do not rotate or change the idle mode. Research 47 observed no change in
  bounded ground idle pulses. Applying a no-op in other contexts is a deliberate
  current policy, not proof of universal original L/R behavior.
- Modes tied to original courses, bosses, cannons or scripted scenes are outside
  this Overworld integration. C-down outside inspection currently has no effect.
  Mouse deltas remain consumed by MarioMouse; no mouse-look was introduced.

Camera state advances once per native step. Pause collapses previous/current
camera vectors and the existing clock discards backlog; Start is still sampled
outside that clock. World/player teardown restores render-view ownership,
perspective, debug-camera setting, mouse helper and existing gameplay settings.
A new native session resets mode, poses and button edges, including on reload.
Rebasing native coordinates does not rebase world-coordinate camera vectors.

## Voxel policy and remaining fidelity deviations

The actual collision boxes are expanded by 0.12 block and intersected with the
focus-to-eye segment, with a 0.01-block inset. This is a conservative swept-box
approximation, not a sphere or the exact original query. Eight samples at 0.125
increments supply missing-chunk barriers; they are not eight original geometry
queries. When an obstruction occurs beyond half the segment, candidates within
30 degrees can adjust the render orbit with a 0.05 blend, without rotating input
or doing a full-circle best-view search. All segments, including interpolated
ones, are clipped again. Tight spaces shorten the camera toward its clear focus.

If smoothing places focus inside a changed solid, it falls back to the current
Mario anchor; a containing anchor triggers a camera-only axis search up to 1.6
blocks. Inspection clears its eye while preserving its look vector. A completely
enclosed neighborhood can still have no clear viewpoint; the camera remains
local rather than inventing a distant view through the world. This is an explicit
adapter limit. These rules do not change native collision recovery or Mario pose.

[Research 47](research/MARIO64-ISSUE47-CAMERA-MODES-CONTROLS.md) supplies numeric
modes, representative control transitions, distinct control/render state, follow
and bounded obstruction evidence. Its target constants 0.8/0.3 inspire separate
horizontal/vertical vector smoothing here. The original speed-field approach
step 0.05 is **not** proof of our yaw law. The chosen four-block distance,
20-degree land/10-degree water pitch, 45-degree C orbit with 0.1 settling,
0.05 movement-heading follow, inspection rates/limits and entry/exit gates are
reviewable adapter approximations. They are not fitted whole-trajectory retail
matches. Exact mode-specific original tuning, broader L/R contexts and alternate
C-down zoom behavior remain fidelity follow-up work. Research's roughly 1.3-second
C-right settling sample is a bounded comparison, not a universal deadline.
There is no Project64 runtime obstruction reference; no new emulator research
or fabricated reference capture is claimed.

## Validation and reproduction

The dedicated CameraProbe drives real Minecraft KeyBinding samples on an
existing natural bank in a fresh seed-20261026 Overworld, with no block edits.
It covers C-right/release, inspection/stick look/return, L/R idle pulses, a real
Start pause, walking/jumping, camera obstruction and save/unload/reload. The
legacy GameplayProbe frame-195 best-clearance visual override was removed.
Private framebuffer QA is retained separately from scalar assertions.

```powershell
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task build
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task client -CameraProbe
python scripts/verify-camera-trace.py .local/run-client/camera-trace.jsonl .local/camera-checks.json
```

The wrapper archives earlier results/trace/log/image files and requires fresh
passing JSON. The first 360-step result stays pending until the same world reloads
and a new camera session runs another 30 steps. Raw input/state traces, worlds,
logs, captures, ROM/native resources and generated geometry remain ignored under
`.local/`. Published scalar results contain no private artifact paths.

Seven camera unit cases cover pulse settling, mode/action/edge contracts, movement
basis separation, clipping/interpolation/pause, changed-solid focus, inspection
ceiling safety, reset and signed yaw wrap. Synthetic water/action tests establish
only the adapter contract, not in-world swimming proof. Earlier clock tests retain
30/60/144/240 Hz and pause/catch-up coverage. No all-terrain visibility or exact
retail camera equivalence is asserted.

The exploratory first run produced recognizable full-body Mario on a natural
foliage bank, and exposed an inspection look-target collision fault that is now
fixed. Its initial C/shoulder assertions were confounded by spawn settling and
residual C orbit. A second launch was canceled during loading after inspection
found stale site readiness in the new reload fixture. The third run passed all
camera and reload observations but failed its greater-than-one-block travel gate:
forward input faced the terrain behind the bank approach. The final recipe uses
backward input toward the checked clear approach; the travel gate is unchanged.
Those earlier runs are retained privately and are not passing acceptance evidence.

## Passing integrated observation

The final run selected the existing three-block bank at `(-236,65,131)`, facing
negative Z. Setup relocation occurred before native ownership; there were zero
terrain edits. It recorded 360 consecutive native camera steps, then saved,
unloaded, reloaded the same world and ran 30 more steps with mode 16 and the owned
camera restored. Both native contexts closed normally and the wrapper exited 0.
[Integrated scalars](validation/TICKET23-RESULT.json) and the independent
[trace checks](validation/TICKET23-CAMERA-CHECKS.json) preserve the passing values.

The 15-step C-right pulse produced 35.7349 degrees of control-yaw change. Forty
release steps (1.333 seconds at 30 Hz) left 0.5282 degrees; this is qualitatively
consistent with research 47's bounded settling observation, not an exact trace
match. Modes changed 16 to 6 to 16. Forty inspection steps included actual
movement-stick input with less than 0.01-block player displacement. Idle shoulder
pulses did not change yaw/mode. Native jump and later ledge-grab actions were
observed during the backward-input route, which displaced Mario 3.6559 blocks.
The route is terrain constrained and is not a flat-floor trajectory comparison.
Maximum observed control/render yaw separation was 8.9414 degrees. All recorded
eye/yaw/pitch values were finite. The smallest obstruction fraction was 0.0946251;
severe shortening can bring the camera close to Mario, rather than teleporting
to a distant angle.

A real pause froze native frame 170 and world tick 279. The final private
framebuffer at frame 200 shows recognizable full-body Mario and the surrounding
natural grass/dirt/trees with a clear silhouette. This is one representative view,
not a visibility proof for all voxels. No moved-wrongly/moved-too-quickly warning
occurred. The existing player/server bridge and world AI ownership are unchanged;
this camera slice does not repeat mob-combat acceptance or claim moving server
pose-error coverage.

The live post-optimization mesh rebuild samples were 112.6348, 61.4222, 75.0667
and 29.5921 ms (including reload). These still exceed a 33.3 ms step budget in
three cases and retain the known terrain hitch limitation from issue 22. They
are bounded observations, not a camera performance benchmark or a worst-case
bound. No performance rewrite was added to this camera slice.

The final Java 8/Gradle build passed all 24 unit tests, assembly and reobfuscation.
The seven new camera tests are synthetic adapter checks, including low ceiling
and newly containing solid behavior; they do not imply actual-world piston,
chunk-unload or swimming demonstrations. The in-game evidence covers the natural
bank/foliage obstruction, movement/action transitions, pause and world reload.
