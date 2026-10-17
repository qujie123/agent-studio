@echo off
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0scripts\prepare-python-preview.ps1"
if errorlevel 1 pause
