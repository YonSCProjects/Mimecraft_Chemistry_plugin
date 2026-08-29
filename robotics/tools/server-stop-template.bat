@echo off
REM ---------------------------------------------------------------------------
REM  Stop the RoboCraft server cleanly, without typing anything in its window.
REM  Double-click this, or run it from any prompt.
REM
REM  It sends the  stop  command over RCON (port 25575), which is exactly what
REM  typing  stop  in the server console does: save the world, unload plugins,
REM  exit. Closing the server window with the X instead can lose the last few
REM  minutes of student progress.
REM ---------------------------------------------------------------------------
echo Stopping RoboCraft (rcon 25575)...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0rcon.ps1" -Port 25575 stop
if errorlevel 1 (
  echo.
  echo   Could not reach it. Either it is already stopped, or it is still
  echo   starting up - RCON only opens once the world has finished loading.
)
echo.
pause
