# Minecraft Java Edition 1.12.2 baseline (issue #2)

This report is the reproducible starting point for the Minecraft research
sequence in [issue #2](https://github.com/BrantTanner/MarioCraft/issues/2).
It establishes artifact identity, the local analysis prerequisites, one
bounded static finding, and ownership for the follow-up research tickets.
It does not claim complete subsystem analysis; issues #3–#8 perform that work,
and issue #9 audits the resulting dossier.

## Release artifacts and provenance

The default Minecraft Launcher installation had no `1.12.2` directory. The
official Mojang 1.12.2 launcher metadata was retrieved from
`https://launchermeta.mojang.com/v1/packages/6e69e85d0f85f4f4b9e12dd99d102092a6e15918/1.12.2.json`.
Its SHA-1 is `6e69e85d0f85f4f4b9e12dd99d102092a6e15918`, matching the package
identifier in the official URL; its local SHA-256 is
`827aaf8f97209f7747897e6c78875ae2c1bf732bb81ab85a298e6d22223cf385`. The
metadata identifies a release dated 2017-09-18 and provides the client and
server object hashes below.

| Artifact | Official object URL | Size | SHA-1 (Mojang object identity) | SHA-256 (local verification) |
| --- | --- | ---: | --- | --- |
| Client jar | `https://launcher.mojang.com/v1/objects/0f275bc1547d01fa5f56ba34bdc87d981ee12daf/client.jar` | 10,180,113 bytes | `0f275bc1547d01fa5f56ba34bdc87d981ee12daf` | `8ada07da5ee77dad3527bd7278fbd05ee1fc8a597813b216a871a2d7d64cc64f` |
| Vanilla server jar | `https://launcher.mojang.com/v1/objects/886945bfb2b978778c3a0288fd7fab09d315b25f/server.jar` | 30,222,121 bytes | `886945bfb2b978778c3a0288fd7fab09d315b25f` | `fe1f9274e6dad9191bf6e6e8e36ee6ebc737f373603df0946aafcded0d53167e` |

The local SHA-1 values exactly match both SHA-1 object identifiers from the
version metadata. The downloads used Mojang's official launcher host at
`https://launcher.mojang.com/mc/game/1.12.2/client/<sha1>/client.jar` and
`https://launcher.mojang.com/mc/game/1.12.2/server/<sha1>/server.jar`; the
table lists the content-addressed URLs returned by the version metadata.
Mojang's release announcement also links the cross-platform server jar
directly. The metadata has no `javaVersion` field; Java compatibility was
established separately from the Forge 1.12 documentation and the class file
target below.

The files and all generated inspection material are kept under the
git-ignored `MarioCraft/tools/` directory. Do not add game jars, extracted game
assets, runtime captures, or local credentials to GitHub. The original files
remain untouched.

## Local tool prerequisites

| Tool | Verified local identity | Purpose / result |
| --- | --- | --- |
| Eclipse Temurin JDK 8 | `8.0.504` (`8u504-b01`), 64-bit Windows; installed at `tools/java/temurin8/jdk8u504-b01` from Adoptium's official binary API; downloaded archive SHA-256 `ea43d46ede95b51e44a12c66711706cddc762e0a766c54bccea18954e902b2aa` | Runtime baseline for Minecraft/Forge 1.12.2 research and future execution. `java -version` verified. |
| Eclipse Temurin JDK 21 | `21.0.12.1+1-LTS`; under `tools/java/jdk-21.0.12.1+1` | Runs current Ghidra and CFR. |
| CFR | `0.152`; `java -jar tools/cfr-0.152.jar --version` returned `CFR 0.152` | Focused Java class decompilation. |
| Ghidra | `12.1.4_PUBLIC` with the pinned N64 loader already configured | Present for later native/MIPS investigations; not required for this Java finding. |
| Minecraft Forge MDK | Recommended `1.12.2-14.23.5.2859`, downloaded from the official Maven host; local SHA-1 `347338fc96359640c3d9673438db26742bd658d5`, matching the Forge index | Correct Forge development baseline is available locally. No MDK build or game launch was needed for this ticket. |

Forge's official 1.12 documentation says Minecraft and Forge versions 1.12–1.16
compile against Java 8 and recommends JDK 8 for development. The official
download index currently lists `14.23.5.2864` as latest and `14.23.5.2859` as
recommended. We pin the recommended `.2859` MDK for the research and eventual
prototype baseline; the existence of a later latest build is recorded so the
choice can be revisited with evidence. The official 1.12 MDK workflow expects
the Gradle wrapper and Forge's decompilation setup; do not infer successful
Gradle resolution merely from the verified MDK download.

## Bounded inspection and finding

**Question:** what server tick cadence and overload catch-up policy does the
selected vanilla integrated/dedicated server class implement?

**Original evidence:** the class is
`net/minecraft/server/MinecraftServer.class` in the official server jar above.
Its extracted byte SHA-256 is
`246e7916fde4261e776c4beb17c5b7e84a80948c5ce60abf1e8011ca88288b0f`. `javap
-verbose` reports class-file major version `52` (Java 8). The class is the
server jar's declared main class (`META-INF/MANIFEST.MF`).

**Static finding:** CFR 0.152's reconstruction of `MinecraftServer.run()`
accumulates elapsed milliseconds and schedules server updates at 50 ms
intervals, corresponding to a 20 Hz target. When more than 2,000 ms behind, it
logs a “Can't keep up” warning (rate-limited to one warning per 15 seconds) and
caps accumulated backlog before continuing the loop. If the accumulator still
exceeds 50 ms, it performs repeated update calls to catch up. This is a finding
from decompilation of one class in the identified original server jar; it is
not a runtime observation or proof of the behavior of every subsystem. CFR
output has incomplete external types and obfuscated identifiers, so the
meaning of unrelated short-named members is not asserted.

The resulting CFR source file SHA-256 is
`8def155c73356237b36779df37fd9ac663e722dfd9b280064d7413ea033e7f2c`.

**Reproduction (PowerShell from the workspace root):**

```powershell
$tools = 'MarioCraft/tools'
$metadata = Get-Content "$tools/version-1.12.2.json" -Raw | ConvertFrom-Json
foreach ($file in @("$tools/version-1.12.2.json", "$tools/client-1.12.2.jar", "$tools/server-1.12.2.jar")) {
  foreach ($algorithm in @('SHA1', 'SHA256')) {
    Get-FileHash -LiteralPath $file -Algorithm $algorithm
  }
}
$metadata.downloads.client.sha1
$metadata.downloads.server.sha1
$jar = 'tools/java/jdk-21.0.12.1+1/bin/jar.exe'
$java = 'tools/java/jdk-21.0.12.1+1/bin/java.exe'
$javap = 'tools/java/jdk-21.0.12.1+1/bin/javap.exe'
$classDir = 'MarioCraft/tools/inspection'
New-Item -ItemType Directory -Force $classDir | Out-Null
Push-Location $classDir
& "../../../$jar" xf '../server-1.12.2.jar' 'net/minecraft/server/MinecraftServer.class'
Pop-Location
Get-FileHash "$classDir/net/minecraft/server/MinecraftServer.class" -Algorithm SHA256
& $javap -classpath 'MarioCraft/tools/server-1.12.2.jar' -verbose net.minecraft.server.MinecraftServer
& $java -jar 'tools/cfr-0.152.jar' `
  'MarioCraft/tools/inspection/net/minecraft/server/MinecraftServer.class' `
  --outputdir 'MarioCraft/tools/inspection/decompiled' --silent true
```

Tool identities and observations are recorded above; this report plus the
ignored extracted class and CFR output form the repeatable local evidence
bundle. Only the selected class was extracted and decompiled.

## Complete research ownership map

The jar inventory establishes the selected client (7,957 entries: 3,310 class
files and 4,644 non-class, non-`META-INF` entries) and server (20,554 entries)
as the artifacts for the next research tickets. Minecraft 1.12.2's game
classes are substantially obfuscated; the isolated decompilation above is not
a substitute for a named subsystem map. The following map assigns the full
research surface, including features excluded from the MarioCraft MVP. Each
ticket should add its own class/data references, behavior evidence, relevant
assets, interactions, and bounded unknowns.

| Ticket | Assigned subsystem scope |
| --- | --- |
| [#3](https://github.com/BrantTanner/MarioCraft/issues/3) | Client input devices and key bindings; player control and movement; interaction and targeting; chat and input-facing GUI behavior; client/server requests and acknowledgements at these interaction seams. |
| [#4](https://github.com/BrantTanner/MarioCraft/issues/4) | Block and fluid state/behavior; collision and ray tests; chunks and block entities; world generation, terrain, biomes, structures, dimensions, lighting, weather, redstone, and other world-simulation mechanisms. |
| [#5](https://github.com/BrantTanner/MarioCraft/issues/5) | Player and non-player entities; AI, navigation, spawning, ticking and interaction; attack and damage sources; health, status effects, death, fall/fire/lava/water hazards, and invulnerability behavior. |
| [#6](https://github.com/BrantTanner/MarioCraft/issues/6) | Inventory and hotbar; items, equipment and durability; recipes and crafting; loot, experience, enchanting, potions, trading and all progression/objective systems, including MVP-excluded systems. |
| [#7](https://github.com/BrantTanner/MarioCraft/issues/7) | Client rendering and display; models, animation, HUD, particles and effects; menus and screens; resource packs, textures, fonts, shaders, sound, voice, music and presentation-side settings. |
| [#8](https://github.com/BrantTanner/MarioCraft/issues/8) | Client startup/shutdown and configuration; authentication/session and external-service boundaries; network protocol; integrated and dedicated server lifecycle, threads/ticks and commands; world/player/chunk persistence, loading and saving; crash/reporting paths; Forge loader, event hooks and development/runtime packaging as cross-cutting integration surfaces. |
| [#9](https://github.com/BrantTanner/MarioCraft/issues/9) | Cross-ticket completeness audit: confirm every identified client and integrated-server subsystem has an owner and evidence; reconcile overlaps and gaps; create bounded follow-up research issues if new uncovered scope is found. |

The map gives every identified subsystem family an owner, so this ticket does
not create duplicate research issues ahead of the six planned investigations.
Issue #9 is explicitly responsible for creating bounded follow-up tickets if
those investigations expose a gap. MVP exclusions limit the eventual playable
slice only; they do not remove the relevant Minecraft systems from research.

## Scope and limitations

- No Minecraft process was launched, no world was created or changed, and no
  gameplay prototype was built.
- The one static finding describes one server class only. It does not prove
  integrated-server scheduling under a live client or establish rendering,
  simulation, networking, persistence, or client frame behavior.
- Jar entry counts are inventory summaries, not subsystem completeness proofs.
  Obfuscated names, resources and dynamically selected code still require
  focused examination in issues #3–#8.
- Source and decompiler output must be checked against the original artifact
  identity. Static reconstruction is not original Mojang source and does not
  establish execution.

## Primary sources

- [Mojang 1.12.2 release announcement](https://www.minecraft.net/en-us/article/minecraft-1122-released) (release date and official cross-platform server-jar link).
- [Mojang launcher metadata](https://launchermeta.mojang.com/v1/packages/6e69e85d0f85f4f4b9e12dd99d102092a6e15918/1.12.2.json) (release identity and client/server hashes).
- [Forge 1.12 development setup guide](https://docs.minecraftforge.net/en/1.12.x/gettingstarted/) (Java 8 and decompilation/setup workflow).
- [Official Forge 1.12.2 downloads](https://files.minecraftforge.net/net/minecraftforge/forge/index_1.12.2.html) (latest/recommended versions and artifact checksums).
- [Adoptium Temurin releases and binary API](https://adoptium.net/temurin/releases/?os=windows&package=jdk&version=8) (local JDK 8 distribution source).

