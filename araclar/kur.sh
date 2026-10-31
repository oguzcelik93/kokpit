#!/usr/bin/env bash
# Kokpit · APK'yı kurar, ana ekran yapar ve hızlandırma iznini verir.
#   ./kur.sh kokpit93.apk
set -e
source "$(cd "$(dirname "$0")" && pwd)/ortak.sh"
APK="${1:?Kullanım: ./kur.sh <apk dosyası>}"
need_adb
adb install -r "$APK"
PKG=io.github.oguzcelik93.kokpit93
adb shell pm grant $PKG android.permission.WRITE_SECURE_SETTINGS </dev/null || true
adb shell cmd package set-home-activity $PKG/io.github.yorulmazsinan.kokpit.MainActivity </dev/null >/dev/null 2>&1 \
    && echo "Kokpit ana ekran yapıldı." || echo "Ana ekranı ev tuşu → Kokpit → Her zaman ile seçin."
adb shell am start -n $PKG/io.github.yorulmazsinan.kokpit.MainActivity </dev/null >/dev/null
echo "Kuruldu. Geri dönmek için: ./geri-al.sh --fabrika"
