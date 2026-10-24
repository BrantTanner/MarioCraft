# Minecraft 1.12.2 lifecycle, saving, networking, and server execution

This report completes the research slice for [issue #8](https://github.com/BrantTanner/MarioCraft/issues/8). It is scoped to the official vanilla 1.12.2 client and server jars, plus the representative client and dedicated-server observations linked below. All static class identities are checked against the original jar bytes. CFR 0.152 reconstructions are navigation aids; they are not Mojang source and their local paths are ignored by Git.

## Release and evidence boundary

The client jar is SHA-256 `8ada07da5ee77dad3527bd7278fbd05ee1fc8a597813b216a871a2d7d64cc64f`; the dedicated server jar is `fe1f9274e6dad9191bf6e6e8e36ee6ebc737f373603df0946aafcded0d53167e`. These are the official release artifacts recorded in the [baseline report](MINECRAFT-1.12.2-BASELINE.md). The live vanilla client matched the client jar and reported Java 8u51 in the [runtime report](MINECRAFT-CLIENT-PROGRESSION-RUNTIME.md). Source-level names below were cross-checked against the pinned ForgeGradle 1.12.2 mapping cache for navigation; every table row gives the original jar entry and the SHA-256 of its extracted bytes.

The observed client world was only the disposable `codexissue33run202610`, seed `33102024`, with cheats and documented gamerule changes. The clean save, reload, player NBT, death and respawn observations are recorded in the [runtime report](MINECRAFT-CLIENT-PROGRESSION-RUNTIME.md). The dedicated-server runtime evidence is the loopback-only server and RCON command path in the [creature report](MINECRAFT-CREATURES-COMBAT.md): it establishes server startup, command dispatch, save and stop, but does not establish external multiplayer behavior.

## Startup and ownership

The launcher selects the version, Java runtime, libraries, assets, account session and game directory before it invokes the client. The official 1.12.2 version metadata names `net.minecraft.client.main.Main` as the launch entry. Its `main` method parses launcher arguments, constructs `Minecraft` (`bib.class`), and starts it; `bib` owns the runtime loop and client state. The client owns the display, input, current screen, client world, renderer and `Timer`. Its main loop is `Minecraft.runGameLoop`: it updates the timer, drains client scheduled tasks, runs up to ten client ticks per rendered frame, renders, and presents the frame. `Minecraft.runTick` is the client-side world/player update path. Its rate is related to elapsed time and the client's timer; it is not a second 20 Hz server loop.

Opening a singleplayer save constructs an `IntegratedServer` (`chd.class`) as a `MinecraftServer` subclass, prepares three server dimensions and starts the standard server thread. `Minecraft` then creates a local `NetworkManager` channel and proceeds through the login and play handlers. The integrated server is therefore a real server simulation in the same process, connected to the client through the packet path; it is not simply the client world ticking twice. The dedicated server entry `nz.class` also subclasses the same common `MinecraftServer`, but has its own console, properties, network listener and lifecycle, without client rendering or GUI.

`MinecraftServer.run()` is the authoritative server scheduling loop. It accumulates elapsed milliseconds and invokes its server tick at 50 ms intervals (20 Hz). If it is more than 2,000 ms behind, it emits the overload warning and caps catch-up backlog; otherwise it executes repeated ticks until the accumulator falls below 50 ms. Common tick work drains queued jobs, ticks worlds and entities, processes commands and scheduled work, and synchronizes state. Normal world saves are attempted every 900 server ticks (about 45 seconds at target cadence); shutdown explicitly saves players and all loaded worlds. These are static traces of the identified bytecode through CFR, not claims that a healthy server always achieves its target cadence.

The server thread is named `Server thread`; `MinecraftServer` keeps its thread reference and tests ownership by comparing it with `Thread.currentThread()`. Netty event-loop threads own socket reads and writes. Packet handlers use `PacketThreadUtil.checkThreadAndEnqueue` or `addScheduledTask` to move play-state work to the server thread; the client handler similarly schedules world/player changes onto the client thread. Thus packet callbacks do not become an alternate gameplay simulation thread. Client rendering and input remain on the client thread; integrated world simulation remains on the server thread.

## Pause, world open and close

The pause decision is made in `Minecraft.runGameLoop`, after a frame: it is true only when a singleplayer integrated server is active, a current screen exists and says it pauses gameplay, and the integrated server is not published to LAN. That flag is exposed by `Minecraft.isGamePaused`. In the exact `IntegratedServer` tick override (`chd.C()`), a transition into pause logs “Saving and pausing game…”, saves connected player data, and then avoids the parent server tick while still draining queued server tasks. When a world is opened to LAN, `getPublic()` becomes true and the same pause screen no longer freezes server simulation. A dedicated or remote multiplayer server is never paused by a local client's menu.

This control flow matters for Mario's clock. Keep Mario's fixed-step accumulator independent from Minecraft's 20 Hz tick and from frame rendering; derive its step interval from the later Mario evidence. Advance it from one authoritative simulation path only, and do not advance it while the integrated server is paused. Rendering may interpolate snapshots, but it must not apply another movement update. Replacing the vanilla movement/collision/camera/HUD behavior must preserve the Minecraft player identity and the server's player/network lifecycle. This is a timing and ownership constraint for later implementation, not a prototype or a choice of Mario's step rate.

The game menu's “Save and Quit to Title” path stops the connection and clears the client world; the integrated server then exits through the common server stop path, which saves player data, flushes each world/chunk provider and closes server resources. `SaveHandler` checks a per-world `session.lock`, writes `level.dat_new`, rotates the prior `level.dat` to `level.dat_old`, and renames the new file into place. It saves player data through a temporary UUID-named `.dat.tmp` file, then renames it to `.dat`. A later open prefers `level.dat` and falls back to `level.dat_old`. Chunk serialization proceeds through `AnvilChunkLoader` and `RegionFile` into region files. These paths show recovery precautions; they do not guarantee recovery of changes since the last save after power loss or process termination.

Death is a server player-state transition, not a world restart. The client presents `GuiGameOver` (`bkv.class`); its respawn control calls the player respawn request and then clears the screen after the server response. The observed disposable-world run showed the death screen, respawn with the expected reset health/inventory state under its recorded rules, and a later save/reload. `EntityPlayer`/`EntityPlayerMP` serialize entity/player fields to NBT; the player list loads from the world's singleplayer player record for the integrated owner or from `playerdata/<UUID>.dat` for other players, and saves each connected player on pause, periodic save and shutdown. Death drops and whether inventory is retained depend on game mode and `keepInventory`; the runtime report records the settings and observed drops instead of treating them as universal defaults.

## Protocol, commands, and settings

The common packet registry is divided into handshake, status, login and play connection states (`EnumConnectionState`, `gx.class`); each state maps packet IDs to packet classes and codecs. The 1.12.2 protocol version is 340. `NetworkManager` (`gw.class`) attaches the active packet listener and connection-state attribute to a Netty channel. Its `channelRead0` dispatches decoded packets to that listener; state changes pause auto-read until the event loop installs the new codec state. When the channel is open, sends flush any waiting packets and dispatch writes on the Netty event-loop (using `eventLoop().execute` when called from another thread). The lock-protected outbound queue holds packets while the channel is not yet open; it is not the general off-event-loop path. Disconnect and exception paths close the channel and retain a disconnect reason. `NetworkSystem` (`oz.class`) owns server listeners; the integrated server binds a local endpoint for its in-process client and can create a LAN listener when the user selects “Open to LAN.” Dedicated servers bind their configured address and port. The handshake listener selects status or login; login handlers exchange profile/authentication state, encryption/compression negotiation and player identity before installing the play listener. The play listener receives movement, world, entity, chat and command packets. `PacketThreadUtil.checkThreadAndEnqueue` moves gameplay work from Netty onto the owning client/server thread. Static class inspection does not establish successful authentication against a current external service or any public-server compatibility.

Chat commands travel as client chat packets and are parsed on the server by `ServerCommandManager` and `CommandHandler`. Permission checks, operator levels, command feedback and player/world mutations are server-owned. The dedicated server reads `server.properties` (including `online-mode`, address/port, level name, difficulty, game mode, view distance and other server settings), requires EULA acceptance, and maintains operator, whitelist and ban files. Client display, controls, sound, language, resource-pack and other client preferences live separately from world saves. Singleplayer's cheats setting and world game rules are saved with that world. The in-game “Open to LAN” action changes the integrated server's public/command settings for that session. The inspected UI exposed the server's LAN and command controls; no external peer joined this investigation.

The client and integrated-server setup constructs Mojang's authentication service, session service and profile repository from the current client session. The dedicated server's default online mode also verifies player profiles; offline mode disables that check. These are external account/service boundaries and configuration choices; this ticket did not inspect account credentials, exercise sign-in, measure service traffic, or validate Realms. Launcher downloads and updates also sit outside the two selected game jars.

## Persistence, crash and extension seams

World metadata, chunks, player data, statistics and advancements have distinct storage paths. `SaveHandler` construction writes `MinecraftServer.getCurrentTimeMillis()` (a `System.currentTimeMillis()` value) to `session.lock`; `checkSessionLock` compares the on-disk value with that handler instance value before writes and aborts on mismatch. This is an ownership guard implemented as a token check, not an OS-held exclusive file lock. `WorldInfo` is serialized into `level.dat_new`, the previous `level.dat` is rotated through `level.dat_old`, and load tries `level.dat` before the old copy. Player data is UUID-named NBT written to `.dat.tmp` then renamed; player records are loaded and saved through `PlayerList` as players join, leave, pause-save, periodic save, or server shutdown. Chunk and block-entity state is stored in Anvil region files; player state is NBT; statistics and advancements are separate per-player data. The runtime run demonstrated the concrete boundary: clean Save and Quit, reopen the same disposable world, inspect saved player state, die and respawn, save again, then parse the final player NBT. Its pre-death player file was later overwritten by the post-death save, so the report preserves the recorded values/hash and does not claim a retained pre-death binary snapshot.

Both client and server paths construct a crash report under `crash-reports` and log whether it was written. The server's tick-loop `catch` writes a timestamped `-server.txt` report, then its `finally` path stops the server; that stop path attempts player and world saves and closes resources. Client crash handling writes `-client.txt` and exits. The server loop therefore has an orderly cleanup attempt after a caught failure, while sudden process or host termination is outside that guarantee. On clean restart, the normal world selection and load path reads the saved data described above. `level.dat_old` is a fallback for a damaged/missing current metadata file, not a transaction for every player or chunk record.

Vanilla persistence extension seams visible in the selected release are `WorldSavedData` plus per-world `MapStorage` for named world-scoped data, and entity/player NBT read/write methods for player-scoped fields. A mod should attach Mario state to the player's save lifecycle and store world-global state through a world save object; it should not use the client loop or a transient GUI as the source of truth. The exact Forge capability/event integration is deferred to the architecture review: the pinned Forge 14.23.5.2859 development launch currently fails before the title screen, so no successful Forge lifecycle hook was observed.


## Reproduction

From the project workspace root in PowerShell, confirm the jar hashes, extract the listed bytecode entries, hash those exact class files, and decompile with CFR. This regenerates the local ignored evidence under `MarioCraft/tools/inspection/issue8/`:

```powershell
$client = (Resolve-Path 'MarioCraft/tools/client-1.12.2.jar').Path
$server = (Resolve-Path 'MarioCraft/tools/server-1.12.2.jar').Path
$jar = (Resolve-Path 'tools/java/jdk-21.0.12.1+1/bin/jar.exe').Path
$java = (Resolve-Path 'tools/java/jdk-21.0.12.1+1/bin/java.exe').Path
$cfr = (Resolve-Path 'tools/cfr-0.152.jar').Path
Get-FileHash $client,$server -Algorithm SHA256
$out = (New-Item -ItemType Directory -Force 'MarioCraft/tools/inspection/issue8').FullName
$clientEntries = @('net/minecraft/client/main/Main','bib','blg','bkv','bry','brz','bsb','chd','bfb','bfc','bfd','bfe','gw','gx')
$serverEntries = @('net/minecraft/server/MinecraftServer','nz','oz','pl','pa','pb','pc','hv','bfb','bfc','bfd','bfe','gw','gx','aye','ayj','bj','dh','oq','aed','bet','bfm')
New-Item -ItemType Directory -Force "$out/client","$out/server","$out/client-decompiled","$out/server-decompiled" | Out-Null
Push-Location "$out/client"
foreach ($name in $clientEntries) { & $jar xf $client "$name.class" }
Pop-Location
Push-Location "$out/server"
foreach ($name in $serverEntries) { & $jar xf $server "$name.class" }
Pop-Location
Get-ChildItem "$out/client","$out/server" -Recurse -Filter '*.class' | Get-FileHash -Algorithm SHA256
Get-ChildItem "$out/client" -Recurse -Filter '*.class' | ForEach-Object {
  & $java -jar $cfr $_.FullName --outputdir "$out/client-decompiled" --silent true
}
Get-ChildItem "$out/server" -Recurse -Filter '*.class' | ForEach-Object {
  & $java -jar $cfr $_.FullName --outputdir "$out/server-decompiled" --silent true
}
```

The mapped method anchors cross-checked for navigation are `Main.main` -> `bib` construction/start; `bib.runGameLoop` and `runTick`; `blg`/`GuiIngameMenu` and `bkv`/`GuiGameOver`; `chd.C()`/`IntegratedServer.tick`; server `MinecraftServer.run`, `C` (server tick), `u` (save all worlds) and run-loop `finally`; `gw.channelRead0`, outbound dispatch and queue drain `m`; `pb.a(C00Handshake)`/`NetHandlerHandshakeTCP.processHandshake`; `pc`/`NetHandlerLoginServer` login-state transitions; `pa`/`NetHandlerPlayServer`; `hv.a(Packet, INetHandler, IThreadListener)`/`PacketThreadUtil.checkThreadAndEnqueue`; `pl`/`PlayerList`; and `bfb.i()` (write `session.lock` value), `bfb.c()` (check it), metadata save and player-data read/write. Storage anchors include `aye`/`AnvilChunkLoader`, `ayj`/`RegionFile`, `oq`/`EntityPlayerMP`, `bet`/`WorldSavedData`, and `bfm`/`MapStorage`. Mapped names are navigation aids; the table binds each class to its original jar entry and hash.

For runtime observations, use only the existing `codexissue33run202610` world. The client report records its seed and changed gamerules. Its clean Save and Quit/reload, saved player NBT, death/respawn and second clean save are reproducible using the reported commands and menu path. For pause, `/time query gametime` returned 14625 before opening Escape's Game Menu; after approximately 10 seconds there and a second query it returned 14738. This is only coarse qualitative corroboration of the pause branch; command-entry time and host scheduling were not measured precisely. The client was saved back to Title after the run.

## Original class byte table

| Mapped class | Original jar entry | Original class SHA-256 |
| --- | --- | --- |
| `Main` | client `net/minecraft/client/main/Main.class` | `ad187c7e00716c1657acf4b6879fcaff9de8a6beba3b70761193d11848af9898` |
| `Minecraft` | client `bib.class` | `d68fe8e77645e11f28d356b56f718d180a0bc10dc72096dc1a755f7dbd5358da` |
| `GuiIngameMenu` | client `blg.class` | `a3d31d55ba978ddeaf726b27cfac792abb78f44901ba2a226f882f442bb76cf8` |
| `GuiGameOver` | client `bkv.class` | `9b1d796294fa4ec63799099e737c4f08af38000a73c568bf46534b297e074e92` |
| `NetHandlerLoginClient` | client `bry.class` | `7086bf72848bddedfcd2ac96b263e6c9098b387092fe33b7c706e30e832f8b70` |
| `NetHandlerPlayClient` | client `brz.class` | `63528a4f5e46664a6aca2c63948ea82b7ae9da43ae925a0097d625fafc737eaa` |
| `WorldClient` | client `bsb.class` | `adda45b720ef371d72be159457cc42bbd538ae4ea84f5ef969421fcea83ec24b` |
| `IntegratedServer` | client `chd.class` | `92780fa94053927fd9a0184b70dfff1cc6b7cbd60e8e1c4d6805f04777b2ce1f` |
| `SaveHandler` | client `bfb.class` | `16d47be91db464339910fadae710dad2a5c625ef16ca0b453ba3b69fa53640d` |
| `SaveFormatOld` | client `bfc.class` | `83f8b5bc665f62cdba24310d9ad54e413beeb2ffbc77a9064b08dcc6022cb23` |
| `WorldInfo` | client `bfd.class` | `79e9a2143ec297e093acf968f161f8ee7d6824301cff7fd1d183327988daa05e` |
| `ISaveHandler` | client `bfe.class` | `9ffc6dafceeea72b77b7c5a8035fd302346a71fa85a0c707587eca9015894f82` |
| `NetworkManager` | client `gw.class` | `4d64dcbcdc872339d7480fbdf8b6c1ffdebde917a8a97de273218bdf6b65d68c` |
| `EnumConnectionState` | client `gx.class` | `edd91a432b4d7f86a89a3d384e6f626443308a566e18e8c19adced43df6146ec` |
| `MinecraftServer` | server `net/minecraft/server/MinecraftServer.class` | `246e7916fde4261e776c4beb17c5b7e84a80948c5ce60abf1e8011ca88288b0f` |
| `DedicatedServer` | server `nz.class` | `58e6ffba6e052a2752bc90d8549639493a3fb2d04de2711c7732fe337f1446f9` |
| `NetworkSystem` | server `oz.class` | `1e527d902bfa4c32b01aef8ff7353faabf2eba47b5806a2744050e9f6237a4ab` |
| `PlayerList` | server `pl.class` | `4f47d211d4a1180e32664c3a0cf2cd6831d05564aa68df93f183d5a1822e6504` |
| `NetHandlerPlayServer` | server `pa.class` | `1d9236294726031c9d7c096bfb61fd858b233d6d95c4be0423d85fdd63ab9753` |
| `NetHandlerHandshakeTCP` | server `pb.class` | `6b03d958c138abca9f3680930c69d1e4b99049bdcc8131508a73b169ef995580` |
| `NetHandlerLoginServer` | server `pc.class` | `d1e47b460ae9f238f85eb0987527a771a66ff5e15a7f797d9873ab71a2316cef` |
| `PacketThreadUtil` | server `hv.class` | `3f482e6e6bd43ddf6e89d9be27fd9eb1f8e0577706724f2e0dcefe3e63c1dfd2` |
| `NetworkManager` | server `gw.class` | `4e6e4f208794de5b2cc9bdf925e54746afa871879f1402ddc3202bd80c716d36` |
| `EnumConnectionState` | server `gx.class` | `edd91a432b4d7f86a89a3d384e6f626443308a566e18e8c19adced43df6146ec` |
| `AnvilChunkLoader` | server `aye.class` | `97d1e356802cf6f02f4cf88d7f89335872c19ba3b024887b970d04d652256162` |
| `RegionFile` | server `ayj.class` | `36ccebdab446971ea33d2c1c06f3369d1d4a0b52ab518ec4a11bf3c432e704ec` |
| `CommandHandler` | server `bj.class` | `05e7dcbaa3f1cd6491885cab05a61552108c8f9c9b8fa3e36e11c9594ec9bd90` |
| `ServerCommandManager` | server `dh.class` | `07b9faf4e065ad2feef7528ed1b1f8578413d8ce84f4b83a210598b0216b8e51a` |
| `EntityPlayerMP` | server `oq.class` | `c41347d65b39c5e93c04967af20566fcded1ece81e15f2425cfdb8db8d328328` |
| `EntityPlayer` | server `aed.class` | `b871389d38f79f5fe018c2240a9cf6af68c900db7db409b368200059f823e084` |
| `WorldSavedData` | server `bet.class` | `a2d457a9475cad1812d4dc454901f4c7afd0c9dd5d783c6b48498a88752d8f6e` |
| `MapStorage` | server `bfm.class` | `45d75f1c24d5c7c954d8ca22cd99a3255bbe01341c4cde8fcde61da1ba805117` |

## Issue #8 checklist and remaining limits

- [x] Startup, client and server scheduling, integrated and dedicated server, protocol, commands, persistence, and world lifecycle are traced.
- [x] World open/close, Escape pause, clean save/reload, death/respawn, and player NBT are tied to representative observations and reproducible paths.
- [x] The Mario timing constraint separates the later-derived fixed step from Minecraft ticks, rendering and pause, and names one authoritative simulation owner.
- [x] Crash reports, server cleanup, reload fallback, thread ownership, save extension seams and client/server settings are documented.
- [x] Dedicated/multiplayer machinery is researched; runtime observation is restricted to the existing vanilla dedicated-server command check. No remote peer, authentication transaction or public multiplayer session was exercised.
Forge-specific capability or event-hook behavior is a follow-up question for the architecture review because the pinned Forge 14.23.5.2859 launch still fails before title; vanilla serialization extension points and lifecycle are documented above.