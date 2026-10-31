#!/usr/bin/env bash
# Kokpit · hizlandir.sh'nin yaptıklarını geri alır.
#   ./geri-al.sh            son çalıştırmayı geri alır
#   ./geri-al.sh --hepsi    tüm çalıştırmaları geri alır
#   ./geri-al.sh --fabrika  hepsini geri alır ve üreticinin ana ekranını varsayılan yapar
#   ./geri-al.sh --kaldir   --fabrika + Kokpit'i kaldırır
set -e
source "$(cd "$(dirname "$0")" && pwd)/ortak.sh"
need_adb
case "${1:-}" in
    --hepsi) run_tool geri-al-hepsi ;;
    --fabrika) run_tool fabrika ;;
    --kaldir) run_tool fabrika; adb uninstall io.github.oguzcelik93.kokpit93 || true ;;
    "") run_tool geri-al ;;
    *) echo "Bilinmeyen seçenek: $1"; exit 1 ;;
esac
