@echo off
setlocal EnableDelayedExpansion
rem Furnace 시작 스크립트. 빌드가 없으면 installDist 로 만든다(테스트는 돌리지 않는다).
rem
rem JVM 옵션 지정 (뒤에 쓴 것이 이긴다):
rem   1) jvm.args 파일: 한 줄에 옵션 하나 (# 로 시작하는 줄은 주석)
rem   2) 환경변수 JAVA_OPTS
rem   3) 앞쪽 인자 중 -X, -D 로 시작하는 것은 JVM 옵션으로 간다 (그 뒤부터 서버 인자)
rem      예) start.bat -Xmx4G "-Dfurnace.sharedRegionPool=true" --nogui
rem      주의: cmd 는 = 를 구분자로 보므로 = 가 든 옵션은 큰따옴표로 감싼다.
rem -Xmx 를 아무 데서도 안 주면 기본 6G (set FURNACE_XMX=4G 로 기본값만 바꿀 수도 있다).
cd /d "%~dp0"
set APP=furnace-server\build\install\furnace-server
if not exist "%APP%\lib" (
  call gradlew.bat -PskipOracle=true :furnace-server:installDist || exit /b 1
)
if "%FURNACE_XMX%"=="" set FURNACE_XMX=6G

set "JVM_ARGS="
if exist jvm.args (
  for /f "usebackq eol=# tokens=* delims=" %%L in ("jvm.args") do set "JVM_ARGS=!JVM_ARGS! %%L"
)
if not "%JAVA_OPTS%"=="" set "JVM_ARGS=!JVM_ARGS! %JAVA_OPTS%"

:parse
set "A=%~1"
if "!A!"=="" goto run
if "!A:~0,2!"=="-X" goto jvmarg
if "!A:~0,2!"=="-D" goto jvmarg
goto run
:jvmarg
set "JVM_ARGS=!JVM_ARGS! "%~1""
shift
goto parse

:run
echo !JVM_ARGS! | find "-Xmx" >nul || set "JVM_ARGS=-Xmx%FURNACE_XMX% !JVM_ARGS!"
rem AppCDS: JDK 19 이상이면 클래스 공유 아카이브를 자동으로 만들고 다음 기동부터 쓴다. FURNACE_CDS=0 이면 끈다.
set "CDS_ARGS="
if not "%FURNACE_CDS%"=="0" (
  echo !JVM_ARGS! | find "SharedArchiveFile" >nul || echo !JVM_ARGS! | find "AutoCreateSharedArchive" >nul || echo !JVM_ARGS! | find "Xshare" >nul || call :cds_probe
)
java !CDS_ARGS! !JVM_ARGS! -cp "%APP%\lib\*" org.domi.furnace.server.FurnaceMain %*
exit /b %ERRORLEVEL%

:cds_probe
set "JVM_VER="
for /f "tokens=3" %%V in ('java -version 2^>^&1 ^| find "version"') do if not defined JVM_VER set "JVM_VER=%%~V"
set "JVM_MAJOR="
for /f "tokens=1 delims=." %%M in ("!JVM_VER!") do set "JVM_MAJOR=%%M"
if defined JVM_MAJOR if !JVM_MAJOR! GEQ 19 set "CDS_ARGS=-XX:+AutoCreateSharedArchive "-XX:SharedArchiveFile=%APP%\furnace.jsa""
exit /b 0
