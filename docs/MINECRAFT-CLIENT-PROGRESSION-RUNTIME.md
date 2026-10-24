# Minecraft 1.12.2 connected-client runtime follow-up

Status: partial; issue [#33](https://github.com/BrantTanner/MarioCraft/issues/33), [#6](https://github.com/BrantTanner/MarioCraft/issues/6), and [#9](https://github.com/BrantTanner/MarioCraft/issues/9) remain open.

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

## Runtime boundary and resume action

A fresh Computer Use inventory returned a Java window titled `Minecraft 1.12.2`. The ensuing state capture displayed an unrelated Downloads/File Explorer image; activating the returned Minecraft window then failed with `computer-use request timed out: activate_window`. No Minecraft UI or player state was observed. Ctrl+C ended the task's Gradle session and JDB disconnected; the subsequent shell process query returned no `java` or `javaw` processes. No connected player, disposable world, inventory mutation, crafting result, progression/effect change, save/reload, or player-death/drop scenario was observed.

Resume by bringing the actual Minecraft window to the foreground and approving/restoring Computer Use for this task if the host offers that prompt. The tool enumerated a Minecraft window but could not activate it and returned an unrelated image. Until a usable client can be observed, every connected-client acceptance item in #33 remains unverified; do not treat the debugger run or the Side source/class discrepancy as a successful Forge repair. The pre-existing static item dossier remains the source for code and resource traces.
