$ErrorActionPreference = 'Stop'
$secureKey = Read-Host '请输入模型 API Key' -AsSecureString
if ($secureKey.Length -eq 0) {
  throw 'API Key 不能为空。'
}
$keyPath = Join-Path $PSScriptRoot '.ai-key.dpapi'
$encrypted = ConvertFrom-SecureString -SecureString $secureKey
[IO.File]::WriteAllText($keyPath, $encrypted, [Text.UTF8Encoding]::new($false))
Write-Host 'API Key 已使用当前 Windows 用户的 DPAPI 加密保存。'
