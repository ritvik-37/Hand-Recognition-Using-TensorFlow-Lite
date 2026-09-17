@echo off
REM Starts the Python gesture server. Run this before launching SignClient.
setlocal
cd /d "%~dp0"

if not exist ".venv\Scripts\python.exe" (
    echo [ERROR] No virtual environment found at .venv
    echo Create one with a Python 3.11 or 3.12 interpreter, then install the deps:
    echo     py -3.11 -m venv .venv
    echo     .venv\Scripts\python -m pip install -r requirements.txt
    exit /b 1
)

echo Starting gesture server...
".venv\Scripts\python.exe" server.py
