# DonutSMP Trader Build Script (Java 25 - Minecraft 26.2)
$ErrorActionPreference = "Stop"

$jdkPath = "C:\Program Files\Zulu\zulu-25\bin"
$javac = "$jdkPath\javac.exe"
$jar = "$jdkPath\jar.exe"

$processedJars = Get-ChildItem "$env:APPDATA\ModrinthApp\profiles\26.2 Fabric\.fabric\processedMods\*.jar" | Select-Object -ExpandProperty FullName
$mcLibs = Get-ChildItem -Recurse -Filter "*.jar" "$env:APPDATA\.minecraft\libraries" | Select-Object -ExpandProperty FullName

$cpList = [System.Collections.Generic.List[string]]::new()
$cpList.Add(("$env:APPDATA/.minecraft/versions/26.2/26.2.jar" -replace "\\", "/"))
$cpList.Add(("$env:APPDATA/ModrinthApp/meta/libraries/net/fabricmc/fabric-loader/0.19.3/fabric-loader-0.19.3.jar" -replace "\\", "/"))
$cpList.Add(("$env:APPDATA/ModrinthApp/profiles/26.2 Fabric/mods/fabric-api-0.158.0+26.2.jar" -replace "\\", "/"))

foreach ($pj in $processedJars) {
    $cpList.Add(($pj -replace "\\", "/"))
}
foreach ($lib in $mcLibs) {
    $cpList.Add(($lib -replace "\\", "/"))
}

$cp = $cpList -join ";"

Write-Host "[1/3] Temizleniyor..." -ForegroundColor Cyan
if (Test-Path "build") { Remove-Item -Recurse -Force "build" }
New-Item -ItemType Directory -Force -Path "build\classes", "build\libs" | Out-Null

Write-Host "[2/3] Java dosyalari derleniyor (Zulu OpenJDK 25)..." -ForegroundColor Cyan
$javaFiles = Get-ChildItem -Recurse -Filter "*.java" "src\main\java" | Select-Object -ExpandProperty FullName
if ($javaFiles.Count -eq 0) {
    Write-Error "Derlenecek Java dosyasi bulunamadi!"
}

$argLines = [System.Collections.Generic.List[string]]::new()
$argLines.Add("-encoding")
$argLines.Add("UTF-8")
$argLines.Add("-cp")
$argLines.Add("`"$cp`"")
$argLines.Add("-d")
$argLines.Add("build/classes")

foreach ($jf in $javaFiles) {
    $argLines.Add("`"$($jf -replace "\\", "/")`"")
}

$argFile = "build/javac_args.txt"
$enc = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllLines((Join-Path (Get-Location) $argFile), $argLines, $enc)

& $javac "@$argFile"
if ($LASTEXITCODE -ne 0) {
    Write-Error "Derleme basarisiz oldu!"
}

Write-Host "[3/3] JAR paketi olusturuluyor..." -ForegroundColor Cyan
if (Test-Path "src\main\resources") {
    Copy-Item -Recurse "src\main\resources\*" "build\classes\"
}
& $jar -cf "build\libs\donutsmp-trader-1.0.0.jar" -C "build\classes" .

Write-Host "Basarili! Mod dosyasi: build\libs\donutsmp-trader-1.0.0.jar" -ForegroundColor Green