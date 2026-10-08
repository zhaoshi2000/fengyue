param(
  [string]$MysqlHost = '127.0.0.1',
  [int]$MysqlPort = 3306,
  [string]$RootUser = 'root'
)

$ErrorActionPreference = 'Stop'
$mysqlExe = (Get-Command mysql.exe -ErrorAction Stop).Source
$rootPassword = Read-Host '请输入本地 MySQL 管理员密码' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($rootPassword)
try {
  $rootPlain = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
} finally {
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
}

$appPassword = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24)).ToLowerInvariant()
$sql = @"
CREATE DATABASE IF NOT EXISTS aquantancee CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER IF NOT EXISTS 'aquantancee'@'localhost' IDENTIFIED BY '$appPassword';
CREATE USER IF NOT EXISTS 'aquantancee'@'127.0.0.1' IDENTIFIED BY '$appPassword';
ALTER USER 'aquantancee'@'localhost' IDENTIFIED BY '$appPassword';
ALTER USER 'aquantancee'@'127.0.0.1' IDENTIFIED BY '$appPassword';
GRANT ALL PRIVILEGES ON aquantancee.* TO 'aquantancee'@'localhost';
GRANT ALL PRIVILEGES ON aquantancee.* TO 'aquantancee'@'127.0.0.1';
"@

try {
  $env:MYSQL_PWD = $rootPlain
  & $mysqlExe --no-defaults --connect-timeout=5 -h $MysqlHost -P $MysqlPort -u $RootUser --execute=$sql
  if ($LASTEXITCODE -ne 0) { throw 'MySQL 数据库或项目账号创建失败' }
} finally {
  Remove-Item Env:MYSQL_PWD -ErrorAction SilentlyContinue
  $rootPlain = $null
}

$config = @(
  "DB_URL=jdbc:mysql://${MysqlHost}:${MysqlPort}/aquantancee?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai",
  'DB_USER=aquantancee',
  "DB_PASSWORD=$appPassword"
)
[IO.File]::WriteAllLines((Join-Path $PSScriptRoot '.env.local'), $config, [Text.UTF8Encoding]::new($false))
Write-Host '本地数据库和项目专用账号已配置；连接信息保存在忽略提交的 .env.local。'
