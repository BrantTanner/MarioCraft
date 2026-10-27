# Safe Minecraft save/resume — issue 29

Mario now resumes position, health and lives from the ordinary Minecraft save.
The adapter recreates native Mario and recomputes collision/water state; it never
serializes actions, velocities, timers, native structures, input, camera objects,
Minecraft players, channels or server references.

## Evidence and architecture

The existing [Minecraft lifecycle evidence](MINECRAFT-LIFECYCLE-SAVING-NETWORKING.md),
[health contract](TICKET25-MARIO-HEALTH.md), [water contract](TICKET26-WATER.md),
[hazard contract](TICKET27-FIRE-LAVA.md) and [life/respawn contract](TICKET28-RESPAWN.md)
provide the bounded research for this slice. No new EEPROM, course, star, emulator
or broad retail lifecycle investigation was needed.

One local correction to the earlier lifecycle prose: the pinned mapped
`IntegratedServer.tick` bytecode calls `saveAllPlayerData()` and then
`saveAllWorlds(false)` when entering pause (instructions 54 through 63 after the
“Saving and pausing game…” log). Thus ordinary pause, world saves and shutdown
reach Minecraft MapStorage. No custom disk flush, replacement vanilla serializer
or player-data rewrite is introduced.

`MarioSaveData` is an Overworld `WorldSavedData` entry named
`mariocraft_players`, with per-UUID records, scoped to that concrete save's
MapStorage. Storage discovery/loading and all NBT operations occur on the server
thread. The client only replaces immutable semantic values in that instance's
concurrent map. `isDirty()` keeps the entry eligible for every Minecraft save.
Serialization therefore samples the latest complete value even after native
teardown removes health and pose channels. There is no global UUID-only save
registry. The client releases its storage reference after server shutdown.

The root format is version 1; each record is version 1 and carries dimension,
finite bounded X/Y/Z, health, life-state version, remaining lives, monotonic death
count and ALIVE/AWAITING_RESPAWN phase. Decode requires exact NBT types and all
mandatory fields. Health must be 0..2176, with ALIVE requiring at least 256;
lives/phase/deaths use `MarioLives.restore` validation. Coordinates are finite
and bounded by 29,999,984 in magnitude, including Y so legitimate movement above
build height or below the world cannot disable publication. Restore geometry
validation handles those anchors separately. No unknown format is guessed or
silently coerced into the current schema.

Missing, older, future, malformed or invalid records deterministically use a fresh
four-life/full-health state at a validated vanilla position or designated safe
spawn. A bad record cannot poison another owner. Valid health/lives are retained
when only an otherwise valid coordinate is no longer safe in the loaded world.
The mod writes its own MapStorage entry; unrelated vanilla player/world NBT,
inventory, food, XP, entities and terrain keep their normal serializers.

## Restoration and lifecycle policy

On world open, a concrete client/server-player hold blocks vanilla physics,
damage/healing and movement/use packets while the server loads and validates the
record and acknowledges its selected position. Queued work checks the active
hold and concrete server-player identity; world teardown invalidates the hold.
Native ownership is acquired before releasing it. A server restore validation/search failure
retains the hold and shows an explicit safe-resume error. Native initialization
errors retain the existing logged teardown behavior and release control.

A dry anchor requires a clear 0.74 by 1.6 body, world-border containment and no
nearby lava, fire, cactus or magma. A downwards trace finds support within 160
blocks and projects an airborne checkpoint to that floor. Obstructed,
out-of-build-height, unsupported, changed-dimension or otherwise unsuitable
anchors use issue 28's deterministic designated safe-spawn search. The search
edits no blocks and fails explicitly if no safe candidate exists.

A clear anchor in vanilla `BlockLiquid` water may resume in water. The new native
context receives the current connected-column `WaterVolume` result every step;
no saved water plane is reused. Sufficiently deep water enters native swimming;
shallow/cap positions may naturally settle to grounded state. Native creation
retains the existing 10-native-unit (0.1-block) spawn margin and normal entry
physics. Position evidence checks the exact acknowledged restore anchor before
that first physics step, rather than mislabeling small native entry motion as a
restore error. Native initialization/landing can precede settled idle; low health
legitimately selects panting idle `0x0C400205` instead of `0x0C400201`.

`MarioSession.restoreHealth` is a deliberate production API restricted to a fresh,
created, unstepped native Mario, with living-health range validation. It cannot
revive a dead native action or reset health during gameplay. The test-only health
setup guard remains distinct. Fresh creation clears native timers, action,
velocity and resources; normal teardown resets damage/combat channels, camera
inspection, interpolation, key contexts and neutral-input gates.

A saved AWAITING_RESPAWN record already includes its one life subtraction. Disk
resume completes `respawn()` once, uses full health and a newly validated safe
spawn, and never calls `die()` again. Zero pending lives reset to four through the
existing life contract. A pending Nether death returns to the Overworld through
a no-portal teleporter before safe positioning; it creates no portal or terrain.
Concrete pending original/clone objects never enter disk state. Publishing the
pending snapshot precedes native close and queued Minecraft death, covering the
save-before-server-death and clone-initialization windows.

## Executable checks

The opt-in `PersistenceProbe` runs two disposable ordinary generated Survival
saves with seed `20261026`, without terrain edits or desktop input. It drives 13
actual integrated opens and closes: damaged ground, water, a midair jump,
one-section health, real death screen/pending-death reload, repeated A/B world
switches, and malformed/older/missing mod-record fixtures. Malformed/older fixtures invoke
the actual server decoder before saving, which discards their invalid records;
the following cold open therefore proves missing-record fallback. Separate parser
tests cover invalid/older input. No corrupted-disk injection is claimed. Each phase has a
300-native-frame bound, plus a whole-run timeout. The wrapper preserves earlier
results and requires a fresh passing JSON.

The proof checks first-frame health and exact acknowledged restore anchors,
settled idle/swim state, camera/input/packet ownership, a single concrete vanilla
player, empty ownership registries and released native/texture/camera/channel/key
resources. Tagged inventory, XP, food, unrelated entity NBT, a passive marker
entity and a natural bedrock marker are checked across reloads. Setup health,
relocation, synthetic jump input, post-login server attack and malformed records
are explicitly test-only fixtures. They are not claims about ordinary gameplay
inputs. Earlier diagnostic runs remain private: one used a premature attack during
login immunity and an overly strict full-health idle predicate; another compared
a water position after native entry physics and mislabeled a cumulative verdict
as a current cleanup result. The final verifier requires independent assertions and zero Minecraft ERROR/FATAL records from the first server world load through final shutdown. Existing Forge development bootstrap diagnostics before any world opens are outside that runtime check. A later lifecycle review caught that raw `loadWorld(null)` omits the vanilla disconnect step; the final harness opens the actual `GuiIngameMenu`, waits for the observed integrated pause, then follows its inspected Save/Quit sequence: `world.sendQuittingDisconnectingPacket()`, `loadWorld(null)`, then main menu. Earlier runs with queued old-connection packet errors are retained as failed evidence, not counted as lifecycle proof. A direct disconnect from unpaused gameplay also exposed a vanilla shutdown Future race; its thread dump is retained privately. The pause-menu context avoids processing logout before the shutdown task is admitted. Unload waits have a separate 30-second wall-clock bound.

Reproduce with the configured Java 8 JDK and Gradle cache:

```powershell
./scripts/dev.ps1 -Task build -JavaHome $env:JAVA_HOME -GradleHome $env:GRADLE_USER_HOME
./scripts/dev.ps1 -Task client -PersistenceProbe -JavaHome $env:JAVA_HOME -GradleHome $env:GRADLE_USER_HOME
python scripts/verify-persistence-trace.py .local/run-client/persistence-trace.jsonl .local/run-client/logs/latest.log .local/run-client/gameplay-result.json persistence-checks.json
```

Six asset-free regression tests cover semantic round-trip, exact field types,
missing/old/future formats, invalid health/lives/coordinates, valid finite
out-of-build-height publication, pending death without a second subtraction,
instance isolation, bad-neighbor isolation and unrelated NBT preservation.

[Final sanitized gameplay checks](validation/TICKET29-PERSISTENCE-CHECKS.json) pass:
13 actual integrated loads/unloads and 13 native creates/closes, 13 exact anchor,
camera/input/packet ownership, concrete single-player and closed-resource checks,
and 11 vanilla-state preservation checks. Ground health 1280 and water health 1896
survived their checkpoints; jump `0x03000880` resumed idle and health 256 resumed
panting idle. Pending death saved zero health/three lives and reopened at 2176
health/three lives, with one recorded death. Repeated world A loads retained
`3,3,3` lives while world B retained `4,4`; decoder-invalid/older then cold-missing
fallbacks produced `4,4,4`. Runtime world lifecycles logged zero ERROR/FATAL
records and zero movement warnings. All setup terrain edit counts were zero.

[Final build checks](validation/TICKET29-BUILD-CHECKS.json) pass: 51 unit tests,
zero failures/errors/skips, reobfuscated JAR and native/health/water/hazard smoke
checks. Synthetic smoke checks remain separate from actual integrated gameplay.
Terrain rebuilding still hit 57.2561 ms maximum across 16 meshes, with three above
33.3 ms; no performance improvement is claimed. Private logs, traces, worlds, screenshots, ROMs,
native DLLs and derived game assets are excluded from publication. Clean save and
reload is the durability contract; no power-loss atomicity beyond Minecraft's
normal MapStorage behavior is claimed. Existing terrain rebuild hitches are
measured, with no performance improvement claimed.
