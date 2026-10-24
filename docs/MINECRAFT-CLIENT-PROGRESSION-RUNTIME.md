# Minecraft 1.12.2 connected-client runtime follow-up

Status: partial; issue [#33](https://github.com/BrantTanner/MarioCraft/issues/33), [#6](https://github.com/BrantTanner/MarioCraft/issues/6), and [#9](https://github.com/BrantTanner/MarioCraft/issues/9) remain open.

## Launch evidence

The official Forge 1.12.2-14.23.5.2859 MDK run was repeated on 2026-10-24 with workspace Temurin 8u504 and the project-local Gradle cache. Command, from `tools/mdk-work`:

```powershell
$env:JAVA_HOME = '<workspace>\tools\java\temurin8\jdk8u504-b01'
$env:GRADLE_USER_HOME = '<workspace>\MarioCraft\tools\gradle-cache'
.\gradlew.bat runClient --no-daemon --stacktrace
```

The run reached LWJGL and the FML construction phase, then reproduced the original pre-title failure. The local report is `tools/mdk-work/run/crash-reports/crash-2026-10-24_12.16.10-client.txt`; its relevant stack is:

```
LoaderExceptionModCrash: Caught exception from Forge Mod Loader (FML)
Caused by: java.lang.NullPointerException
  at NetworkRegistry.newChannel(NetworkRegistry.java:207)
  at FMLNetworkHandler.registerChannel(FMLNetworkHandler.java:185)
  at FMLContainer.modConstruction(FMLContainer.java:92)
```

A debugger run with `--debug-jvm` stopped at the channel insertion line. JDB showed `side=CLIENT`, `name=FML`, the `FMLContainer` argument, and a channel-map key set rendered as `[CLIENT, SERVER]`; a lookup rendered null. JDB's expression evaluator resumed the target while evaluating method calls, so this is a lead for a future debugger pass, not a verified classloader or initialization diagnosis. That run later logged connection-handshake attempts for both CLIENT and SERVER and exposed a window titled `Minecraft 1.12.2`.

## Runtime boundary and resume action

A screenshot/state request for that window failed with the exact tool result `Computer Use app approval timed out`. No UI interaction or screenshot was obtained. The task-created Forge client, debugger, and Gradle processes were stopped and verified absent afterward. No connected player, disposable world, inventory mutation, crafting result, progression/effect change, save/reload, or player-death/drop scenario was observed.

Resume by approving/restoring Computer Use for this task so the Minecraft window can be observed and driven. Until that works, every connected-client acceptance item in #33 remains unverified; do not treat the debugger run as a successful Forge repair. The pre-existing static item dossier remains the source for code and resource traces.
