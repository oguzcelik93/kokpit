#!/usr/bin/env bash
# Kokpit · araç multimedyasını ADB ile hızlandırır. Root gerekmez, hiçbir şey silinmez.
#   ./hizlandir.sh                      güvenli liste
#   ./hizlandir.sh listeler/ek.txt      ek liste
# Geri almak için: ./geri-al.sh
set -e
source "$(cd "$(dirname "$0")" && pwd)/ortak.sh"
LIST="${1:-$DIR/listeler/guvenli.txt}"
[ -f "$LIST" ] || { echo "Liste bulunamadı: $LIST"; exit 1; }
need_adb
adb push "$LIST" "$REMOTE/kokpit-liste.txt" >/dev/null
run_tool hizlandir "$REMOTE/kokpit-liste.txt"
pull_backups
