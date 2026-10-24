@echo off
REM ==============================================================================
REM TontOS Windows CMD Launcher
REM Invokes PowerShell build engine with ExecutionPolicy Bypass
REM Maintainer: FalseThinks <paproken@protonmail.com>
REM ==============================================================================
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0docker_build_rom.ps1" %*
