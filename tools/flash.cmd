@echo off
rem Build and flash the ESP32 room node, then show its logs.
rem Usage:  tools\flash.cmd           (asks which port)
rem         tools\flash.cmd COM10     (first flash over USB)
rem         tools\flash.cmd OTA       (later updates over Wi-Fi)

rem An empty git repo in the user folder (%USERPROFILE%\.git) breaks ESP-IDF's version lookup,
rem so stop git from searching up into it. Harmless if there's no such repo.
set GIT_CEILING_DIRECTORIES=%USERPROFILE%
set MSYSTEM=

set ROOT=%~dp0..
if "%~1"=="" (
  "%ROOT%\.venv\Scripts\esphome.exe" run "%ROOT%\esphome\room-node.yaml"
) else (
  "%ROOT%\.venv\Scripts\esphome.exe" run "%ROOT%\esphome\room-node.yaml" --device %1
)
