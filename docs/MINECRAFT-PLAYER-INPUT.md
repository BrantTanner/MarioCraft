# Minecraft 1.12.2 player input, movement, camera, and interaction

Research for [issue #3](https://github.com/BrantTanner/MarioCraft/issues/3). The target is the official 1.12.2 client and server identified in [the baseline](MINECRAFT-1.12.2-BASELINE.md). This document follows original class-file bytes and uses the official Forge 1.12.2/MCP snapshot mapping only to label obfuscated classes and methods. The named Forge development sources are cross-checks and reveal Forge additions; they are not silently treated as vanilla behavior.

## Findings and evidence boundary

The official client jar SHA-256 is `8ada07da5ee77dad3527bd7278fbd05ee1fc8a597813b216a871a2d7d64cc64f`; the server jar SHA-256 is `fe1f9274e6dad9191bf6e6e8e36ee6ebc737f373603df0946aafcded0d53167e`. Original classes were extracted without modification and decompiled individually with CFR 0.152. Forge's official recommended 1.12.2 MDK `.2859` supplied MCP snapshot `20171003-1.12`; the joined mapping SHA-256 is `57527913C5BEA02E110AA0D1FA929DA6F995F1A7836B0E46D891014B94779F4E`. The generated SRG-to-MCP mapping SHA-256 is `EC8384AA598E20C182DB0025123BC794460659DA1DC9B551F099C3ED2FAB22BD`.

This is static evidence about code in those exact artifacts. CFR output is a readable reconstruction, not Mojang source; mappings provide names, not proof of semantics. Where Forge-mapped sources add hooks, they are identified as Forge-only integration surfaces. A local Forge `runClient` attempt reached Minecraft/Forge initialization but crashed during FML mod construction before the menu or a world. The crash report records `NetworkRegistry.newChannel` → `FMLNetworkHandler.registerChannel` → `FMLContainer.modConstruction` NPE under Temurin 8u504 and Forge 14.23.5.2859. Thus no keyboard, controller, movement, collision, or interaction behavior is claimed as runtime-observed. The issue asks for bounded runtime observation “where needed”; source traces answer the control-flow and ownership questions here, while the unavailable live confirmation is explicitly bounded. A later runnable harness should add live observations before gameplay claims depend on them.

## Behavior specification

### Input and tick flow

Vanilla `GameSettings` defines keyboard and mouse key bindings. `MovementInputFromOptions.updatePlayerMoveState()` polls forward/back/left/right and jump/sneak bindings, sets the four directional components to `-1`, `0`, or `1`, and applies a `0.3` multiplier to forward/strafe while sneaking. The keys map through MCP names `T/V/U/W/X/Y` in obfuscated `GameSettings` to `keyBindForward`, `keyBindBack`, `keyBindLeft`, `keyBindRight`, `keyBindJump`, and `keyBindSneak`. A native gamepad/analog-controller input path was not found in the traced vanilla classes; treat controller support as a future binding/input-adapter responsibility, not existing vanilla behavior.

The client creates a 20-tick-per-second `Timer` (`Minecraft` constructor) and its render loop updates that timer, then calls `runTick` up to ten times per render-loop iteration to catch up. In `runTick`, key binding dispatch, mouse-over updates, and click actions are driven from key/mouse state. On player creation, `Minecraft` assigns `new MovementInputFromOptions(gameSettings)` to the client player. During `EntityPlayerSP.onUpdate`, the movement input is polled before the inherited living update applies movement/physics. `EntityPlayerSP.onUpdateWalkingPlayer` is the network reporting seam, not the sole local movement step.

Input handling also branches into screens: `processKeyBinds` opens `GuiChat` for chat and `GuiChat("/")` for the command key (only when chat is visible; the command path also requires no current screen). Once a screen is open, its text/key handling owns the focused input until it closes. This is why a replacement movement adapter must honor the active-screen/focus state rather than continuously consuming movement keys. The same binding dispatch includes perspective toggle, while mouse clicks are processed through the world interaction path only when the game is in the corresponding in-world state.

### Position updates, timing, and authority

For a non-riding local player that is the current view entity, `onUpdateWalkingPlayer` selects a `CPacketPlayer` variant based on deltas from the last reported state:

* Position and rotation if position delta squared exceeds `9.0E-4` (0.03 blocks per coordinate vector) or 20 reporting ticks elapsed, and rotation changed.
* Position only if that position threshold/heartbeat is met but rotation did not change.
* Rotation only if yaw/pitch changed.
* Ground-state-only otherwise.

The packet Y coordinate is the player's bounding-box `minY`, not eye height. Rotation is yaw/pitch. Sprint and sneak transitions use `CPacketEntityAction`. Riding has a separate branch reporting input/vehicle state. This is client prediction plus periodic state reporting: in multiplayer, server `NetHandlerPlayServer.processPlayer` checks finite/range/speed/validity constraints, derives motion against current server state, applies accepted position/rotation to the server `EntityPlayerMP`, and may correct the client with `SPacketPlayerPosLook`. The client handler applies that correction and sends the teleport confirmation/updated state. Therefore the authoritative multiplayer position belongs to the server player entity; the client player remains the local prediction and presentation object. In integrated single-player, the logical server still processes the same server-side player path in-process; this static trace does not measure thread scheduling.

### Collision and stepping

`Entity.move` resolves collision using world collision boxes, clipping requested displacement in Y, then X, then Z and updating bounding box/position from the clipped result. It includes a sneak-edge branch that reduces horizontal displacement in 0.05 increments when the player is sneaking without support below the prospective footprint. A step-height alternative compares the ordinary clipped move with a candidate that raises by `stepHeight`; the better horizontal route is selected subject to the method's conditions. These mechanics are shared entity movement behavior, not an independent Mario physics engine.

For a Mario controller that preserves world simulation and ordinary mobs, retain the Minecraft world/entity/collision queries while replacing the player movement decision/update path. The source-bound integration seam is the client player (`EntityPlayerSP` input/update and `move` path), coordinated with the server counterpart when multiplayer remains in scope. Set the player's step height to zero and suppress `EntityPlayerSP` auto-jump (`isAutoJumpEnabled`/`autoJumpEnabled`) for the intended no-step behavior; verify Forge events/hooks do not re-enable it. Do not replace the entity object on ordinary ticks: entity identity, ID, capabilities, world membership, network handler and mob target references rely on the existing player/entity lifecycle.

### Camera, targeting, and interactions

`EntityRenderer.getMouseOver` takes the active render-view entity, uses its eye position and look vector for the ray, tests blocks and entities, and applies reach limits. `orientCamera` renders first person or third person according to `GameSettings.thirdPersonView`; third-person camera distance is shortened against collision. The renderer's view entity is a separate choice from the logical player; changing it changes what is rendered and targeted, not who owns server position. There is no vanilla “Mario camera” seam; camera replacement belongs at render-view/camera orientation while preserving the player entity and server state.

`Minecraft.clickMouse` routes left-click according to the current `objectMouseOver`: entity attack or block damage. `rightClickMouse` routes to entity interaction, block use, or held-item use. `PlayerControllerMP` performs client-side prediction/event hooks and sends `CPacketUseEntity`, `CPacketPlayerDigging`, `CPacketPlayerTryUseItemOnBlock`, or `CPacketPlayerTryUseItem` as appropriate. Server `NetHandlerPlayServer` handlers validate and dispatch entity use, digging, and block use to `PlayerInteractionManager` and the relevant world/entity logic. Block-state packets such as `SPacketBlockChange` reconcile client-visible state; movement correction uses `SPacketPlayerPosLook` plus teleport confirmation. Do not route excluded inventory/mining/item MVP behavior into Mario attacks; implement explicit action-to-Minecraft-damage translation at the entity interaction seam.

### Lifecycle and implementation boundary

`Minecraft.loadWorld`, join-game, respawn and dimension transitions replace or rebind client player/world state. Player creation assigns the client movement input object; respawn creates a new player instance with the new server entity ID. A movement replacement must reattach to each created player, and must not retain stale world/player references across unload/respawn/dimension change. Server `EntityPlayerMP` remains responsible for multiplayer authority; ordinary mobs continue their own world ticks and AI. These identity/ownership statements are derived from client/server lifecycle call paths; no live respawn or multiplayer scenario was run in this ticket.

Recommended seam for the later implementation: keep `EntityPlayerSP`/`EntityPlayerMP` as Minecraft's player identities and leave `World`, block collision, entities, and ordinary mob tick/scheduling intact; substitute the player input and movement/pose/collision policy, then explicitly translate attack/damage outcomes. Keep separate rates: Minecraft simulation target is 20 Hz, render is variable, and Mario's own fixed-step cadence must be independently scheduled from both. The coordinate system is Minecraft world X/Z horizontal and Y vertical; player packets use feet/base (`boundingBox.minY`), while camera rays begin at eye position. The exact Mario-to-block scale, input sampling cadence for an external controller, safe policy for server corrections, cross-version Forge hook compatibility, and runtime timing/jitter remain open design/measurement items for later tickets.

## Source-bound class map

The obfuscated class names below are those extracted from the original jar; the MCP labels are the joined mapping names. Hashes are SHA-256 of the extracted original class file, not the decompiled text.

| Side | Original class | MCP/Forge mapping label | SHA-256 |
| --- | --- | --- | --- |
| Client | `buc` | `MovementInputFromOptions` | `C2883BD3F57FCA240381ECBD225CE002CD85A95624BCCAC3736ABB3D98969AA2` |
| Client | `bub` | `MovementInput` | `1C368EF48F7D96B3CFDB3B3A8F1D0F906E8E972A2B4E8E3AC8149C7E5F330B73` |
| Client | `bid` | `GameSettings` | `3C1648E3A72AC8C87EF5582F44C68FB81CDBBDEB5C75BA89B72141DCCAB43EAF` |
| Client | `bhy` | `KeyBinding` | `BDA12B085A337D05A0E2B93C7D2AFE912438E7FEC73CEB5F8F85D35FF98EEA18` |
| Client | `bih` | `Timer` | `ABB74D349477B4FD40EBBBAD73975273729736ECFEBCC23E13F73402A9518489` |
| Client | `bib` | `Minecraft` | `D68FE8E77645E11F28D356B56F718D180A0BC10DC72096DC1A755F7DBD5358DA` |
| Client | `bud` | `EntityPlayerSP` | `D1384AF46236FB4517CD669BF1BEFA97C0C8DC4ECF5CCDD10C3C7134C28844E9` |
| Client | `bsa` | `PlayerControllerMP` | `FE0DE8A36D649751FAB0074FD90BB8FF8259FCED26FD9C099367BABE8D83F378` |
| Client | `vg` | `Entity` | `70130AEFFA96586131E23E3B701D4A1FAEB4E3ADD3A56B1A085EE3F049ED1BD3` |
| Client | `buq` | `EntityRenderer` | `28208FA59D12FA3F3290D66A32548DE4EEDF5F367F14363B665EB77D45F84700` |
| Client | `brz` | `NetHandlerPlayClient` | `63528A4F5E46664A6ACA2C63948EA82B7AE9DA43AE925A0097D625FAFC737EAA` |
| Client | `lk` | `CPacketPlayer` | `CB5B97FDC9935CD5823A5E901F5B3116E1F0BF51D8BC8AFF166F2CED73E4A170` |
| Client | `li` | `CPacketUseEntity` | `3959ED11C7EB7066972F4505417B4DAC1F03B8B5A2DE0D069C5BAEB72C1C9B98` |
| Client | `lp` | `CPacketPlayerDigging` | `0A9A6FB4B9A25D2AD8F645E1CA818AD83428AC47B4946335F71C37DBF89C382C` |
| Client | `ma` | `CPacketPlayerTryUseItemOnBlock` | `E3CBFB6A73E43E05570A124B487F38A4F0B24B574DEE95E5C2E9CF6926BE8585` |
| Client | `mb` | `CPacketPlayerTryUseItem` | `A568140FE7793D926C2515B5DDE67A6A32F1E10E48A0E45882C7AB1B0E6AFEA0` |
| Client | `lq` | `CPacketEntityAction` | `3266CC185E091E6A27E8F96297C4E84D86D9C7FD47579CE06DF2C158922034B3` |
| Client | `jq` | `SPacketPlayerPosLook` | `CAC57EDCB38BD73E8D17184DAA7FE5C332661FC280C713A723F84B099E9C170C` |
| Client | `ij` | `SPacketBlockChange` | `1537AFAEA6FDDCB826E4B2844A1117FE41067B82EF1B3175AF468477B1150C25` |
| Server | `pa` | `NetHandlerPlayServer` | `1D9236294726031C9D7C096BFB61FD858B233D6D95C4BE0423D85FDD63AB9753` |
| Server | `oq` | `EntityPlayerMP` | `C41347D65B39C5E93C04967AF20566FCDED1ECE81E15F2425CFDB8DB8D328328` |
| Server | `or` | `PlayerInteractionManager` | `A80B6C12FF32B0941638E84A957796102CCD356B32CA2F510C0633C2986E24B5` |
| Server | `vg` | `Entity` | `6CC1996D992C935506DCEF8FA5D7E97E2478A76C158AEC2B5A61FB0C02744096` |

The principal source paths, with MCP names, are `Minecraft.runGameLoop/runTick/processKeyBinds/clickMouse/rightClickMouse`, `EntityPlayerSP.onUpdate/onUpdateWalkingPlayer/onLivingUpdate/move`, `Entity.move`, `EntityRenderer.getMouseOver/orientCamera`, `PlayerControllerMP` attack/use methods, and server `NetHandlerPlayServer.processPlayer/processPlayerDigging/processTryUseItemOnBlock/processUseEntity`. Key line locations in the generated mapped development view are recorded as navigation aids only: `Minecraft.java` 1103, 1122, 1411, 1444, 1551, 1819, 2013–2114; `EntityPlayerSP.java` 279, 295, 769, 886; `Entity.java` 590–720; `EntityRenderer.java` 403, 537–643; `PlayerControllerMP.java` 349, 452; `NetHandlerPlayServer.java` 519–713, 945; `NetHandlerPlayClient.java` 849, 1085. Always verify against the original class hash above before relying on a line-level reconstruction.

## Reproduction

All game binaries, extracted classes, decompiler outputs, mappings, and the Forge crash remain local under the ignored `tools/` directory. From the `MarioCraft` checkout in PowerShell:

```powershell
$tools = 'tools'
$java21 = '..\tools\java\jdk-21.0.12.1+1\bin\java.exe'
$jar21 = '..\tools\java\jdk-21.0.12.1+1\bin\jar.exe'
$cfr = '..\tools\cfr-0.152.jar'
$classes = 'tools\inspection\repro-original'
$out = 'tools\inspection\repro-decompiled'
New-Item -ItemType Directory -Force $classes, $out | Out-Null
Push-Location $classes
& $jar21 xf '..\..\client-1.12.2.jar' 'buc.class' 'bud.class' 'bsa.class' 'brz.class' 'vg.class' 'buq.class' 'bib.class' 'bid.class' 'bhy.class' 'bih.class' 'lk.class' 'li.class' 'lp.class' 'ma.class' 'mb.class' 'lq.class' 'jq.class' 'ij.class'
Pop-Location
Get-FileHash "$classes\buc.class", "$classes\bud.class", "$classes\vg.class" -Algorithm SHA256
& $java21 -jar $cfr "$classes\buc.class" --outputdir $out --silent true
& $java21 -jar $cfr "$classes\bud.class" --outputdir $out --silent true
```

For the server path, extract `pa.class`, `oq.class`, `or.class`, and `vg.class` from `tools\server-1.12.2.jar` into a separate directory, hash them, and decompile with the same CFR command. Map short names through the recorded joined MCP/SRG files generated by the official Forge MDK. The decompiled mapped Forge view is available at `tools\inspection\mapped-decompiled`; compare method control flow with original CFR before making vanilla claims. Reproducing the failed runtime attempt uses the already provisioned Forge MDK: from `tools\mdk-work`, run `gradlew.bat runClient` under Temurin 8u504; the attempt fails before a game world, with the local crash report `tools\mdk-work\run\crash-reports\crash-2026-10-18_22.01.02-client.txt`. No input observation should be inferred from that failure.

## Remaining uncertainty

No in-world runtime input trace was obtained because the local Forge loader crashed before the title screen. This does not prevent establishing source-level ordering, packet thresholds, collision algorithm, camera target source, or server dispatch; it does leave live confirmation of timing, actual input-device support, and the Forge launch environment open. The 1.12.2 `GameSettings`/`KeyBinding` trace exposes keyboard and mouse bindings; no native controller path is asserted. The precise Mario scale and whether a custom integrated-server/player subclass can preserve all Forge event/mod interactions require later implementation and measurement. Reported 20 Hz is the timer/server target in code, not an observed achieved frequency or frame-time guarantee.

