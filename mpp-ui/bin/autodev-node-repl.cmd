@echo off
setlocal

set "ROOT=%~dp0.."
set "NODE_BIN=%NODE_REPL_NODE_PATH%"

if "%NODE_BIN%"=="" (
  if exist "%ROOT%\vendor\node\win32-x64\node.exe" set "NODE_BIN=%ROOT%\vendor\node\win32-x64\node.exe"
)

if "%NODE_BIN%"=="" set "NODE_BIN=node"

"%NODE_BIN%" --experimental-vm-modules "%ROOT%\dist\jsMain\typescript\node-repl\server.js" %*
