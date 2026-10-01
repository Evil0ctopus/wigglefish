$ErrorActionPreference = "Stop"
$workspace = Split-Path -Parent $PSScriptRoot
$comfyRoot = Join-Path $PSScriptRoot "comfyui"
$python = Join-Path $comfyRoot ".venv\Scripts\python.exe"
$entryPoint = Join-Path $comfyRoot "main.py"

if (!(Test-Path $python) -or !(Test-Path $entryPoint)) {
    throw "ComfyUI is not installed under tools/comfyui."
}

& $python $entryPoint --listen 127.0.0.1 --port 8188 --disable-auto-launch --preview-method auto
