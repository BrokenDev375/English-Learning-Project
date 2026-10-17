$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$envPath = Join-Path $projectRoot '.env'
if (-not (Test-Path -LiteralPath $envPath)) {
    $template = [IO.File]::ReadAllText((Join-Path $projectRoot '.env.example'))
    $template = $template.Replace('local-app-change-me', [Guid]::NewGuid().ToString('N'))
    $template = $template.Replace('local-root-change-me', [Guid]::NewGuid().ToString('N'))
    $template = $template.Replace('generate-admin-password', 'Admin@' + [Guid]::NewGuid().ToString('N'))
    [IO.File]::WriteAllText($envPath, $template, (New-Object Text.UTF8Encoding($false)))
    Write-Output 'Da tao .env. Mat khau admin ban dau nam trong BOOTSTRAP_ADMIN_PASSWORD.'
}
