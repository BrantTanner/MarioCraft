# Outgoing creature combat - issue 24

Mario attacks now use action-specific contact admission and a UUID contact ledger,
then damage the existing Minecraft creature through its ordinary living damage
entry. The creature is never replaced, rescheduled, frozen or assigned a Mario AI.
The actual Minecraft player remains the attributed attacker and AI target.

## Original evidence and pinned backend

[Research 53](research/MARIO64-ISSUE53-ATTACK-CONTACT.md) distinguishes collision
candidates, active action/flag windows, facing, attack classification and the
target-specific handler. [Research 54](research/MARIO64-ISSUE54-ENEMY-FAMILIES.md)
is selected original enemy evidence, not a rule that every Minecraft creature is
a Goomba. [Minecraft combat](MINECRAFT-CREATURES-COMBAT.md) supplies living damage,
armor, hurt resistance, targeting, navigation, death and drop behavior.

The backend remains libsm64 `fd11813208272b4271d92bd92feb8f3fdbe61be5`.
Its `libsm64.c:sm64_mario_attack` calls `fake_interact_bounce_top`, which both
classifies and applies Mario recoil/bounce. Java therefore checks local action,
contact geometry, missing chunks and terrain before invoking it. A successful
native admission is consumed even if authoritative damage is later rejected.
`fake_determine_interaction` has no special unconditional dive branch: after the
explicit punch/pound/slide-kick branches its fast-attack branch requires absolute
forward speed at least 26. A slower airborne dive can still classify as a stomp
when descending above a target. Java preserves that distinction. Rising hits from
below, caps, shell riding, twirling and original object selectors are not added as
new Minecraft attacks here.

Native punch/kick flags provide their active windows, rather than a guessed fixed
cooldown. Facing uses the pinned classifier's +/-0x2AAA angle for punch/kick and
+/-0x4000 for the crouch trip. Native first/second punch and their fast recovery
animations share their respective ledger stage; ground kick, crouch trip and jump
kick have separate stages. Native recoil changing punch to moving-punch does not
reopen contact. Dive and dive-slide share a stage, as do pound and pound landing.
A descending flight contact remains consumed until the rising/grounded interval
or another attack stage starts. Pause does not advance or reset the ledger.

## Explicit Minecraft crossover rules

These numbers are adapter choices, not original Mario damage values:

| Attack | Requested living damage | Target conditions | Target knockback |
| --- | ---: | --- | --- |
| Punch | 3 | Active native punch flag, facing cone, body overlap | Ordinary vanilla attributed-hit strength 0.4 |
| Kick / crouch trip / slide kick | 4 | Native active flag/cone for punch-family kicks; explicit slide-kick action for slide kicks; body overlap | Same vanilla strength 0.4 |
| Stomp | 5 | Airborne descent crossing the target top, including a slowed dive fallback | Same vanilla strength 0.4 |
| Dive / dive slide | 6 | Explicit dive family, absolute native forward speed >=26, body overlap | Same vanilla strength 0.4 |
| Ground pound | 8 | Pound descent/top contact; landing shares the contact stage | Same vanilla strength 0.4 |

All rows retain vanilla target knockback resistance, armor, shields, absorption,
immunities, hurt resistance, retaliation, death attribution and drops. The
adapter does not add a second knockback impulse or reset hurt-resistant time.
Requested damage can therefore differ from health loss, and an admitted contact
can deal zero damage. The named `mario.*` EntityDamageSource identifies the action
and real player without calling attack-with-current-item, using weapons, attack
cooldown, enchantment bonuses, critical hits or inventory actions. Players are
excluded from this creature adapter. Entity-specific immunity remains meaningful;
all bosses, multipart entities or modded mobs are not claimed validated.

Horizontal contact uses the original Mario object's 37-unit radius at 100 units
per block plus half the actual target collision-box width. Vertical body overlap
uses the pinned 160-unit hitbox, or 100 units for ACT_FLAG_SHORT_HITBOX. Minecraft
boxes become upright cylinders for this contact test; this is a stated shape
conversion, not transplanted original enemy volumes. Top attacks require a
previous foot height at least target-top minus 0.25 block and a current/prospective
foot height at most target-top plus 0.25. Sideways low contact cannot become a
stomp or pound. The 0.25-block contact tolerance is an adapter choice.

A pre-step downward foot sweep covers one current native vertical-velocity step
at the current horizontal position, capped at 0.8 block. It lets a small target receive contact before
native floor landing removes the airborne action. It invokes the native helper
on the valid descending state, without writing native action or velocity. This
is a discrete crossover sweep; it is not a full swept moving-creature solver and
can start bounce up to one native step before the unswept feet overlap. Horizontal
sweep and exact moving-target time-of-impact are not claimed. Ordinary post-step
contact remains for side attacks and current overlap.

A 0.03-block padded contact corridor checks actual world collision boxes. Top
corridors begin at the prior feet and end at the target top; side corridors use
shared body height. Thin/partial blocks, containing endpoints and unavailable
chunks reject before the native helper. The server repeats classification and
terrain checks against the actual living entity. Session token, captured server
and world identity, live real player, active pose ownership and target UUID guard
queued work across teardown, world reload, death and numeric entity-ID reuse.
A creature that has moved outside the valid contact before the server executes
can reject the request; the same action does not retry every frame.

## Executable validation

The opt-in CombatProbe uses actual Minecraft
KeyBinding samples and native simulation in a normally generated Overworld.
Five deterministic NoAI contact fixtures isolate punch, air kick, chicken stomp,
running dive and ground pound. Separate ordinary AI-enabled zombie, flying bat
and bow skeleton scenarios establish representative real creature behavior. NoAI
fixture results are never used to claim ordinary AI proof. Setup relocates the
player between bounded recipes on an existing natural runway; no blocks change.
The probe configures night/normal difficulty and heals the vanilla test player
between scenarios, so incoming Mario health is not inferred from this work.

```powershell
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task build
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task nativeSmoke
./scripts/dev.ps1 -JavaHome .local/java/jdk8u504-b01 -GradleHome .local/gradle-home -Task client -CombatProbe
python scripts/verify-combat-trace.py .local/run-client/combat-trace.jsonl .local/run-client/logs/latest.log .local/run-client/gameplay-result.json .local/combat-checks.json
```

The wrapper archives prior results, combat traces, logs and captures before a new
run, then requires fresh passing JSON. Worlds, raw traces, logs, captures, ROM,
DLL, generated geometry and other private resources stay ignored locally.

Asset-free tests cover active windows/cones/volumes, top descent and short-target
sweep, slowed-dive fallback, explicit damage values, combo/recovery/jump-kick and
flight ledger stages, thin walls, containing endpoints, partial-block clearance
and floor obstruction. They are synthetic adapter checks, not actual-world wall
or partial-block combat evidence. Camera fidelity, movement and terrain ownership
remain the preceding slices; incoming damage/health and hazards remain issue 25.

## Passing integrated observation and limits

The final run passed 1,620 required consecutive steps in a fresh normally
generated seed-20261026 Overworld. The natural runway was selected near
`(-257,64,-3)`; setup relocated between recipes, with zero block edits. The
[scalar result](validation/TICKET24-RESULT.json) and
[independent checks](validation/TICKET24-COMBAT-CHECKS.json) record eight accepted
contacts against eight distinct creature UUIDs. Each recipe had exactly one
server attempt and accepted health-decreasing hit despite multi-frame active
windows. There were five NoAI fixture contacts and three ordinary AI contacts.

Fixture health changed pig 10 to 7 on punch, skeleton 20 to 16 on air kick,
chicken 4 to 0 on stomp, pig 10 to 4 on dive, and pig 10 to 2 on ground pound.
The chicken used the normal death/item-drop path. Ordinary AI zombie health
changed 20 to 17.06, reflecting vanilla armor; flying bat 6 to 3 and bow skeleton
20 to 17. The zombie retained the actual player as target, an active navigator
path and a vanilla attributed attack on that player. The skeleton retained player
targeting/navigation and fired its own arrow. The bat continued airborne motion.
They were observed at 90, 90 and 214 ordinary entity updates respectively; measured
travel was 3.675, 10.506 and 12.132 blocks. These are representative scenarios,
not every creature family, AI goal or lifecycle circumstance.

The first run exposed premature dive input (below the native speed gate), fixed
by a longer actual-input approach. An observer also read a previous NoAI fixture
using the next recipe's phase; capturing and validating phase/UUID fixed that
measurement race without relaxing the AI assertion. Later setup corrections
placed side targets along the observed native facing and beyond the punch's
startup movement. One earlier bat contact applied native recoil locally but was
rejected on the server because the interpolated client bat was still ahead while
the actual bat had flown behind Mario. The final bounded recipe starts it farther
ahead. That conservative moving-target limitation remains: Java does not rewind
server creatures or undo native recoil after a rejected asynchronous request.
No universal moving-target timing or exact retail contact equivalence is claimed.
The unsuccessful runs remain private and are not passing acceptance evidence.

The independent verifier confirmed action IDs and actual A/B/Z input recipes,
finite poses, eight distinct server targets, one attempt/hit per recipe, requested
damage values, separate fixture/ordinary AI attribution, lifecycle observations,
normal native teardown/server save/unload and no moved-wrongly/moved-too-quickly
warnings. The wrapper required fresh passing JSON and exited successfully. No
camera override or replay was added for this combat ticket. The prior terrain
hitch limitation remains; this flat runway is not a performance benchmark.

The final asset-free build passes 34 unit tests, assembly and reobfuscation.
A separate pinned-native synthetic input check exercises an active jump kick
whose facing cone misses a target behind Mario while descending top contact
still produces native stomp fallback/bounce (next-step vertical velocity 26).
The [native scalar record](validation/TICKET24-NATIVE-CHECKS.json) labels this
separately. It is an executable classifier
regression, not an additional normal-world scenario. Incoming Mario damage,
invulnerability/health translation and hazards remain issue 25.
