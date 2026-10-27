[CmdletBinding()]
param([switch]$Launch, [switch]$Probe)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
$local=Join-Path $root '.local'
[IO.Directory]::CreateDirectory($local) | Out-Null
$jdk=Join-Path $local 'java/jdk8u504-b01'
if(-not (Test-Path -LiteralPath (Join-Path $jdk 'bin/java.exe'))) {
    $zip=Join-Path $local 'temurin8u504.zip'
    if(-not (Test-Path -LiteralPath $zip)) {
        Invoke-WebRequest -UseBasicParsing -Uri 'https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u504-b01/OpenJDK8U-jdk_x64_windows_hotspot_8u504b01.zip' -OutFile $zip
    }
    if((Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne 'EA43D46EDE95B51E44A12C66711706CDDC762E0A766C54BCCEA18954E902B2AA') { throw 'JDK archive SHA-256 mismatch.' }
    Expand-Archive -LiteralPath $zip -DestinationPath (Join-Path $local 'java') -Force
}
Push-Location $root
try {
    & python scripts/fetch-native.py
    if($LASTEXITCODE -ne 0) { throw 'Native dependency download failed.' }
    & python scripts/build-native.py
    if($LASTEXITCODE -ne 0) { throw 'Native build failed.' }
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/dev.ps1 -JavaHome $jdk -Task build
    if($LASTEXITCODE -ne 0) { throw 'Mod build failed.' }
    if($Launch -or $Probe) {
        $arguments=@('-NoProfile','-ExecutionPolicy','Bypass','-File','scripts/dev.ps1','-JavaHome',$jdk,'-Task','client','-Gameplay')
        if($Probe) { $arguments+='-GameplayProbe' }
        & powershell.exe @arguments
        if($LASTEXITCODE -ne 0) { throw 'Client launch or probe failed.' }
    }
} finally { Pop-Location }
