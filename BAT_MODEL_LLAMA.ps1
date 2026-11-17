param(
    [string]$LlamaDirectory = (Join-Path ([Environment]::GetFolderPath('UserProfile')) 'llama.cpp'),
    [int]$MainGpuLayers = 99,
    [int]$EvaluationGpuLayers = 0,
    [int]$StartupTimeoutSeconds = 240
)
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$fabricExecutable = Join-Path $LlamaDirectory 'bin/llama-server.exe'
$fabricModels = Join-Path $LlamaDirectory 'models'
$fabricLogs = Join-Path $LlamaDirectory 'logs'
if (-not (Test-Path -LiteralPath $fabricExecutable -PathType Leaf)) {
    throw "Missing llama-server.exe: $fabricExecutable"
}
if ($MainGpuLayers -lt 0 -or $EvaluationGpuLayers -lt 0 -or $StartupTimeoutSeconds -lt 1) {
    throw 'GPU layers must be >= 0 and startup timeout must be positive.'
}
$fabricRoles = @(
    @{ alias='gemma-main'; port=8081; file='gemma-3-4b-it-Q4_K_M.gguf'; layers=$MainGpuLayers },
    @{ alias='qwen-eval'; port=8082; file='Qwen2.5-7B-Instruct-Q4_K_M.gguf'; layers=$EvaluationGpuLayers }
)
# Validate both files before starting either process.
foreach ($fabricRole in $fabricRoles) {
    $fabricModelFile = Join-Path $fabricModels $fabricRole.file
    if (-not (Test-Path -LiteralPath $fabricModelFile -PathType Leaf)) { throw "Missing GGUF: $fabricModelFile" }
    $fabricStream = [IO.File]::OpenRead($fabricModelFile)
    try {
        $fabricMagic = New-Object byte[] 4
        if ($fabricStream.Read($fabricMagic, 0, 4) -ne 4 -or [Text.Encoding]::ASCII.GetString($fabricMagic) -ne 'GGUF') {
            throw "Not a valid GGUF header: $fabricModelFile"
        }
    } finally { $fabricStream.Dispose() }
}
[IO.Directory]::CreateDirectory($fabricLogs) | Out-Null
function Test-FabricModelReady([int]$Port, [string]$Alias) {
    try {
        $fabricHealth = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/health" -TimeoutSec 2
        if ($fabricHealth.status -ne 'ok') { return $false }
        $fabricList = Invoke-RestMethod -Uri "http://127.0.0.1:$Port/v1/models" -TimeoutSec 2
        return @($fabricList.data | Where-Object { $_.id -eq $Alias }).Count -gt 0
    } catch { return $false }
}
$fabricStarted = New-Object 'System.Collections.Generic.List[object]'
try {
    foreach ($fabricRole in $fabricRoles) {
        $fabricAlias = [string]$fabricRole.alias
        $fabricPort = [int]$fabricRole.port
        if (Test-FabricModelReady $fabricPort $fabricAlias) {
            Write-Host "Already ready: $fabricAlias on $fabricPort"
            continue
        }
        $fabricPidFile = Join-Path $fabricLogs ($fabricAlias + '.pid.json')
        $fabricProcess = $null
        if (Test-Path -LiteralPath $fabricPidFile) {
            try {
                $fabricOld = Get-Content -LiteralPath $fabricPidFile -Raw | ConvertFrom-Json
                $fabricCandidate = Get-Process -Id ([int]$fabricOld.processId) -ErrorAction SilentlyContinue
                if ($null -ne $fabricCandidate -and $fabricCandidate.Path -eq $fabricExecutable -and
                    ([string]$fabricCandidate.StartTime.ToUniversalTime().Ticks) -eq ([string]$fabricOld.startTicks)) {
                    $fabricProcess = $fabricCandidate
                }
            } catch { $fabricProcess = $null }
        }
        $fabricErrorLog = Join-Path $fabricLogs ($fabricAlias + '.err.log')
        if ($null -eq $fabricProcess) {
            $fabricArguments = '-m "{0}" --alias {1} --host 127.0.0.1 --port {2} --ctx-size 8192 --parallel 1 --n-gpu-layers {3} --jinja' -f
                (Join-Path $fabricModels $fabricRole.file), $fabricAlias, $fabricPort, $fabricRole.layers
            $fabricStartOptions = @{
                FilePath=$fabricExecutable; ArgumentList=$fabricArguments; PassThru=$true
                RedirectStandardOutput=(Join-Path $fabricLogs ($fabricAlias + '.out.log'))
                RedirectStandardError=$fabricErrorLog
            }
            if ($env:OS -eq 'Windows_NT') { $fabricStartOptions.WindowStyle = 'Hidden' }
            $fabricProcess = Start-Process @fabricStartOptions
            $fabricStarted.Add(@{ process=$fabricProcess; metadata=$fabricPidFile })
            $fabricStartTime = $fabricProcess.StartTime
            if ($null -eq $fabricStartTime) {
                if (Test-Path -LiteralPath $fabricErrorLog) { Get-Content -LiteralPath $fabricErrorLog -Tail 12 | Write-Host }
                throw "llama-server did not remain running for $fabricAlias. Check $fabricErrorLog"
            }
            [pscustomobject]@{
                processId=$fabricProcess.Id; executable=$fabricExecutable
                startTicks=([string]$fabricStartTime.ToUniversalTime().Ticks); alias=$fabricAlias; port=$fabricPort
            } | ConvertTo-Json | Set-Content -LiteralPath $fabricPidFile -Encoding UTF8
        }
        Write-Host "Loading $fabricAlias on $fabricPort. Log: $fabricErrorLog"
        $fabricDeadline = [DateTime]::UtcNow.AddSeconds($StartupTimeoutSeconds)
        $fabricReady = $false
        while ([DateTime]::UtcNow -lt $fabricDeadline) {
            if (Test-FabricModelReady $fabricPort $fabricAlias) { $fabricReady = $true; break }
            $fabricProcess.Refresh()
            if ($fabricProcess.HasExited) {
                if (Test-Path -LiteralPath $fabricErrorLog) { Get-Content -LiteralPath $fabricErrorLog -Tail 12 | Write-Host }
                throw "llama-server exited for $fabricAlias. Check the log and port $fabricPort."
            }
            Start-Sleep -Milliseconds 500
        }
        if (-not $fabricReady) { throw "Startup timeout for $fabricAlias. Read $fabricErrorLog" }
        Write-Host "Ready: $fabricAlias on $fabricPort"
    }
} catch {
    foreach ($fabricOwned in $fabricStarted) {
        if (-not $fabricOwned.process.HasExited) { Stop-Process -Id $fabricOwned.process.Id -ErrorAction SilentlyContinue }
        Remove-Item -LiteralPath $fabricOwned.metadata -Force -ErrorAction SilentlyContinue
    }
    throw
}
Write-Host 'Both model servers are ready. Run .\CHAY_APP_LLAMA.ps1 in the project folder.'
