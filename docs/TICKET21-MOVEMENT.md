# Ordinary advanced movement — issue 21

The existing pinned libsm64 integration already executes the ordinary move set.
This slice makes that claim reviewable through real Minecraft bindings, native
state/animation records and bounded normal-world scenarios. It adds validation
and test setup, not a second movement simulation. No production input or action
fault was found. Early failures to enter speed-dependent actions were caused by
nearby block faces; they are not reported as repaired control defects.

## Finite inventory and implementation ownership

Native backend: `fd11813208272b4271d92bd92feb8f3fdbe61be5`. Its `libsm64.c`
updates A/B/Z edge/down state and camera-relative stick input before
`bhv_mario_update`, then returns action, velocity, animation ID/frame and mesh.
Java forwards the complete sampled controls once per 30 Hz native step. It does
not choose or force actions, change their velocity or replace animations.

| Required family | Native ownership | Evidence status |
| --- | --- | --- |
| Analog walking/running, acceleration, turning, release/braking | `mario_actions_moving.c`: walking-speed update, walking, turning-around, braking/decelerating handlers | Integrated partial/full input in #20 and current baseline; turnaround/braking actions also recorded here. Exact complete acceleration trajectory comparison is unmeasured. |
| Normal jump and variable height, freefall/landing, air control | `mario.c` jump setup; `mario_step.c` gravity; `mario_actions_airborne.c` jump/freefall and air-control handlers | Integrated jump, A-release quarter-velocity rule and landings; separate native flat-floor held/released arcs and lateral airborne steering passed. Lateral steering trajectory was not compared in-world to Project64. |
| Double/triple jump and landing window | `mario.c` landing jump selector and airborne setup; airborne/landing handlers | Integrated normal→double→triple chain with fresh A edges after landings, all three actions and triple landing observed. |
| Backflip and sideflip | Crouch/turnaround gates, shared jump setup, airborne handlers | Integrated crouch→backflip and turnaround→sideflip plus landings; entry velocities and advancing animations checked. |
| Long jump | Moving crouch-slide gate, jump setup, `act_long_jump` | Integrated run→Z-slide→A, long-jump entry and landing observed. Pre-contact arc only is used for comparison. |
| Crouch, crawl, release; moving crouch slide | Stationary crouch/crawl transitions and moving handlers | Integrated stationary crouch, crawl, stop-crawl/stop-crouch, moving slide, release and settling observed. Detailed clearance belongs to #22. |
| Slope-initiated butt/stomach slides | Moving slide selection and butt/stomach-slide handlers | Retained natively; dedicated slope-trigger representatives are unobserved here. Ordinary voxel tops supply flat faces and steps rather than continuous inclines. This slice does not invent sloped terrain to trigger them. |
| Slide kick and slide/stop | Crouch-slide B gate; airborne slide-kick and moving slide handlers | Integrated run→Z→B, slide kick→slide→stop. Research's speed ≥10 gate is retained natively; the slow control is statically supported, not remeasured here. |
| Ground/air dive, dive slide and forward rollout | Moving dive-or-punch speed gate; airborne dive; moving dive-slide A/B exit | Integrated fast ground B and airborne B dives; dive-slide→A→forward-rollout and separate stomach-slide stop observed. Backward rollout handler is retained but not runtime observed; no negative-speed setup was invented. |
| Ground pound and landing/stop | Airborne Z gate, ground-pound state/timer and landing handlers | Integrated jump→fresh Z→ground-pound→land→butt-slide-stop; start and descent recorded. |
| Low-speed airborne B jump-kick | Airborne B gate and `act_jump_kick` | Integrated normal jump→B→jump-kick→landing observed. Full attack/contact classification belongs to #24. |

These families derive from research #11/37–40. Native handlers preserve their
speed gates, timers, momentum, gravity, animation choices and quirks. The inventory
is finite; it is not an all-action-by-terrain matrix. Wall kicks, ledges, ceilings,
tight-space collision and seams belong to #22; full camera modes to #23, mob
attack rules to #24, incoming damage to #25 and water to #26. Caps, cannons, stars
and course progression are excluded. No excluded/deferred research is completed
by this slice.

## Integrated evidence

The successful run used Forge 1.12.2 / 14.23.5.2864, Java 8u504, Gradle 4.9 and
the pinned native backend, in a fresh normally generated seed-20261026 Overworld.
The test inspected natural terrain within a bounded radius and selected an
existing five-block-wide flat runway extending Z −8 through +28 from
`(-257,64,-3)`. No blocks were placed, removed or rewritten and no obstacle-course
world was added. Initial server relocation happened before native ownership.
Before each movement recipe, test-only position relocation returned Mario to the
same start, followed by 40 neutral native steps. Action, velocity and facing were
never set. Existing surface refresh ran immediately after each setup relocation.
The baseline camera selected yaw 0, aligned to the +Z runway; no test camera
reset was introduced. Later flight can leave that checked rectangle and encounter
the surrounding generated terrain.

After the existing control/pause/contact checks, ten 180-step recipes used actual
`KeyBinding.setKeyBindState` and `MarioBindings.sample`. They did not call a native
action setter. Recipes in `AdvancedMovementProbe` specify held intervals in native
steps; the jump chain submits a fresh five-step A hold on each observed landing.
The trace contains every submitted input and resulting action, position, velocity,
forward speed and animation frame. The final result requires every recipe's
expected primary action, ground-pound impact and forward rollout. No successful
baseline JSON is written early. The independent scalar verifier additionally
checks expected intermediate/landing states, finite values, all 1,800 consecutive
steps and the following comparisons.

| First post-update state | Integrated value | Existing Project64 #39 sample |
| --- | --- | --- |
| Backflip vY / forward speed | 58 / −15.65 | 58 / −15.65 |
| Sideflip vY / forward speed | 58 / 9.15 | 58 / 9.15 |
| Long jump vY | 28 | 28 |
| Triple jump vY | 65 | 65 |

The numeric check tolerance is 0.001 native velocity units for output rounding,
not a new whole-trajectory equivalence tolerance. Backflip and sideflip each
reached 512 units above the flat takeoff floor and remained airborne for 31
recorded steps, consistent with the retained #39 representatives. Animation IDs
were respectively 4, 191, 19 and 193, and frames advanced within each action.
Normal-jump A release at movement step 970 changed vY 28.369263→7.0923157,
matching the researched quarter-velocity rule. Long/triple forward speeds differ
from the Project64 setups, so their complete arcs are not matched comparisons.

The clean long-jump segment rose through its first 240-unit apex. At step 636,
later in that recipe, vY changed −26→28 without an A edge near the retained
baseline contact pig. The native mob-admission path can bounce Mario; this
confounds the later arc/duration, which is excluded from reference comparison.
The first exploratory run, preserved privately, reached several actions but
nearby blocks interrupted speed-dependent recipes. It is not the passing run.

The successful baseline also retained a zero stationary client/server pose error,
real pause freeze, suppressed vanilla movement/use/dig/entity-use, two server
contacts on the separate NoAI fixture, and ordinary AI-enabled mobs with 401
updates. That pose sample precedes advanced recipes; there is no claim of a
per-action moving server-error sweep. Native ownership continuously canceled
vanilla living physics and applied native pose at server tick END. No
moved-wrongly/moved-too-quickly warning occurred. The client automatically closed,
saved and unloaded normally; the wrapper required fresh passing JSON.

## Other checks and reproduction

Native smoke separately passed ABI checks, two existing run/jump/land/geometry
cycles and new input-driven flat-floor checks. Stationary normal jump's first
post-update vY was 38; releasing A on the next step yielded 9.5. Held/released
peaks were 242/96.5 native units. Lateral input applied while airborne changed
velocity/position, the jump animation advanced and Mario landed. These native
checks are not Minecraft terrain or physical-controller evidence.
The final build, nine asset-free unit tests, JAR assembly and reobfuscation passed;
tracked source line endings were preserved and the diff whitespace check passed.

```powershell
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task build
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task nativeSmoke
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task client -GameplayProbe
python scripts/verify-movement-trace.py .local/run-client/movement-trace.jsonl .local/movement-checks.json
```

Published scalar evidence: [integrated result](validation/TICKET21-RESULT.json)
and [movement checks](validation/TICKET21-MOVEMENT-CHECKS.json). Raw logs, complete
input/state traces, worlds, captures, ROM/native resources and derived geometry
stay private under ignored `.local/`. Exact Project64 evidence and limitations
remain in the existing [jump report](research/MARIO64-ISSUE39-JUMP-ARCS-AIR-CONTROL-AND-COMBINATIONS.md)
and [crouch/slide/dive report](research/MARIO64-ISSUE40-CROUCH-SLIDE-DIVE-GROUND-POUND.md).
No new emulator session or research expansion was required.
