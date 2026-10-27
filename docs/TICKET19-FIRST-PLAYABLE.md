# First playable acceptance candidate — 26 October 2026

Issue [#19](https://github.com/BrantTanner/MarioCraft/issues/19) now has a
reviewable implementation and bounded integrated evidence. Closure remains for
review; this does not complete the focused mod or any broad research ticket.
The earlier [checkpoint](FIRST-SLICE-CHECKPOINT.md) records the first slice.

## Implemented and checked

- The documented Windows bootstrap downloaded and hash-verified a project-local
  Java 8 JDK, initialized a new Gradle cache, built the pinned native backend,
  compiled/reobfuscated the mod and launched the development Minecraft client.
  Existing pinned native archives were reused after hash verification; the
  Java/Gradle setup did not depend on the earlier development cache.
- The cold launch exposed the legacy ForgeGradle icon download failure. Online
  client launch now verifies the Mojang-declared asset index and object sizes
  and SHA-1 hashes, preserving valid cache entries and fetching missing objects
  from official HTTPS. The normal ForgeGradle asset task still runs. All game
  objects remain ignored in the selected local cache.
- Mario resources load automatically from the existing authorized USA ROM,
  verified by SHA-256, and pinned upstream geometry importer. No manual asset
  extraction, game assets or native DLLs are committed.
- A real LWJGL/JInput controller path samples normalized X/Y and buttons through
  the same mapper used by synthetic integration input. It preserves analog
  magnitudes above a documented 0.08 radial drift cutoff and caps diagonals at 1.
  The fixed profile is buttons 0=A, 1=B, 4=Z. Physical device calibration,
  persistent binding profiles and reconnect handling remain #20 work.
- Display uses translation interpolation between native 30 Hz positions without
  blending meshes whose topology may change. Pause collapses the previous pose
  to the current pose and discards accumulated time. Physics/server authority
  remain on the current native pose. Native animation still updates at 30 Hz.
- The baseline third-person camera matches vanilla's eight obstruction rays and
  selects an alternate yaw when clearance would collapse below 1.5 blocks. The
  corrected-camera run visibly showed recognizable full-body Mario. This is a
  baseline, not the original Mario camera modes or complete #23 obstruction work.

Five asset-free tests passed: controller analog magnitude/diagonals/combined
buttons/release/drift, invalid samples, 30 Hz totals at 30/60/144/240 display Hz,
pause/stall behavior and render interpolation including paused rendering.
These clock tests do not claim full-game trajectory equivalence at every refresh
rate. Final build, JAR assembly and reobfuscation passed on the new local tools.

## Integrated result

The [sanitized result](validation/TICKET19-RESULT.json) is copied from the
successful `ticket19-final-probe` run. The actual Forge client loaded a fresh
normally generated Overworld with seed 20261026; no course or terrain edits were
used. Synthetic controller state reached native simulation and the actual
Minecraft player. The route covered 5.47994559 blocks, rose 3 blocks in the jump,
and landed. This route includes generated ledges; these are observed route
measurements, not flat-floor reference tolerances.

A real pause-menu interval froze native frame 170 and world tick 218. The
stationary client/server pose error was 0 blocks at native frame 340. Ordinary
AI-enabled mobs had received up to 334 entity updates. One native-admitted
attack produced one accepted server hit on the separate NoAI contact fixture,
changing pig health 10 to 6. Production mob AI and collision/LOS admission rules
were unchanged. The fixture chooses a nearby unobstructed forward placement
using server block collision boxes and LOS, and explicitly fails if none exists.
No moved-wrongly/moved-too-quickly warning occurred in the successful run.
Native teardown, server/world save/unload and successful Gradle exit followed.

Camera clearance measured 4 blocks. Private framebuffer QA observed full-body
Mario in the earlier corrected-camera cliff run. Its image was overwritten by
another local probe capture; the latest retained frame has foreground terrain
occluding Mario below the cap. This is recorded as a camera limit, not complete
all-terrain visibility acceptance. Raw logs/images remain private and ignored.
The successful scalar result was preserved from the probe output and retained
successful log, not a current raw result file or raw-file hash. A later
optional visual-only replay was canceled before world load; it is not evidence.

## Reproduce and boundaries

From the checkout on Windows with Python 3 and PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/bootstrap.ps1 -Probe
# Interactive opt-in gameplay instead:
powershell -ExecutionPolicy Bypass -File scripts/bootstrap.ps1 -Launch
```

The probe creates a new normal world each run; world spawn/terrain affects the
route, so exact travel and screenshots can vary. The wrapper removes the prior
runtime result (archiving it and its existing image under a unique private run
ID first) and requires fresh passing JSON, rather than treating process
exit as gameplay success. All controller bindings and remaining keyboard/menu
behavior belong to #20; camera modes/foreground occlusion to #23. Incoming native
health/reactions, water/hazards, broader combat and final installed-mod packaging
remain incomplete. This validates the development launch path; it is not a
standalone distribution containing the JNA/native runtime.
