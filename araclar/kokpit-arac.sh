#!/system/bin/sh
# Kokpit · cihaz tarafı hızlandırma / geri alma betiği.
# Bilgisayardaki hizlandir.* / geri-al.* bunu /data/local/tmp'ye kopyalayıp çalıştırır.
# Yedekler cihazda da tutulur (/data/local/tmp/kokpit-yedek), bilgisayardaki yedek
# kaybolsa bile geri alınabilir.
#
#   sh kokpit-arac.sh hizlandir <liste>
#   sh kokpit-arac.sh geri-al [yedek-klasörü]     son çalıştırmayı geri alır
#   sh kokpit-arac.sh geri-al-hepsi               tüm çalıştırmaları geri alır
#   sh kokpit-arac.sh fabrika                     geri-al-hepsi + üreticinin ana ekranı

KOKPIT=io.github.oguzcelik93.kokpit93
BASE=/data/local/tmp/kokpit-yedek
KEYS="window_animation_scale transition_animation_scale animator_duration_scale wifi_scan_always_enabled ble_scan_always_enabled"

clean() { echo "$1" | sed 's/#.*//; s/[[:space:]]//g'; }

hizlandir() {
    list=$1
    [ -f "$list" ] || { echo "Liste bulunamadı: $list"; exit 1; }
    stamp=$(date +%Y%m%d-%H%M%S)
    bk=$BASE/$stamp
    mkdir -p "$bk" || exit 1
    {
        echo "model=$(getprop ro.product.model)"
        echo "android=$(getprop ro.build.version.release)"
        echo "platform=$(getprop ro.board.platform)"
    } > "$bk/cihaz.txt"
    pm list packages -d | sed 's/^package://; s/[[:space:]]//g' > "$bk/once-kapali.txt"
    pm list packages | sed 's/^package://; s/[[:space:]]//g' > "$bk/paketler.txt"
    : > "$bk/ayarlar.txt"
    for k in $KEYS; do echo "$k=$(settings get global $k)" >> "$bk/ayarlar.txt"; done
    cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null \
        | tail -n 1 | sed 's/[[:space:]]//g' > "$bk/ana-ekran.txt"
    : > "$bk/kapatilanlar.txt"

    echo "Gereksiz uygulamalar kapatılıyor:"
    while IFS= read -r line || [ -n "$line" ]; do
        pkg=$(clean "$line")
        [ -z "$pkg" ] && continue
        if ! grep -qx "$pkg" "$bk/paketler.txt"; then echo "   -  $pkg (cihazda yok)"; continue; fi
        if grep -qx "$pkg" "$bk/once-kapali.txt"; then echo "   =  $pkg (zaten kapalı)"; continue; fi
        if pm disable-user --user 0 "$pkg" 2>&1 | grep -q disabled; then
            echo "$pkg" >> "$bk/kapatilanlar.txt"
            echo "   OK $pkg"
        else
            echo "   !! $pkg kapatılamadı (korumalı sistem paketi)"
        fi
    done < "$list"

    echo "Animasyonlar hızlandırılıyor (0.5x), arka plan taramaları kapatılıyor."
    settings put global window_animation_scale 0.5
    settings put global transition_animation_scale 0.5
    settings put global animator_duration_scale 0.5
    settings put global wifi_scan_always_enabled 0
    settings put global ble_scan_always_enabled 0

    if grep -qx "$KOKPIT" "$bk/paketler.txt"; then
        echo "Kokpit: animasyon izni, uyku muafiyeti, önceden derleme."
        pm grant "$KOKPIT" android.permission.WRITE_SECURE_SETTINGS 2>/dev/null
        dumpsys deviceidle whitelist +"$KOKPIT" >/dev/null 2>&1
        cmd package compile -m speed -f "$KOKPIT" >/dev/null 2>&1
    fi
    echo "$stamp" > "$BASE/son.txt"
    echo ""
    echo "Bitti. $(wc -l < "$bk/kapatilanlar.txt" | tr -d ' ') uygulama kapatıldı. Cihaz yedeği: $bk"
    echo "Geri almak için: geri-al.sh (ya da geri-al.bat)"
}

restore_settings() {
    [ -f "$1/ayarlar.txt" ] || return
    while IFS='=' read -r k v || [ -n "$k" ]; do
        [ -z "$k" ] && continue
        v=$(clean "$v")
        if [ -z "$v" ] || [ "$v" = "null" ]; then settings delete global "$k" >/dev/null 2>&1; else settings put global "$k" "$v"; fi
    done < "$1/ayarlar.txt"
    echo "   Ayarlar geri yüklendi ($(basename "$1"))."
}

enable_from() {
    [ -f "$1/kapatilanlar.txt" ] || return
    while IFS= read -r pkg || [ -n "$pkg" ]; do
        pkg=$(clean "$pkg")
        [ -z "$pkg" ] && continue
        if pm enable "$pkg" >/dev/null 2>&1; then echo "   OK $pkg açıldı"; else echo "   !! $pkg açılamadı"; fi
    done < "$1/kapatilanlar.txt"
}

restore_home() {
    home=$(cat "$1/ana-ekran.txt" 2>/dev/null)
    case "$home" in
        */*) cmd package set-home-activity "$home" >/dev/null 2>&1 && echo "   Ana ekran: $home" ;;
    esac
}

geri_al() {
    bk=$1
    [ -z "$bk" ] && [ -f "$BASE/son.txt" ] && bk=$BASE/$(cat "$BASE/son.txt")
    [ -d "$bk" ] || { echo "Yedek bulunamadı. 'geri-al-hepsi' deneyin."; exit 1; }
    echo "Geri alınıyor: $bk"
    enable_from "$bk"
    restore_settings "$bk"
    restore_home "$bk"
}

geri_al_hepsi() {
    [ -d "$BASE" ] || { echo "Cihazda hiç yedek yok."; exit 1; }
    oldest=""
    for bk in $(ls -1 "$BASE" | grep -v '\.txt$' | sort -r); do
        echo "Geri alınıyor: $bk"
        enable_from "$BASE/$bk"
        oldest=$BASE/$bk
    done
    # En eski yedek, hiçbir şey değişmeden önceki ayarları tutar.
    [ -n "$oldest" ] && restore_settings "$oldest" && restore_home "$oldest"
    dumpsys deviceidle whitelist -"$KOKPIT" >/dev/null 2>&1
}

fabrika() {
    geri_al_hepsi
    vendor=$(cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME 2>/dev/null \
        | sed 's/[[:space:]]//g' | grep / | grep -v "^$KOKPIT/" | grep -v FallbackHome | head -n 1)
    if [ -n "$vendor" ]; then
        cmd package set-home-activity "$vendor" >/dev/null 2>&1 && echo "Üretici ana ekranı varsayılan yapıldı: $vendor"
        am start -a android.intent.action.MAIN -c android.intent.category.HOME -n "$vendor" >/dev/null 2>&1
    else
        echo "Üretici ana ekranı bulunamadı; ev tuşuna basıp seçin."
    fi
}

case "$1" in
    hizlandir) hizlandir "$2" ;;
    geri-al) geri_al "$2" ;;
    geri-al-hepsi) geri_al_hepsi ;;
    fabrika) fabrika ;;
    *) echo "Kullanım: sh kokpit-arac.sh hizlandir <liste> | geri-al [yedek] | geri-al-hepsi | fabrika"; exit 1 ;;
esac
