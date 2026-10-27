# Minecraft respawn and Mario lives — issue 28

Native lethal health now enters Minecraft's real `GuiGameOver`/respawn-clone loop.
One latched death decrements one life. Health below `0x100` qualifies regardless of
native action, including lethal lava that remains in `LAVA_BOOST`. Native pose
ownership is removed before the server death handoff; pending clone identity and
server identity guard delayed work. No server task calls native code.

The crossover policy starts at four lives, retains the decremented nonzero count
on respawn, displays zero during the fourth death screen, and resets to four on
that respawn. Every respawn creates a fresh native context, whose initial health
is `0x880`. The world, inventory, undamaged armor, XP and food are retained.
Minecraft's damage/drop/XP death transaction is not replayed after native death;
no world game rule is changed. The life counter appears beside the power HUD.

The existing [Minecraft lifecycle evidence](MINECRAFT-LIFECYCLE-SAVING-NETWORKING.md)
establishes the screen/request/clone boundary. The [Mario source evidence](research/MARIO64-ISSUE65-EEPROM-SAVE-LIFECYCLE.md)
establishes initialization at four lives/`0x880` and distinguishes lives/health
from saved progress. Those are source facts, not a new retail reset experiment.
The zero-life reset while preserving this Minecraft world is an explicit adapter
policy required by this ticket. Existing retail lava life-loss evidence is reused;
no emulator or EEPROM work was performed.

## Spawn and reset policy

After Minecraft chooses the respawn dimension (normally the Overworld even after
Nether death), the server selects its designated world spawn, ignoring bed offset.
It scans surface columns on a four-block lattice in deterministic X/Z ring order,
radius 0 through 128, at most 4,225 columns. The candidate must fit the 0.74 by 1.6
body, have actual support and world-border containment, and have no water, lava,
fire, cactus or magma in its immediate neighborhood. Position and yaw are set on
the server; native recreation waits for the concrete clone and position acknowledgement.
The search loads/generates ordinary chunks and edits no blocks. Extreme worlds
without a qualifying candidate stop at an explicit error screen; they do not
silently resume vanilla control or manufacture a platform.

Native recreation clears action/velocity, burning/damage counters, invincibility,
water state and geometry. Java teardown clears damage queues/history/source,
combat contacts, interpolation, camera/inspection and input gates; held controls
require a neutral sample. Native initialization legitimately uses spawn-landing
`0x1325` before idle `0x0C400201`; the executable settled-state check samples at
native frame 30 without forcing an action or velocity. Packet and damage ownership
remain active through clone initialization, closing the former vanilla movement
window. Vanilla health/armor/food/air HUD and inventory interaction remain suppressed
through that handoff. Same-server dimension replacement preserves lives; a stopped
server/title exit releases concrete pending player/world/channel references.

A fully loaded collision region with no admissible recovery for 30 simulation
attempts is also a qualifying structural death. This covers unsupported voids and
newly enclosing solids. Missing chunks keep the existing collision barriers.
This is a bounded safety policy, not a claim of retail Mario void behavior; this
branch was reviewed in source rather than separately terrain-edited in gameplay.

## Executable evidence

[Sanitized checks](validation/TICKET28-RESPAWN-CHECKS.json) come from one fresh normal
Survival world, seed `20261026`, with no terrain edits. Six native lethal events
produced six real death screens and six real respawn requests. Screen lives were
`3,2,1,0,3,2`; post-respawn lives were `3,2,1,4,3,2`. The fifth event used a naturally
generated Overworld lava block; its lethal native action was `0x010208B7`. The sixth
died in the same save's Nether and returned to the Overworld with two lives.
All six respawns had health `2176`, settled idle action, exactly zero velocity,
invincibility zero, empty new damage channel/source and camera inspection off.
Repeated Overworld spawn positions matched. No movement warnings occurred.

Setup is explicit: five server `LivingAttack` damage requests; one low-health
setup and relocation into actual lava; one same-save dimension transfer; tagged
three-apple/iron-helmet inventory, level-seven XP and food-14 fixtures; and an
invulnerable/no-gravity passive entity plus natural bedrock marker for world
continuity. Those fixtures are not gameplay discoveries. Exact tagged stacks,
armor condition, XP and food survived all clones; no tagged inventory drops or
new nearby XP orbs appeared. Ambient world item entities are allowed and excluded
from the player-drop claim. Four actual vanilla HUD pre-events were canceled.
The probe invokes the same respawn method as the enabled Minecraft button after
observing each actual screen; it never mocks the screen or revives dead native health.

Reproduce with a configured Java 8 JDK and project Gradle cache:

```powershell
./scripts/dev.ps1 -Task client -RespawnProbe -JavaHome $env:JAVA_HOME -GradleHome $env:GRADLE_USER_HOME
python scripts/verify-respawn-trace.py .local/run-client/respawn-trace.jsonl .local/run-client/logs/latest.log .local/run-client/gameplay-result.json respawn-checks.json
```

The wrapper archives earlier evidence and requires a fresh passing JSON. Raw logs,
worlds, local captures, ROM/native assets and private paths are not published.
The earlier health/hazard probes now finish at their observed lethal native step,
before this production handoff, and their verifiers retain full nonlethal phase
coverage rather than inventing a fixed-frame dead-body tail. Historical ticket-27
1,200-step evidence remains historical and unchanged. The updated
[hazard regression](validation/TICKET28-HAZARD-REGRESSION.json) passes at actual
frame 964, retaining both burning phases and both nonlethal lava recoveries.
The updated [health regression](validation/TICKET28-HEALTH-REGRESSION.json) passes
at frame 1,863 with all seven recipes, real zombie/skeleton AI and arrow targeting,
three fall thresholds, 61 drain steps and lethal explosion. Both regressions have
zero movement warnings. [Build checks](validation/TICKET28-BUILD-CHECKS.json) pass:
45 unit tests (including three life-state tests), reobfuscated JAR build, and
separate native, health and hazard smoke tests. These synthetic smoke results are
not substituted for the actual-world respawn proof.
Terrain rebuild hitches remain: the respawn run measured 13 mesh builds, maximum
63.9472 ms with five above 33.3 ms; hazard regression measured 83.7485 ms maximum,
three of 32 above 33.3 ms. No performance improvement is claimed.

## Contract for issue 29

`MarioLives.Snapshot` version 1 contains only `lives`, monotonic `deaths` and
`ALIVE`/`AWAITING_RESPAWN`. `die()` and `respawn()` are idempotent transitions.
Restore validates version/range/phase; a pending death already includes its life
subtraction and cannot subtract again. An alive state cannot have zero lives.
`HealthBridge.Channel.lifeState` publishes the alive immutable snapshot;
`RespawnBridge.Pending.lifeState` retains the pending-death immutable snapshot.
The concrete pending objects, native actions/timers, input, camera and channels
are transient and must never be serialized. Pending restore must select/revalidate
safe spawn and recreate native Mario; it must not resurrect stale action state.
Disk persistence/save-reload implementation remains issue 29.
