# Minecraft 1.12.2 terrain, blocks, fluids and dimensions

This dossier records the world-system evidence for [issue #4](https://github.com/BrantTanner/MarioCraft/issues/4). It covers the full Minecraft research surface assigned to this ticket, including systems excluded from the MarioCraft playable MVP. The evidence supports an adapter design and identifies what still needs a connected-client gameplay observation; it does not claim that those unobserved paths were played.

## Evidence basis and limits

The release identity is the official Minecraft 1.12.2 server jar from the [issue #2 baseline](MINECRAFT-1.12.2-BASELINE.md): SHA-256 `fe1f9274e6dad9191bf6e8e36ee6ebc737f373603df0946aafcded0d53167e`. The baseline also records the official Mojang object URL and client hash. For this investigation, 28 subsystem classes were extracted from that server jar and hashed in the ignored local file `MarioCraft/tools/inspection/issue4/original-class-hashes.csv`. Selected classes were decompiled with CFR 0.152. Obfuscated class identity is tied to readable names using the local MCP `joined.tsrg` map (SHA-256 `57527913C5BEA02E110AA0D1FA929DA6F995F1A7836B0E46D891014B94779F4E`) and snapshot map (SHA-256 `EE5FD085BBBA67199FACFA15265BE72D20B2E94F130DB98C16558BD9F136C9C0`). The table below records key original class hashes.

The readable excerpts in `MarioCraft/tools/inspection/issue4/mapped-cfr/` come from the Forge 1.12.2-14.23.5.2859 mapped artifact (SHA-256 `64E8A3F965B6FC8CB576D5E3375E73BC905EA53E9CF84DBB27047D5F95C2AA06`). They were used as a navigation aid for names and control flow. That artifact is Forge-patched: for example, its `World.getCollisionBoxes` posts `GetCollisionBoxesEvent`. This event is a Forge hook and is not described here as vanilla behavior. Original vanilla classes are separately preserved under `original/` with per-class hashes; static conclusions are constrained to corresponding behavior in original obfuscated bytecode. CFR output is reconstructed source, not Mojang source.

| Original server class | Obfuscated name | SHA-256 |
| --- | --- | --- |
| `World` | `amu` | `56F90D5F016D476FAF474CB298D155742376F9029924F03F48E74CBC98DB5927` |
| `WorldServer` | `oo` | `2CBD59850B7F251578676E7CFC9890511E4EF07726AEE588D600BCF99C183C55` |
| `ChunkProviderServer` | `on` | `6BB87907624AF98D5E865C02D5C21140FFEFC40E90B52AF13248D9CD3E051997` |
| `ChunkGeneratorOverworld` | `aze` | `316ED0D08378885B963F8840BC5862528F494303A0F8FF319F090B853D81EDF9` |
| `Chunk` | `axw` | `A8E150576D9ECB246DFC2E691097C13082C57CDE1D6CC5119878C73D94FAF12E` |
| `Block` | `aow` | `7A97D3B7C84F644BE668A2E243BDEBC9708A7A47E1E3350C7EDA8AB778CC1C7F` |
| `BlockLiquid` | `aru` | `D0E89A5646DB3D7A3A6BCC3CFFF7248E2D0BEA9EE4D4CA7CC31FD626219E6956` |
| `Entity` | `vg` | `6CC1996D992C935506DCEF8FA5D7E97E2478A76C158AEC2B5A61FB0C02744096` |
| `WorldInfo` | `bfd` | `FA749E0E68044EA43B3EA7AAE2B566635CB20680305E23A60BD23E3FD27372EC` |

The live observation used the official vanilla server jar (same hash above), Temurin 8u504, seed `43112`, Survival mode, Peaceful difficulty, `view-distance=4`, `spawn-protection=0`, and `max-build-height=256`, bound to loopback on port 25596. The first startup reached `Done (6.798s)`, console queries returned seed 43112 and daytime 79, and `stop` saved the Overworld chunks. The ignored save at `MarioCraft/tools/runtime-issue4-console/issue4-overworld/level.dat` records spawn `(188,64,244)`. A follow-up run loaded that spawn area. `testforblock` at `(188,64,244)`, `(188,65,244)`, and `(188,66,244)` returned Stone. At `(188,200,244)`, `testforblock ... air` succeeded; `setblock ... stone 0 replace` succeeded; `testforblock ... stone` succeeded; then `setblock ... air 0 replace` and `testforblock ... air` both succeeded. The follow-up server was stopped cleanly and saved the world. This demonstrates a representative loaded-spawn terrain read, block-state mutation, and restoration through the vanilla server command path. It did not demonstrate a player interaction, collision, fluid motion, or portal crossing. Earlier probes at origin or distant coordinates returned “Cannot place/test for block outside of the world”; those failed probes did not establish whether the cause was build bounds or chunk availability and are not used as behavior evidence. The generated world, logs, and crash from a sandbox-blocked restart attempt remain ignored local evidence.

## World, chunks and generation

Minecraft uses integer block positions and a 16-block horizontal chunk grid. Chunk coordinates are derived from X and Z with arithmetic right-shift by four, which is floor-like for negative coordinates: block X `-1` belongs to chunk `-1`, not chunk `0`. Chunks span 16×16 columns and the 1.12.2 build height is Y `[0,256)`; out-of-height `World.getBlockState` returns AIR and writes are rejected. Server properties cap the configured world size, and a `WorldBorder` further bounds usable X/Z; those are horizontal world limits, distinct from build height and chunk availability. Entity positions and AABBs use doubles, while block positions and chunk keys are integer-valued. Keep continuous player motion in double precision and convert to integer cells with floor semantics, especially at negative coordinates.

The server chunk provider first looks for a loaded chunk, then tries its chunk loader, and otherwise asks the dimension's `IChunkGenerator` to generate and populate it. The Overworld generator builds terrain from biome/noise settings and then populates structures, lakes, dungeons, decoration, animals, and surface weather features. A chunk's block states are stored in vertical 16×16×16 sections; the palette/bit storage compresses repeated states. Chunk lifecycle includes load/populate, queued unload, save, and load-from-file. A request that generates a chunk is materially different from a loaded-only query; adapter reads and collision probes should not silently generate terrain.

The dedicated server prepared the seed's spawn area before announcing readiness and saved Overworld region files on shutdown. That is direct runtime evidence of world creation, spawn-area preparation, block-state read/write at the recorded spawn chunk, and persistence for this seed, not a controlled visual assessment of terrain composition. No Nether or End terrain files were created during the no-player run, so their generation is source evidence only.

## Blocks, state, collision and geometry invalidation

A block is not a sufficient terrain sample: each location holds an `IBlockState`, which combines a block identity with declared properties such as facing, half, shape, moisture, and fluid level. State transitions can change collision, opacity, emitted light, rendering, support, or neighbor behavior. An adapter should query the resolved state at the requested position rather than infer shape from a block name or legacy metadata alone.

Collision and rendering are separate interfaces. Blocks can report a render bounding box, a collision bounding box, side-render rules, and one or more collision boxes. Full cubes are only one case. Slabs, stairs, fences, walls, panes, snow layers and other partial shapes require state-dependent boxes; some states can produce multiple boxes. Liquids are not full or opaque cubes and report a null block collision box, despite having a surface used for rendering and ray interactions. A visual mesh must never be substituted for a gameplay collision shape. Collision queries gather intersecting block boxes in the queried AABB and, when an entity is supplied, can add entity collision boxes. Entity movement uses swept/expanded AABB queries and clips requested displacement along Y, X and Z; this makes contact geometry and update ordering observable to a Mario adapter.

For an adapter, cache collision geometry only with an explicit invalidation path. `World.setBlockState` stores a new state and, when light opacity/emission differs, triggers light checks; update flags separately control client/listener notification and neighbor/comparator updates. Neighbor changes, scheduled ticks, random ticks, chunk load/unload, and state-dependent shape changes can all alter nearby geometry. Invalidate the changed cell and any support/neighbor-dependent collision cells; refresh on chunk lifecycle transitions. Do not use a render-chunk rebuild as the sole collision invalidation signal.

`World.isBlockLoaded` maps a position to its chunk with `x >> 4` / `z >> 4`. A request for a loaded area can fail because the required surrounding chunks are absent; this is not the same condition as a coordinate lying outside build height or world border. Light recalculation explicitly requires a loaded area around its target. Scheduled block updates are accepted only for loaded blocks and are deferred when the required update area is unavailable. Treat unloaded as unknown/unavailable to the adapter, not AIR or empty collision, unless the game design explicitly chooses a streaming policy.

## Fluids and dynamic simulation

Water and lava use `BlockLiquid` states with a `level` property from 0 through 15. Static/flowing variants share liquid geometry and material behavior but differ in update responsibilities. Flow direction is derived from neighboring liquid depth and passability, and scheduled/random update paths can change neighboring cells over time. The source code distinguishes the liquid's render/surface bounds from its collision box; lava is not passable even though liquids do not provide ordinary solid collision. The liquid ray-collision predicate also differs from the entity collision predicate, so “can be targeted” is not equivalent to “blocks movement.”

World simulation combines scheduled block ticks, random ticks in eligible chunk sections, neighbor notifications, block entity ticks, weather, and dimension-specific updates. Water/lava flow and redstone propagation therefore are not static terrain properties. Block entities (for example, chests, furnaces, signs, and other stateful blocks) hold data and may tick independently of the block's state; if represented in a later slice, their persistent data and tick-driven effects need an explicit adapter contract. Redstone's power and neighbor propagation may affect block state and geometry indirectly; it is in research scope but not in the MVP slice. The server tick runs these systems even when a client render mesh is not being rebuilt.

## Lighting, weather and world simulation

Minecraft tracks block light and sky light on a 0–15 scale. Blocks contribute opacity and emitted light; opacity/emission changes schedule local light recalculation. Chunks hold light data separately from block state, and light propagation is bounded by loaded-area checks, so a replica should not conflate “not currently computed/loaded” with darkness. Lighting affects rendering and gameplay rules such as spawning, but does not define collision geometry.

Weather and time are world-level inputs. Server ticks update rain/thunder and can trigger freeze, snow accumulation, and rain-sensitive block callbacks in eligible areas. Random ticks operate on sections flagged for random ticking and use the world `randomTickSpeed` rule. Use deterministic seeds and recorded game rules when measuring behavior; weather, random ticks, and biome-dependent decoration otherwise make terrain observations hard to reproduce.

## Dimensions and portals (research scope; MVP excluded)

The three built-in dimension types are Overworld (`0`), Nether (`-1`), and End (`1`). Providers choose dimension-specific world properties and chunk generators. The Nether has no ordinary sky-light behavior and uses a movement factor that scales portal X/Z coordinates by 8 relative to the Overworld; the End has its own terrain/structures and persistent dragon-fight data. Entity dimension changes recreate or transfer entities into the target world's bounds and use a teleporter to find or construct a portal destination. Nether portals are frame/axis-dependent blocks and route through the teleporter; End portals and End gateways have separate mechanics. These systems demonstrate that a terrain adapter must treat each dimension as a distinct world with its own generator, border/light rules, and transition policy.

Nether, End, portal creation, dimension transfer, dragon/gateway logic, and any portal-triggered Mario transition are excluded from the playable MVP. They remain covered here so exclusion is a deliberate product boundary, not an undocumented omission. No portal, dimension crossing, or remote dimension chunk was exercised at runtime.

## Adapter boundary and unverified paths

For the first playable slice, use the Overworld's integer block grid as the terrain address space, keep player motion as double-precision coordinates, and ask a collision adapter for actual state-derived AABBs over the movement sweep. Keep rendering, collision, lighting, fluid effects, and block update simulation as separate outputs. Treat world height/border and unavailable chunks as distinct outcomes. Record geometry invalidation at state mutation, neighbor update, and chunk lifecycle seams. This is a source-based adapter proposal; the Mario block/unit scale and movement tolerances must be measured against Mario USA and the later integrated gameplay seam before fixing conversion constants.

Not yet observed live: player interaction or digging; partial-block collision and ray hitboxes; fluid flow or lava contact; block entity ticking/persistence; redstone; light propagation; weather transitions; chunk unload/reload while moving; Nether generation or scaling; End structures; any portal transition. These are explicit gaps for the later integrated evidence/review tickets, not implied successes. The Forge client startup issue recorded in [issue #3](https://github.com/BrantTanner/MarioCraft/issues/3) remains a separate tooling limitation; this server-side observation did not depend on that client.

## Reproduction

From the workspace root, verify the jar hash against the baseline, then start the local server from a disposable ignored directory with the same settings above. Accept the EULA for that local test only, set `server-ip=127.0.0.1`, set a non-conflicting port, and use `level-seed=43112`, `level-name=issue4-overworld`, `view-distance=4`, `max-build-height=256`, `spawn-protection=0`, and `max-tick-time=-1`. Run with the pinned Temurin JDK 8 and official `MarioCraft/tools/server-1.12.2.jar`. After `Done`, issue `seed`, `time query daytime`, and at the recorded spawn chunk run:

```text
testforblock 188 64 244 stone
testforblock 188 200 244 air
setblock 188 200 244 stone 0 replace
testforblock 188 200 244 stone
setblock 188 200 244 air 0 replace
testforblock 188 200 244 air
stop
```

The `setblock` step is reversible because the preceding query confirms the cell is air. Preserve the local log and save under `MarioCraft/tools/`, which is git-ignored. Do not upload the generated world or runtime logs. A connected-player gameplay observation requires joining with a client and recording the actual player position; this server-command procedure does not claim collision or player interaction behavior.

## Evidence inventory and reproduction tooling

The ignored `MarioCraft/tools/inspection/issue4/` bundle contains the selected original server classes, corresponding Forge-mapped classes, CFR outputs, and `original-class-hashes.csv`. The focused class set includes `World`, `WorldServer`, `WorldProvider` and its built-in providers, `ChunkProviderServer`, Overworld/Nether/End generators, `Chunk`, storage/state containers, block/liquid/portal classes, `Teleporter`, `TileEntity`, `Entity`, `Biome`, `BiomeProvider`, and `WorldInfo`. The local runtime log and saved region files live under `MarioCraft/tools/runtime-issue4-console/`. All jars, generated code views, worlds, and logs remain ignored local evidence; this repository contains only the research report.

The report is reproducible at the level needed for the current source and bounded server checks, but it does not provide an executable harness for connected-player block/collision scenarios. Those scenarios remain for the integrated gameplay seam and must be captured before making live behavior claims.

