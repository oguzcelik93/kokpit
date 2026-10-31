@echo off
rem Kokpit - APK'yi kurar, ana ekran yapar, hizlandirma iznini verir.
rem   kur.bat kokpit93.apk
chcp 65001 >nul
cd /d "%~dp0"
if "%~1"=="" ( echo Kullanim: kur.bat ^<apk dosyasi^> & pause & exit /b 1 )
where adb >nul 2>&1 || ( echo adb bulunamadi. & pause & exit /b 1 )
adb wait-for-device
adb install -r "%~1"
adb shell pm grant io.github.oguzcelik93.kokpit93 android.permission.WRITE_SECURE_SETTINGS
adb shell cmd package set-home-activity io.github.oguzcelik93.kokpit93/io.github.yorulmazsinan.kokpit.MainActivity
adb shell am start -n io.github.oguzcelik93.kokpit93/io.github.yorulmazsinan.kokpit.MainActivity
echo Kuruldu. Geri donmek icin: geri-al.bat --fabrika
pause
