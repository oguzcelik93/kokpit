# hizlandir.sh / geri-al.sh / kur.sh için ortak yardımcılar (doğrudan çalıştırılmaz).
DIR="$(cd "$(dirname "$0")" && pwd)"
REMOTE=/data/local/tmp

need_adb() {
    if ! command -v adb >/dev/null 2>&1; then
        echo "adb bulunamadı. Android Platform Tools'u kurun: https://developer.android.com/tools/releases/platform-tools"
        exit 1
    fi
    echo "Cihaz bekleniyor (USB hata ayıklama ya da 'adb connect <ip>:5555')..."
    adb wait-for-device
    echo "Bağlı: $(adb shell getprop ro.product.model </dev/null | tr -d '\r') · Android $(adb shell getprop ro.build.version.release </dev/null | tr -d '\r')"
}

run_tool() {
    adb push "$DIR/kokpit-arac.sh" "$REMOTE/kokpit-arac.sh" >/dev/null
    adb shell sh "$REMOTE/kokpit-arac.sh" "$@" </dev/null | tr -d '\r'
}

pull_backups() {
    mkdir -p "$DIR/yedek"
    adb pull "$REMOTE/kokpit-yedek/." "$DIR/yedek/" >/dev/null 2>&1 && echo "Yedekler bilgisayara da alındı: $DIR/yedek"
}
