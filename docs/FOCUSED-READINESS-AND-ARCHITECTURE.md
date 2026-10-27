# Focused implementation contract — 26 October 2026

The user explicitly narrowed the mod to Mario camera, movement and interactions
with Minecraft mobs, declared existing research sufficient, and authorized
implementation using one GPT-6.1 Sol worker at a time. This supersedes the older
full-research gates in issues 17/18 and planning documents. It does not establish
that excluded research was completed. Issues 59 (progression), 60 (additional
enemy families) and 65 (EEPROM persistence) remain outside this implementation
readiness claim. Neither broad research ticket is closed by this document.

Evidence used: Minecraft player/input, terrain and creatures/combat reports;
Mario movement 11/37–41, collision/health 12/42–46, camera 47, presentation 48,
object lifecycle 52, attack classification 53 and selected enemy evidence 54.
Original SM64 AI is not transplanted: Minecraft mobs retain their existing AI,
identity and normal world membership. Their interaction with Mario is adapted.

## Decision

Use libsm64 commit `fd11813208272b4271d92bd92feb8f3fdbe61be5` through
JNA 4.4.0 for movement/actions, collision and animated Mario geometry. Java owns
Minecraft integration and the applicable camera behavior from research 47.
The [upstream API](https://github.com/libsm64/libsm64/blob/fd11813208272b4271d92bd92feb8f3fdbe61be5/src/libsm64.h)
accepts triangle surfaces and camera-relative input, returns action/health and
geometry, and exposes attack/damage functions. There is no camera API.
This preserves substantially more original movement logic than rebuilding every
action in Java. A pure Java implementation avoids native deployment but has a
much larger fidelity and validation burden, so it is not selected.

Upstream declares CC0 in LICENSE.md and identifies its decompilation origin in
README.md. Its importer downloads Mario geometry/model source at pinned
`n64decomp/sm64` commit `06ec56df7f951f88da05f468cdcacecba496145a`.
The resulting DLL therefore contains imported geometry: keep the DLL, downloaded
sources, ROM, generated textures and other game-derived output local and ignored.
CC0 is not represented as clearing third-party game assets. Publish our adapter
and reproducible local bootstrap, not the generated DLL or ROM contents.

## Integration contract

- Minecraft 1.12.2, Forge 14.23.5.2864, ForgeGradle 3.0.197, mappings
  20171003-1.12, Java 8, Gradle 4.9. Guard the generated Side.class repair using
  the existing issue34 script; an untouched MDK is not claimed to work.
- Begin with 100 Mario units per block: the researched 160-unit clearance becomes
  1.6 blocks. Treat this as a crossover choice, then verify terrain fit in game.
- Use native floor/wall/ceiling collision against triangulated Minecraft collision
  boxes; use local origin rebasing to preserve precision and bounded native space.
  Partial blocks need their actual collision boxes, not their visual full cube.
- One native context, owned by one simulation thread. Tick at 30 Hz, interpolate
  for display, freeze on pause and discard paused backlog. Cap stall catch-up at
  five steps. Do not run an independent vanilla player movement step.
- Retain the real Minecraft player identity for mob targeting. Coordinate server
  position and damage explicitly; rendering alone cannot establish integration.
- Keyboard defaults remain WASD/full, Ctrl/walk, Alt/intermediate, Space/A,
  J/B, Shift/Z, arrows/C, Q/E/L/R. Normalize diagonals and clear input on focus/menu
  changes. Mouse camera remains excluded.
- Start with camera mode16/free-roam follow and C controls, then applicable
  mode6 inspection and mode8 water transitions. Terrain obstruction must be
  measured; camera47 did not runtime-validate every obstruction placement.
- Crossover attack damage is separate from the original attack classifier.
  Mob overlap alone is not accepted attack evidence; check action/timing, facing,
  target geometry, obstruction and a contact ledger before server-side damage.
  Incoming hits must use Mario invulnerability and avoid duplicate vanilla damage.

## First slice and honest boundaries

Bootstrap, mod entry and world/player lifecycle probes precede gameplay hooks.
Native smoke verifies ABI sizes against compiled C, ROM identity, static floor,
run/jump/land, nonempty geometry, health and repeated teardown/reinitialization.
Asset-free tests verify 30 Hz totals across refresh rates and pause/stall policy.
These checks do not establish a playable integrated mod. The next slice must
connect real terrain, player position, rendering, camera and one mob interaction,
then demonstrate it in a normal generated Overworld before closing issue19.

No broad #17 closure, perfect equivalence, full mob coverage, final persistence,
menu/audio completion or final packaging is implied by scaffolding or native tests.
