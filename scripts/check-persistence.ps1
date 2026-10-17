param(
    [ValidateSet('prepare','verify')] [string]$Phase = 'verify',
    [string]$BaseUrl = 'http://127.0.0.1:5173'
)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$stateDir = Join-Path $projectRoot '.test-state'
$statePath = Join-Path $stateDir 'persistence.json'
$config = @{}
Get-Content -LiteralPath (Join-Path $projectRoot '.env') | ForEach-Object {
    if ($_ -match '^([A-Z_]+)=(.*)$') { $config[$matches[1]] = $matches[2] }
}
function RequestJson($session, [string]$method, [string]$path, $payload = $null, [string]$token = '') {
    $parameters = @{ Uri = ($BaseUrl + $path); WebSession = $session; Method = $method; UseBasicParsing = $true; TimeoutSec = 20 }
    if ($token) { $parameters.Headers = @{ 'X-CSRF-TOKEN' = $token } }
    if ($null -ne $payload) {
        $parameters.ContentType = 'application/json'
        $parameters.Body = [Text.Encoding]::UTF8.GetBytes(($payload | ConvertTo-Json -Compress))
    }
    $result = Invoke-WebRequest @parameters
    if ($result.Content) { return ($result.Content | ConvertFrom-Json) }
}
function Login([string]$email, [string]$password) {
    $session = New-Object Microsoft.PowerShell.Commands.WebRequestSession
    $token = RequestJson $session 'GET' '/api/auth/csrf'
    $account = RequestJson $session 'POST' '/api/auth/login' @{ email = $email; password = $password } $token.token
    $token = RequestJson $session 'GET' '/api/auth/csrf'
    return @{ Session = $session; Token = $token.token; Account = $account }
}
$teacher = Login $config['DEMO_TEACHER_EMAIL'] $config['DEMO_TEACHER_PASSWORD']
$student = Login $config['DEMO_STUDENT_EMAIL'] $config['DEMO_STUDENT_PASSWORD']
if ($Phase -eq 'prepare') {
    $room = RequestJson $teacher.Session 'POST' '/api/classes' @{
        name = ('Persistence Demo ' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss'))
        description = 'Created for the restart persistence check.'
        requestId = [Guid]::NewGuid().ToString()
    } $teacher.Token
    $join = RequestJson $student.Session 'POST' '/api/classes/join' @{ code = $room.joinCode } $student.Token
    if ($join.classroom.id -ne $room.id) { throw 'Join returned a different class.' }
    New-Item -ItemType Directory -Path $stateDir -Force | Out-Null
    [pscustomobject]@{ ClassId = $room.id; ClassName = $room.name; StudentId = $student.Account.id } |
        ConvertTo-Json | Set-Content -LiteralPath $statePath -Encoding UTF8
    Write-Output 'PREPARED: class and membership saved. Restart/recreate services, then run verify.'
} else {
    if (-not (Test-Path -LiteralPath $statePath)) { throw 'Run -Phase prepare before restarting services.' }
    $state = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
    $teacherRooms = @(RequestJson $teacher.Session 'GET' '/api/classes')
    $studentRooms = @(RequestJson $student.Session 'GET' '/api/classes')
    $members = @(RequestJson $teacher.Session 'GET' ('/api/classes/' + $state.ClassId + '/members'))
    if (-not ($teacherRooms | Where-Object { $_.id -eq $state.ClassId -and $_.name -eq $state.ClassName })) { throw 'Teacher class was not preserved.' }
    if (-not ($studentRooms | Where-Object { $_.id -eq $state.ClassId })) { throw 'Student class was not preserved.' }
    if (-not ($members | Where-Object { $_.id -eq $state.StudentId })) { throw 'Membership was not preserved.' }
    Write-Output 'PASS: accounts, class and membership survived service restart/recreation.'
}
