# MarioCraft scope

Recorded October 18, 2026 (America/Denver).

## Product

Run inside actual Minecraft Java Edition 1.12.2. Play as recognizable original
North American Nintendo 64 Super Mario 64 Mario, with original model, textures,
animations, voice, action sounds, movement states, controls, camera, action
timing and applicable quirks. Preserve Mario simulation timing independently
of display frame rate and Minecraft simulation.

The world retains Minecraft block geometry, Normal difficulty, creature AI,
day/night, world simulation and background music. Mario attacks cause Minecraft
damage through explicit crossover rules. Mobs recognize Mario as the player;
hits account for his body, action state and invulnerability.

Use Mario collision, wall kicks, ledge grabs, ceilings, crouching, fall damage,
swimming, drowning and water-related healing. Disable Minecraft automatic
step-up. Lava and fire use Mario's corresponding damage/action behavior.
Other hazards require documented translations. Derive one consistent spatial
scale from research rather than inventing it before investigation.

Keep Minecraft-style title/world/pause/settings screens, with MarioCraft
branding. Every death uses Minecraft's respawn screen and subtracts a life.
Respawning at zero lives resets the life counter; preserve the world. Save
position, health and lives; reload into an appropriate safe idle/swimming state.

## Controls

Controller input is primary and analog. Native keyboard support maps original
N64 buttons, with configurable defaults:

| Keyboard | N64 input |
| --- | --- |
| WASD | Stick directions at full magnitude |
| Ctrl + WASD | Walking magnitude |
| Alt + WASD | Intermediate magnitude |
| Space | A |
| J | B |
| Shift | Z |
| Arrow keys | C buttons |
| Q / E | L / R |
| Enter | Start |

Exact speed magnitudes come from research. Check simultaneous-key conflicts
during implementation. No mouse camera in the MVP.

## MVP exclusions

Mining, block placement, inventory/item interactions, hunger, armor, held weapons
or enchantments, coins/hearts, power-up caps/cap loss, cannons, stars/course
objectives, Mario course music, multiplayer, portals, Nether and End travel.
No separate obstacle-course world. Exclusions apply to the playable MVP,
not the comprehensive game research scope.

## Research and approval gates

1. Complete the grill session and obtain shared-understanding confirmation
   before starting game investigation. The final summary was presented;
   the next user request was to create this repository and save the session.
   Do not treat that request as an unambiguous authorization to begin research.
2. Investigate the complete selected Minecraft release first.
3. Investigate the complete selected Mario 64 release second.
4. Deliver navigable dossiers with system maps, relevant code/data references,
   asset inventories, behavior specifications, evidence, interactions and
   explicit unresolved questions for every identified subsystem.
5. Obtain user review/approval of both dossiers before any prototype.
6. After implementation, compare movement, camera, swimming and damage against
   repeatable Project64 recordings and measurements.

The user wants fidelity sufficient to understand how exact copies could work,
then combine them. Exhaustive recovery and equivalence under every possible
input cannot be promised. Unknowns remain visible; crossover rules are design
decisions, not behavior observed in either original game.

## Tool status and authorization

REA 6.7.0 is connected. Portable Ghidra 12.1.4 and Java 21 are installed locally.
The Ghidra provider is available. A pinned N64LoaderWV build passed synthetic
cartridge CPU/mapping/decompilation checks. Neither game has been investigated.

REA's current importer cannot directly open N64 ROMs or raw MIPS inputs.
N64 work uses standalone Ghidra; do not describe those outputs as REA Evidence.
Initial loader mapping is not proof of complete runtime mapping. Compressed
assets, DMA, overlays, BSS, indirect calls and graphics/audio microcode require
separate investigation. Loader compatibility was not yet tested on Mario.

The user reports installing Project64 and explicitly gives permission to use
it whenever needed during reverse engineering. This covers routine launching
with the supplied ROM, testing scenarios, debugger/breakpoints, memory
inspection, dumps, recordings and save states. No repeated permission is needed
for these authorized investigative uses. Preserve originals and unrelated data.

The supplied ROM path is retained in local project memory, not this repository.
Personal local project; public distribution is not part of current scope.

