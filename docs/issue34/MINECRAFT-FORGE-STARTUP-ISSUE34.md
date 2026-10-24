# Forge 1.12.2 startup diagnosis and isolated repair

Status: diagnosed and repaired in a disposable Forge 14.23.5.2864 MDK run. This is a local generated-runtime repair based on the official 2864 universal artifact; the stock ForgeGradle 3.0.197 build still generates the faulty enum and needs the script below after setup. The clean 14.23.5.2859 and 14.23.5.2864 runs both reproduce the original startup crash. The successful runtime test used 2864 only.

## Reproduction and cause

Two clean MDKs extracted from official Forge Maven releases used Temurin 8u504, Gradle 4.9, ForgeGradle 3.0.197, snapshot mappings `20171003-1.12`, and separate fresh `GRADLE_USER_HOME` directories. The 2864 build changed only the Forge dependency coordinate from `14.23.5.2859` to `14.23.5.2864`. Both unmodified runs failed before the title screen with:

```text
NullPointerException
  at NetworkRegistry.newChannel(NetworkRegistry.java:207)
  at FMLNetworkHandler.registerChannel(FMLNetworkHandler.java:185)
  at FMLContainer.modConstruction(FMLContainer.java:92)
```

The precise artifact chain identifies the cause. In each clean build, official Minecraft client and server inputs contain no `net/minecraftforge/fml/relauncher/Side.class`; their stripped outputs also contain no such entry. The first generated copy appears in MCP's `joined/merge/output.jar`. Its bytes match the `Side.class` bundled inside `mergetool-0.2.3.2-fatjar.jar` exactly (SHA-256 `A51ECF8993CDDD938BF916BC64D2DCD7BB3C2D3BF7C5A2A19EB48BAA7E7E1ACA`). The MCP merge log shows that exact Mergetool as the merge process classpath and `--ann 1.12.2` as an argument. Later ForgeGradle processing transforms the enum class before writing the mapped runtime JAR: its faulty `Side.class` entry has SHA-256 `331A5ED0C4EB9EA48CE0CBF5D02007F11F1A300AB2BCEFE358D895A0C63EBAE3`. These are hashes from distinct pipeline stages, not competing reads of the same class.

The official Mergetool 0.2.3.2 source explains the copy: `mcp_config` requests annotation injection for 1.12.2; `AnnotationVersion.NMF` names `net.minecraftforge.fml.relauncher.Side` and `SideOnly`, and `Merger.process()` writes those classes from its own resources into the merged output. Its bundled `Side.java` declares `CLIENT, SERVER, BUKKIT`. The official 2859 and 2864 Forge universal JARs and source JARs instead contain the two-value `CLIENT, SERVER` enum. The raw 2864 universal JAR SHA-1 is `d0ab8e116da0e50c6e6099791f97772a08469626`, matching its official Maven `.sha1` sidecar. The Mergetool SHA-1 is `77f0f6ace8058bfd35afda95d3037f6a43b57694`, also matching its official Maven sidecar.

This is not a stale-cache or old-mapped-JAR collision: each version had a separate fresh Gradle cache. Their `joined/merge/output.jar` files are byte-identical (SHA-256 `A832146B6162F6CCA8F01EC405826EFDA6925FC3BB51592BF1BB2A2A4861FC10`), and both contain the Mergetool's three-value class. In the failing 2864 runtime, Java `-verbose:class` reports both `Side` and `NetworkRegistry` loaded from the exact `14.23.5.2864_mapped_snapshot_20171003-1.12.jar` in that fresh cache. `NetworkRegistry.newChannel` loops over `Side.values()` but initializes channel maps only for CLIENT and SERVER; the injected BUKKIT enum therefore yields a null lookup and the observed NPE.

An official later `mcp_config-1.12.2-20200226.224830.zip` was also inspected. Its merge arguments explicitly pass `--inject false`, while the older `mcp_config-1.12.2.zip` used by the ForgeGradle 3.0.197 vanilla merge omits that option and relies on Mergetool's default `true`. The dated config ZIP MD5 sidecar matches (`7479ea8da2d129fad5bf04a6d98cc6e1`). Forge 14.23.5.2864's official userdev3 metadata references that dated MCP config, but the observed vanilla merge output path in this MDK build is still the base `mcp_config/1.12.2` path. This report does not claim that changing only to the dated config was tested end-to-end.

## Verified generated-artifact repair

For the 2864 test, only `Side.class` in the disposable generated mapped runtime JAR was replaced with the exact `Side.class` entry from the SHA-1-verified official 2864 universal JAR. The original generated mapped JAR was preserved byte-for-byte as a backup. The replacement class hash is `ED510ED100CF24A7DAC6EDF2B28B0528A5BB981FA9C426A92DA79A33EDA103AE`; the mapped runtime JAR changed from SHA-256 `42C2F814D198D653FE34ABF9119B455CB0D3E3DEDE84C6552417602327FF1061` to `BE82C6488A807C9A69F3FA31466F845C40DC19C5C47E9C3415496E9D32462E5C`. A direct ZIP payload comparison found 4,935 file entries before and after, with exactly one differing payload: `net/minecraftforge/fml/relauncher/Side.class`. The ZIP directory records were rewritten as archive metadata; no other file payload changed.

The client was then launched without debugger or class-origin instrumentation. Forge 14.23.5.2864 reached the title screen and reported five loaded/active mods. In a new world under the disposable MDK's own `run/saves` directory, the integrated server reached spawn preparation, the player entered the world, and “Save and Quit to Title” completed. The world was `New Worlad`; the name field's text entry was imperfect, but its path is isolated under `MarioCraft/tools/issue34-clean-2864/run/saves/` and no pre-existing world was opened. The test client was then cleanly closed; the separate user vanilla Java 8u51 client remained open.

The script [`repair-generated-forge-side.ps1`](repair-generated-forge-side.ps1) makes this repair reproducible for the diagnosed generated class. It requires explicit paths, checks the official universal artifact SHA-1 and `Side.class` SHA-256, refuses unexpected generated bytes, preserves a matching backup, patches only that ZIP entry in a temporary copy, validates the result, and is idempotent. From the workspace root, after the clean MDK setup and failing run, execute:

```powershell
$env:JAVA_HOME = (Resolve-Path 'tools/java/temurin8/jdk8u504-b01').Path
$env:GRADLE_USER_HOME = (Join-Path (Resolve-Path 'MarioCraft/tools/issue34-clean-2864').Path 'gradle-home')
Push-Location 'MarioCraft/tools/issue34-clean-2864'
.\gradlew.bat runClient --no-daemon -x downloadAssets
Pop-Location
```

Stop at the reproduced `NetworkRegistry.newChannel` crash, then run this exact command against the isolated 2864 cache and the locally verified official artifact before rerunning `runClient`:

```powershell
$mapped = 'MarioCraft/tools/issue34-clean-2864/gradle-home/caches/forge_gradle/minecraft_user_repo/net/minecraftforge/forge/1.12.2-14.23.5.2864_mapped_snapshot_20171003-1.12/forge-1.12.2-14.23.5.2864_mapped_snapshot_20171003-1.12.jar'
$universal = 'MarioCraft/tools/issue34-official-artifacts/forge-1.12.2-14.23.5.2864-universal.jar'
& 'MarioCraft/docs/issue34/repair-generated-forge-side.ps1' -MappedJar $mapped -ForgeUniversalJar $universal
Push-Location 'MarioCraft/tools/issue34-clean-2864'
.\gradlew.bat runClient --no-daemon -x downloadAssets
Pop-Location
```

This exact script-driven uninstrumented run reached the 2864 title screen. The runtime verification used `-x downloadAssets` because ForgeGradle rejected the Windows asset task's `icons/minecraft.icns`; this skips asset completeness validation and is a limitation of this run. The same one-entry artifact repair was tested separately through disposable world creation and clean save/quit.

## Official artifact provenance

All archive downloads used direct HTTPS requests to the official Forge Maven host; published sidecars were checked where available. SHA-256 values below identify the downloaded bytes. The complete archive set and sidecars remain local under `MarioCraft/tools/issue34-official-artifacts/`.

| Artifact | Official Maven URL | SHA-256 |
| --- | --- | --- |
| Forge 2859 MDK | [forge-1.12.2-14.23.5.2859-mdk.zip](https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2859/forge-1.12.2-14.23.5.2859-mdk.zip) | `52992D8BD80F3517891019D4E1E07C081D6B09C4117313C36C8BEED8746527DB` |
| Forge 2859 universal | [forge-1.12.2-14.23.5.2859-universal.jar](https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2859/forge-1.12.2-14.23.5.2859-universal.jar) | `BD8314729D787E35164476D75AFB337AFB9783917E4F6FA1F77F049ADBA22F2D` |
| Forge 2859 sources | [forge-1.12.2-14.23.5.2859-sources.jar](https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2859/forge-1.12.2-14.23.5.2859-sources.jar) | `9D79D8FAD7D2ED0B802DE9D76BE6D00FB10D0FBD6E6467E80E92805602650742` |
| Forge 2864 MDK | [forge-1.12.2-14.23.5.2864-mdk.zip](https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2864/forge-1.12.2-14.23.5.2864-mdk.zip) | `BED660E5F8DEAB6DE687D2E894FB489501F00E7A47A9BEA260C84A460F8F6733` |
| Forge 2864 universal | [forge-1.12.2-14.23.5.2864-universal.jar](https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2864/forge-1.12.2-14.23.5.2864-universal.jar) | `FF578D670D2C720A72F8FFF31EA3D6868595C7E980ECDECBA3254F307EF2C2A9` |
| Forge 2864 sources | [forge-1.12.2-14.23.5.2864-sources.jar](https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2864/forge-1.12.2-14.23.5.2864-sources.jar) | `FA4E0961FD4986267A9F16BC7E0D579E86A882374BDE8CD074661692237318E2` |
| Forge 2864 userdev3 | [forge-1.12.2-14.23.5.2864-userdev3.jar](https://maven.minecraftforge.net/net/minecraftforge/forge/1.12.2-14.23.5.2864/forge-1.12.2-14.23.5.2864-userdev3.jar) | `D4F10B29F9C0602EE9A73C4499675C885DCC7829F612B906A26373852EDBA833` |
| Mergetool 0.2.3.2 fat jar | [mergetool-0.2.3.2-fatjar.jar](https://maven.minecraftforge.net/net/minecraftforge/mergetool/0.2.3.2/mergetool-0.2.3.2-fatjar.jar) | `00F405D3BCBDB20A3D4E41B3A22C9199D1FD0CCE481DA3363A9433867803749D` |
| Mergetool 0.2.3.2 sources | [mergetool-0.2.3.2-sources.jar](https://maven.minecraftforge.net/net/minecraftforge/mergetool/0.2.3.2/mergetool-0.2.3.2-sources.jar) | `8FED960F3C672BDFCC0E85F8CDB353A10A57E5C6C705B68127ED2DC3BFD84E48` |
| MCP 1.12.2 dated config | [mcp_config-1.12.2-20200226.224830.zip](https://maven.minecraftforge.net/de/oceanlabs/mcp/mcp_config/1.12.2/mcp_config-1.12.2-20200226.224830.zip) | `E2DDD3A7BB65618AD0DE1D8FC0334527E5C346180300D94BFC7D3727B1976B42` |

This is an evidence-backed local repair of a generated artifact, not a claim that the untouched MDK launch works or that the supported `--inject false` configuration was integrated into ForgeGradle 3.0.197. The exact generated artifact transformation is now known; a supported build-level override remains a follow-up if the project needs a fully hands-off ForgeGradle setup.

## Evidence and handoff

Official 2859 and 2864 MDK, universal, source, userdev, MCP-config and Mergetool archives, their hashes, fresh Gradle caches, unmodified crash logs, runtime origin logs, generated JARs, and the disposable save remain local under `MarioCraft/tools/issue34-*`. They are excluded from publication. The sanitized reports and repair script contain hashes, command paths, and findings only.

Issue #34 is the provenance/reproduction/fix record for [#9](https://github.com/BrantTanner/MarioCraft/issues/9) and the Forge readiness dependency in [#18](https://github.com/BrantTanner/MarioCraft/issues/18). Vanilla lifecycle observations remain in `MINECRAFT-CLIENT-PROGRESSION-RUNTIME.md` and `MINECRAFT-LIFECYCLE-SAVING-NETWORKING.md`; this issue does not perform the separate #9 audit or #18 readiness review.

