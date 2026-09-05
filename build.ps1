# DonutSMP Trader build (Windows)
# Derleme Gradle + Fabric Loom ile yapilir; JDK'yi Loom kendisi indirir.
$ErrorActionPreference = "Stop"

& "$PSScriptRoot\gradlew.bat" build
if ($LASTEXITCODE -ne 0) {
    Write-Error "Derleme basarisiz oldu!"
}

$jar = Get-ChildItem "build\libs\donutsmp-trader-*.jar" |
    Where-Object { $_.Name -notlike "*-sources*" } |
    Sort-Object LastWriteTime -Descending | Select-Object -First 1
Write-Host "Basarili! Mod dosyasi: $($jar.FullName)" -ForegroundColor Green
Write-Host "Bu dosyayi Minecraft KAPALIYKEN mods klasorunuze kopyalayin." -ForegroundColor Yellow
