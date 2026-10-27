[CmdletBinding()]
param(
    [ValidateSet('build', 'client', 'server', 'nativeSmoke', 'traversalSmoke')][string]$Task = 'build',
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$GradleHome = $env:GRADLE_USER_HOME,
    [switch]$LifecycleProbe,
    [switch]$ClientProbe,
    [switch]$Gameplay,
    [switch]$GameplayProbe,
    [switch]$TraversalProbe,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) { throw 'Pass -JavaHome with a Java 8 JDK directory.' }
$env:JAVA_HOME = (Resolve-Path -LiteralPath $JavaHome).Path
if (-not $GradleHome) { $GradleHome = Join-Path $root '.local/gradle-home' }
[IO.Directory]::CreateDirectory($GradleHome) | Out-Null
$env:GRADLE_USER_HOME = (Resolve-Path -LiteralPath $GradleHome).Path
$GradleHome = $env:GRADLE_USER_HOME
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
    if ($TraversalProbe) { $GameplayProbe=$true; $options += '-Dmariocraft.traversalProbe=true' }
    if ($GameplayProbe) { $options += @('-Dmariocraft.gameplay=true','-Dmariocraft.gameplayProbe=true','-Dmariocraft.clientProbe=true') }
    if ($GameplayProbe) {
        $resultPath=Join-Path $root '.local/run-client/gameplay-result.json'
        if(Test-Path -LiteralPath $resultPath) {
            $runId=[DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ')+'-'+[Guid]::NewGuid().ToString('N')
            $archive=Join-Path $root ('.local/probe-results/'+$runId)
            [IO.Directory]::CreateDirectory($archive) | Out-Null
            Copy-Item -LiteralPath $resultPath -Destination (Join-Path $archive 'gameplay-result.json')
            foreach($evidence in @('movement-trace.jsonl','traversal-trace.jsonl','logs/latest.log')) {
                $evidencePath=Join-Path $root ('.local/run-client/'+$evidence)
                if(Test-Path -LiteralPath $evidencePath) { Copy-Item -LiteralPath $evidencePath -Destination (Join-Path $archive (Split-Path $evidence -Leaf)) }
            }
            $capture=Join-Path $root '.local/run-client/screenshots/mariocraft-gameplay.png'
            if(Test-Path -LiteralPath $capture) { Copy-Item -LiteralPath $capture -Destination (Join-Path $archive 'mariocraft-gameplay.png') }
            Remove-Item -LiteralPath $resultPath
        }
    }
    if ($Task -eq 'client' -and -not $Offline) {
        & python ./scripts/ensure-minecraft-assets.py --gradle-home $GradleHome
        if ($LASTEXITCODE -ne 0) { throw 'Verified Minecraft HTTPS asset setup failed.' }
    }
    & ./gradlew.bat @options $gradleTask
    if ($LASTEXITCODE -ne 0) { throw ('Gradle task failed: ' + $gradleTask) }
    if ($GameplayProbe) {
        $result = Get-Content -LiteralPath './.local/run-client/gameplay-result.json' -Raw | ConvertFrom-Json
        if (-not $result.passed) { throw 'Integrated gameplay probe failed; inspect .local/run-client/gameplay-result.json and runtime log.' }
    }
} finally { Pop-Location }
