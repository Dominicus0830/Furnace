@echo off
rem Furnace 시작 스크립트. 빌드가 없으면 installDist 로 만든다. 기본 힙 -Xmx6G (바꾸려면 set FURNACE_XMX=4G).
cd /d "%~dp0"
set APP=furnace-server\build\install\furnace-server
if not exist "%APP%\lib" (
  call gradlew.bat -PskipOracle=true :furnace-server:installDist || exit /b 1
)
if "%FURNACE_XMX%"=="" set FURNACE_XMX=6G
java -Xmx%FURNACE_XMX% %JAVA_OPTS% -cp "%APP%\lib\*" org.domi.furnace.server.FurnaceMain %*
