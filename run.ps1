$ErrorActionPreference = 'Stop'
$configPath = Join-Path $PSScriptRoot '.env.local'
if (!(Test-Path -LiteralPath $configPath)) {
  throw '缺少 .env.local；先运行 .\setup-local-db.ps1。'
}
foreach ($line in [IO.File]::ReadAllLines($configPath)) {
  if ($line -match '^([A-Z_]+)=(.*)$') {
    [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
  }
}
$keyPath = Join-Path $PSScriptRoot '.ai-key.dpapi'
if (Test-Path -LiteralPath $keyPath) {
  $secureKey = ConvertTo-SecureString -String ([IO.File]::ReadAllText($keyPath).Trim())
  $keyPtr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureKey)
  try {
    $env:AI_API_KEY = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($keyPtr)
  } finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($keyPtr)
  }
}
$env:JAVA_HOME = 'D:\dev\jdk-21'
if (!(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
  throw '未找到 Java 21，请修改 run.ps1 中的 JAVA_HOME。'
}
$ttsPython = Join-Path $PSScriptRoot '.venv-tts\Scripts\python.exe'
if (Test-Path -LiteralPath $ttsPython) {
  $env:TTS_PYTHON = $ttsPython
}
Push-Location $PSScriptRoot
try { & mvn.cmd spring-boot:run } finally { Pop-Location }
