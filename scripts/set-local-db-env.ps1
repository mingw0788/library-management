param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('dev', 'test')]
    [string]$Profile
)

$ErrorActionPreference = 'Stop'
$configPath = Join-Path $PSScriptRoot '..\.local\mysql-connection.json'
if (-not (Test-Path -LiteralPath $configPath)) {
    throw "本机数据库配置不存在：$configPath。请复制 config/mysql-connection.example.json 到 .local/mysql-connection.json 并填写专用账号。"
}

$config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$db = if ($Profile -eq 'dev') { $config.development } else { $config.test }
$hostValue = [string]$config.host
$portValue = [string]$config.port
if ([string]::IsNullOrWhiteSpace($hostValue) -or [string]::IsNullOrWhiteSpace($portValue) -or
    [string]::IsNullOrWhiteSpace([string]$db.username) -or [string]::IsNullOrWhiteSpace([string]$db.password)) {
    throw "配置文件中的 $Profile 数据库连接字段不完整；请补齐专用库账号和密码。"
}

if ($Profile -eq 'dev') {
    if ($db.database -ne 'library_dev') { throw '开发配置必须指向 library_dev，拒绝加载以防连接串混用。' }
    $env:LIBRARY_DB_HOST = $hostValue
    $env:LIBRARY_DB_PORT = $portValue
    $env:LIBRARY_DB_USERNAME = [string]$db.username
    $env:LIBRARY_DB_PASSWORD = [string]$db.password
} else {
    if ($db.database -ne 'library_test') { throw '测试配置必须指向 library_test，拒绝加载以防连接串混用。' }
    $env:LIBRARY_TEST_DB_HOST = $hostValue
    $env:LIBRARY_TEST_DB_PORT = $portValue
    $env:LIBRARY_TEST_DB_USERNAME = [string]$db.username
    $env:LIBRARY_TEST_DB_PASSWORD = [string]$db.password
}

Write-Host "已为当前 PowerShell 会话加载 $Profile 数据库连接（密码未显示）。"
