@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not defined ANDROID_SDK_ROOT set "ANDROID_SDK_ROOT=%LOCALAPPDATA%\Android\Sdk"
if not defined JAVA_HOME if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
python build-apk.py
if errorlevel 1 (
  echo Build failed. Read the error above. Python 3, JDK 17 and Android SDK 35 are required.
) else (
  echo APK created. See the build output for its path.
  explorer "%~dp0"
)
pause
