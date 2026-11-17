Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$env:AI_MODEL_PROVIDER = 'llamacpp'
$env:AI_MODEL_ENDPOINT = 'http://127.0.0.1:8081'
$env:AI_MODEL_NAME = 'gemma-main'
$env:EVAL_MODEL_PROVIDER = 'llamacpp'
$env:EVAL_MODEL_ENDPOINT = 'http://127.0.0.1:8082'
$env:EVAL_MODEL_NAME = 'qwen-eval'
$env:AI_MODELS_FILE = Join-Path $PSScriptRoot 'ai-models.json'
if ([string]::IsNullOrWhiteSpace($env:DATABASE_URL)) {
    $fabricDatabase = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot 'fabric.db')).Path.Replace('\','/')
    $env:DATABASE_URL = 'jdbc:sqlite:' + $fabricDatabase
}
Push-Location $PSScriptRoot
try { & '.\mvnw.cmd' spring-boot:run }
finally { Pop-Location }
