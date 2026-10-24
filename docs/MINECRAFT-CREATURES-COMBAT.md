# Minecraft 1.12.2 creatures, combat and environmental damage

Research for [issue #5](https://github.com/BrantTanner/MarioCraft/issues/5). This dossier follows the official 1.12.2 server artifact and records what a MarioCraft adapter must preserve, what the MVP should translate, and what still needs a connected-player observation. It does not claim that an integrated Mario player or ordinary creature combat was played in this ticket.

## Evidence basis and limits

The source of truth is the official vanilla server jar identified by [the baseline](MINECRAFT-1.12.2-BASELINE.md), SHA-256 `fe1f9274e6dad9191bf6e6e8e36ee6ebc737f373603df0946aafcded0d53167e`. I extracted and hashed 28 original class files; every local file matches its row in the ignored `MarioCraft/tools/inspection/issue5/original-class-hashes.csv` (28 checked, zero mismatches). The mapping used to label obfuscated classes is the MCP `joined.tsrg`, SHA-256 `57527913C5BEA02E110AA0D1FA929DA6F995F1A7836B0E46D891014B94779F4E`, from the pinned Forge 1.12.2-14.23.5.2859 development cache. It is a naming map, not vanilla source.

The original bytes under `tools/inspection/issue5/original/` and their CFR 0.152 reconstructions under `original-cfr/` are the byte-bound evidence. The Forge-mapped classes and reconstructed sources under `mapped/` and `mapped-cfr/` were navigation aids only; Forge event calls in those files are excluded from vanilla claims. CFR output is reconstructed, and the table below identifies the exact original class bytes behind the traces.

| Original class | Obfuscated name | SHA-256 |
| --- | --- | --- |
| `EntityList` | `vi` | `B4B9BEAF20389CDD97B643669BBDA4636F54CB67FD5C86BCC27B537DEDCBF118` |
| `Entity` | `vg` | `6CC1996D992C935506DCEF8FA5D7E97E2478A76C158AEC2B5A61FB0C02744096` |
| `EntityLivingBase` | `vp` | `D849E7333D04F3E764814E796F83B0CEF11E054F63951A041B48B7C51D82F9CA` |
| `EntityLiving` | `vq` | `4704792B3E9B75F42DD7A1B3704720FBC84A35D4CFBC633559E6CAD05FEA7E86` |
| `EntityCreature` | `vx` | `642220AA93067E780666DE43F3B71DF85A0DF17F6750C85C0C9B36E8573DE4C5` |
| `EntityPlayer` | `aed` | `B871389D38F79F5FE018C2240A9CF6AF68C900DB7DB409B368200059F823E084` |
| `EntityAITasks` | `xf` | `0152086DB18BA01934D93512FA67CDD1491DE96DD562169C317D58EE8D31FADE` |
| `EntityAIAttackMelee` | `xo` | `44A05CDD4F32C1FC1E7E00364BE96C522909C401123A3B35105D1BDF4BDE8675` |
| `PathNavigateGround` | `zd` | `284A25A312FEEDACA9C1C2FC50659E6E7764727D2FFD0EC731BC0B79504322F` |
| `WorldEntitySpawner` | `anb` | `594CD6EEFF46514D8652B0F2C27FA4D45CA6B7828CC19928F4C319BBAED4D8AC` |
| `DamageSource` | `ur` | `BDA8DFF1FB47BA647E88F002234005076BAFCBAE6860B7F5F56F913041D148F2` |
| `EntityDamageSource` | `us` | `7B1C80078B704D0C55BEAC226FF573E43FB49428D52478DE64FE01E3C748BA6A` |
| `EntityArrow` | `aeh` | `8F139221665014B96A84D0A80F1CDD2282C1D63CC0A331A619AFCC6DD5D1EC05` |
| `Explosion` | `amp` | `262AFCD07A158A848ACB5FA2F2B8EFE1DF884B19DA6A38FE550760694FBD2C4B` |
| `BlockLiquid` | `aru` | `D0E89A5646DB3D7A3A6BCC3CFFF7248E2D0BEA9EE4D4CA7CC31FD626219E6956` |
| `EntityCreeper` | `acs` | `B1623C22BB534E4F208C187DA638B31746C3F8245644107D93A1D34CCAB56FBA` |
| `EntitySkeleton` | `adk` | `BB36916205241F35A63447BC3569D90429C775D60360FC2CB1F0D4A9162BA6DF` |
| `EntitySpider` | `adn` | `8EB8A5D69EA5F2CA7108AD1258208EE37F174335C2816379E69C82621E84883D` |
| `EntityZombie` | `adt` | `6C0D4D69DE2F7B85FF4780983FD700D783C1E6F872BAC5282385A88EFFF09FBC` |

## Creature inventory and families

Vanilla `EntityList` (`vi`, original registry routine and static table) registers these living entities in this release. The list is the release registry, not a claim that every entry naturally spawns in every biome or dimension.

| Family | Registered living types |
| --- | --- |
| Hostile humanoid and undead | Blaze, Creeper, Giant, Husk, Skeleton, Skeleton (Stray), Skeleton (Wither), Zombie, Zombie Villager, Zombie Pigman, Witch, Evoker, Vindicator, Illusioner, Vex |
| Hostile non-humanoid | Cave Spider, Spider, Silverfish, Endermite, Enderman, Ghast, Guardian, Elder Guardian, Shulker, Slime, Magma Cube |
| Boss | Ender Dragon, Wither |
| Passive or neutral land animals | Bat, Chicken, Cow, Donkey, Horse, Llama, Mooshroom, Mule, Ocelot, Parrot, Pig, Polar Bear, Rabbit, Sheep, Wolf |
| Aquatic | Squid |
| Villagers and golems | Villager, Iron Golem (`villager_golem`), Snow Golem (`snowman`) |
| Horse variants | Skeleton Horse, Zombie Horse |

“Passive” and “neutral” here are planning buckets, not a guarantee of fixed hostility: wolves, polar bears, endermen, zombie pigmen, golems and spiders have conditional targeting or retaliation. Entity registry also contains non-living types (arrows, fireballs, potions, snowballs, eggs, shulker bullets, llama spit, evoker fangs, thrown pearls, TNT, falling blocks, boats, minecarts, items, XP, crystals, and others); these are not omitted from the projectile discussion below merely because they are not mobs.

Mob behavior is data and code per concrete class. Shared base behavior does not make family members interchangeable. Representative hashed class traces show the Creeper fuse/explosion path, Skeleton ranged behavior, Spider attack/target goals, Zombie attack/target and daylight code, Slime size-dependent collision/attack behavior, Ghast fire immunity, and Witch potion decisions. Zombie daylight behavior has variant-specific rules; verify each variant class before porting it. The full registry inventory above comes from original `EntityList`, but this 28-class inspection bundle does not prove every subtype's method overrides. Preserve ordinary Overworld mob identities, AI, attacks, effects and world interactions; the MVP scope does not authorize dropping families from the world.

## Tick, target, navigation and spawn trace

The living tick reaches `EntityLiving.updateEntityActionState`, which checks despawn, clears sensing caches, evaluates `targetTasks`, evaluates ordinary `tasks`, advances navigation, runs the mob-specific AI update, and updates move/look/jump helpers. The task scheduler (`EntityAITasks.onUpdateTasks`) increments its local counter and re-evaluates task starts/stops every third invocation by default; already-running tasks still receive `updateTask` on intervening invocations. Mutex bits prevent incompatible tasks from owning the same controls, and a higher-priority task can interrupt a lower-priority interruptible task. This is a priority scheduler layered over ordinary entity motion, not a single “mob behavior” callback.

Target selection is a separate goal set. Common goal instances find the nearest attackable player, retaliate against an attacker, or target a family-specific class. They apply distance, visibility, line-of-sight, team/creative/spectator, and entity-specific predicates. The selected `attackTarget` is then consumed by action goals. Creeper is a representative trace: its nearest-player target goal feeds its swell goal; the swell state advances toward its 30-tick fuse while in range, backs down when the target leaves, and then calls its explosion path. A Mario adapter should make the existing server player entity the target, so vanilla target rules can discover it without replacing mob AI.

Ground navigation (`PathNavigateGround`) requests a path through the navigator/path-finder using entity dimensions, terrain node types, collision and malus rules; it then advances the path and asks the move helper for motion. AI goals can choose speeds and target positions, but actual movement still passes through entity/world collision. Preserve this relationship: a Mario-only player update must not bypass mob navigation, world collision, or ordinary entity ticks.

`WorldEntitySpawner.findChunksForSpawning` enumerates eligible chunks around players, applies per-creature-category caps and biome spawn entries, chooses a pack position, and calls the world entity-spawn path only after placement/conditions pass. Natural spawning therefore depends on players, loaded chunks, biome, light/ground rules, difficulty, category caps, and game rules. Spawn eggs, commands, structure/dungeon spawners, and special boss/variant creation are distinct paths. `EntityLiving.despawnEntity` resets idle for persistence-required entities; otherwise ordinary hostile/peaceful mobs can be removed by player-distance and idle/random rules (farther than 128 blocks, or after idle time with a random check beyond 32 blocks). “Entity missing later” is not proof of hazard death.

## Representative combat traces

**Mob melee to player.** Target task selects the server-side `EntityPlayer`; a melee goal obtains a navigator path, turns toward the target, and compares distance against the attack reach. Its attack timer is 20 ticks. When it reaches zero and the target is visible (unless long-memory behavior applies), it calls `EntityCreature.attackEntityAsMob`. The attack path creates a mob `DamageSource`, applies damage to the target, and applies family-specific effects or knockback. The standard route is server-side; client animation/particles are presentation and prediction.

**Player attack to mob.** The vanilla client click path resolves the current mouse-over entity and the player controller sends the entity-use/attack packet. The server validates the packet target and invokes `EntityPlayer.attackTargetEntityWithCurrentItem`; damage depends on the player attack attribute, held-item contribution, enchantment/critical/sprint state and target response. Mario’s action cannot be inferred from vanilla held-item/attack state. Integration should instead submit a server-validated Mario action hit against the ordinary target through the normal living damage entry, carrying an explicit crossover `DamageSource`/attacker attribution. Do not install a replacement mob or turn each Mario action into a vanilla weapon click.

**Arrow to entity.** `EntityArrow.onUpdate` traces the swept segment from its current position to the next position against blocks, then finds an entity along that segment. On impact it computes base damage from arrow speed and configured arrow damage, with critical behavior, and sends an arrow `EntityDamageSource` that distinguishes the projectile as immediate source and shooter as true source. It then applies knockback, fire, potion payload, pickup, or removal behavior as appropriate. Tipped/spectral arrows and other projectile classes add their own effects; fireballs, potions, snowballs, eggs, shulker bullets and llama spit have different impact rules and should not be normalized to arrows.

**Explosion to nearby entities.** `Explosion` samples rays through blocks to derive affected blocks, then queries entities in its affected AABB. For each eligible entity, it computes distance-normalized impact and block exposure, applies explosion damage, and adds outward motion. The damage source retains the exploder/attacker when one exists. Block destruction, sound/particles, entity damage and knockback are coupled in the explosion routine but remain separate effects for a rendering or adapter layer.

**Accepted damage to health/death.** `EntityLivingBase.attackEntityFrom` rejects invulnerable entities, remote-world damage, dead/zero-health targets, and fire damage while fire resistance is active. Shields can block eligible frontal damage; helmet protection applies to anvil/falling-block hits. A 20-tick `maxHurtResistantTime` window suppresses equal or weaker repeated hits in its protected half and only applies the excess over the prior hit for stronger incoming damage. Accepted damage then passes through armor unless the source bypasses it, resistance unless damage is absolute, and applicable absorption. Accepted entity-attributed hits set revenge/attacking-player context and can apply default knockback. Death dispatches the damage source to death/drop logic; this attribution matters for loot and retaliation.

Knockback is not the same as damage. The living knockback method checks the target’s knockback-resistance attribute, halves existing horizontal motion, adds normalized horizontal impulse, and—when grounded—adds upward motion capped at `0.4`. Accepted attributed damage uses a default strength of `0.4`; player critical/sprint/enchantment routes can use additional strengths. Mario attacks should decide action-specific impulse at the integration seam and preserve the target’s resistance and collision response.

## Damage, immunity and effects

`DamageSource` is a typed policy object: it records flags such as fire, projectile, explosion, magic, unblockable, absolute and creative-harming, plus immediate and true attackers when applicable. `EntityDamageSource` and indirect-source variants preserve the attacker chain. Armor, resistance, shields, fire resistance, absorption, player creative invulnerability, entity-specific immunity and hurt-resistance frames affect different stages; do not implement one global “damage multiplier” in place of the source checks.

| Source | Source-bound outcome and limits |
| --- | --- |
| Fall | `EntityLivingBase.fall` reduces effective fall distance for jump boost and begins ordinary damage above 3 blocks after modifier; the `FALL` source is applied on landing. Mario player fall damage is a planned translation to Mario health/action rules. |
| Fire | Entity base tick deals 1 point of `ON_FIRE` damage when the fire counter reaches each 20-tick interval, then decrements the counter. Fire resistance and fire immunity can reject or suppress it. |
| Lava | Entity base tick checks material overlap; for a non-fire-immune entity, lava applies 4 points of `LAVA` damage and sets 15 seconds of fire. This is a source finding; the runtime attempt below did not establish a live damage outcome. |
| Water | Water is not a generic damage source. Entity water movement changes motion and extinguishes wet entities; living entities that cannot breathe underwater lose air and take drowning damage at the exhausted-air transition. Endermen and other special classes add their own water reactions. |
| Explosion | Impact depends on distance and block exposure; damage and outward impulse are distinct. Armor/resistance checks still apply according to the explosion source flags. |
| Direct attack/projectile | Melee and projectile damage carry attacker attribution. Armor, shields, resistance, hit cooldown, target immunity and family-specific rules decide the final health change. |

Potion effects are stored as active effects on living entities with duration, amplifier, source/visibility metadata, and periodic update behavior. Instant damage/heal effects and periodic poison, wither, regeneration, fire resistance, resistance, weakness, slowness, blindness, levitation and similar effects have distinct application and immunity rules. Splash potions compute eligible targets in a radius and scale duration/effect by distance; tipped arrows apply their configured payload on successful hit. Treat the effect as state with a tick lifetime, not as a one-time visual label. Armor, enchantments, shields, equipment durability, status effects, absorption and health are all research scope, although most are excluded from Mario’s playable MVP.

## Mario hit-volume and action-state seam

The player-input dossier traces local `EntityPlayerSP` input/update and the server `EntityPlayerMP` authority path. Keep those player identities and their world membership. A later adapter should:

1. Read the current Mario action state and pose from the Mario simulation at a recorded tick boundary.
2. Derive a world-space hit volume from measured Mario geometry and motion, then query ordinary living entities in that volume on the authoritative server.
3. Apply a named crossover attack only when the action’s active hit window and collision rule pass; include Mario as the true source and the hit action as an auditable damage type.
4. Let the normal entity damage path handle armor/resistance, immunity frames, revenge targeting, death, drops and entity knockback, while overriding impulse/health mapping only where Mario’s tested behavior requires it.
5. Leave `EntityLiving` target goals, `EntityAITasks`, navigator, other entities, world ticking and collision intact. The replacement belongs to the player input/motion/combat boundary, not the mob scheduler.

Mario USA/N64 action timing, hit-box shape, attack-specific damage, water recovery, fire/lava response, invulnerability overlap, and Mario-to-block scale are not measured in issue #5. Values must be selected from Mario-specific research and then verified at the integrated gameplay seam, not invented here.

## MVP translation and research-only systems

| System | MVP decision |
| --- | --- |
| Ordinary Overworld mobs and their world behavior | Preserve the complete ordinary mob roster and vanilla AI, target selection, navigation, spawning, despawning, attacks, projectiles, effects, damage, drops and environmental interactions. Runtime validation should cover representative families without turning that sample into a content exclusion. |
| Mario action attacks and hurt states | Adapt to Mario’s action-state/hit-volume model and Mario-specific health/invulnerability behavior; translate to server-validated Minecraft entity damage with explicit attribution and measured knockback. Keep unresolved crossover choices linked to the combat/action design tickets ([#18](https://github.com/BrantTanner/MarioCraft/issues/18), [#24](https://github.com/BrantTanner/MarioCraft/issues/24), [#25](https://github.com/BrantTanner/MarioCraft/issues/25)). |
| Falls, water, drowning, fire and lava affecting Mario | Adapt to Mario behavior specified in project scope; do not blindly apply vanilla player health or drowning values to Mario. Preserve ordinary mobs’ vanilla environmental rules. |
| Vanilla mob projectile and status attacks against Mario | Preserve the mob’s attack and effect application. Decide how resulting health, recovery, movement, visibility and invulnerability affect Mario through the crossover design tickets ([#18](https://github.com/BrantTanner/MarioCraft/issues/18), [#24](https://github.com/BrantTanner/MarioCraft/issues/24), [#25](https://github.com/BrantTanner/MarioCraft/issues/25)); do not remove witch potions or other ordinary mob effects as an MVP shortcut. |
| Player armor, held weapons, enchantments, shields, hunger and inventory progression | Excluded player-facing MVP systems per project scope. Do not add them as prerequisites for Mario; retain vanilla mob attack and damage processing where those systems are not required. Mob drops and equipment behavior remain world behavior even when Mario has no matching inventory UI. |
| Nether/End travel and dimension-owned spawning/content | Research-only for the playable MVP because those dimensions and portals are excluded by project scope. This does not exclude ordinary Overworld bosses, mob attacks, effects, or projectiles. Structures in the active Overworld remain part of its world behavior. |
| Damage/death sounds, particles, client-side hit reactions | Preserve or replace presentation only after the server result is established; cosmetics do not decide hit validity. |

## Runtime evidence and reproduction

The official vanilla server check used Temurin 8u504, seed `514207`, local-only ports 25597/25598, and a generated world under ignored `tools/runtime-issue5/`. On 2026-10-24 at server log time 11:34:46 MDT, RCON reported gametime 5609, successfully summoned a persistent no-AI zombie named `I5Probe`, found it with `testfor`, returned its NBT (`Health:20.0f`, `PersistenceRequired:1b`, position `[52.5,65.0,256.5]`), removed it by its unique name, and then found no matching entity. The complete sanitized command/response record is `MarioCraft/tools/runtime-issue5/issue5-command-path.txt`. This verifies server startup, local control, entity command creation/query/removal, and world save/shutdown only. It did not verify natural spawning, target selection, AI, player attacks, projectile motion, health changes or damage.

After resumption I attempted a paired lava check in the separate seed-514207 `issue5-lava-control` save. With `doDaylightCycle=false`, daytime 18000 and `doMobSpawning=false`, a persistent `NoAI` zombie with `Health:1.0f` was summoned at `(50.5,64,256.5)` in a confirmed lava block and a matched zombie at `(54.5,64,256.5)`. The server reported gametime 942 at setup and 1094 then 1662 at later checks. `entitydata` returned `Health:1.0f`, `HurtTime:0s`, unchanged positions and `OnGround:0b` for both each time; the control position also resolved to terrain stone at y=64. There was no connected player, so this did not establish that entity ticks were active. I reset neither save nor world to force an outcome; I stopped and saved the task-created server. This comparison is **inconclusive**, not evidence that lava failed to damage or that lava killed the entity. The earlier one-health disappearance test likewise lacked a no-lava control and a verified pre-lava health/tick trace, and is excluded.

The accepted runtime evidence is consequently bounded to server startup, RCON command handling, and command-driven summon/find/remove on the vanilla server. For a safe reproduction, use a disposable ignored directory, the pinned JDK 8 and official jar above, set `server-ip=127.0.0.1`, select unused local ports, enable RCON with a fresh private password, and bind RCON to loopback. After the server reaches `Done`, issue `seed`, `time query gametime`, `summon zombie <x> <y> <z> {CustomName:"unique-probe",NoAI:1b,PersistenceRequired:1b}`, `testfor @e[name=unique-probe]`, `entitydata @e[name=unique-probe] {}`, `kill @e[name=unique-probe]`, `testfor @e[name=unique-probe]`, and `stop`. Do not publish RCON credentials or save files. A valid hazard experiment must additionally demonstrate a connected/actively ticking area, verify both entities’ starting NBT and health, record exact game/day time, keep both in loaded dark chunks, and compare identical persistent entities over the same interval. A connected-player attack/AI/projectile trace remains for the integrated validation ticket.

## Remaining creature and combat questions

The following decisions are still open and must remain visible to the implementation/review tickets:

- Which representative hostile, passive/neutral and aquatic families provide useful first integration coverage for the full preserved Overworld roster, and what objective results establish that ordinary AI remains active?
- What Mario world-space body and attack volumes correspond to each measured action pose, and when do their active windows begin/end relative to the independent Mario and Minecraft clocks?
- Which Mario actions damage which mob classes, how is per-action damage encoded, and what source attribution should drive retaliation, drops, statistics and death messages?
- How do Mario’s health, invulnerability, knockback, recovery, fall, water, fire and lava rules map onto the server living-entity damage path without giving Mario vanilla armor/hunger behavior?
- What measured impulse and lift should each Mario action apply, how does target knockback resistance modify it, and which Mario actions are immune to incoming knockback?
- Which vanilla protections (armor, shields, resistance/fire resistance, enchantments, absorption, creative/spectator and hurt-resistance frames) remain reachable for Mario, and how are exclusions communicated in the MVP?
- What collision sampling/hit validation is authoritative in the integrated single-player logical server, and how should client presentation and action animation reconcile with server decisions?
- Which natural spawn rules, mob-cap choices, biome/difficulty/light rules and spawn-prevention settings does each MVP world use? Which variant, structure, spawner and dimension-owned spawn paths need separate measurement?
- How do Mario’s action hit volumes interact with the full ordinary mob projectile roster, swept collision, owner attribution, status payloads and damage rules?
- How should mob despawn, chunk unload/reload, persistent named mobs, death drops and time-paused/suspended worlds behave while Mario is in menus or transitioning?
- Which representative families and hazards must be covered by the first integrated actual-Minecraft test, and what Project64 input/timing reference will serve as the Mario-side oracle?

## Reproduction files

The ignored local evidence bundle is `MarioCraft/tools/inspection/issue5/`: original `.class` bytes, per-class hashes, original and mapped CFR navigation views, and the private loopback RCON helper. The run worlds and logs are under `MarioCraft/tools/runtime-issue5/` and `MarioCraft/tools/runtime-issue5-lava-control/`. Do not add jars, extracted classes, decompiler output, worlds, logs, captures or credentials to the repository. The prior [baseline](MINECRAFT-1.12.2-BASELINE.md), [player/input dossier](MINECRAFT-PLAYER-INPUT.md), and [terrain dossier](MINECRAFT-TERRAIN.md) define the linked artifact, movement seam and world collision contract.

