#!/usr/bin/env bash
# Modu Modrinth profiline kurar. Minecraft KAPALI olmali.
set -euo pipefail

PROFILE="${1:-Fabric 26.2}"
MODS="$HOME/Library/Application Support/ModrinthApp/profiles/$PROFILE/mods"
# Surum gradle.properties'ten geliyor; burada tekrar yazmak ikisini ayirir.
JAR="$(ls -t "$(dirname "$0")"/build/libs/donutsmp-trader-*.jar 2>/dev/null | grep -v -- '-sources' | head -1)"

[ -n "$JAR" ] && [ -f "$JAR" ] || { echo "Once derleyin: ./gradlew build"; exit 1; }
[ -d "$MODS" ] || { echo "Profil bulunamadi: $MODS"; exit 1; }

if pgrep -f "net.fabricmc.loader" > /dev/null; then
    echo "Minecraft acik. Jar'i calisan oyunun altinda degistirmek oyunu cokertir; once kapatin."
    exit 1
fi

cp "$JAR" "$MODS/"
echo "Kuruldu: $MODS/$(basename "$JAR")"
