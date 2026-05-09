[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$ApiKey,

    [string]$BaseUrl = 'https://api.longcat.chat/openai',

    [string]$ChatModel = 'LongCat-Flash-Thinking-2601',

    [string]$Profile = 'local',

    [string]$DbHost = '127.0.0.1',

    [int]$DbPort = 3306,

    [string]$DbUsername = 'root',

    [string]$DbPassword = 'root',

    [string]$RedisHost = '127.0.0.1',

    [int]$RedisPort = 6379,

    [int]$RedisDatabase = 0,

    [string]$RabbitMqHost = '127.0.0.1',

    [int]$RabbitMqPort = 5672,

    [string]$RabbitMqUsername = 'guest',

    [string]$RabbitMqPassword = 'guest',

    [switch]$Wait
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$jarPath = Join-Path $repoRoot 'target\knowflow-backend-0.0.1-SNAPSHOT.jar'

if (-not (Test-Path $jarPath)) {
    throw "Jar not found: $jarPath. Run Maven package first."
}

$env:KNOWFLOW_LLM_OPENAI_ENABLED = 'true'
$env:KNOWFLOW_LLM_OPENAI_BASE_URL = $BaseUrl
$env:KNOWFLOW_LLM_OPENAI_API_KEY = $ApiKey
$env:KNOWFLOW_LLM_OPENAI_CHAT_MODEL = $ChatModel
$env:KNOWFLOW_DB_HOST = $DbHost
$env:KNOWFLOW_DB_PORT = [string]$DbPort
$env:KNOWFLOW_DB_USERNAME = $DbUsername
$env:KNOWFLOW_DB_PASSWORD = $DbPassword
$env:KNOWFLOW_REDIS_HOST = $RedisHost
$env:KNOWFLOW_REDIS_PORT = [string]$RedisPort
$env:KNOWFLOW_REDIS_DATABASE = [string]$RedisDatabase
$env:KNOWFLOW_RABBITMQ_HOST = $RabbitMqHost
$env:KNOWFLOW_RABBITMQ_PORT = [string]$RabbitMqPort
$env:KNOWFLOW_RABBITMQ_USERNAME = $RabbitMqUsername
$env:KNOWFLOW_RABBITMQ_PASSWORD = $RabbitMqPassword

$process = Start-Process -FilePath 'java' `
    -ArgumentList '-jar', $jarPath, "--spring.profiles.active=$Profile" `
    -WorkingDirectory $repoRoot `
    -PassThru

Write-Output ("Started KnowFlow backend. PID={0}" -f $process.Id)
Write-Output ("Profile={0}" -f $Profile)
Write-Output ("Model={0}" -f $ChatModel)
Write-Output ("DB={0}:{1}/{2}" -f $DbHost, $DbPort, 'knowflow')
Write-Output ("Redis={0}:{1}/{2}" -f $RedisHost, $RedisPort, $RedisDatabase)
Write-Output ("RabbitMQ={0}:{1}" -f $RabbitMqHost, $RabbitMqPort)

if ($Wait) {
    Wait-Process -Id $process.Id
}
