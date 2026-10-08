param([string]$Name)
$ErrorActionPreference = 'Stop'
if (!$Name) { $Name = Read-Host '要授权为管理员的现有账号昵称' }
if ([string]::IsNullOrWhiteSpace($Name)) { throw '昵称不能为空。' }

$configPath = Join-Path $PSScriptRoot '.env.local'
if (!(Test-Path -LiteralPath $configPath)) { throw '缺少 .env.local，请先配置本地数据库。' }
$config = @{}
foreach ($line in [IO.File]::ReadAllLines($configPath)) {
  if ($line -match '^([A-Z_]+)=(.*)$') { $config[$Matches[1]] = $Matches[2] }
}
if (!$config.DB_USER -or !$config.DB_PASSWORD) { throw '数据库账号配置不完整。' }
$hexName = [Convert]::ToHexString([Text.Encoding]::UTF8.GetBytes($Name))
$env:MYSQL_PWD = $config.DB_PASSWORD
try {
  $count = & mysql.exe --no-defaults -N -h 127.0.0.1 -u $config.DB_USER aquantancee --execute="SELECT COUNT(*) FROM users WHERE name=CONVERT(0x$hexName USING utf8mb4)"
  if ($LASTEXITCODE -ne 0) { throw '连接数据库失败。' }
  if (($count | Select-Object -First 1) -ne '1') { throw '没有找到该昵称对应的账号。' }
  & mysql.exe --no-defaults -h 127.0.0.1 -u $config.DB_USER aquantancee --execute="INSERT IGNORE INTO admin_users(user_id) SELECT id FROM users WHERE name=CONVERT(0x$hexName USING utf8mb4)"
  if ($LASTEXITCODE -ne 0) { throw '授权失败。' }
  Write-Host "已授予 $Name 管理员权限。请刷新网页。"
} finally {
  Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
}
