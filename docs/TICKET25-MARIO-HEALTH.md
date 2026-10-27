# Incoming damage and native health - issue 25

Mario's native health now owns incoming creature, projectile and explosion damage.
The HUD draws eight original sections from `unsigned health >> 8`, capped at eight;
it retains raw health rather than treating the display as a linear Minecraft HP
bar. `0x880` displays eight, `0x100` one, and the native `0xFF` sentinel zero.
The code-drawn display publishes no game-derived textures. Vanilla hearts, armor,
food and air overlays are suppressed while native ownership is active.

## Evidence and explicit conversion

[Health research 44](research/MARIO64-ISSUE44-HEALTH-AND-HAZARDS.md) establishes the
USA health updater, `0x40` hurt-counter drain, `0x880` cap, `0xFF` sentinel, and
code-verified landing thresholds. Its actual retail fall capture establishes only
a small no-damage interval, not dynamically measured damage thresholds.
[Collision research 42](research/MARIO64-ISSUE42-COLLISION-AND-GEOMETRY.md) and
[Minecraft combat](MINECRAFT-CREATURES-COMBAT.md) supply the existing body, source,
AI, arrow and explosion evidence. No additional emulator work was performed.

The backend is still libsm64 `fd11813208272b4271d92bd92feb8f3fdbe61be5`.
Its `fake_damage_knock_back` adds four pending hurt ticks per requested section;
the ordinary updater drains `0x40` per native step. A two-section hit therefore
does not subtract `0x200` immediately. Native remaining-health calculation includes
the pending counter when selecting soft/ordinary/hard forward/backward knockback.
Native ground/air/water action selection, velocity, animation, recovery actions
and invincibility timer remain in that implementation.

The crossover conversion is `ceil(Minecraft damage / 2)`, bounded to 1..8 sections
for positive finite damage. Zero, negative and nonfinite inputs request nothing.
This is an explicit adapter policy, not a claim that Minecraft damage numbers
exist in the original game. It uses the amount reaching Forge LivingAttack,
before vanilla armor, absorption, shields, resistance and hurt-resistant timers.
Native cap/body rules still apply inside the pinned helper; no new cap mechanic
is introduced. Explosions use the actual Forge Detonate origin for the same world
tick. Other sources use their damage location, then immediate entity position;
unattributed sources without either have a conservative one-block +Z fallback.

## Ownership, admission and events

The earliest active-player LivingAttack handler cancels vanilla damage. Only the
matching integrated server/world can enqueue a request. A fresh channel belongs
to each native session and captures both client and server world identities.
An immutable request includes source type, immediate and true source UUIDs,
source coordinates, server tick, original amount and the observed body dimensions.
One earliest pending request is retained; additional requests before consumption
are coalesced, not accumulated into a burst or substituted by a stronger hit.
The queue is bounded to one, and close invalidates/clears the whole channel.

The 30 Hz owning client thread consumes at most one request per native step,
checks the same live server/world, a bounded tick age (at most 20 server ticks,
with five ticks of client synchronization tolerance), initialized pose, live native
health, ACT_FLAG_INVULNERABLE/INTANGIBLE and native invincibility timer. The
pinned helper repeats its own native protection check. Its cached protection can
lag the final exported timer decrement by one step; a Java-admitted request can
therefore still be conservatively rejected by native code. Log `admitted` means
the Java request gate, not a return value from the void native damage API.
Observed health/action deltas establish actual effects. No native calls occur
on the server thread and a consumed request is never replayed after protection.

The real player retains UUID, world membership, targeting and AI relationships.
Its interaction AABB projects the native 37-unit radius and 160-unit height,
or 100 for ACT_FLAG_SHORT_HITBOX, at 100 units/block. This is an axis-aligned
Minecraft projection of native body dimensions, not a transplanted native object
collision engine. The box is reasserted after native setPosition, LivingUpdate,
PlayerTick.END and ServerTick.END so ordinary player size/position paths cannot
silently restore the vanilla box. Teardown reconstructs the ordinary box at the
current position. Vanilla eye height/AI look target remains unchanged; the separate
Mario camera already owns the rendered viewpoint. Aim behavior is consequently
an adapter distinction, especially when crouched.

Native-owned living physics and the server end-tick pose remain authoritative.
Independent vanilla explosion motion does not supply an additional native impulse;
native knockback uses the captured blast origin. Vanilla healing events are
canceled for the matching active player, and starvation is explicitly ignored.
Vanilla fall damage is canceled because native landing already owns it. Hunger,
armor and regeneration do not change Mario health. Fire and drowning are reserved
for issues 26/27 and are suppressed here rather than converted into invented rules.
No coins, pickups, production healing or Minecraft-to-native food healing is added.

Canceling LivingAttack makes the ordinary attack caller receive false. In
particular, an arrow follows vanilla's rejected-hit/bounce behavior instead of its
accepted-hit removal/payload route, and caller-side melee effects can be skipped.
This ticket preserves ordinary source emission, targeting and native damage; it
does not claim complete projectile payload or accepted-return equivalence. The
tested skeleton fired distinct arrows and caused separate native hits. There is
no evidence here of one retained arrow bypassing native protection.

`HealthBridge.Channel.snapshot` is an immutable post-native-step snapshot with
sequence, raw/projected health, action, timer, previous health/action, change and
death-transition flags. `lastRequestedExternalSource` is explicitly the most recent
Java-admitted external request (initially null), never a causal label for this
event or an attribution of later native ambient/fall deltas.
`eventsAfter(sequence)` returns an immutable copy of the latest 64 health/action
change snapshots. Slow readers must reconcile with the latest snapshot after a
history overrun. This is a bounded observation seam for water, fire, death and
persistence; it does not serialize native pending counters or implement those slices.

## Falls and terrain support

Native `check_fall_damage` is retained: velocity below -55, peak-to-landing height
strictly greater than 1150 units for the lesser branch and greater than 3000 for
the hard branch, with the researched action/surface/slipperiness exceptions.
With the ordinary native cap state these queue 8 or 16 hurt ticks (two/four
sections). The shipped unreachable 600-unit ground-pound comparison remains
unmodified. No vanilla fall-distance formula is added.

The earlier eight-block-deep mesh could leave native find_floor without a floor,
which stops actions even high above a valid real landing. The mesh now also finds
the nearest collision-bearing real block below that volume in each nearby column
within three blocks horizontally, stopping at world Y=0. Only actual collision
boxes are submitted; the full ordinary volume takes over before landing. Missing
chunks retain barriers, and changes below the player invalidate support lookup.
There is no artificial floor. A truly unsupported void, native coordinate floor
limits and final death/respawn policy remain issue 28 boundaries.

## Reproduction and evidence split

```powershell
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task build
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task healthSmoke
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task nativeSmoke
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task client -HealthProbe
python scripts/verify-health-trace.py .local/run-client/health-trace.jsonl .local/run-client/logs/latest.log .local/run-client/gameplay-result.json .local/health-checks.json
```

HealthProbe drives actual A/movement bindings in a fresh normally generated
Overworld and leaves every block unchanged. Ordinary AI zombie repeat attacks
and bow skeleton arrows are separate from explicitly initiated real vanilla
explosion fixtures (`damagesTerrain=false`). Representative falls use actual
A-input airborne action followed by setup-only elevation (2/12/31 blocks), not
a claim of input-only traversal off natural cliffs. Recipes relocate to an
existing natural runway. Native health resets isolate recipes; the final recipe
explicitly seeds the last native section (`0x100`) before a real blast. This is
test setup, not a production recovery mechanic. The old CombatProbe also resets
native health between recipes and observes captured incoming-source identity
instead of vanilla lastDamageSource, which is no longer set by canceled damage.
The probe-only diamond helmet and alternating empty/full vanilla food setup
exercise armor/hunger independence; the helmet can produce an ordinary Minecraft
advancement in the retained screenshot. Neither is a new Mario gameplay mechanic.

The separate synthetic HealthSmoke leaves actual-purpose-built ledges by input.
It verifies 200/1150-unit falls at `0x880`, 1200/3000 at `0x680`, and 3100 at
`0x480`; exact thresholds are not claimed as retail runtime measurements. It
also checks gradual hurt-counter drain, native protected repeat requests and
lethal `0xFF`/death action. Asset-free tests cover conversion, display, admission
and immutable transition semantics. Private raw traces/logs/screenshots/worlds,
native resources and game-derived assets remain ignored under `.local`.

Earlier candidates correctly failed the final lethal assertion: Minecraft blast
exposure converted to five sections in one full-health case and one in a later
two-section case. Those private runs are preserved, not passing evidence. The
final last-section fixture avoids assuming a particular blast exposure amount.
The wrapper archives prior artifacts and requires fresh passing result JSON.

## Passing observation

The final normal-world run passed 2100 required consecutive native steps, with
one additional recorded step while asynchronous server final observations reached
shutdown. The selected natural runway was at `(-280,64,-10)`; zero blocks changed.
[Integrated scalars](validation/TICKET25-RESULT.json) and
[independent checks](validation/TICKET25-HEALTH-CHECKS.json) record seven incoming
requests: three zombie melee requests (one rejected during native protection), two
distinct skeleton arrow contacts and two explosion requests. Actual source amounts
3, 4 and 1 converted to two, two and one native sections. Both ordinary AI mobs
targeted the real player; they were observed for 93 and 100 entity ticks.

Native minima by recipe were `0x480`, `0x480`, `0x780`, `0x880`, `0x680`, `0x480`
and `0xFF`. The verifier checked 61 health-drain transitions (`0x40`, except the
final one-unit sentinel clamp), real input/airborne fall intervals, finite poses,
the eight-section projection, actual incoming AABB width/height 0.74/1.6, conversion
against original source amounts, and a native health decrease after every Java-
admitted request in this run. Native recovery timer reached 30; the protected
zombie request arrived with timer 27 and did not deal more damage. Vanilla health
remained full despite test armor/food changes. The final low-health blast entered
native hard knockback and death action `0x21315`; no Minecraft respawn was inferred.

The private frame-200 image visibly shows eight code slots, four filled and four
empty, recognizable Mario and the actual natural terrain, without vanilla hearts,
food or armor. Normal native close and server save/unload completed; no moved-
wrongly/too-quickly warning occurred. Independent vanilla motion fields were not
universally zero (maximum sampled absolute component sum 0.0784000015); pose
ownership prevents their integration from replacing the native trajectory.
This is not proof that every vanilla method refrains from writing motion.

The final build passes 38 asset-free unit tests, assembly and reobfuscation.
[Synthetic native checks](validation/TICKET25-NATIVE-CHECKS.json) separately record
the input-driven ledges, progressive damage and native death sentinel. The final
world used the completed behavior; the subsequent source change only renamed
snapshot metadata to make its non-causal semantics explicit. The ordinary native
smoke also passes. No additional gameplay replay was used to inflate coverage.
Live collision rebuilds had 31 samples, maximum 36.5219 ms, with one above a
33.3 ms step budget. This runway observation retains the existing terrain-hitch
limitation and is not a worst-case performance bound.

At native `0xFF` the HUD reports fallen and native death action remains in
control. Minecraft health is not killed/reset to manufacture respawn. Respawn,
water/fire hazards and persistence are explicitly issues 28, 26/27 and 29.
