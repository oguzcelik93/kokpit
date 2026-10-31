@echo off
rem Kokpit - arac multimedyasini ADB ile hizlandirir. Hicbir sey silinmez.
rem   hizlandir.bat                     guvenli liste
rem   hizlandir.bat listeler\ek.txt     ek liste
rem Geri almak icin: geri-al.bat
chcp 65001 >nul
cd /d "%~dp0"
set LIST=listeler\guvenli.txt
if not "%~1"=="" set LIST=%~1
if not exist "%LIST%" ( echo Liste bulunamadi: %LIST% & pause & exit /b 1 )
call :need_adb || exit /b 1
adb push "%LIST%" /data/local/tmp/kokpit-liste.txt >nul
adb push kokpit-arac.sh /data/local/tmp/kokpit-arac.sh >nul
adb shell sh /data/local/tmp/kokpit-arac.sh hizlandir /data/local/tmp/kokpit-liste.txt
if not exist yedek mkdir yedek
adb pull /data/local/tmp/kokpit-yedek/. yedek\ >nul 2>&1 && echo Yedekler bilgisayara da alindi: %~dp0yedek
pause
exit /b 0

:need_adb
where adb >nul 2>&1 || ( echo adb bulunamadi. Platform Tools: https://developer.android.com/tools/releases/platform-tools & pause & exit /b 1 )
echo Cihaz bekleniyor...
adb wait-for-device
exit /b 0
