# Minecraft fire and lava - issue 27

Actual vanilla Minecraft fire cells and lava fluid volumes now drive Mario's
native hazard reactions. The pinned backend owns damage, action, animation,
forced movement, collision, interruption and recovery on the existing 30 Hz
clock. Vanilla fire/lava health damage remains canceled by HealthBridge. No
second burning timer or Java damage formula is added.

## Contact and crossover policy

Scale remains 100 native units per block. Fire uses strict overlap with the
entire actual fire block cell and Mario's axis-aligned body: radius 0.37 block,
height 1.6, or 1.0 with native SHORT_HITBOX. Boundary touching alone does not
ignite. This is the agreed Minecraft projection of the native body, rather
than the original object engine or the visual shape of a flame texture.

Vanilla BlockLiquid lava uses LEVEL: exposed source/falling cells occupy 8/9
block; flowing levels 1..7 occupy (8-level)/9. A lava cell with lava above is
full. Lava wins if the body overlaps both fire and lava. Other mod fluids are
not mapped. Fluid and body sampling use current world coordinates after rebase
and collision recovery, immediately before the native step. Unavailable cells
supply no hazard contact; existing terrain chunk barriers remain in force.

The exposed top of each actual lava cell supplies two upward SURFACE_BURNING
triangles at its quantized fluid height. Lava has a native supporting/bouncing
top in this crossover; it is not treated as swimmable water. This deliberately
maps a fluid surface to the original burning-floor behavior. No vanilla solid
block, world collision box, artificial fluid side wall or world block is added.
The fluid top is excluded from solid-body voxel depenetration. Existing terrain
triangles and SurfaceBoundary remain unchanged. Actual underlying solid support
still supplies native floor context above the bounded local fluid mesh.

Feet landing on that top invoke the original burning-floor helper and native
lava action, including repeated landings. A body already overlapping the lava
volume (including side, shallow or above-feet contact) invokes the original
burning-wall entry, using the nearest horizontal cell face normal pointing away
from the fluid; center ties use +X. This body-overlap policy is explicit and
is not represented as an original SM64 fluid-volume rule. Already active
LAVA_BOOST is not restarted by body samples; native floor recontacts own
repeated bursts. Swimming suppresses this side entry, matching the ordinary
burning-floor swimming exclusion. Native water classification remains the
connected feet-column policy from issue 26; neither biome snow nor Minecraft
currents are introduced.

## Native behavior and narrow entry adapter

Backend remains libsm64 fd11813208272b4271d92bd92feb8f3fdbe61be5. Its native
burning and lava handlers remain unmodified. It omits the original flame-object
entry from its public API, so scripts/native-hazards.c adds a mod-owned entry
adapter alongside the backend. This is a locally extended backend, not a claim
of unmodified upstream functionality. The adapter binds the requested instance;
it does not retain Minecraft entities, fake original objects or game assets.
Its temporary lava wall normal is restored before returning.

The flame entry follows interact_flame at the exact importer pin
[n64decomp/sm64 06ec56d](https://github.com/n64decomp/sm64/blob/06ec56df7f951f88da05f468cdcacecba496145a/src/game/interaction.c):
alive/non-intangible, no INVULNERABLE action or nonzero native recovery timer,
no metal/vanish cap, and no swimming/metal-water action or water surface more
than 50 native units above Mario. On admission it clears the native burn timer
and selects original BURNING_JUMP with argument 1, or BURNING_FALL for a descending
airborne Mario. No cap feature is added; ordinary cap-on-head state is retained.

- Native burning jump initializes vertical speed 31.5 and forward speed 8.
  Its first post-step export is Y +31.5, vertical speed 27.5 and animation 41.
  Burning fall retains its original fall animation. Landing enters burning
  ground; A can select the original burning jump. B/Z do not invent an escape.
- Native burning jump/fall/ground directly subtract 10 health per applicable
  update. Air adds 3 to the burn timer; ground adds 2 and exits when it exceeds
  160. Ground runs toward speed 32, with native minimum/maximum/steering/slope
  logic. Duration depends on time airborne and the original action transitions,
  not a universal wall-clock deadline. A neutral synthetic floor trial has 71
  damaging updates and recovery on update 72.
- Ground burning extinguishes if waterLevel-floorHeight exceeds 50; deep water
  uses the existing native plunge cancellation. Shallow-water interruption and
  water-contact rejection are synthetic native checks; no Nether water trial
  is claimed. The previous actual-world water evidence and waterSmoke remain
  regression coverage.
- Lava floor entry initializes vertical speed 84, forward speed 0 and queues
  12 hurt ticks with the ordinary cap-on-head state (18 without it). Each native
  hurt tick drains 64 health. First export is Y +84, vertical speed 80.8 and
  action 0x010208B7, animation 41. The original wall entry uses action argument 1
  and ensures forward speed at least 24. Lava deliberately does not use the
  external-hit invulnerability admission formula: the original floor/wall
  helpers own their gates. Lava recontacts requeue native hurt ticks.
- Original native collision, steering, wall reflection and landing bounces
  continue. Leaving lava does not clear the action: native solid landing can
  bounce twice, then enters LAVA_BOOST_LAND and normal recovery. Fire continues
  after leaving the fire cell until native recovery or interruption; sustained
  fire does not reset the timer while the action is protected, and eligible
  later overlap can ignite again. No parallel Java reaction timer exists.

Health/action snapshots still publish through HealthBridge, including 0xFF.
The external-request source remains non-causal request history. Dead hazard
entry is rejected. At lethal lava health the native LAVA_BOOST action continues;
its original level-warp callback is not a Minecraft respawn implementation.
Death/respawn remains issue 28 and persistence issue 29. Health-only test setup
is never used as a production revival mechanic.

## Actual-world result and comparison

[Result](validation/TICKET27-RESULT.json) and
[independent retained-trace checks](validation/TICKET27-HAZARD-CHECKS.json)
record 1,200 consecutive native steps in a fresh seed-20261026 normal save.
The probe generates the ordinary same-save Nether and relocates there with a
no-portal teleporter. This is not a custom test dimension or an obstacle course.
No terrain is edited. Natural generation emits bounded cascading-worldgen and
server-lag warnings during setup; these are not hidden as gameplay performance.
Natural sites are fire (-37.5,51.02,32.5) and lava (-15.5,32.2,-15.5).

Five 240-step phases use five explicit health setups and seven setup-only
relocations. They never set actions or velocities, and no health reset follows
death in this final run. Phase minima are 1466,1466,640,1408,255:

1. Actual fire contact enters native burning jump/ground and then recovers.
2. A later setup return to the real fire cell ignites again and recovers.
   Across both phases the trace records 141 adjacent -10 pairs. A real wall
   stops the forced run in 74 adjacent burning-ground pairs while native speed
   and damage continue; no terrain collision bypass is added.
3. Actual lava floor contact exports a rise of 84 native units (world 31.89 to
   32.73), vertical speed 80.8, action 0x010208B7 and animation 41. Two queued
   three-section bursts reduce full health to 0x280. Setup-only relocation at
   phase step 70 leaves the fluid while alive. Native bounces, lava-land and
   late ordinary alive actions establish recovery without an action reset.
4. Setup puts the body into real lava 0.5 block below the preceding start.
   One real body-overlap sample invokes the native burning-wall entry, retaining
   forward motion. Setup-only exit at step 15 leads to independently checked
   late alive native recovery. Minimum is 0x580.
5. A final 0x180 test-health setup at the real lava site reaches native 0xFF.
   Across lava phases there are 37 adjacent exact -64 pairs; sentinel clamps
   are separately retained. Vanilla server health remains full.

The existing Project64 references are reused, not new emulator measurements:
[#46 lava](research/MARIO64-ISSUE46-WATER-LAVA-RUNTIME.md) directly observed
floor type 1 at Y 0 followed by Y 84, vertical speed 80.8 and action 0x010208B7.
The current actual-world first post-update measurements match those scalar
values at 100 units/block. [#44 health](research/MARIO64-ISSUE44-HEALTH-AND-HAZARDS.md)
observed repeated 12-tick -64 lava bursts and the 0xFF sentinel. Current repeated
floor contacts and health deltas agree. Fire's detailed entry/duration and
water extinguishing checks are pinned-source/native-test evidence, not a newly
captured Project64 fire trial or frame-aligned emulator trajectory.

A separately retained earlier 960-step run validly demonstrates full-health
repeated actual lava exposure through 0xFF. Its later low-health phase incorrectly
followed a health reset after lethal exposure, so that phase is excluded from
acceptance and the final five-phase run replaces its pass predicate. The full
raw earlier trace remains private; its full-health lava sequence is supporting
observation, not substituted final evidence.

Normal native teardown, save/unload and process exit completed. No moved-wrongly
or moved-too-quickly warnings occurred. Final hazard gameplay mesh measurements:
69 rebuilds, maximum 51.7414 ms, five above 33.3 ms. This retains the terrain-hitch
limitation; it is not a worst-case bound or a performance improvement claim.

Private frame-200 QA shows recognizable Mario, five filled power slots, actual
Nether terrain/fire, and no vanilla hearts/food/armor. A lingering vanilla
first-person fire overlay obscures much of that retained frame after native
burn recovery. The final source therefore cancels only FIRE block overlays for
the current native-owned player. Water/block overlays remain unchanged. This
reversible presentation fix was compile/build checked after the passing run;
no later screenshot is claimed. Native particle flags are exported but this
slice does not add native particle rendering or native sound playback.

## Build, checks and reproduction

scripts/build-native.py hashes every native source/header, importer, compiler,
build script and mod-owned shim, alongside the upstream pin. The cached DLL is
reused only when its own SHA-256 and the input digest both match. Rebuilds stage
a new DLL, check hazard ABI/export presence in a short-lived loader, then replace
the output and write the manifest. Enumeration happens after the importer for
cold builds. ABI 1 is an additional runtime compatibility check, not a replacement
for content hashing. Missing/incompatible exports fail native session creation
cleanly. dev.ps1 verifies/rebuilds the native cache before Java compilation or
launch; do not rebuild under a live client. Generated DLL, imported geometry,
ROM, assets, worlds, screenshots and traces stay ignored under .local.

```powershell
./scripts/bootstrap.ps1 -Launch
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task build
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task hazardSmoke
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task client -HazardProbe
python scripts/verify-hazard-trace.py .local/run-client/hazard-trace.jsonl .local/run-client/logs/latest.log .local/run-client/gameplay-result.json .local/hazard-checks.json
```

The wrapper archives prior result/trace/log/framebuffer before the next probe
and requires a fresh passing JSON. Site search is bounded and fails without
manufacturing terrain. Asset-free boundary tests cover lava height levels,
strict body overlap and short-height distinctions. Synthetic hazardSmoke covers
first fire/lava update, fire timeout/reentry, shallow-water interruption, native
hit protection, water-contact rejection and full-health repeated lava death.
Existing native, water, health and traversal smoke checks are separate regressions.
The final build passes 42 unit tests, assembly/reobfuscation and all five smoke
tasks; [native scalars](validation/TICKET27-NATIVE-CHECKS.json) label their synthetic
provenance. The cache verifier also passes after its staged build/export check.
No full respawn loop, modded fluids, projectile fire policy, natural Overworld
fire/water transition trial or broad course/cap research is claimed.
