# Minecraft 1.12.2 connected-client runtime follow-up

Status: connected-client vanilla runtime evidence complete for issue [#33](https://github.com/BrantTanner/MarioCraft/issues/33). The Forge launch failure remains unresolved and is documented below; the user authorized vanilla 1.12.2 for release-runtime research with Forge behavior labeled separately. Issues [#6](https://github.com/BrantTanner/MarioCraft/issues/6) and [#9](https://github.com/BrantTanner/MarioCraft/issues/9) remain open for parent review.

## Launch evidence

The official Forge 1.12.2-14.23.5.2859 MDK run was repeated on 2026-10-24 with workspace Temurin 8u504 and the project-local Gradle cache. Command, from `tools/mdk-work`:

```powershell
$env:JAVA_HOME = '<workspace>\tools\java\temurin8\jdk8u504-b01'
$env:GRADLE_USER_HOME = '<workspace>\MarioCraft\tools\gradle-cache'
.\gradlew.bat runClient --no-daemon --stacktrace
```

The run reached LWJGL and FML construction, then reproduced the pre-title failure twice. The repeat at 12:29:28 is in `tools/mdk-work/run/crash-reports/crash-2026-10-24_12.29.28-client.txt`; its relevant stack is:

```text
LoaderExceptionModCrash: Caught exception from Forge Mod Loader (FML)
Caused by: java.lang.NullPointerException
  at NetworkRegistry.newChannel(NetworkRegistry.java:207)
  at FMLNetworkHandler.registerChannel(FMLNetworkHandler.java:185)
  at FMLContainer.modConstruction(FMLContainer.java:92)
```

A later `--debug-jvm` run stopped at `NetworkRegistry.newChannel` line 207. Field-only JDB inspection found `side=CLIENT` (ordinal 0), `name=FML`, and an `FMLContainer` argument. The live `EnumMap.keyUniverse` contained CLIENT (0), SERVER (1), and BUKKIT (2); its first two values were non-null concurrent maps, while value slot 2 was null. This fits the call site iterating `Side.values()` and then inserting into `channels.get(side)`, but does not establish where the third enum value came from or why the runtime differs from the cached source artifact.

The cached Forge 14.23.5.2859 source JAR has SHA-256 `9D79D8FAD7D2ED0B802DE9D76BE6D00FB10D0FBD6E6467E80E92805602650742`; its `Side.java` declares only CLIENT and SERVER. The mapped runtime Forge JAR has SHA-256 `64E8A3F965B6FC8CB576D5E3375E73BC905EA53E9CF84DBB27047D5F95C2AA06`; `javap` on its `Side.class` includes BUKKIT. This is an unresolved source/class discrepancy, not a verified root-cause or repair. The debugger run advanced further and showed Forge's connection-handshake log lines, but it was instrumented and does not count as a usable uninstrumented launch.

## Connected vanilla client run

On 2026-10-24, the user foregrounded their Minecraft 1.12.2 client and Computer Use returned a fresh window titled `Minecraft 1.12.2`. The main menu and F3 screen identified release `1.12.2/vanilla`; F3 reported Java 1.8.0_51 64-bit. The user's live client JAR at `%APPDATA%\\.minecraft\\versions\\1.12.2\\1.12.2.jar` has SHA-256 `8ADA07DA5EE77DAD3527BD7278FBD05EE1FC8A597813B216A871A2D7D64CC64F`, matching the project's official `tools/client-1.12.2.jar`. The project's official server JAR SHA-256 is `FE1F9274E6DAD9191BF6E6E8E36EE6EBC737F373603DF0946AAFCDED0D53167E`. This is vanilla release evidence; it does not repair or establish behavior in the failing Forge setup above.

I created only the disposable world `codexissue33run202610` (Survival, cheats enabled, structures enabled) with explicit seed `33102024`. The save is local at `%APPDATA%\\.minecraft\\saves\\codexissue33run202610`; no pre-existing world was opened or changed. The initial F3 location was approximately `(9.5, 74, 259.5)` in ForestHills. `/seed` returned `33102024`; `/time query daytime` returned `1945`. `doDaylightCycle` and `doMobSpawning` were set false for a stable, controlled test. Other temporary gamerules are recorded with each scenario below.

### Inventory, crafting and progression

The player began with an empty hotbar/inventory, 20 health, food level 20, and XP level 0. In-game `/give @p minecraft:log 4 0` returned four oak logs. In the inventory screen I moved a log into the 2x2 crafting grid and clicked the displayed output; the UI produced four oak planks and displayed the new-recipe toast. The saved stats file records `stat.craftItem.minecraft.planks: 4`. The pre-death player NBT recorded three planks and three remaining logs, so one crafted plank was not present in the saved player inventory; its disposition was not captured and no use/loss is inferred. This is an actual UI crafting/result path; the crafting-table recipe was unlocked but a crafting table itself was not crafted.

For a progression/effect path, `/effect @p minecraft:speed 6000 0 true` returned the Speed effect for 6000 seconds, and the inventory UI showed the active Speed icon. `/give @p minecraft:iron_helmet 1` returned an iron helmet and emitted the vanilla `[Suit Up]` advancement message. I placed a helmet in the head slot using `/replaceitem entity @p slot.armor.head minecraft:iron_helmet 1`; the UI then showed the helmet in the armor slot and on the player model. The advancement UI showed “Suit Up — Protect yourself with a piece of iron armor.” The local advancement JSON records `minecraft:story/obtain_armor` done with criterion `iron_helmet`. XP was set with `/xp 7L @p`; server feedback said seven levels, and both HUD and saved player NBT showed level 7. Item provisioning and XP were command-prepared, while crafting, equipment display, advancement feedback and effect display were observed in the client.

### Save, clean stop and reload

The world was saved using the in-game “Save and Quit to Title” action. At the pre-death save, the player NBT (SHA-256 `e98726414e9afe5faa0a00c917cc42788a43fe601b7cfce6c60ad62f24cb9175`) was parsed and recorded; it showed Survival mode, position `(9.5, 74, 259.5)`, health 20, food 20/saturation 5, XP level 7, three planks/three logs and a milk bucket, the iron helmet in head slot 103, and Speed amplifier 0 with duration 117632 ticks. Stats then recorded four crafted planks and one clean leave; advancement JSON recorded the iron-armor advancement. The UI's advancement and statistics screens were also inspected. The pre-death playerdata itself was later overwritten by the post-death save; no archived pre-death binary snapshot is claimed.

I reloaded the same world through the Select World screen. The in-game inventory again showed Speed, the equipped helmet and inventory items; the HUD showed XP level 7 and armor. The advancement tree showed the completed Suit Up node. This verifies those inventory, hunger, equipment, XP, effect, advancement and statistics claims across a clean save/reload. Food was full at the sampled save point; hunger depletion was not exercised. Advancement progression was limited to armor acquisition; other advancement paths remain unexercised.

### Mob loot and player death

For controlled mob comparisons, `doMobSpawning=false` remained set; test cows were explicitly summoned. With `doMobLoot=true`, killing the first cow returned `Killed Cow` and the hotbar gained one leather and three raw beef. I then set `doMobLoot=false`, summoned a second cow, and killed it; the server returned `Killed Cow` and the hotbar counts remained unchanged. One short-radius selector missed after the cow moved; the wider-radius retry succeeded. `keepInventory=false` and `doTileDrops=true` were set for the player-death check.

With `doMobLoot=false`, `keepInventory=false` and `doTileDrops=true`, `/kill @p` showed the death screen (“fell out of the world”). After Respawn, the hotbar was empty, health and hunger had refilled, and XP was no longer shown. `/testfor @e[type=Item,r=10]` found oak-log, oak-plank and raw-beef item entities near the death area. A more specific helmet-item query over radius 30 found two iron-helmet item entities and one leather entity; other nearby item entities did not match the helmet query. This separates player inventory/armor drops (controlled by `keepInventory`) from ordinary cow loot (controlled by `doMobLoot`): player items were present after death even with mob loot disabled. Stats after the clean post-death save record one death, four crafted planks, three beef and one leather picked up, two clean leaves, 12547 play ticks, and 1269 ticks since death. `doTileDrops=true` was recorded, but breaking a block to observe its drop was not exercised.

After the second clean save, the final player NBT was parsed from the disposable world's playerdata file (SHA-256 `2b0cc3c8bfdbf8d8edd698de57980fca5bd5f2e83c1f627e4113062ca564080d`). It records Survival mode, health 20, food 20/saturation 5, XP level/progress/total all zero, an empty inventory, and no active effects. This agrees with the observed respawn state. The final post-death playerdata, stats and advancement files remain local in the world directory; the pre-death playerdata file was overwritten and is represented only by its recorded parsed values and hash above. UUID-named filenames are intentionally not reproduced here.

### Timing, remaining limits and cleanup

The world directory was created at 2026-10-24 20:25:34 UTC. The pre-death clean save updated `level.dat` and playerdata at about 20:32:55 UTC; the post-death clean save updated them at about 20:37:26 UTC. The daylight-cycle rule kept the sampled daytime at 1945. Inputs were the listed in-game chat commands, inventory-grid clicks for log-to-planks crafting, and menu actions to save, reload, inspect advancements/statistics, respawn and save again. The integrated world was cleanly stopped twice via “Save and Quit to Title”; the user's Minecraft application remains open at its title screen. No external test server or task-owned Java client remains running. World files remain local and were not uploaded.

This run provides real release-specific vanilla UI, inventory, crafting, effect, advancement, persistence and death/drop evidence. It does not resolve the Forge pre-title NPE; the BUKKIT-versus-source `Side` discrepancy remains an unresolved diagnostic finding, not a root-cause claim. Multiplayer server correction, natural wood harvesting, block tile drops, and held-right-click item consumption were not exercised and are not claimed. These are outside the bounded single-player evidence requested here. The earlier Computer Use activation failure was resolved when the user foregrounded the actual client. The vanilla client was cleanly returned to title and left open; all test-world files remain local.

