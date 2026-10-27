# Minecraft water swimming - issue 26

Ordinary vanilla Minecraft water now supplies the pinned native Mario simulation
with its actual connected surface height before each 30 Hz step. Native code owns
entry, swimming, acceleration/strokes, buoyancy, surface clamp, exit, underwater
health loss, surface healing, water knockback and drowning. No second oxygen,
damage or healing model was added.

## Volume and ownership contract

`WaterVolume` samples the column containing the center of Mario's feet. The feet
must actually lie inside a water block's fluid volume. It follows only vertically
connected vanilla `BlockLiquid` states, stopping at air, solids or unavailable
chunks. A disconnected pool overhead cannot flood an air pocket below it. At an
exposed top, source/falling water occupies 8/9 block and flowing levels 1..7 use
their actual `(8-level)/9` height; connected blocks below that top are full.
Lava and non-vanilla fluid implementations are not treated as water.

The height is translated using the current terrain origin and rounded to an
integer native unit (100 units/block). Quantization is at most 0.005 block. This
happens after origin rebasing/recovery and immediately before native tick; dry
samples submit local sentinel -11000. Rebasing keeps Mario within 5000 native
units, so this sentinel cannot submerge a dry Mario or manufacture a void floor.
Native collision still uses actual solid Minecraft geometry. Water contributes
no floor/wall triangles and no terrain is changed. Horizontal fluid boundaries
use this center-column policy; Minecraft flow currents are not independently
applied over native swimming. Biome temperature is not mapped to the original
special snow-terrain health branch; ordinary water uses the native normal-water
rules.

The pinned setter writes Mario's signed-short `waterLevel`; it does not query
Minecraft or replace collision. The native entry predicate is strictly below
surface minus 100 units. Entry reduces forward/vertical speed and sets feet at
surface minus 100. The native surface clamp is minus 80. A shallow source block
with a standing floor is therefore grounded fluid contact, not swimming. The
adapter forwards A/B/Z and analog input rather than forcing actions/velocities.

While swimming, W/forward (-Y in shared `ControlState`) pitches down, S/back (+Y)
pitches up, A/D steer, and Space/A strokes; holding/repeating A follows native
flutter/stroke timing. This matches the user-facing Project64 #44 observation:
A+AnalogUp dives and A+AnalogDown surfaces. Internally the pinned libsm64 wrapper
copies `stickY*64`, its swimming target pitch is `252*controllerY`, and vertical
speed includes `forwardVelocity*sin(pitch)`. Ground movement still uses its
camera-relative intended yaw. Native focused +/-Y checks verify the resulting
vertical signs; they do not claim a new emulator input capture. At the surface,
upward pitch plus a fresh A edge can produce the original water jump.

Camera mode 8 follows the native swimming action flag through the existing
camera contract, returning to mode 16 on exit. Its input update consumes the last
native action, giving a one-step mode transition delay. Native death actions stay
active; Minecraft respawn remains issue 28. Existing living-update ownership
suppresses vanilla motion/oxygen updates and `HealthBridge` cancels vanilla
drowning damage and healing. The eight-slot HUD continues to suppress vanilla
health/food/armor/air. The existing external-source field remains request history,
not a causal label for later underwater health changes.

`HealthBridge.Channel.swimRestoration` exposes immutable semantic anchors:
DRY, SURFACE, SUBMERGED or UNSAFE. Dead, intangible, invulnerable and recovery-timer
states cannot become swimming candidates. A later persistence implementation must
revalidate world identity, connected water and a clear body at the anchor before
creating Mario. This is a safe classification seam, not serialization of native
actions, structures or velocities, and does not implement issue 29.

## Bounded integrated evidence

The [world result](validation/TICKET26-RESULT.json) and
[independent checks](validation/TICKET26-WATER-CHECKS.json) describe 1680 native
steps in a fresh normally generated Overworld, seed 20261026. Site finding and
seven native position/health setups are explicitly test fixtures. They do not
write blocks, set actions/velocities, revive a native death state, or claim
unassisted travel between distant sites. Actual gameplay between those setups
uses the same input/native/player/world path as production.

- Actual shallow source water retained grounded actions. Falling into deeper
  natural water entered plunge and swimming; repeated A edges drove native
  strokes and traveled 9.7725 blocks in the first route.
- The first A pulse progressed through native breaststroke at 0.5, 1, 1.5, 2,
  2.5 and later 4, 5.5, 7, 8.5, 10 native speed, then swimming-end at 9.75.
  Later repeated pulses reached 24; this is a different input sequence from the
  single reference pulse and is not claimed as a matched full trajectory.
- A low-health surface fixture recovered by 26 per step (43 independently
  checked adjacent pairs), capped at 0x880 and settled within 0.011 block of the
  native 0.8-block surface offset.
- On a separately found natural shoreline, input produced water jump
  `0x01000889`, dry grounded actions on the bank, then a return through native
  plunge into water. There were 92 grounded and 120 swimming steps in that
  recipe. Camera modes followed 8 -> 16 -> 8. No relocation occurred inside
  this shoreline exit/return recipe.
- A real non-terrain-damaging Minecraft explosion requested six sections from
  original damage 11, caused native water knockback, reached health 0x280 and
  recovered at the surface. No separate vanilla health loss occurred.
- The final already-swimming low-health fixture started at 0x180 in deep water.
  It drained one per native step, reached 0xFF on its 129th step and entered
  `ACT_DROWNING` (`0x300032C4`). The verifier observed 128 adjacent -1 pairs.
  This fixture does not represent a measured full-health world drowning timer.
- Actual server health remained full and server air remained 300 in sampled
  observations. Native teardown and server save/unload completed normally,
  without moved-wrongly/too-quickly warnings. The existing private frame-200
  image shows recognizable Mario at the natural water bank and eight filled
  HUD slots, with no vanilla hearts or oxygen bubbles. It is a shallow-water
  view, not a screenshot of the submerged/death recipes.

The earlier failed fixture is retained privately: an overly close explosion
killed Mario before the drowning recipe. A health-only test reset correctly did
not revive its native death action. The successful run uses the prior damage
fixture's two-block blast separation. No further optional world replay followed
the required pass.

## Reference comparisons and synthetic checks

No emulator was launched or refocused. Existing Project64 observations remain
the references, not newly collected acceptance evidence:

| Existing reference | Current evidence and limit |
| --- | --- |
| [#46 water](research/MARIO64-ISSUE46-WATER-LAVA-RUNTIME.md): strict below water-100 entry, settled water-80, A stroke 0.5 -> 10 | Native smoke tests strict 100/101 entry and exact -80 clamp. Actual-world plunge and first-stroke values agree; the complete input/position trace is not a frame-aligned emulator comparison. |
| [#44 health](research/MARIO64-ISSUE44-HEALTH-AND-HAZARDS.md): deep -1, surface +26, cap 0x880, death sentinel 0xFF; up dives/down rises | Actual-world health deltas, recovery, drowning and native focused +/-Y velocity signs agree. A separate synthetic deep volume reaches lethal sentinel on full-health step 1921 (64.033 seconds at 30 Hz); this timing is native-source/test evidence, not a newly measured Project64/world full-breath trial. |
| [#47 camera](research/MARIO64-ISSUE47-CAMERA-MODES-CONTROLS.md): ground 16 -> water 8 and shoreline return 16 | Actual natural shoreline transitions and reentry agree. Existing camera tuning approximations remain; this adds no camera rewrite. |

[Native scalar checks](validation/TICKET26-NATIVE-CHECKS.json) additionally label
90 exact -1 steps, 44 +26 steps, dry exit, stroke, pitch signs and full-health
drowning in synthetic geometry. Asset-free tests cover separate water columns/
air pockets, flowing/falling heights, unavailable/dry samples, origin quantization
and safe/unsafe restoration classifications. These tests do not demonstrate
actual-world flowing waterfalls or modded fluids.

The final build passes 41 unit tests, JAR assembly/reobfuscation and native,
water, health and traversal smoke regressions. The world had 96 mesh rebuild
samples, maximum 73.3243 ms, five above 33.3 ms. This retains the known terrain
hitch limitation and is not a worst-case bound or a water performance claim.

## Reproduce

```powershell
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task build
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task waterSmoke
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task client -WaterProbe
python scripts/verify-water-trace.py .local/run-client/water-trace.jsonl .local/run-client/logs/latest.log .local/run-client/gameplay-result.json .local/water-checks.json
```

The wrapper archives earlier result/trace/log/image evidence and requires fresh
passing JSON. Normal generated site finding is bounded and can fail rather than
edit terrain to manufacture a route. All worlds, ROM/native/game-derived assets,
raw traces, logs and images remain private and ignored under `.local/`. Fire/lava,
respawn and actual persistence remain issues 27, 28 and 29.
