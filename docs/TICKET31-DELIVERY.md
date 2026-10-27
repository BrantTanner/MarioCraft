# MarioCraft focused delivery  -  issue 31

## Supported experience

MarioCraft is a local Windows, single-player Minecraft 1.12.2 / Forge 14.23.5.2864
client with the original USA Mario model, textures, animation, native movement,
voice/action synthesis, eight-section health and lives. Minecraft supplies ordinary
world generation, blocks, day/night, mobs, background music and saving. Supported
worlds are Normal Survival Overworld saves without Hardcore. Unsupported mode,
difficulty or dimension opens a paused explanation with Save and Quit; admission
does not change those saved settings. New worlds select Normal automatically.

This is the user's focused movement/camera/Minecraft-creature scope. Parent
stories 33-34 requesting complete-release research were superseded/deferred by
the recorded scope decision, not completed. The broad research issues remain
separate. The existing focused Project64 references are reused; no additional
emulator or desktop automation was needed for this delivery.

## Requirements and evidence reconciliation

Numbers refer to the [parent user stories](https://github.com/BrantTanner/MarioCraft/issues/1).
The linked slice reports identify actual scenarios, private raw evidence,
sanitized scalar checks, reference identity, tolerances and limitations.

| Stories / requirement | Implemented behavior and observed acceptance |
| --- | --- |
| 1, 24 | [30 presentation/audio](TICKET30-PRESENTATION.md): Minecraft title/world/pause/settings; actual PCM, music independence, volume/focus/pause/reload/cleanup checks. Final production runtime replay checks SRG audio reflection. |
| 2, 23 | [19 normal generated world](TICKET19-FIRST-PLAYABLE.md), [24 ordinary AI](TICKET24-MOB-COMBAT.md); Normal-only admission repaired in #79. Final long-distance input scenario checks fresh natural chunks and watched-player handoff. |
| 3 | [19 native geometry/rendering](TICKET19-FIRST-PLAYABLE.md), [21 animation](TICKET21-MOVEMENT.md), [30 original audio](TICKET30-PRESENTATION.md). Game-derived assets stay local. |
| 4, 12, 13 | [21 movement](TICKET21-MOVEMENT.md), [22 collision/traversal](TICKET22-COLLISION-TRAVERSAL.md): native actions/acceleration/jumps/crawl/wall/ledge/ceiling; auto-step disabled. Natural terrain fixtures, no obstacle-course world. |
| 5, 11 | [19 clock/scale](TICKET19-FIRST-PLAYABLE.md), [21 timing](TICKET21-MOVEMENT.md): 30 native Hz, 100 units/block, display-independent clock, bounded catch-up, pause backlog discard, translation interpolation. |
| 6-8 | [20 controls](TICKET20-CONTROLS.md): keyboard magnitudes/rebinding, XInput analog profile, reconnect/focus gate, persisted settings; adapter-synthetic controller proof, no physical controller acceptance claim. |
| 9 | [23 camera](TICKET23-CAMERA.md), [26 water camera](TICKET26-WATER.md): explicit modes 16/6/8 and C controls, voxel obstruction; tuning/query differences remain explicit. |
| 10 | [22 real collision-box union](TICKET22-COLLISION-TRAVERSAL.md): exposed surfaces, missing-chunk walls, bounded real-geometry recovery; final fresh-chunk runtime replay. |
| 14-16 | [24 native combat](TICKET24-MOB-COMBAT.md): punch/kick/stomp/dive/ground-pound damage 3/4/5/6/8, server revalidation, once per action/target, actual zombie/bat/skeleton AI and loot; final production replay with scope guards enabled. |
| 17-20 | [25 incoming health/fall](TICKET25-MARIO-HEALTH.md), [28 regression](TICKET28-RESPAWN.md): native invulnerability/hurt/fall; creature/projectile/explosion conversion ceil(MC damage/2), bounded 1-8. |
| 21 | [26 water](TICKET26-WATER.md): actual swimming/surface/deep healing/drowning/shore transitions; native water state drives camera mode. |
| 22 | [27 fire/lava](TICKET27-FIRE-LAVA.md), [28 regression](TICKET28-RESPAWN.md): native burning/lava boost/damage/death, vanilla duplicate damage suppressed. |
| 25-27 | [30 scope boundaries](TICKET30-PRESENTATION.md): mining/inventory/armor/hunger/item/XP interactions and alternate packets blocked; Mario object/progression engine absent; multiplayer/LAN/portal entry blocked. #79 adds existing non-Overworld admission rejection. |
| 28-30 | [28 death/respawn](TICKET28-RESPAWN.md): every native death pays one life and shows real Minecraft respawn screen, zero resets to four, bounded safe spawn, retained inventory/XP/world. |
| 31-32 | [29 persistence](TICKET29-PERSISTENCE.md): UUID-scoped versioned location/health/lives, actual pause/save/disconnect/reopen, safe idle/swim, pending-death completion once, corrupt/missing/unsafe fallbacks, world isolation. |
| 33-34 | Superseded/deferred full-game research; focused architecture/reference review completed before gameplay. Not represented as completed broad research. |
| 35 | Focused reports distinguish direct observations, adapter choices/inferences and unresolved fidelity limits; this evidence-discipline requirement remains applicable and fulfilled. |
| 36 | [21](TICKET21-MOVEMENT.md), [23](TICKET23-CAMERA.md), [25](TICKET25-MARIO-HEALTH.md), [26](TICKET26-WATER.md), [27](TICKET27-FIRE-LAVA.md): retained repeatable Project64 source observations and explicit comparison limits. |

## Reproduction and local artifact

On the authorized Windows host with Python 3 and PowerShell, from a source checkout:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/bootstrap.ps1 -Launch
```

Bootstrap downloads hash-pinned project-local Java 8u504, LLVM/native source,
Gradle/Forge build dependencies and official Minecraft assets, builds/reobfuscates
the mod and assembles `.local/MarioCraft/`. ROM discovery uses the existing authorized
USA ROM in `~/Downloads/Super Mario 64/Super Mario 64 (USA).z64` and validates it.
No code edits, asset extraction, external launcher configuration or credentials
are required. First setup needs network access. It does not modify an installed
Minecraft launcher or existing user worlds. The playable runtime has its own
`game/saves`, options and logs; opt-in probes use a separate `probe-game` directory.
The delivered user game starts with only the mod installed. Runtime metadata
records payload SHA-256 checksums; the launcher verifies them before starting. Launch again using `.local/MarioCraft/Launch MarioCraft.cmd`.
This private artifact contains local game/native assets and must not be uploaded.

The build uses its checkout's `.local/gradle-home` explicitly. Packaging reuses only
that build's verified dependency metadata/JNA and hash-checked asset objects; it
copies no development saves, options, logs, ROM or Gradle launch classpath. The
runtime uses the reobfuscated mod JAR, official obfuscated Minecraft client and
Forge FMLTweaker, bundled Java runtime, verified libraries/assets and local DLL.
The Java-only profile explicitly excludes the installer manifest's optional
Scala/Akka language adapters (exact coordinates recorded in `runtime.json`):
legacy `_mc` continuations artifacts are unavailable from official hosts. No
third-party mirror or replacement binary is used. Additional Scala mods are
outside this isolated package's supported profile.

Controls: WASD; Ctrl walk 0.4; Alt intermediate 0.7; Space jump/A; J attack/B;
Shift crouch/Z; arrows camera/C; Q/E shoulders; Enter pause/Start. Keyboard
rebinding is in Settings > Controls. MarioCraft Controls configures XInput,
analog cutoff/inversion and original-sound toggle. Minecraft music/master and
Players volume remain in Sound Settings. No mouse-look. Save and Quit normally
before closing. See [20](TICKET20-CONTROLS.md) for the controller profile table.

## Final validation

The production combat replay passed 1,440 consecutive frames: eight distinct
creatures, exactly one accepted attempt per recipe, all five attack types,
ordinary zombie/bat/skeleton AI, incoming zombie/arrow contact and retained loot.
The distance replay traveled 176.162695 blocks across 11 chunk boundaries in
951 native frames. Six fresh chunks beyond 80 blocks supplied actual natural
water data on both client and server (16 samples each); 31 server watcher checks
passed. Collision surfaces remained nonempty. It used only native A swimming
strokes after a labeled initial setup relocation; no terrain/action/velocity edits.

The production presentation replay measured 29.9992 rendered/native Hz at a
30 FPS cap, and 143.2892 rendered Hz / 30.0397 native Hz at a 144 FPS cap over
100-step windows. Actions, vertical/forward velocity and native height matched
at native frames 60, 100, 130 and 160 within 0.001 native units. Acceptance uses
a 29-31 native-Hz scheduling envelope for these short windows; this does not
weaken the rational clock's exact deterministic cadence tests. Both original
audio peaks were 22,378, managed playback and Minecraft music were active, and
pause/focus/mute/reload/cleanup passed. Unsupported Peaceful, Nether and End
fixtures were rejected, retained their intended difficulty/dimension/mode and
closed without creating a native Mario owner. The private Peaceful fixture
locks its intended setting to prevent vanilla's initial client WorldInfo from
rewriting the test setup to Normal before admission.

Final run values and independent checks are recorded in the sanitized issue-31
validation JSON. Earlier evidence is reused for unchanged movement/camera/health/
water/hazard/lives/persistence behavior; this delivery adds actual production
runtime combat, long-distance chunk handoff, unsupported admission, presentation
and a separate clean-checkout build/launch. Failed fixture runs are retained
privately and excluded from passing results.

The independent delivery verifier passed all 42 checks. The final clean build
passed 51 unit tests with zero failures/errors/skips. Its full production replay
at source `83aa436` measured 30.0495 rendered / 29.7520 native Hz at cap 30, and
142.7952 rendered / 29.9990 native Hz at cap 144; all four matched native frames
passed. The final UI-only anchor delta at `32460d3` used a separate short clean
HUD launch (at least 165 native steps, one native create/close). Its framebuffer
shows active recipe/advancement toasts, unobscured eight-section POWER and LIVES,
and no tutorial or crosshair. These distinct runs are not merged into one
historical result. All world-phase error counts are zero.

The clean checkout initially had no `.local` directory. It downloaded its own
JDK, LLVM/native source, Gradle, Minecraft/Forge dependencies and assets. Setup
repairs were rerun against that checkout using only its own downloaded/generated
cache; no development cache, native output, options or worlds were copied in.
Final source was rebuilt/reobfuscated there before launch. The private playable
artifact was assembled from that clean build and verifies 45 payload SHA-256
checksums. Its user game contains only the mod JAR, with no probe worlds or logs.
Sanitized evidence is in [delivery checks](validation/TICKET31-DELIVERY-CHECKS.json),
[combat checks](validation/TICKET31-COMBAT-CHECKS.json),
[clean replay](validation/TICKET31-CLEAN-CHECKS.json),
[final HUD](validation/TICKET31-HUD-CHECKS.json) and
[build/provenance](validation/TICKET31-BUILD-CHECKS.json).

The asset-free clock checks simulate 30/60/144/240 display Hz over ten seconds
and require exactly 300 native steps; they also verify pause/stall backlog rules.
These are deterministic clock checks, not measured GPU refresh equivalence.
The retained movement velocity comparisons use 0.001 native-unit rounding tolerance
for named samples, not a whole-trajectory equivalence envelope.

The official Forge startup emits its missing-signature-data diagnostic. Final
world-phase error counts are reported separately; no global zero-error claim
is inferred from a successful launch. The title framebuffer was inspected at
854x480 and its labels/buttons fit without overlap. Final visual review also
found and resolved #80: the private MarioCraft profile disables vanilla tutorial
prompts through the Tutorial API, and native ownership hides the unused
Minecraft crosshair. The final clean framebuffer anchors POWER and LIVES at the upper left, with
lives below the meter, clear of ordinary right-side advancement/recipe toasts.
Minecraft advancement and recipe logic remain active.
Unrelated vanilla profiles are outside the isolated game directory.

## Residual limits

- Camera follow/yaw/pitch/distance, swept voxel query and inspection gates are
  documented adapters, not perfect original camera trajectories. Broader L/R and
  alternate C-down behavior remain limited as reported in issue 23.
- Exact native movement/actions do not imply identical cross-game terrain or mob
  behavior. Damage, mob hit cylinders, knockback, water columns, safe-spawn and
  structural-death policy are explicit Minecraft integration rules.
- Terrain rebuilds can hitch (prior measurements roughly 30-100+ ms); the clock
  catches up at most five steps then discards excess backlog. No worst-case
  performance or real-time equivalence under heavy stalls is claimed.
- XInput hardware handling exists; physical-controller usability was not tested.
  DirectInput-only devices are unsupported.
- Project64 comparisons cover retained representative actions/timings and bounded
  camera/water/damage observations, not exhaustive trajectory or audiovisual
  equivalence. Native audio output proves nonzero managed playback and lifecycle,
  not a listening-quality comparison.
- Private local Windows delivery only; no public ROM/game-asset distribution,
  multiplayer, other dimensions, course/object progression or complete-release
  research completion is claimed.
