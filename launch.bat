# TF2 x Minecraft Passthrough — Quick Launch
# Double-click to start everything

@echo off
setlocal

set RELAY_PORT=25600

echo =========================================
echo  TF2 x Minecraft Passthrough
echo =========================================
echo.

REM Check Titanfall 2
if not exist "W:\SteamLibrary\steamapps\titanfall2\Titanfall2.exe" (
    echo ERROR: Titanfall 2 not found.
    echo Please verify your Steam library location.
    pause
    exit /b 1
)

REM Check Northstar mod folder
if not exist "%USERPROFILE%\AppData\Roaming\Northstar\mods\tf2xmc.Passthrough" (
    echo WARNING: TF2 mod not found in Northstar mods folder.
    echo Please copy f2mod/ to:
    echo   %%USERPROFILE%%\AppData\Roaming\Northstar\mods\tf2xmc.Passthrough\
    echo.
)

echo Starting Minecraft...
start "" "C:\Users\13347\AppData\Local\Programs\Modrinth\ModrinthApp.exe"
timeout /t 3 /nobreak >nul

echo Starting Titanfall 2 via FlightCore...
start "" "C:\Program Files\FlightCore\FlightCore.exe"

echo.
echo Both games launched.
echo.
echo   - Minecraft: open/create a world
echo   - Titanfall 2: Esc -> Mods -> tf2xmc.Passthrough -> Settings
echo   - Run 'tf2xmc_status' in TF2 console to check connection
echo.
echo The relay runs embedded in the Minecraft mod on port 25600.
echo.
pause