# MarioCraft

Current status (27 October 2026): the focused local MarioCraft experience is
implemented for Normal Survival Overworld worlds: Mario movement, camera,
Minecraft creature combat, health/water/hazards, lives, saving, menus and audio.
See [delivery evidence, requirements matrix and residual limits](docs/TICKET31-DELIVERY.md).
Full-game research/course/progression work remains superseded or deferred.

## Build and run

On Windows with Python 3 and PowerShell:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/bootstrap.ps1 -Launch
```

Bootstrap installs pinned project-local Java/native tools, builds and reobfuscates
the mod, then assembles and launches a private production Forge client under
`.local/MarioCraft/`. Later use `.local/MarioCraft/Launch MarioCraft.cmd`. Its worlds
and settings live under `.local/MarioCraft/game/`. The authorized USA ROM is found
automatically at `~/Downloads/Super Mario 64/Super Mario 64 (USA).z64` and checked
by SHA-256. First setup needs network access; no game asset extraction or manual
tool configuration is required on this Windows host with Python 3/PowerShell.
Do not upload this local package: it includes private game/native resources.

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

`-Task client -PersistenceProbe` is the issue-29 save/resume acceptance path. It
uses the real pause-menu disconnect lifecycle across two normal disposable saves,
with ground/water/action/death checkpoints and repeated world switching. Run
`scripts/verify-persistence-trace.py` for independent lifecycle/error checks; see
[the persistence policy and evidence](docs/TICKET29-PERSISTENCE.md).

`-Task client -GameplayProbe` alternates synthetic controller profile samples and real
Minecraft KeyBinding state without keyboard focus,
measures run/jump/landing and camera controls, and spawns a test-only NoAI pig
for deterministic outgoing-contact testing. Production mob AI is unchanged.
Before clearing a prior result, the wrapper archives it and its existing image
under a unique private run ID. The wrapper checks the private
`.local/run-client/gameplay-result.json`; process exit alone is not test success.
The probe also runs ten bounded ordinary movement recipes on inspected natural
terrain, recording input/action/velocity/animation scalars. See
[movement evidence and setup limits](docs/TICKET21-MOVEMENT.md).
Each gameplay probe creates a fresh normally generated world with the same seed;
the lifecycle-only probe separately reuses its world. A real pause-menu interval
checks that native frames and world ticks freeze. Framebuffer captures remain private.

Native smoke independently checks C/JNA layout, ROM identity, native run/jump/land,
geometry and repeated teardown. Translation-only render interpolation avoids
blending changing native mesh topology. Asset-free tests check 30 Hz timing across
refresh rates and pause/stall handling.

`-Task client -CombatProbe` exercises punch, kick, stomp, dive and ground pound
fixtures plus ordinary AI-enabled zombie, bat and bow-skeleton contacts. See
[outgoing combat rules, evidence and limits](docs/TICKET24-MOB-COMBAT.md) and the
[Overworld camera implementation](docs/TICKET23-CAMERA.md).

Native incoming health, falls, water, fire/lava, lives/respawn, persistence and
presentation are implemented. Slice reports [25](docs/TICKET25-MARIO-HEALTH.md),
[26](docs/TICKET26-WATER.md), [27](docs/TICKET27-FIRE-LAVA.md),
[28](docs/TICKET28-RESPAWN.md), [29](docs/TICKET29-PERSISTENCE.md) and
[30](docs/TICKET30-PRESENTATION.md) preserve their acceptance evidence and limits.
Final production probes run using `python .local/MarioCraft/play.py --probe` followed
by `combat`, `distance`, `presentation` or `admission`; these create disposable
normal worlds and keep raw evidence private. `scripts/bootstrap.ps1 -Probe`
builds/packages and executes the production presentation/lifecycle scenario.

## Historical planning record

A personal project to bring the original Super Mario 64 player, movement,
camera, and combat into an actual Minecraft Java Edition 1.12.2 world.

## Planning record

- [Grill session: all 55 decisions](docs/GRILLING-SESSION.md)
- [Scope and research gates](docs/PROJECT-SCOPE.md)

The following text records the original plan; current status and scope are above.

Research order: Minecraft first, then Super Mario 64. Both research dossiers
must be reviewed and approved before prototype work begins.

The original repository began with planning documentation. Game binaries,
ROMs, extracted assets, credentials, tool caches, and emulator saves are excluded.

