$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'init-env.ps1')
$previous = @{}
$settings = @{
    APP_PORT = '5176'
    API_PORT = '8083'
    WS_ALLOWED_ORIGINS = 'http://localhost:5176,http://127.0.0.1:5176'
}
Push-Location $projectRoot
try {
    cmd.exe /d /c 'docker info --format "{{.ServerVersion}}" 2>nul' *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Hay mo Docker Desktop va doi engine chay, sau do chay lai start-main.cmd.' }
    foreach ($name in $settings.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
    }
    # Merge native stderr before PowerShell sees it: Compose progress uses stderr
    # even on success, which otherwise becomes a terminating error when redirected.
    cmd.exe /d /c 'docker compose -p english-learning-main up -d --build --wait 2>&1'
    if ($LASTEXITCODE -ne 0) { throw 'Khoi dong chua thanh cong. Xem docker compose -p english-learning-main logs.' }
    Write-Output 'Mo giao dien: http://localhost:5176'
    Write-Output 'Du lieu dung volume cua english-learning-main. Huong dan demo M05 nam trong README.md.'
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
    Pop-Location
}
