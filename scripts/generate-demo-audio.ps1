param([switch]$Force)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$projectRoot = Split-Path -Parent $PSScriptRoot
$resourceRoot = Join-Path $projectRoot 'source/server/src/main/resources/demo'
$curriculum = Get-Content -LiteralPath (Join-Path $resourceRoot 'curriculum-v1.json') -Raw -Encoding UTF8 | ConvertFrom-Json
$synth = New-Object System.Speech.Synthesis.SpeechSynthesizer
try {
    $synth.SelectVoice('Microsoft Zira Desktop')
    $synth.Rate = -1
    foreach ($material in $curriculum.materials) {
        if ($material.data.kind -ne 'AUDIO') { continue }
        $target = Join-Path (Join-Path $resourceRoot 'materials') $material.file
        if ((Test-Path -LiteralPath $target) -and -not $Force) { continue }
        $synth.SetOutputToWaveFile($target)
        $synth.Speak([string]$material.data.transcript)
        $synth.SetOutputToNull()
        $size = (Get-Item -LiteralPath $target).Length
        if ($size -le 44 -or $size -gt 5242880) { throw "Invalid audio size: $($material.file)" }
        Write-Output "$($material.file): $size bytes"
    }
} finally { $synth.Dispose() }
