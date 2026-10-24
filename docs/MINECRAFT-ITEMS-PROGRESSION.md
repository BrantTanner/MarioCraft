# Minecraft 1.12.2 items, inventory, and progression

Research for [issue #6](https://github.com/BrantTanner/MarioCraft/issues/6). This dossier inventories the full-release player item and progression systems, follows representative state changes through UI/network/save code, and identifies player-specific exclusion seams for the MarioCraft MVP. The research target is the official Minecraft Java Edition 1.12.2 client and server recorded in the [release baseline](MINECRAFT-1.12.2-BASELINE.md). All runtime claims are separated from static findings below.

**Status:** The representative static research is complete and reproducible. Issue #6 remains open because the exhaustive registry/alternate-path inventory and the connected-client UI/save reload observation are tracked as blocking follow-ups [#32](https://github.com/BrantTanner/MarioCraft/issues/32) and [#33](https://github.com/BrantTanner/MarioCraft/issues/33). The current evidence does not satisfy those remaining portions.

## Evidence and limits

The client jar SHA-256 is `8ada07da5ee77dad3527bd7278fbd05ee1fc8a597813b216a871a2d7d64cc64f`; the server jar SHA-256 is `fe1f9274e6dad9191bf6e6e8e36ee6ebc737f373603df0946aafcded0d53167e`. REA opened the client jar as a ZIP and verified the first digest. Its inventory Evidence is `ev_e9ed08c6c3b08bef6a1cbfe9b44cc5ba9d5927075c7a770a811543fe7d28e35d` (shipped-artifact, observed); its complete summary is `ev_d01587b180e2386bc4688477424648c908690c6e60bdb95dc1c1c3dcb762106f`. REA reports no integrity contradictions. The direct Java `jar tf` and .NET ZIP directory checks both count 7,957 client entries, including 3,310 classes and 3 META-INF entries. REA's summary reports 7,958 observed file entries; that one-entry inventory discrepancy is retained as an open evidence question and is not used to calculate the resource counts below.

Original obfuscated class bytes were read from the official jars and hashed in the ignored local evidence file `tools/inspection/issue6/original-class-hashes.csv`. CFR 0.152 reconstructed selected classes from these bytes. The obfuscated-to-MCP mapping source is the Forge 1.12.2 MCP snapshot `20171003-1.12`, `joined.tsrg` SHA-256 `57527913c5bea02e110aa0d1fa929da6f995f1a7836b0e46d891014b94779f4e`; the SRG-to-snapshot map is `ec8384aa598e20c182db0025123bc794460659da1dc9b551f099c3ed2fab22bd`. Mappings label the original classes and methods; they do not establish behavior by themselves. The Forge `.2859` mapped jar (SHA-256 `64e8a3f965b6fc8cb576d5e3375e73bc905ea53e9cf84dbb27047d5f95c2aa06`) and its CFR output were used only to navigate readable names. Forge event and capability additions visible there are not attributed to vanilla. The representative original class hashes are:

| Side / class | Original name | SHA-256 |
| --- | --- | --- |
| Server `EntityPlayer` | `aed.class` | `b871389d38f79f5fe018c2240a9cf6af68c900db7db409b368200059f823e084` |
| Server `InventoryPlayer` | `aec.class` | `6c2652c0a47dd685ef7452558c5e403cb3b45298c5e1b1a1d7cc34f3d96c3094` |
| Server `ContainerPlayer` | `agi.class` | `be1cb69643072a3baa68a53cb3625c2dd612b5b8b7b8861cd87e95044565723e` |
| Server `Container` | `afr.class` | `777ccd13dbccbab9c72e572145faa2c569c6549ba0d038c0a4d8caa8650f4911` |
| Server `SlotCrafting` | `ago.class` | `21b78c3e0db24eeeb2584818c860b3660e582c0051ceecc830eefd914d78f8db` |
| Server `ItemStack` | `aip.class` | `69ca68540ddb28020e3d727466979083745482b4bbfd5e54c2bebbb390498f4a` |
| Server `FoodStats` | `afp.class` | `542c7810863935b36754a61604e13a77ee3cfc40cbd6df49036773b126ea45ae` |
| Server `CraftingManager` | `aku.class` | `104a4bd09aeb9a8e320a653a30fb8593248c31f5468b4b728937efcab2234154` |
| Server `Enchantment` / helper | `alk.class` / `alm.class` | `b02c3f17e28df3ce4aa876267c9d21051582159ef90f5877ac80f78b02893aa6` / `f80a0752976b0a884dfd8137d142602470bd073afa638e2802c8b39a68ac8e5b` |
| Server `Potion` / `PotionEffect` | `uz.class` / `va.class` | `44bfd8bb814d940118fe8d296522061cbfb57bf8d665c361c5299893bebbc8dd` / `d0689f03024a0b58d7926351360094353b970e9d7104ea5b1538c1459efc77d6` |
| Client `GuiInventory` / `GuiContainer` | `bmx.class` / `bmg.class` | `b8d3e22f4986ebfffd75a137a2e3f84ae08e8c78b6f932db1c03b486b1cecab3` / `55e926ac79411ddd31f09031d74c91019fc58c598501aeb40cc7749db17ad90f` |
| Client `CPacketClickWindow` | `lf.class` | `4a2296e4a7449bcde2e3fd105a88b2c151d641c975e7881d4be55c902f3ce43b` |
| Server `PlayerAdvancements` / manager | `np.class` / `ns.class` | `63ab8960588603a2ec17519b4d680285d74feb54c0a89596f5dbf503251291b6` / `cb4207149fa33f328fd732c1486f63194dcf3f8bb91dd1787dc47481fad87d00` |
| Server `EntityItem` / `EntityXPOrb` | `acl.class` / `vm.class` | `cb11bc6167bfa1fcf728b86511335522286ec895ea52d7c5f44c477d1f939e06` / `7fbd58214ae656544de5484cf4fd1f14c6c8cd5d396964a1097fb2a9669eea01` |

The table is a reproducible representative subset, not the complete extraction list; the ignored CSV records every extracted class and its side and entry-byte hash. Release resources were counted from the client jar's ZIP central directory. It contains 432 `assets/minecraft/recipes/*.json` files (342 shaped and 90 shapeless), 488 advancement JSONs, 82 loot-table JSONs (14 chests, 63 entities, 4 gameplay, and one `empty.json`), 717 item models, and 343 item textures. The 488 advancement files include the recipe book tree: 433 under `advancements/recipes/` and 55 non-recipe advancements (story 16, adventure 10, Nether 13, End 9, husbandry 7). Therefore 488 is not a count of visible achievement-tree entries. The 432 recipe files also do not enumerate every cooking or brewing rule: furnace and brewing behavior has code-defined registries/recipes. These are file inventory counts, not counts of every item variant or runtime outcome.

The local client launch documented in [issue #3's report](MINECRAFT-PLAYER-INPUT.md) failed during Forge mod construction before the title screen (`NetworkRegistry.newChannel` NPE). No live inventory screen or player save/reload was observed for this ticket. The vanilla server runs described in the terrain and creature reports had no connected player. Accordingly, all behavior and persistence traces below are static reconstructions of original jar classes or direct resource data. No claim is made that the live UI or state transitions were executed.

## System inventory

| System | 1.12.2 state and behavior |
| --- | --- |
| Items and stacks | `ItemStack` carries an item registry identity, count, damage/durability and optional NBT tag; item classes supply use, food, armor, tool, weapon, potion and other callbacks. Block items and metadata-bearing items make an item name alone insufficient to identify every stack variant. Item use is dispatched for the active hand and may act on a block, an entity, or the player/world. |
| Player inventory and equipment | `InventoryPlayer` has 36 main slots, four armor slots and one offhand slot. The nine selected hotbar slots are the first nine main slots; `currentItem` selects the active one. The cursor-held stack is a separate container/player field. Ender chest inventory is a separate 27-slot container and save entry. `ContainerPlayer` exposes the two-by-two crafting grid/result, armor, main inventory, hotbar and offhand. |
| Containers and workstation screens | `Container` owns slot rules, click modes, transfers, drag state and slot change detection. Player, workbench, furnace, brewing stand, enchanting table, anvil, beacon, merchant and other container subclasses define workstation behavior. Closing a container returns control to the player container; container access and distance checks are server-side. |
| Crafting and recipe book | `CraftingManager` loads shaped/shapeless JSON recipes and matches a crafting grid. The player inventory grid is two-by-two; a workbench grid is three-by-three. Furnace and brewing have separate code-driven registries/ticking paths. The recipe book stores discovered recipes, is unlocked by advancement criteria, and has client synchronization and ghost-recipe feedback. Recipe discovery and recipe execution are separate: a recipe can be crafted without a visible recipe-book UI. |
| Hunger and food | `FoodStats` stores food level, saturation, exhaustion and a food tick counter. Sprinting and other activity can add exhaustion; food items change level/saturation, and special foods can apply potion effects. The periodic food update drives healing/starvation behavior according to its thresholds and game difficulty. This is player state, separate from mob health and mob equipment. |
| Armor and durability | Armor stacks occupy equipment slots, contribute armor/toughness attributes and can lose durability when eligible hits are accepted. Held weapons/tools and item attributes/enchantments affect vanilla player attacks and block actions. Armor, shields, absorption, resistance and hurt immunity are separate stages of living-entity damage described in [the creature and combat report](MINECRAFT-CREATURES-COMBAT.md). Preserve these rules for ordinary mobs that wear equipment. |
| Enchanting, anvil and trading | The enchanting container checks the input item, lapis/materials, enchantability and player XP/seed, then applies selected enchantments and updates XP. Anvil operations combine/rename/repair stacks and consume levels/materials. Villager merchant recipes consume input stacks and produce an output through a merchant container. These are independent item/XP transfer paths, not ordinary crafting-grid interactions. |
| Potions and status effects | Potion items, tipped arrows, splash/lingering projectiles, brewing and mob attacks can apply effects. A `PotionEffect` stores its effect identity, duration and amplifier and is ticked on its target. Area/projectile effects have their own target and impact paths. Witch attacks and other mob-applied effects must remain available to ordinary entities and remain in the #5 damage/effect crossover design. |
| Loot and experience | Entity death/loot rules create `EntityItem` stacks and `EntityXPOrb` objects in the world. A player collision with an item attempts inventory insertion before consuming the item entity; XP-orb collision increases player XP. Mobs can separately pick up and equip item entities. Mob death, loot rolls, item entities, mob equipment, and orb spawning are world behavior. |
| Advancement and progression | Advancement JSON defines parent trees, display, criteria, optional rewards and triggers; recipe advancements unlock recipe-book entries. `PlayerAdvancements` tracks criteria/progress and sends advancement updates to the client. `StatisticsManagerServer` records statistics separately. In this release the progression UI is advancements, not the older achievement system. Nether/End advancement trees remain part of the release dossier even though those dimensions are excluded from the MVP. |

## Representative state-to-UI-to-save traces

**Inventory and equipment.** The server player owns `InventoryPlayer`, the default `inventoryContainer`, an `openContainer`, `FoodStats` and an ender-chest inventory. The client `GuiInventory`/`GuiContainer` shows the same slot model. A click, quick-move, drag, hotbar swap or drop updates a `Slot` or the cursor-held stack through `Container.slotClick`; accepted mutations notify listeners. `ItemStack.writeToNBT` serializes the registry ID, count, damage and optional tag. `InventoryPlayer.writeToNBT` writes indexed stack entries for main inventory, armor and offhand. `EntityPlayer.writeEntityToNBT` stores those entries as `Inventory`, selected hotbar index as `SelectedItemSlot`, food data, XP progress/level/total/seed, capabilities and `EnderItems`. The matching read path repopulates those fields on player load. Static save code establishes the format; this ticket did not write and reload a player save at runtime.

**Crafting.** The client grid is a `ContainerPlayer` or `ContainerWorkbench` view of an inventory crafting matrix and result slot. On a click, `GuiContainer` sends `CPacketClickWindow` with window, slot, button, click type, transaction/action number and carried-stack state. The server handler validates the active window/transaction, calls the open container's click logic and sends confirmation plus updated slot/window contents. `SlotCrafting.onTake` calls the crafting result hook, resolves recipe remainders, decrements one item from each nonempty matrix slot, then places remainders into their source slots, the player inventory or (if insertion fails) into the world. It records crafted-item/recipe progression. The exact original server `ago.class` bytes were decompiled; the mapped Forge view was used to label `SlotCrafting` and expose added Forge hooks, which are not part of this vanilla claim. A crafted item becomes persistent only after it resides in a persisted inventory/container or world entity and the relevant data is saved.

**Food/effects.** Consuming an eligible item is an item-use operation through the active hand. Its item callback can decrement stack count, update `FoodStats`, apply effects, play feedback and trigger use/consume statistics/advancement criteria. `FoodStats` is serialized with player NBT; active effects are living-entity state serialized through the entity data path. Incoming mob potion attacks do not require player item use, so blocking potion consumption/brewing must leave ordinary mob potion attacks and effects intact.

**Advancement and recipes.** Shipped JSON resources define criteria and client display keys. For example, `story/mine_stone.json` is an `inventory_changed` criterion for cobblestone, while `husbandry/balanced_diet.json` tracks many distinct consumed food items and grants 100 XP. Server progress is serialized by `PlayerAdvancements.save()` to a UUID-named JSON file in the world's advancements directory; statistics use a separate UUID-named JSON in the stats directory. The server sends advancement information/progress packets to the client advancement screen. Recipe unlocks and gameplay rewards therefore can change player/recipe-book state even when an inventory container is not open.

**Drops, XP and death.** Mob death calls the existing loot/drop and experience routines and spawns world `EntityItem`/`EntityXPOrb` instances. Later, the player pickup collision tries inventory insertion or XP collection and sends corresponding pickup/experience feedback. Player death has its own inventory/XP drop behavior, conditioned by the `keepInventory` game rule and player-death code. The MVP should suppress only player collection, player-owned inventory/equipment use, and any player inventory/XP drops; it must preserve mob deaths, mob loot tables, orb and item-entity lifetimes, other mobs' item pickup/equipment behavior, and normal world/chunk saves. A Mario death must continue through the chosen Minecraft respawn/world-preservation flow without exposing hidden vanilla inventory as drops.

## Safe MVP exclusion seams

Hiding the inventory screen alone does not exclude these systems. Alternate entry paths include the inventory key and mouse UI; number keys and scroll wheel selecting hotbar slots; offhand swap; drop and hotbar-swap keys; workstation/block/entity activation; use-in-air, use-on-block and entity interaction; recipe output, shift-click, drag and cursor-drop; held-item updates; and creative inventory edits. The network handlers also accept distinct `CPacketClickWindow`, `CPacketEnchantItem`, `CPacketCreativeInventoryAction`, `CPacketHeldItemChange`, `CPacketPlayerDigging`, `CPacketPlayerTryUseItem`, `CPacketPlayerTryUseItemOnBlock` and `CPacketUseEntity` paths. Commands and external inventory automation can also mutate player inventory when enabled. The #3 report traces the ordinary client/server input and packet routes; the inventory screen and container sources add the click/slot routes.

The narrow integration boundary is the player interaction and pickup side:

1. Keep the `EntityPlayerSP`/`EntityPlayerMP` identities and world membership described in the input report, but prevent inventory/workstation screens and player container transactions from opening. Gate every server packet path that mutates player slots, consumes/equips items, enchants, changes the held slot, drops a player stack or uses a player-held item. A client-only key or screen change does not enforce this boundary.
2. Route Mario attacks through the explicit server-validated hit seam from [the combat dossier](MINECRAFT-CREATURES-COMBAT.md), rather than the vanilla held-item attack path. Suppress player pickup/insertion and XP collection at the player collision boundary so item entities and orbs remain in the world with their ordinary lifetime and other entities can still interact with them.
3. Keep ordinary mobs' equipment slots, armor mitigation, effects, AI, loot-table death paths and XP generation. Do not globally cancel item entities, experience orbs, `EntityLiving` death, damage, or loot processing. Retain ordinary Overworld structures and mobs, including bosses; dimension-only entries are research inventory, not a reason to remove Overworld systems.
4. Keep the #1 player/world save and death design. Ensure vanilla inventory, hunger, equipment and XP fields cannot silently accumulate or reappear on reload; persist Mario's position, health and lives through the selected MarioCraft state path. Suppress only the player's vanilla inventory/XP death drops, while allowing world data and unrelated entity drops to save normally.

Advancement visibility and player statistics need an explicit product decision: the project excludes inventory/item interactions and vanilla progression gameplay from Mario's playable loop, but does not say whether the familiar advancement/stat screens remain visible as read-only presentation. If retained, their triggers must not grant recipe unlocks, XP or player-item actions as hidden progression. This is an implementation question, not evidence about the original release.

## Reproduction

From the workspace root in PowerShell, count and inspect the original data without modifying the jars:

```powershell
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jar = [System.IO.Compression.ZipFile]::OpenRead((Resolve-Path 'MarioCraft/tools/client-1.12.2.jar').Path)
$jar.Entries | Where-Object { $_.FullName -like 'assets/minecraft/recipes/*.json' } | Measure-Object
$jar.Entries | Where-Object { $_.FullName -like 'assets/minecraft/advancements/*.json' } | Measure-Object
$jar.Entries | Where-Object { $_.FullName -like 'assets/minecraft/loot_tables/*.json' } | Measure-Object
$entry = $jar.GetEntry('assets/minecraft/recipes/wooden_pickaxe.json')
$reader = [IO.StreamReader]::new($entry.Open())
$reader.ReadToEnd()
$reader.Close()
$jar.Dispose()
```

The original classes and CFR 0.152 outputs are under ignored `MarioCraft/tools/inspection/issue6/`; `original-class-hashes.csv` records exact side-specific entry hashes. The joined MCP mapping and mapped Forge jar remain in the ignored `MarioCraft/tools/gradle-cache/`. To reproduce an individual original class hash, extract that class path from the corresponding official jar with the pinned Java 21 `jar.exe`, then run `Get-FileHash -Algorithm SHA256`. Do not publish the jars, extracted classes, local runtime captures or credentials.

## Open questions and bounded follow-up

- The full item registry and every metadata/NBT variant have not been turned into a stable item-to-variant catalog. Recipe JSON counts do not include code-defined cooking/brewing rules, all loot paths, modded Forge registrations or runtime-generated stack tags.
- The audit has not exhaustively followed every item callback through every possible workstation, automation, command, creative, packet and modded capability path. The representative container, crafting, food, enchantment, potion, drop, advancement and XP traces identify the common seams but are not proof that no alternate item mutation path exists.
- Player advancement and statistics JSON destinations and payload structure are source-derived; a connected-client save/reload and advancement-screen observation is outstanding because the Forge client failed before title. The integrated gameplay harness should verify persistence after the loader is repaired.
- MarioCraft needs a settled read-only/hidden policy for advancement and statistics presentation, and exact handling of player-earned XP versus world XP orbs. Until that policy is selected, the safe exclusion recommendation is to block player collection/reward changes while retaining world-generated mob drops/effects.
- REA's client ZIP inventory count differs by one from independent `jar tf` and .NET ZIP counts. The release digest matches the baseline; reconcile the tool's occurrence accounting before using its total entry count as evidence.

These gaps are tracked in [follow-up issue #32](https://github.com/BrantTanner/MarioCraft/issues/32) (exhaustive registry/variant and alternate-path inventory) and [follow-up issue #33](https://github.com/BrantTanner/MarioCraft/issues/33) (connected-client UI, progression and save/reload confirmation after Forge launch repair). They are blocking prerequisites for the #9 cross-ticket dossier audit; neither changes the research-only status of MVP-excluded systems.

## Related evidence

- [Issue #2 release baseline](MINECRAFT-1.12.2-BASELINE.md) for official jar provenance, tool identities and full subsystem ownership.
- [Issue #3 player input and interaction](MINECRAFT-PLAYER-INPUT.md) for keyboard/mouse, client/server request paths, item-use packets and the Forge client launch failure.
- [Issue #4 terrain](MINECRAFT-TERRAIN.md) for preserving block/container/world behavior and world saves.
- [Issue #5 creatures and combat](MINECRAFT-CREATURES-COMBAT.md) for ordinary mob AI, damage/effects, mob drops, XP and death boundaries.
- Parent scope [issue #1](https://github.com/BrantTanner/MarioCraft/issues/1) excludes inventory/item interactions, hunger, armor, held weapons/enchantments and progression from the MVP, while requiring full-release research.




