$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'init-env.ps1')
Push-Location $projectRoot
try {
    docker info --format '{{.ServerVersion}}' *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Hay mo Docker Desktop va doi engine chay, sau do chay lai start.cmd.' }
    docker compose up -d --build --wait
    if ($LASTEXITCODE -ne 0) { throw 'Khoi dong chua thanh cong. Xem docker compose logs.' }
    $port = 5173
    $portLine = Get-Content -LiteralPath '.env' | Where-Object { $_ -match '^APP_PORT=' } | Select-Object -First 1
    if ($portLine) { $port = $portLine.Split('=', 2)[1] }
    Write-Output "Mo giao dien: http://localhost:$port"
    Write-Output 'Tai khoan demo va huong dan nam trong README.md / .env.'
} finally { Pop-Location }
