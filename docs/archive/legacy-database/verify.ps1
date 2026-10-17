param(
    [string]$Image = 'mysql:8.4',
    [string]$ResultPath = (Join-Path $PSScriptRoot 'validation.json')
)
$ErrorActionPreference = 'Stop'
function Invoke-DockerResult {
    param([string[]]$DockerArgs)
    $previousPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        $nativeOutput = & docker @DockerArgs 2>&1
        $nativeExit = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $previousPreference
    }
    [pscustomobject]@{ ExitCode = $nativeExit; Output = (($nativeOutput | ForEach-Object { "$_" }) -join [Environment]::NewLine) }
}
function Invoke-DockerChecked {
    param([string[]]$DockerArgs)
    $result = Invoke-DockerResult -DockerArgs $DockerArgs
    if ($result.ExitCode -ne 0) { throw $result.Output }
    $result.Output
}
$runId = [Guid]::NewGuid().ToString('N')
$containerName = 'ielts-db-check-' + $runId.Substring(0,12)
$containerCreated = $false
$schemaPath = Join-Path $PSScriptRoot 'schema.mysql.sql'
$catalogPath = Join-Path $PSScriptRoot 'catalog-seed.sql'
$checksPath = Join-Path $PSScriptRoot 'verify-constraints.sql'
foreach ($filePath in @($schemaPath,$catalogPath,$checksPath)) {
    if (-not (Test-Path -LiteralPath $filePath)) { throw "Missing: $filePath" }
}
$expectedChecks = [regex]::Matches([IO.File]::ReadAllText($checksPath), '(?m)^CALL assert_(?:true|rejected)\(').Count
try {
    Invoke-DockerChecked -DockerArgs @('run','--rm','-d','--name',$containerName,
        '--label',('ielts.design-run=' + $runId),'--network','none',
        '--tmpfs','/var/lib/mysql:rw','--memory','768m',
        '--env','MYSQL_ALLOW_EMPTY_PASSWORD=yes','--env','MYSQL_DATABASE=ielts_schema_check',
        $Image,'--character-set-server=utf8mb4','--collation-server=utf8mb4_0900_ai_ci') | Out-Null
    $containerCreated = $true
    $ready = $false
    for ($poll = 0; $poll -lt 60; $poll++) {
        $probe = Invoke-DockerResult -DockerArgs @('exec',$containerName,'mysql','--host=127.0.0.1',
            '-uroot','--database=ielts_schema_check','--batch','--skip-column-names','--execute=SELECT 1')
        if ($probe.ExitCode -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) {
        $log = Invoke-DockerResult -DockerArgs @('logs','--tail','30',$containerName)
        throw ('MySQL readiness failed: ' + $probe.Output + [Environment]::NewLine + $log.Output)
    }
    foreach ($fileName in @('schema.mysql.sql','catalog-seed.sql','verify-constraints.sql')) {
        Invoke-DockerChecked -DockerArgs @('cp',(Join-Path $PSScriptRoot $fileName),($containerName + ':/tmp/' + $fileName)) | Out-Null
    }
    Invoke-DockerChecked -DockerArgs @('exec',$containerName,'sh','-c',
        'mysql -uroot --default-character-set=utf8mb4 --database=ielts_schema_check < /tmp/schema.mysql.sql') | Out-Null
    Invoke-DockerChecked -DockerArgs @('exec',$containerName,'sh','-c',
        'mysql -uroot --default-character-set=utf8mb4 --database=ielts_schema_check < /tmp/catalog-seed.sql') | Out-Null
    $checkOutput = Invoke-DockerChecked -DockerArgs @('exec',$containerName,'sh','-c',
        'mysql -uroot --default-character-set=utf8mb4 --database=ielts_schema_check --batch --skip-column-names < /tmp/verify-constraints.sql')
    $checkLines = @($checkOutput -split '\r?\n' | Where-Object { $_ -match '\tPASS$' })
    if ($checkLines.Count -ne $expectedChecks) { throw "Expected $expectedChecks passed checks, received $($checkLines.Count): $checkOutput" }
    $mysqlVersion = Invoke-DockerChecked -DockerArgs @('exec',$containerName,'mysql','-uroot',
        '--batch','--skip-column-names','--execute=SELECT VERSION()')
    $report = [ordered]@{
        testedAtUtc = [DateTime]::UtcNow.ToString('o')
        mysqlVersion = $mysqlVersion.Trim()
        schemaTables = 44
        passedChecks = $checkLines.Count
        checks = @($checkLines | ForEach-Object { ($_ -split '\t')[0] })
        schemaSha256 = (Get-FileHash -LiteralPath $schemaPath -Algorithm SHA256).Hash
        catalogSha256 = (Get-FileHash -LiteralPath $catalogPath -Algorithm SHA256).Hash
        checksSha256 = (Get-FileHash -LiteralPath $checksPath -Algorithm SHA256).Hash
        scope = 'DDL and database constraints only; not application API or authorization tests.'
    }
    $reportJson = $report | ConvertTo-Json -Depth 5
    [IO.File]::WriteAllText($ResultPath,$reportJson,(New-Object Text.UTF8Encoding($false)))
    [pscustomobject]@{ MysqlVersion = $report.mysqlVersion; Tables = $report.schemaTables; ChecksPassed = $report.passedChecks; Report = $ResultPath } | ConvertTo-Json -Compress
} finally {
    if ($containerCreated) {
        $labelResult = Invoke-DockerResult -DockerArgs @('inspect',$containerName,'--format','{{json .Config.Labels}}')
        if ($labelResult.ExitCode -eq 0) {
            $labels = $labelResult.Output | ConvertFrom-Json
            if ($labels.'ielts.design-run' -ne $runId) { throw 'Refusing to remove a container not owned by this verification run.' }
            Invoke-DockerChecked -DockerArgs @('rm','-f',$containerName) | Out-Null
        }
    }
}
