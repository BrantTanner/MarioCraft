[CmdletBinding()]
param([switch]$Launch, [switch]$Probe)
$ErrorActionPreference='Stop'
$ProgressPreference='SilentlyContinue'
$root=Split-Path $PSScriptRoot -Parent
$local=Join-Path $root '.local'
[IO.Directory]::CreateDirectory($local) | Out-Null
$jdk=Join-Path $local 'java/jdk8u504-b01'
if(-not (Test-Path -LiteralPath (Join-Path $jdk 'bin/java.exe'))) {
    $zip=Join-Path $local 'temurin8u504.zip'
    if(-not (Test-Path -LiteralPath $zip) -or (Get-FileHash -LiteralPath $zip -Algorithm SHA256).Hash -ne 'EA43D46EDE95B51E44A12C66711706CDDC762E0A766C54BCCEA18954E902B2AA') {
        & python -c "import urllib.request,sys; r=urllib.request.urlopen(sys.argv[1],timeout=60); open(sys.argv[2],'wb').write(r.read())" 'https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u504-b01/OpenJDK8U-jdk_x64_windows_hotspot_8u504b01.zip' $zip
        if($LASTEXITCODE -ne 0) { throw 'Pinned JDK download failed.' }
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
    & powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts/dev.ps1 -JavaHome $jdk -GradleHome (Join-Path $local 'gradle-home') -Task build
    if($LASTEXITCODE -ne 0) { throw 'Mod build failed.' }
    & python scripts/package-local.py --java-home $jdk
    if($LASTEXITCODE -ne 0) { throw 'Private runtime packaging failed.' }
    if($Launch -or $Probe) {
        $arguments=@('.local/MarioCraft/play.py')
        if($Probe) { $arguments+=@('--probe','presentation') }
        & python @arguments
        if($LASTEXITCODE -ne 0) { throw 'Packaged client launch or probe failed.' }
    }
} finally { Pop-Location }
