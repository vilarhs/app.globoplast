$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

function Test-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

if (-not (Test-Administrator)) {
    throw "Execute este script em um PowerShell aberto como Administrador."
}

$sourceDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$sourceScript = Join-Path $sourceDir "globoplast_sync_planejamento_online.py"
$appDir = Join-Path $env:ProgramData "GloboplastSync"
$targetScript = Join-Path $appDir "globoplast_sync_planejamento_online.py"
$commandFile = Join-Path $appDir "executar_planejamento_online.cmd"
$baseConnector = Join-Path $appDir "globoplast_sync_refugo_online.py"
$configFile = Join-Path $appDir "refugo_online_config.json"
$taskName = "Globoplast Planejamento Online"

if (-not (Test-Path $sourceScript)) {
    throw "Arquivo não encontrado ao lado do instalador: $sourceScript"
}
if (-not (Test-Path $baseConnector) -or -not (Test-Path $configFile)) {
    throw "A configuração existente do sincronizador de Refugo não foi encontrada em $appDir."
}

# A tarefa roda como SYSTEM, que normalmente não possui o comando `py` no PATH.
# Descubra agora o executável real usado pelo launcher e grave o caminho absoluto
# no arquivo .cmd da tarefa.
$pythonExe = $null
try {
    $pythonExe = (& py -3 -c "import sys; print(sys.executable)" 2>$null | Select-Object -Last 1)
} catch {
    $pythonExe = $null
}
if ([string]::IsNullOrWhiteSpace($pythonExe)) {
    try {
        $pythonExe = (& python -c "import sys; print(sys.executable)" 2>$null | Select-Object -Last 1)
    } catch {
        $pythonExe = $null
    }
}
if ([string]::IsNullOrWhiteSpace($pythonExe)) {
    throw "Python não encontrado. Confirme que o comando py ou python funciona neste PowerShell."
}
$pythonExe = $pythonExe.Trim()
if (-not (Test-Path -LiteralPath $pythonExe -PathType Leaf)) {
    throw "Executável do Python não encontrado: $pythonExe"
}

New-Item -ItemType Directory -Path $appDir -Force | Out-Null
if (Test-Path $targetScript) {
    $stamp = Get-Date -Format "yyyyMMdd-HHmmss"
    Copy-Item $targetScript "$targetScript.$stamp.backup" -Force
}
Copy-Item $sourceScript $targetScript -Force

$command = @"
@echo off
cd /d "$appDir"
"$pythonExe" -u "$targetScript" --executar >> "$appDir\planejamento_task.log" 2>&1
exit /b %errorlevel%
"@
[IO.File]::WriteAllText($commandFile, $command, [Text.Encoding]::ASCII)

& schtasks.exe /Create /TN $taskName /TR "cmd.exe /c `"$commandFile`"" /SC MINUTE /MO 2 /RU SYSTEM /RL HIGHEST /F | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "Não foi possível criar a tarefa $taskName."
}

& schtasks.exe /Run /TN $taskName | Out-Host
if ($LASTEXITCODE -ne 0) {
    throw "A tarefa foi criada, mas não iniciou."
}

Write-Host "Sincronização de Planejamento instalada a cada 2 minutos." -ForegroundColor Green
Write-Host "Tarefa: $taskName"
Write-Host "Python: $pythonExe"
Write-Host "Log: $appDir\planejamento_online.log"
