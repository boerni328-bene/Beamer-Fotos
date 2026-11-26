# Lädt die APK eines Releases von GitHub in den Ordner apk\ (nicht im Repository).
# Aufruf: powershell -ExecutionPolicy Bypass -File tools\apk-holen.ps1            (neuestes Release)
#         powershell -ExecutionPolicy Bypass -File tools\apk-holen.ps1 v0.1.0     (bestimmte Version)
param([string]$Tag = "")
$ErrorActionPreference = "Stop"
$ziel = Join-Path (Split-Path $PSScriptRoot -Parent) "apk"
New-Item -ItemType Directory -Force $ziel | Out-Null
$ghArgs = @("release", "download")
if ($Tag) { $ghArgs += $Tag }
$ghArgs += @("--repo", "boerni328-bene/Beamer-Fotos", "--pattern", "beamer-fotos-*.apk*", "--dir", $ziel, "--skip-existing")
& gh @ghArgs
if ($LASTEXITCODE -ne 0) { throw "Herunterladen fehlgeschlagen." }
# Prüfsumme kontrollieren
Get-ChildItem $ziel -Filter "*.apk.sha256" | ForEach-Object {
    $apk = Join-Path $ziel ($_.Name -replace '\.sha256$', '')
    $soll = ((Get-Content $_.FullName -Raw).Trim() -split '\s+')[0].ToLower()
    $ist = (Get-FileHash $apk -Algorithm SHA256).Hash.ToLower()
    if ($soll -ne $ist) { throw "Prüfsumme falsch: $apk" }
    Write-Output "OK  $([IO.Path]::GetFileName($apk))"
}
