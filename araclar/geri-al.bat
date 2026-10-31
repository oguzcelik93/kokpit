@echo off
rem Kokpit - hizlandir.bat'in yaptiklarini geri alir.
rem   geri-al.bat            son calistirmayi geri alir
rem   geri-al.bat --hepsi    tum calistirmalari geri alir
rem   geri-al.bat --fabrika  hepsini geri alir + ureticinin ana ekrani
rem   geri-al.bat --kaldir   --fabrika + Kokpit'i kaldirir
chcp 65001 >nul
cd /d "%~dp0"
where adb >nul 2>&1 || ( echo adb bulunamadi. & pause & exit /b 1 )
echo Cihaz bekleniyor...
adb wait-for-device
adb push kokpit-arac.sh /data/local/tmp/kokpit-arac.sh >nul
set MODE=geri-al
if "%~1"=="--hepsi" set MODE=geri-al-hepsi
if "%~1"=="--fabrika" set MODE=fabrika
if "%~1"=="--kaldir" set MODE=fabrika
adb shell sh /data/local/tmp/kokpit-arac.sh %MODE%
if "%~1"=="--kaldir" adb uninstall io.github.oguzcelik93.kokpit93
pause
