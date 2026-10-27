[CmdletBinding()]
param(
    [ValidateSet('build', 'client', 'server', 'nativeSmoke')][string]$Task = 'build',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$GradleHome = $env:GRADLE_USER_HOME,
    [switch]$LifecycleProbe,
    [switch]$ClientProbe,
    [switch]$Gameplay,
    [switch]$GameplayProbe,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw 'Pass -JavaHome with a Java 8 JDK directory.' }
$env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
if (-not $GradleHome) { $GradleHome = Join-Path $root '.local/gradle-home' }
[IO.Directory]::CreateDirectory($GradleHome) | Out-Null
$env:GRADLE_USER_HOME = (Resolve-Path -LiteralPath $GradleHome).Path
Push-Location $root
try {
    $options = @()
    if ($Offline) { $options += '--offline' }
    & ./gradlew.bat @options classes
    if ($LASTEXITCODE -ne 0) { throw 'Forge setup/compilation failed.' }
    $mapped = @(Get-ChildItem -LiteralPath $GradleHome -Filter 'forge-1.12.2-14.23.5.2864_mapped_snapshot_20171003-1.12.jar' -Recurse -File)
    $universal = @(Get-ChildItem -LiteralPath $GradleHome -Filter 'forge-1.12.2-14.23.5.2864-universal.jar' -Recurse -File)
    if ($mapped.Count -ne 1 -or $universal.Count -ne 1) { throw 'Expected exactly one generated mapped JAR and one official universal JAR in this Gradle cache.' }
    # Separate process: the idempotent upstream repair script exits when already repaired.
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File ./docs/issue34/repair-generated-forge-side.ps1 -MappedJar $mapped[0].FullName -ForgeUniversalJar $universal[0].FullName
    if ($LASTEXITCODE -ne 0) { throw 'Guarded Forge Side.class repair failed.' }
    $gradleTask = switch ($Task) { 'client' { 'runClient' } 'server' { 'runServer' } default { $Task } }
    if ($LifecycleProbe) { $options += '-Dmariocraft.lifecycleProbe=true' }
    if ($ClientProbe) { $options += '-Dmariocraft.clientProbe=true' }
    if ($Gameplay) { $options += '-Dmariocraft.gameplay=true' }
    if ($GameplayProbe) { $options += @('-Dmariocraft.gameplay=true','-Dmariocraft.gameplayProbe=true','-Dmariocraft.clientProbe=true') }
    if ($GameplayProbe) {
        $resultPath=Join-Path $root '.local/run-client/gameplay-result.json'
        if(Test-Path -LiteralPath $resultPath) { Remove-Item -LiteralPath $resultPath }
    }
    & ./gradlew.bat @options $gradleTask
    if ($LASTEXITCODE -ne 0) { throw ('Gradle task failed: ' + $gradleTask) }
    if ($GameplayProbe) {
        $result = Get-Content -LiteralPath './.local/run-client/gameplay-result.json' -Raw | ConvertFrom-Json
        if (-not $result.passed) { throw 'Integrated gameplay probe failed; inspect .local/run-client/gameplay-result.json and runtime log.' }
    }
} finally { Pop-Location }
