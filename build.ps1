# DonutSMP Trader build (Windows)
# Derleme Gradle + Fabric Loom ile yapilir; JDK'yi Loom kendisi indirir.
$ErrorActionPreference = "Stop"

& "$PSScriptRoot\gradlew.bat" build
if ($LASTEXITCODE -ne 0) {
    Write-Error "Derleme basarisiz oldu!"
}

$jar = "build\libs\donutsmp-trader-1.0.0.jar"
Write-Host "Basarili! Mod dosyasi: $jar" -ForegroundColor Green
Write-Host "Bu dosyayi Minecraft KAPALIYKEN mods klasorunuze kopyalayin." -ForegroundColor Yellow
