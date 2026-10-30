$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
& (Join-Path $PSScriptRoot 'init-env.ps1')
$previous = @{}
$settings = @{
    APP_PORT = '5176'
    API_PORT = '8083'
    WS_ALLOWED_ORIGINS = 'http://localhost:5176,http://127.0.0.1:5176'
    BOOTSTRAP_ADMIN_ENABLED = 'true'
    DEMO_ENABLED = 'true'
}
Push-Location $projectRoot
try {
    foreach ($name in $settings.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $settings[$name], 'Process')
    }
    # Keep the existing demo containers and volumes; release its public ports.
    cmd.exe /d /c 'docker compose -p english-learning-main stop 2>&1'
    if ($LASTEXITCODE -ne 0) { throw 'Khong dung duoc demo cu; chua thay doi du lieu.' }
    cmd.exe /d /c 'docker compose -p english-learning-baseline up -d --build --wait 2>&1'
    if ($LASTEXITCODE -ne 0) {
        cmd.exe /d /c 'docker compose -p english-learning-baseline stop 2>&1'
        cmd.exe /d /c 'docker compose -p english-learning-main start 2>&1'
        throw 'Khoi dong baseline that bai; da thu khoi phuc demo cu.'
    }
    $ready = $false
    for ($attempt = 0; $attempt -lt 90; $attempt++) {
        $logs = cmd.exe /d /c 'docker compose -p english-learning-baseline logs --no-color server 2>&1'
        if (($logs -join "`n").Contains('M02 curriculum seed completed:')) { $ready = $true; break }
        Start-Sleep -Seconds 2
    }
    if (-not $ready) { throw 'Server healthy nhung chua xac nhan seed xong. Kiem tra logs baseline.' }
    Write-Output 'Mo http://localhost:5176 va dang nhap lai. Du lieu nen M02, khong nap script bo sung.'
    Write-Output 'Du lieu demo cu van o english-learning-main. Chay lai script nay khong xoa tien do test.'
} finally {
    foreach ($name in $previous.Keys) {
        [Environment]::SetEnvironmentVariable($name, $previous[$name], 'Process')
    }
    Pop-Location
}
