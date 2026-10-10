$ErrorActionPreference = 'Stop'
$venv = Join-Path $PSScriptRoot '.venv-tts'
$python = Join-Path $venv 'Scripts\python.exe'
if (!(Test-Path -LiteralPath $python)) {
  python -m venv $venv
  if ($LASTEXITCODE -ne 0) { throw '无法创建 Python 虚拟环境，请安装 Python 3.10+。' }
}
& $python -m pip install --disable-pip-version-check --timeout 120 --retries 4 -r (Join-Path $PSScriptRoot 'requirements-tts.txt')
if ($LASTEXITCODE -ne 0) { throw 'Edge TTS 依赖安装失败。' }
Write-Host 'Edge TTS 已安装。重新运行 .\run.ps1 后可在聊天页选择音色并朗读。'
