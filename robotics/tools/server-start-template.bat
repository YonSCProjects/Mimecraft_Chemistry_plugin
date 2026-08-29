@echo off
setlocal enabledelayedexpansion
REM ---------------------------------------------------------------------------
REM  RoboCraft dev server - Paper 26.2 / Minecraft 26.2
REM
REM    Game port : 25566      Join with:  localhost:25566
REM    RCON port : 25575      (the port a Claude Code Minecraft bridge uses)
REM
REM  Runs ALONGSIDE the ChemCraft server at C:\26.2_ChemCraft - the two plugins
REM  must never share a world, but they are meant to run at the same time so a
REM  class can be split across both. Separate folders, separate ports, no clash.
REM
REM  PORTABLE: this script finds Java 25 rather than hardcoding a path, so the
REM  whole folder can be copied to another PC. See MOVING.txt.
REM ---------------------------------------------------------------------------

cd /d "%~dp0"

set "JAVA="
REM 1. a JDK bundled beside this script - makes the folder completely self-contained
call :try "%~dp0jdk\bin\java.exe"
REM 2. a JDK shared by both servers at a fixed path - the easiest thing to replicate
call :try "C:\26.2_jdk25\bin\java.exe"
REM 3. whatever this machine already has
if defined JAVA_HOME call :try "%JAVA_HOME%\bin\java.exe"
for %%J in (java.exe) do if not "%%~$PATH:J"=="" call :try "%%~$PATH:J"
for /d %%D in ("%ProgramFiles%\Eclipse Adoptium\jdk-25*")  do call :try "%%D\bin\java.exe"
for /d %%D in ("%ProgramFiles%\Java\jdk-25*")              do call :try "%%D\bin\java.exe"
for /d %%D in ("%ProgramFiles%\Microsoft\jdk-25*")         do call :try "%%D\bin\java.exe"
REM 4. the JDK Gradle provisions for the -Pmc=26.2 build, on a dev machine
for /d %%D in ("%USERPROFILE%\.gradle\jdks\*25*")          do call :try "%%D\bin\java.exe"

if not defined JAVA goto :nojava

echo Using Java: !JAVA!
echo Starting RoboCraft... students join  localhost:25566
echo Type  stop  to shut down cleanly.
echo.
"!JAVA!" -Xms1G -Xmx2G -XX:+UseG1GC -jar paper.jar --nogui
echo.
echo Server stopped.
pause
exit /b 0

REM --- take the first candidate that really reports version 25 ----------------
REM  The quotes in  version "25.0.4"  cannot be escaped inside findstr /c:"...",
REM  so strip every quote from the line first and match plain text.
:try
if defined JAVA exit /b
if not exist "%~1" exit /b
"%~1" -version 2>"%TEMP%\rc_javaver.txt"
set "VLINE="
for /f "usebackq delims=" %%L in ("%TEMP%\rc_javaver.txt") do if not defined VLINE set "VLINE=%%L"
if not defined VLINE exit /b
set "VLINE=!VLINE:"=!"
echo !VLINE! | findstr /c:"version 25." >nul 2>&1 && set "JAVA=%~1"
exit /b

:nojava
echo.
echo   No Java 25 found, and Paper 26.2 will not start on anything older.
echo.
echo   Easiest fix: put a Java 25 JDK in either of these and this script will
echo   pick it up with no editing:
echo.
echo       %~dp0jdk\           ^(just this server^)
echo       C:\26.2_jdk25\      ^(shared by both servers^)
echo.
echo   so that  C:\26.2_jdk25\bin\java.exe  exists.
echo   Or install Adoptium Temurin 25 normally and it will be found on PATH.
echo.
pause
exit /b 1
