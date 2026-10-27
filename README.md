# MarioCraft

Current status (26 October 2026): implementation is underway for the user's
focused scope of Mario movement, camera and Minecraft mob interactions. The
[current architecture contract](docs/FOCUSED-READINESS-AND-ARCHITECTURE.md)
supersedes the historical full-research gates below. This is an early opt-in
single-player slice, not the completed MVP.

## Build and run

On Windows with Python 3 and PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/bootstrap.ps1 -Launch
```

Bootstrap installs pinned project-local Java/native tools, builds libsm64,
builds the mod, applies the guarded Forge generated-artifact repair and launches
an isolated development client. The ROM is automatically found at
`~/Downloads/Super Mario 64/Super Mario 64 (USA).z64` and checked by SHA-256.
Native outputs, game resources and runtime worlds remain ignored under `.local/`.
The default Windows bootstrap was exercised with a newly downloaded project-local
JDK and a new Gradle cache through build and client launch. Online launch verifies
Mojang asset sizes/SHA-1 and fetches missing objects over official HTTPS, repairing
the legacy ForgeGradle icon-download failure without skipping its asset task.

With an existing Java 8 JDK and dependency cache:

```powershell
./scripts/dev.ps1 -JavaHome <jdk8-directory> -GradleHome <gradle-cache> -Task build
./scripts/dev.ps1 -JavaHome <jdk8-directory> -GradleHome <gradle-cache> -Task nativeSmoke
./scripts/dev.ps1 -JavaHome <jdk8-directory> -GradleHome <gradle-cache> -Task client -Gameplay
```

Controls: WASD at full magnitude, Ctrl walk (0.4), Alt intermediate (0.7),
Space=A/jump, J=B/attack, Shift=Z/crouch, arrows=C/camera, Q/E=L/R shoulders,
Enter=Start/pause/resume. Rebind under Options > Controls > MarioCraft; bindings
persist in Minecraft options. Controller mapping, axis inversion, radial cutoff
and slot selection are under Mods > MarioCraft > Config. The Windows controller
backend supports Xbox-compatible XInput devices, including disconnect/reconnect;
DirectInput-only devices are not supported. Release held controls after focus,
menu or connection changes. Mouse buttons may be bound; mouse look is disabled
while Mario owns the player. GUI mouse handling remains available.

See [configurable controls and validation](docs/TICKET20-CONTROLS.md) for the full
controller button/axis table, input ownership policy and synthetic-test limits.

See the [first-playable acceptance candidate](docs/TICKET19-FIRST-PLAYABLE.md)
for current evidence and its limits.

## Local validation

`-Task client -ClientProbe` exercises actual player/world load/save and reloads
one normal world. Initial same-process teardown exposed queued packet errors;
those are a probe limitation, not clean persistence acceptance.

`-Task client -GameplayProbe` alternates synthetic controller profile samples and real
Minecraft KeyBinding state without keyboard focus,
measures run/jump/landing and camera controls, and spawns a test-only NoAI pig
for deterministic outgoing-contact testing. Production mob AI is unchanged.
Before clearing a prior result, the wrapper archives it and its existing image
under a unique private run ID. The wrapper checks the private
`.local/run-client/gameplay-result.json`; process exit alone is not test success.
Each gameplay probe creates a fresh normally generated world with the same seed;
the lifecycle-only probe separately reuses its world. A real pause-menu interval
checks that native frames and world ticks freeze. Framebuffer captures remain private.

Native smoke independently checks C/JNA layout, ROM identity, native run/jump/land,
geometry and repeated teardown. Translation-only render interpolation avoids
blending changing native mesh topology. Asset-free tests check 30 Hz timing across
refresh rates and pause/stall handling.

Incoming damage is not yet converted to native Mario health/reactions. Camera
following and mob crossover rules are provisional. Full combat coverage, water,
hazards and final packaging remain incomplete; later gameplay tickets
remain open.

## Historical planning record

A personal project to bring the original Super Mario 64 player, movement,
camera, and combat into an actual Minecraft Java Edition 1.12.2 world.

## Planning record

- [Grill session: all 55 decisions](docs/GRILLING-SESSION.md)
- [Scope and research gates](docs/PROJECT-SCOPE.md)

Status: planning complete; final scope presented. Repository creation and
preservation of the grill session were requested next. Game investigation has
not started, and no prototype has been built.

Research order: Minecraft first, then Super Mario 64. Both research dossiers
must be reviewed and approved before prototype work begins.

This repository currently contains planning documentation only. Game binaries,
ROMs, extracted assets, credentials, tool caches, and emulator saves are excluded.

