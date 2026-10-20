@echo off
REM ==============================================================================
REM TontOS Windows WSL2 Environment Validator Launcher
REM Runs WSL2 setup validator inside default WSL distribution
REM Maintainer: FalseThinks <paproken@protonmail.com>
REM ==============================================================================
wsl.exe bash -c "cd '%~dp0../..' && bash bin/windows/setup_wsl2_windows.sh"
pause
