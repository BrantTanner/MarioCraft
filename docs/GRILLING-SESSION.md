# MarioCraft grill session

Recorded October 18, 2026 (America/Denver).

This is a structured record of the complete numbered interview, preserving
decisions and changes of direction. It is not a verbatim chat export.
Later answers override earlier proposals where they conflict.

## Original intent

The user requested REA investigation of Minecraft first and Nintendo 64 Super
Mario 64 second, followed by combining the two: Mario's original player,
controls and physics in a Minecraft world. A grill session was required before
investigation. The research scope covers both complete selected releases,
while the first playable build has narrower scope.

## Round 1: Q1–Q5

| ID | Topic | User decision |
| --- | --- | --- |
| Q1 | Core product | Mario physics in the Minecraft world; initially described Mario actions hurting enemies and breaking blocks. Block breaking was later excluded from MVP. |
| Q2 | Success priority | Yes: faithful Mario 64 movement is the defining feature. |
| Q3 | First playable | Yes: prove movement before the broader playable experience. Separate obstacle course later rejected. |
| Q4 | Research breadth | Investigate everything in both games, aiming to understand how exact replicas could be made. Rejected a feature-only research scope. |
| Q5 | Distribution | Personal project. |

## Round 2: Q6–Q11

| ID | Topic | User decision |
| --- | --- | --- |
| Q6 | Actual engine | Yes: run inside actual Minecraft. |
| Q7 | Research boundary | Both complete selected releases; user emphasized every detail and original fidelity. |
| Q8 | Research before prototype | No parallel prototype: understand both games before building. |
| Q9 | Conflict precedence | Mario movement, controls and player; Minecraft world simulation. Understand both comprehensively before combining. |
| Q10 | Character | Exactly recognizable Mario, not merely a skin with similar movement. |
| Q11 | Deliverables | Separate comprehensive research dossiers and a staged playable combination. Research must finish first. |

## Round 3: Q12–Q17

User answered Q16 explicitly, then accepted all remaining recommendations.

| ID | Topic | User decision |
| --- | --- | --- |
| Q12 | Minecraft edition | Java Edition. |
| Q13 | Mario release | Original North American N64 release, not DS or later ports. |
| Q14 | Research completion gate | Evidence-based dossier review covering every identified subsystem and explicit unknowns. Agent disclosed that zero unknowns/universal equivalence cannot be guaranteed. |
| Q15 | Quirks | Preserve original movement quirks where meaningful in Minecraft. |
| Q16 | Input | Controller plus native keyboard mappings of N64 controls. |
| Q17 | Mario identity | Original model, textures, animations, voice, action sounds and action states. |

## Round 4: Q18–Q24

| ID | Topic | User decision |
| --- | --- | --- |
| Q18 | Camera | Mario third-person camera. Mining and placement excluded from MVP. |
| Q19 | Keyboard analog substitute | WASD directions with speed modifiers; controller remains analog. |
| Q20 | Mining | Out of MVP scope. |
| Q21 | Combat | Mario punches, kicks, stomps, dives and ground pounds can damage Minecraft creatures. |
| Q22 | Health | Keep Mario health/damage/death/drowning/healing; exclude hunger and armor. Death presentation later specified as Minecraft respawn screen. |
| Q23 | Minecraft interactions | No added inventory, block placement or item-use controls in MVP. |
| Q24 | Research format | Navigable local dossier with system maps, references, assets, specifications and unknowns. |

## Round 5: Q25–Q30

| ID | Topic | User decision |
| --- | --- | --- |
| Q25 | Terrain | Solid Minecraft block geometry; Mario techniques depend on available terrain. |
| Q26 | Scale | Derive and apply one consistent Mario-to-Minecraft unit conversion. |
| Q27 | Creature damage | Translate Mario attacks into Minecraft damage. |
| Q28 | Healing pickups | Coins/hearts out of scope for now. |
| Q29 | Lives | Keep Mario life counter with designated Minecraft spawn; subsequent answers define death screen and zero-life reset. |
| Q30 | Creature behavior | Preserve normal Minecraft AI except required Mario interactions. |

## Round 6: Q31–Q37

| ID | Topic | User decision |
| --- | --- | --- |
| Q31 | Minecraft version | Requested Java 1.12 instead of proposed 1.21.11; later settled 1.12.2. Supplied a local Mario USA ROM. |
| Q32 | Incoming damage/water | Minecraft hits translated into Mario meter/reactions; Mario-like swimming. |
| Q33 | Healing without pickups | Mario water-related healing and respawn health restoration, no invented healing mechanic. |
| Q34 | Attack equipment | Fixed crossover attack damage without held weapons, enchantments or inventory. |
| Q35 | Death/game over | Minecraft respawn screen when Mario dies; preserve world. |
| Q36 | ROM | Supplied local Super Mario 64 (USA).z64 path. |
| Q37 | Ability scope | Ordinary movement/combat/swimming; exclude caps/cap loss, cannons, stars and objectives from MVP. Research scope unchanged. |

## Setup interlude

The user paused the interview to resolve REA connectivity. A broken cached
installation was backed up and replaced; the MCP handshake succeeded. After
restart, REA tools appeared. Ghidra 12.1.4 and Java 21 were downloaded with
published checksum verification and configured for REA. A synthetic MIPS
decompilation test passed.

The agent clarified that REA 6.7.0 itself does not directly admit N64 cartridges
or raw MIPS inputs. A pinned N64LoaderWV extension was built for standalone
Ghidra; a generated synthetic cartridge passed CPU, address-mapping and
decompilation checks. This is a separate analysis route, not added REA support.
No game investigation or prototype began during setup.

The user installed Project64 and explicitly authorized its use whenever needed
during reverse engineering. The permission and project decisions were saved
in local project memory. The interview then resumed.

## Round 7: Q38–Q43

| ID | Topic | User decision |
| --- | --- | --- |
| Q38 | Exact Minecraft release | Java Edition 1.12.2. |
| Q39 | Death/lives | Each death shows Minecraft respawn screen and subtracts a life; zero-life respawn resets counter and preserves world. |
| Q40 | World | Normal Minecraft world only; explicitly forego obstacle course. |
| Q41 | Timing | Original Mario simulation timing independent of Minecraft tick/render timing. |
| Q42 | Branding/menus | Name MarioCraft; menus and title screen remain Minecraft-style. |
| Q43 | Multiplayer | Single-player only in MVP. |

## Round 8: Q44–Q48

| ID | Topic | User decision |
| --- | --- | --- |
| Q44 | World rules | Normal difficulty, day/night and creatures; hunger disabled; Mario replaces normal player movement. |
| Q45 | Keys | Configurable WASD, Space=A, J=B, Shift=Z, arrows=C, Q/E=L/R, Enter=Start. |
| Q46 | Mouse camera | Excluded; keep original-style camera buttons. |
| Q47 | Materials | Ordinary solid blocks; water as agreed; explicitly add Mario-like lava and fire. Other special Mario surfaces excluded initially. |
| Q48 | Music | Minecraft background music, Mario voice/actions; no Mario course music in MVP. |

## Round 9: Q49–Q54

User accepted all six recommendations.

| ID | Topic | User decision |
| --- | --- | --- |
| Q49 | Hazards | Mario fall/landing rules; documented conversions for non-Mario hazards such as explosions. |
| Q50 | Collision | Mario wall kicks, ledges, ceilings and crouching; disable Minecraft auto step-up. |
| Q51 | Mob targeting | Mobs target Mario as player; reconcile hits with Mario body/action/invulnerability. |
| Q52 | Dimensions | Overworld only; portals/Nether/End outside MVP. |
| Q53 | Persistence | Save position, health and lives; reload in appropriate safe idle/swimming state rather than mid-action. |
| Q54 | Verification | After research approval and implementation, compare against repeatable Project64 behavior/measurements; no obstacle-course world. |

## Round 10: Q55

| ID | Topic | User decision |
| --- | --- | --- |
| Q55 | Speed keys | WASD full stick magnitude; Ctrl walking; Alt intermediate. Configurable, with exact magnitudes derived from research. |

## Final summary and current gate

The agent presented the consolidated scope and asked for confirmation before
starting Minecraft investigation. The user's next instruction was:

> okay now in my github create a new repo name MarioCraft and save the grilling session there

This authorizes repository creation and saving this record. Research has not
started. Retain the explicit confirmation gate before investigating games,
and the separate approval gate for both dossiers before any prototype.

