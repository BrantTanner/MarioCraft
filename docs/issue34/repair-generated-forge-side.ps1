[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $MappedJar,

    [Parameter(Mandatory = $true)]
    [string] $ForgeUniversalJar
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.IO.Compression.FileSystem

$entryName = 'net/minecraftforge/fml/relauncher/Side.class'
$expectedForgeUniversalSha1 = 'd0ab8e116da0e50c6e6099791f97772a08469626'
$expectedOfficialSideSha256 = 'ED510ED100CF24A7DAC6EDF2B28B0528A5BB981FA9C426A92DA79A33EDA103AE'
$expectedGeneratedSideSha256 = '331A5ED0C4EB9EA48CE0CBF5D02007F11F1A300AB2BCEFE358D895A0C63EBAE3'

function Get-ZipEntryBytes([string] $ArchivePath, [string] $EntryPath) {
    $archive = [IO.Compression.ZipFile]::OpenRead($ArchivePath)
    try {
        $entries = @($archive.Entries | Where-Object { $_.FullName -ceq $EntryPath })
        if ($entries.Count -ne 1) {
            throw "Expected exactly one '$EntryPath' entry in '$ArchivePath'; found $($entries.Count)."
        }
        $stream = $entries[0].Open()
        try {
            $buffer = [IO.MemoryStream]::new()
            try {
                $stream.CopyTo($buffer)
                return ,$buffer.ToArray()
            }
            finally { $buffer.Dispose() }
        }
        finally { $stream.Dispose() }
    }
    finally { $archive.Dispose() }
}

function Get-Sha256Hex([byte[]] $Bytes) {
    $sha256 = [Security.Cryptography.SHA256]::Create()
    try { return [BitConverter]::ToString($sha256.ComputeHash($Bytes)).Replace('-', '') }
    finally { $sha256.Dispose() }
}

$mappedPath = (Resolve-Path -LiteralPath $MappedJar).Path
$universalPath = (Resolve-Path -LiteralPath $ForgeUniversalJar).Path
$officialSha1 = (Get-FileHash -LiteralPath $universalPath -Algorithm SHA1).Hash.ToLowerInvariant()
if ($officialSha1 -ne $expectedForgeUniversalSha1) {
    throw "Forge universal SHA-1 mismatch: expected $expectedForgeUniversalSha1, got $officialSha1."
}

$officialSide = Get-ZipEntryBytes $universalPath $entryName
$officialSideSha256 = Get-Sha256Hex $officialSide
if ($officialSideSha256 -ne $expectedOfficialSideSha256) {
    throw "Official Side.class SHA-256 mismatch: expected $expectedOfficialSideSha256, got $officialSideSha256."
}

$currentBytes = Get-ZipEntryBytes $mappedPath $entryName
$currentSha256 = Get-Sha256Hex $currentBytes
if ($currentSha256 -eq $expectedOfficialSideSha256) {
    Write-Output "Already repaired: $mappedPath"
    Write-Output "Side.class SHA-256: $currentSha256"
    exit 0
}
if ($currentSha256 -ne $expectedGeneratedSideSha256) {
    throw "Generated Side.class is not the diagnosed class: expected $expectedGeneratedSideSha256, got $currentSha256."
}

$backupPath = "$mappedPath.issue34-pre-repair"
$originalJarSha256 = (Get-FileHash -LiteralPath $mappedPath -Algorithm SHA256).Hash
if (Test-Path -LiteralPath $backupPath) {
    $backupSha256 = (Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash
    if ($backupSha256 -ne $originalJarSha256) {
        throw "Existing backup '$backupPath' does not match the generated jar; refusing to overwrite it."
    }
}
else {
    Copy-Item -LiteralPath $mappedPath -Destination $backupPath
}

$temporaryPath = "$mappedPath.issue34-repair.tmp"
Copy-Item -LiteralPath $mappedPath -Destination $temporaryPath -Force
$archive = [IO.Compression.ZipFile]::Open($temporaryPath, [IO.Compression.ZipArchiveMode]::Update)
try {
    $entry = $archive.GetEntry($entryName)
    if ($null -eq $entry) { throw "Missing '$entryName' in temporary mapped jar." }
    $entry.Delete()
    $replacement = $archive.CreateEntry($entryName, [IO.Compression.CompressionLevel]::Optimal)
    $output = $replacement.Open()
    try { $output.Write($officialSide, 0, $officialSide.Length) }
    finally { $output.Dispose() }
}
finally { $archive.Dispose() }

$patchedBytes = Get-ZipEntryBytes $temporaryPath $entryName
$patchedSha256 = Get-Sha256Hex $patchedBytes
if ($patchedSha256 -ne $expectedOfficialSideSha256) {
    throw "Temporary repair validation failed: got Side.class SHA-256 $patchedSha256."
}
Move-Item -LiteralPath $temporaryPath -Destination $mappedPath -Force

Write-Output "Generated jar SHA-256 before repair: $originalJarSha256"
Write-Output "Backup: $backupPath"
Write-Output "Official Side.class SHA-256: $patchedSha256"
Write-Output "Repaired jar SHA-256: $((Get-FileHash -LiteralPath $mappedPath -Algorithm SHA256).Hash)"

