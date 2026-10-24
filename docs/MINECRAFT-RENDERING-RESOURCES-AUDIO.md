# Minecraft 1.12.2 rendering, resources, menus, and audio (issue #7)

Status: acceptance evidence gathered for vanilla 1.12.2. This dossier combines static inspection of the exact official client with bounded observations in the user's matching vanilla client. Forge development behavior is kept separate from the vanilla findings; see #34 for the generated-artifact startup diagnosis and local repair.

## Artifact identity and evidence method

The inspected client is the official 1.12.2 client JAR, SHA-256 `8ada07da5ee77dad3527bd7278fbd05ee1fc8a597813b216a871a2d7d64cc64f` (SHA-1 `0f275bc1547d01fa5f56ba34bdc87d981ee12daf`, 10,180,113 bytes). Its identity and official download provenance are recorded in the [#2 baseline](MINECRAFT-1.12.2-BASELINE.md). The user's live `%APPDATA%/.minecraft/versions/1.12.2/1.12.2.jar` had the same SHA-256; the foreground client reported `1.12.2/vanilla` and Java 8u51. Static class behavior below was reconstructed from extracted classes in that exact JAR with CFR 0.152. Cached ForgeGradle 1.12.2 mappings were used only to navigate obfuscated names; they are not treated as evidence of vanilla runtime behavior.

The JAR contains 7,957 ZIP entries and 3,310 class files. REA's archive graph reports 7,958 occurrences. The reason for the one-occurrence difference has not been independently established, so 7,958 is not used as a file count. Generated classes, CFR output, screenshots, and downloaded assets remain under the ignored local `tools/` tree; none are published.

Evidence labels in this report distinguish **static trace** (decompiled original client class), **runtime observation** (visible in the matching vanilla client), and **design inference** (recommended seam derived from the trace). No audio output was claimed as heard: the available computer-use interface exposes screenshots and input, not an audio capture or listening result.

## Client presentation flow

The central client loop is `Minecraft.runGameLoop` (`bib`, method `az` in the original obfuscated JAR). Each loop drains scheduled client tasks, advances the timer, runs up to ten `runTick` steps to catch up, updates the sound listener, invokes `EntityRenderer.updateCameraAndRender` with partial-tick interpolation, and presents the frame through the display update. `Minecraft.runTick` advances GUI/screen state and client systems, including world, renderer, and sound/music updates. This makes simulation tick work and frame rendering distinct: rendering may interpolate between the previous and current tick state, and a render frame is not itself a game tick.

The render path is:

1. `EntityRenderer.updateCameraAndRender` sets frame state and invokes world passes.
2. `EntityRenderer.renderWorldPass` interpolates the camera position from previous/current values using partial ticks, applies camera orientation and view effects, and coordinates world rendering.
3. `RenderGlobal.setupTerrain` prepares visible render sections; `renderBlockLayer` draws solid, cutout, and translucent block layers. `renderSky`, `renderClouds`, weather, and particles are separate world effects.
4. `RenderGlobal.renderEntities` interpolates entity positions with the same partial-tick value and dispatches through `RenderManager` to each entity's renderer. Living renderers interpolate rotations, render their model and layers; `RenderPlayer` chooses the player texture and model visibility, and `ModelPlayer`/`ModelBiped` supply player geometry and pose animation.
5. The world pass draws entity outlines and block-destruction effects where applicable, particles and weather, then first-person hand/item rendering. `GuiIngame.renderGameOverlay` composes the in-game HUD separately from world geometry.

The trace identifies camera interpolation, entity position interpolation, model animation, block layers, entities, particles/weather, hand rendering, and HUD as separate responsibilities. The visible evidence is from the exact release client: in the disposable test world, first person showed the player arm, world, hearts, hunger, and hotbar; the first F5 showed the player body/model from behind, and the second showed the front-facing third-person view while retaining the same world and HUD. Vanilla F5 continues through the camera modes; this observation does not claim the original first-person mode was restored before leaving the world. No texture or model was modified.

## Resource inventory and representative paths

The exact client JAR contains 1,432 `assets/minecraft/textures/` entries, 1,595 model JSON files, 407 blockstate JSON files, 87 shader files (25 under `shaders/post/`), 224 font texture files, one font metadata file (`font/glyph_sizes.bin`), and one English localization file (`lang/en_us.lang`). Examples include title panorama `textures/gui/title/background/panorama_0.png`, HUD icons `textures/gui/icons.png`, menu widgets `textures/gui/widgets.png`, ASCII font texture `textures/font/ascii.png`, and post-process definition `shaders/post/entity_outline.json`. Vanilla's Video Settings screen showed no third-party shader-pack selector; this is distinct from the built-in shader/post-process resources present in the client.

Representative texture path (static trace): Minecraft's resource manager resolves resource identifiers against the active resource-pack stack; `TextureManager` loads/binds a texture resource for GUI or renderer use. The title screen's panorama, the HUD icons, and GUI widgets are examples of resources that feed visible UI. Representative model path (static trace): blockstate/model JSON resources provide block model selection, and `RenderGlobal` draws block geometry; entity renderers such as `RenderPlayer` bind a player texture and render the selected model. Runtime confirms the visible title, world blocks, HUD, and player presentation, but it does not identify which exact object hash supplied each currently loaded texture.

Asset provenance was verified independently of the user's mutable installation. A project-local copy of Mojang's official 1.12 asset index at `https://launchermeta.mojang.com/v1/packages/1584b57c1a0b5e593fad1f5b8f78536ca640547b/1.12.json` is 143,138 bytes, SHA-1 `1584b57c1a0b5e593fad1f5b8f78536ca640547b`, SHA-256 `25c3e688d5762c93e5be55faac0bb58cce7cdcfad2bbe1c65120d1b63ca75fc9`. The official index maps logical sound/resource paths to content-addressed object hashes. The pre-existing installed `1.12.json` differs from this exact official index, so it was left untouched and is not used as provenance.

Language selection is exposed from Options and localization keys are resolved through the language resource. The live Options screen showed a Language button; `assets/minecraft/lang/en_us.lang` is present in the identified client. Other language files may be supplied by the asset objects/resource packs rather than the game JAR. Runtime evidence was limited to the visible English UI; no language switch was performed.

## Resource reload and settings

`Minecraft.scheduleResourcesRefresh` queues a resource refresh on the client task queue. `SimpleReloadableResourceManager` rebuilds the active pack/domain view and invokes registered reload listeners. `TextureManager`, `SoundHandler`, `EntityRenderer`, `RenderGlobal`, and other listeners refresh their cached resources. This is the lifecycle seam for pack changes and texture, sound, or shader reloads. Resource reload and thread-affinity details are static traces from the identified client, not a live reload experiment.

The title Options screen exposed Skin Customization, Music & Sounds, Video Settings, Controls, Language, Chat Settings, Resource Packs, and Snooper. Resource Packs showed the vanilla Default pack selected and no available pack selected. Video Settings displayed: Fancy graphics; 15-chunk render distance; Maximum smooth lighting; Unlimited max framerate; 3D Anaglyph off; View Bobbing on; Auto GUI scale; Crosshair attack indicator; Bright brightness; Fancy clouds; All particles; Fullscreen off; VSync off; 4 mipmap levels; VBOs on; Entity Shadows on. These values were observed, not changed. No options were persisted or restored because none were altered.

Vanilla `Minecraft` owns the LWJGL display and rendering context. The main loop drains scheduled tasks before its rendering work, so changes that touch OpenGL-backed textures, models, or renderer state belong on the client/render thread. The trace supports scheduling such work through the client task mechanism (`addScheduledTask`); it does not support doing GL work directly on a background loader thread.

## Menu, HUD, and audio paths

`GuiMainMenu` renders the title screen and dispatches to the existing screen stack for world selection, multiplayer, options, and quit. In-world screens are `GuiScreen` instances; `GuiIngame` draws the overlay during gameplay. The live title screen displayed the Minecraft logo/panorama and Singleplayer, Multiplayer, Realms, Options, and Quit buttons. The live Options and Resource Packs screens were inspected as described above. `GuiIngame` traces include hotbar, player stats, experience, potion effects, and other overlay components.

Sound resources live in Mojang's indexed asset objects rather than `sounds.json` or OGG files inside the client JAR. The exact official index identifies 1,085 `minecraft/sounds/*` objects. The official `sounds.json` object has SHA-1 `975a99ed9870f51bbae348533d775d730e3fee18` and size 105,658 bytes. It maps event identifiers to files and playback properties. `music.menu` references four streamed menu tracks (`music/menu/menu1` through `menu4`); `music.game` references twelve streamed game tracks (including `music/game/calm1`); `entity.player.attack.weak` selects among four attack variants, and `entity.player.hurt` maps to damage/hit variants. `SoundHandler` reloads sound definitions and resolves/dispatches sound events; `SoundManager` owns playback, listener updates, category volume, pause/resume, and stopping sounds. The client loop updates the listener and `MusicTicker`/sound path. Those are source-backed loading and update traces. The Music & Sounds UI exposed Master, Music, Jukebox/Note Blocks, Weather, Blocks, Hostile Creatures, Friendly Creatures, Players, Ambient/Environment, Voice/Speech, and Show Subtitles controls; observed sample values are recorded in the local runtime notes, and no values were changed. No sound was asserted audible in this run.

## Mario presentation seam

**Design inference from the trace and agreed MarioCraft features:** keep the Minecraft player identity and world/entity integration, while Mario replaces the vanilla player model and animation at the `RenderManager` player renderer seam (`RenderPlayer` with `ModelPlayer`/`ModelBiped`). Mario's movement and collision behavior are defined by the movement/collision implementation tickets; this report does not recommend retaining vanilla movement or collision unchanged. Add the Mario third-person camera behavior through a camera hook while preserving `EntityRenderer`, `RenderGlobal`, block layers, mobs, and other world rendering. Keep Minecraft's menu structure and screens, with MarioCraft branding. Replace the vanilla gameplay HUD presentation with the required eight-section Mario health/lives display; suppress the excluded hunger, inventory, and hotbar presentation. Keep the existing `MusicTicker` and `SoundHandler` flow and `MUSIC` category so Minecraft background music continues. Add Mario action/voice sounds as namespaced sound resources and events routed through the existing sound system and appropriate category. This seam is a recommendation; no Mario renderer, camera hook, HUD, sound, or integration patch is implemented in this research ticket.

Keep `GuiMainMenu`, the screen stack, Options and in-game menus intact. Retain `GuiIngame` and add a focused overlay for Mario-specific lives or health only if the design requires it. Keep the existing `MusicTicker` and `SoundHandler` flow, resource domains, and `MUSIC` category so Minecraft background music continues. Add Mario action/voice sounds as namespaced sound resources and events, routed through the existing sound system and appropriate category. Implement model/texture/shader changes through the resource manager and client task queue, not by replacing the full client or its display loop. This seam is a recommendation; no Mario renderer, HUD, sound, or integration patch is implemented in this research ticket.

## Reproduction and bounded gaps

For original-JAR reproduction from the workspace root, use the verified `MarioCraft/tools/client-1.12.2.jar` and the official SHA-256 above. The table gives original obfuscated ZIP entries and extracted-class SHA-256 values for the principal source anchors. With the project-local JDK 21 and CFR 0.152, the following PowerShell commands extract and decompile the representative classes:

```powershell
$jar = (Resolve-Path 'MarioCraft/tools/client-1.12.2.jar').Path
$out = (New-Item -ItemType Directory -Force 'MarioCraft/tools/inspection/issue7-repro').FullName
$java = (Resolve-Path 'tools/java/jdk-21.0.12.1+1/bin/java.exe').Path
$jarTool = (Resolve-Path 'tools/java/jdk-21.0.12.1+1/bin/jar.exe').Path
$cfr = (Resolve-Path 'tools/cfr-0.152.jar').Path
$names = @('bib','buq','buy','bzf','cct','caa','bqj','bpx','biq','blr','ble','bls','bnw','chm','cho','cev','bid','cdr','bud')
Push-Location $out
foreach ($name in $names) { & $jarTool xf $jar "$name.class" }
Pop-Location
foreach ($name in $names) {
  Get-FileHash (Join-Path $out "$name.class") -Algorithm SHA256
  & $java -jar $cfr (Join-Path $out "$name.class") --outputdir "$out/decompiled" --silent true
}
```

Cached ForgeGradle 1.12.2 mappings translate obfuscated/SRG identifiers for navigation only. The source locations of interest are `Minecraft.runGameLoop`/`runTick`, `EntityRenderer.updateCameraAndRender`/`renderWorldPass`, `RenderGlobal.setupTerrain`/`renderBlockLayer`/`renderEntities`, `RenderManager.renderEntity`, `RenderLivingBase` and `RenderPlayer` render paths, `ModelPlayer`/`ModelBiped`, `GuiIngame.renderGameOverlay`, `GuiMainMenu`, `GuiOptions`, `GuiVideoSettings`, `GuiScreenResourcePacks`, `SimpleReloadableResourceManager`, `TextureManager`, `SoundHandler`, `SoundManager`, and `GameSettings`.

| Mapped source class | Original client JAR entry | Extracted class SHA-256 |
| --- | --- | --- |
| `Minecraft` | `bib.class` | `d68fe8e77645e11f28d356b56f718d180a0bc10dc72096dc1a755f7dbd5358da` |
| `EntityRenderer` | `buq.class` | `28208fa59d12fa3f3290d66a32548de4eedf5f367f14363b665eb77d45f84700` |
| `RenderGlobal` | `buy.class` | `470b0ba8c920a722d25a58ea0151a84e9c6cd1355ea0dca16a1c76aec59ae563` |
| `RenderManager` | `bzf.class` | `0ecaa76ec64e624ec02cca6878cf3ecbed39ab363fe2c4d9a99e41a04cb11812` |
| `RenderPlayer` | `cct.class` | `6328afe2d3fc9e24cd48fffe3f245384a55fa1f7563d9889ab2cef562f61dff8` |
| `RenderLivingBase` | `caa.class` | `67fcedcca1fae72944e54d1ef3e9e5b0855a5b33c629b72c4d355f507d299fb0` |
| `ModelPlayer` | `bqj.class` | `412a6511cdd7aa13b99d3e9870abe9ef885920640659e159433b68e5c72185cc` |
| `ModelBiped` | `bpx.class` | `5af90408e15e58f40af54b0aa35cf99a009410fd21fb0fffc6dda561737c88d3` |
| `GuiIngame` | `biq.class` | `0e8b6f8da8f7ae095a520c516cfdcdb1c62106d97f92378ceabfa44af9874443` |
| `GuiMainMenu` | `blr.class` | `e5dc6a07294430ae987bfb09b8995bf5e9effe9ea275fb77db4bc9be1971701c` |
| `GuiOptions` | `ble.class` | `e13425fc24479e7b0f4027f2f7328c3e2f5a5eba1158b72c407e8512f42925d8` |
| `GuiVideoSettings` | `bls.class` | `6382fb86a9bb88df86cbc8b79560124c160765167340caff53f7f0435ee8d816` |
| `GuiScreenResourcePacks` | `bnw.class` | `42a35895adf2093b37ee8d79f0c010e433508d57cc4f45616d281939e03d3c7a` |
| `SoundManager` | `chm.class` | `8739d50b169f9efbe45fb115507bde845aa17e6fe896e5544b44b0f48c904c5b` |
| `SoundHandler` | `cho.class` | `a1e429a928b2fa85fd59f492336b563ec3ac9f4da89854bb0cea23c1ce8423c7` |
| `SimpleReloadableResourceManager` | `cev.class` | `007d516d9f4c56e6d376485b39554f84bd2d09b32b245b580612dbcf06370281` |
| `GameSettings` | `bid.class` | `3c1648e3a72ac8c87ef5582f44c68fb81cdbbdeb5c75ba89b72141dccab43eaf` |
| `TextureManager` | `cdr.class` | `adea3730f1baab1963c0d1a2fb120e6c792108259e636bff0cd9c78de1917b5` |
| `EntityPlayerSP` | `bud.class` | `d1384af46236fb4517cd669bf1befa97c0c8dc4ecf5ccdd10c3c7134c28844e9` |

For the visible runtime check, use only the disposable `codexissue33run202610` world with seed `33102024` documented in [the #33 runtime report](MINECRAFT-CLIENT-PROGRESSION-RUNTIME.md). From the title screen, inspect Options, Video Settings, Music & Sounds, and Resource Packs; open only that named world; observe first person, press F5 once for rear third person and again for front third person; choose Save and Quit to Title. Do not treat the world as an untouched default: it was created for testing with cheats enabled and has documented gamerule changes. The user-owned client was returned to the title screen and left open. No pre-existing world was opened.

Static inventory and representative vanilla behavior are covered. Untested presentation details include live pack-switch/reload behavior, language switching, changing settings, individual shader-effect activation, dynamic skin download/fallback cases, each HUD/effect overlay state, and actual sound audibility/device output. These gaps do not alter the identified renderer/resource seam; any implementation relying on them should add a targeted runtime check. The original Forge 14.23.5.2859 NPE and its generated-artifact cause and local 2864 repair are documented in the [#34 report](issue34/MINECRAFT-FORGE-STARTUP-ISSUE34.md); this vanilla trace does not claim Forge renderer behavior.

## Issue #7 acceptance checklist

- [x] Rendering pipelines, entity models/animations, interpolation, camera, HUD, resource loading, shaders/settings where present, menus, localization, and audio paths documented.
- [x] Mario player-presentation seam documented while retaining Minecraft world rendering, background music, menus, and HUD pipeline.
- [x] Representative resource/model/UI paths traced from resource/class loading to visible output. Sound definitions and playback path traced from indexed assets through `SoundHandler`/`SoundManager`; no audible result claimed.
- [x] Render-thread ownership, frame/tick relation, reload lifecycle, and observed options documented.
- [x] Evidence, reproduction steps, and bounded remaining presentation checks listed.

