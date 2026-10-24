$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location $repo
$jdk = (Resolve-Path (Join-Path $repo '..\tools\java\temurin8\jdk8u504-b01')).Path
$cfr = (Resolve-Path (Join-Path $repo '..\tools\cfr-0.152.jar')).Path
$serverJar = (Resolve-Path 'tools/server-1.12.2.jar').Path
$expectedJar = 'fe1f9274e6dad9191bf6e6e8e36ee6ebc737f373603df0946aafcded0d53167e'
if ((Get-FileHash $serverJar -Algorithm SHA256).Hash.ToLowerInvariant() -ne $expectedJar) { throw 'Official server jar SHA-256 does not match the dossier baseline.' }
$work = Join-Path $repo 'tools/inspection/issue32/reproduction'
$classes = Join-Path $work 'classes'
$decompiled = Join-Path $work 'cfr'
$probeClasses = Join-Path $work 'probe-classes'
New-Item -ItemType Directory -Force -Path $classes,$decompiled,$probeClasses | Out-Null
Push-Location $classes
& "$jdk\bin\jar.exe" xf $serverJar ain.class aow.class aox.class
if ($LASTEXITCODE -ne 0) { throw 'Could not extract original registry classes.' }
Pop-Location
$hashes = Import-Csv 'docs/research/MINECRAFT-1.12.2-ITEM-PATH-CLASS-HASHES.csv'
foreach ($entry in @(@('ain.class','net/minecraft/item/Item'),@('aow.class','net/minecraft/block/Block'),@('aox.class','net/minecraft/init/Blocks'))) {
  $row = $hashes | Where-Object { $_.Original -eq $entry[0] -and $_.MCP -eq $entry[1] } | Select-Object -First 1
  if (-not $row) { throw "Missing published hash for $($entry[0])." }
  if ((Get-FileHash (Join-Path $classes $entry[0]) -Algorithm SHA256).Hash -ne $row.SHA256) { throw "Original-byte hash mismatch for $($entry[0])." }
  & "$jdk\bin\java.exe" -jar $cfr (Join-Path $classes $entry[0]) --outputdir $decompiled --silent true
  if ($LASTEXITCODE -ne 0) { throw "CFR failed for $($entry[0])." }
}
$generatedCatalog = Join-Path $work 'item-registry.csv'
python 'docs/research/parse_minecraft1122_item_registrations.py' $decompiled $generatedCatalog
if ($LASTEXITCODE -ne 0) { throw 'Original registration grammar parse failed.' }
$libraries = @(Get-Content 'docs/research/MINECRAFT-1.12.2-REGISTRY-LIBRARIES.txt' | Where-Object { $_ -and -not $_.StartsWith('#') } | ForEach-Object { (Resolve-Path $_).Path })
$classpath = (@($serverJar) + $libraries) -join ';'
& "$jdk\bin\javac.exe" -cp $classpath -d $probeClasses 'docs/research/Minecraft1122RegistryProbe.java'
if ($LASTEXITCODE -ne 0) { throw 'Registry probe compilation failed.' }
$runtime = Join-Path $work 'registry-runtime.csv'
& "$jdk\bin\java.exe" -cp "$probeClasses;$classpath" Minecraft1122RegistryProbe $runtime
if ($LASTEXITCODE -ne 0) { throw 'Original-byte runtime registry probe failed.' }
python 'docs/research/validate_minecraft1122_registry.py' $generatedCatalog $runtime
if ($LASTEXITCODE -ne 0) { throw 'Registry comparison failed.' }
python 'docs/research/check_minecraft1122_block_lookup.py' $runtime
if ($LASTEXITCODE -ne 0) { throw 'Block lookup partition check failed.' }


