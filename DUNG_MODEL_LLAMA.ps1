param([string]$LlamaDirectory = (Join-Path ([Environment]::GetFolderPath('UserProfile')) 'llama.cpp'))
Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$fabricExecutable = Join-Path $LlamaDirectory 'bin/llama-server.exe'
foreach ($fabricAlias in @('gemma-main','qwen-eval')) {
    $fabricMetadata = Join-Path $LlamaDirectory ('logs/' + $fabricAlias + '.pid.json')
    if (-not (Test-Path -LiteralPath $fabricMetadata)) { continue }
    try {
        $fabricRecord = Get-Content -LiteralPath $fabricMetadata -Raw | ConvertFrom-Json
        $fabricProcess = Get-Process -Id ([int]$fabricRecord.processId) -ErrorAction SilentlyContinue
        if ($null -eq $fabricProcess) {
            Remove-Item -LiteralPath $fabricMetadata -Force
            continue
        }
        if ($fabricProcess.Path -ne $fabricExecutable -or
            ([string]$fabricProcess.StartTime.ToUniversalTime().Ticks) -ne ([string]$fabricRecord.startTicks)) {
            Write-Warning "Process identity changed for $fabricAlias; it was not stopped."
            continue
        }
        Stop-Process -Id $fabricProcess.Id
        Remove-Item -LiteralPath $fabricMetadata -Force
        Write-Host "Stopped: $fabricAlias"
    } catch { Write-Warning $_.Exception.Message }
}
