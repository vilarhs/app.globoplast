$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = New-Object System.Text.UTF8Encoding($false)

function Test-Administrator {
    $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
    $principal = New-Object Security.Principal.WindowsPrincipal($identity)
    return $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
}

function Get-TaskFullName($task) {
    if ($task.TaskPath -eq "\") {
        return "\$($task.TaskName)"
    }
    return "$($task.TaskPath.TrimEnd('\'))\$($task.TaskName)"
}

function Save-Utf8Xml([xml]$document, [string]$path) {
    $settings = New-Object System.Xml.XmlWriterSettings
    $settings.Encoding = New-Object System.Text.UTF8Encoding($false)
    $settings.Indent = $true
    $writer = [System.Xml.XmlWriter]::Create($path, $settings)
    try {
        $document.Save($writer)
    }
    finally {
        $writer.Dispose()
    }
}

if (-not (Test-Administrator)) {
    throw "Execute este script em um PowerShell aberto como Administrador."
}

$now = Get-Date
$candidates = @(
    Get-ScheduledTask | ForEach-Object {
        $task = $_
        $signature = @(
            $task.TaskPath
            $task.TaskName
            $task.Actions | ForEach-Object { "$($_.Execute) $($_.Arguments) $($_.WorkingDirectory)" }
        ) -join " "

        if ($task.State -eq "Disabled") { return }
        if ($signature -notmatch "(?i)globoplast") { return }
        if ($signature -notmatch "(?i)(apontamento|refugo|erp|sync)") { return }
        if ($signature -match "(?i)backup") { return }

        $info = Get-ScheduledTaskInfo -TaskName $task.TaskName -TaskPath $task.TaskPath
        if ($info.LastRunTime -lt $now.AddMinutes(-15)) { return }
        if ($info.LastTaskResult -ne 0) { return }
        $task
    }
)

if ($candidates.Count -lt 1 -or $candidates.Count -gt 2) {
    Write-Host "Tarefas Globoplast encontradas como candidatas:" -ForegroundColor Yellow
    Get-ScheduledTask | Where-Object {
        $signature = "$($_.TaskPath) $($_.TaskName) " + (($_.Actions | ForEach-Object { "$($_.Execute) $($_.Arguments)" }) -join " ")
        $signature -match "(?i)globoplast" -and $signature -match "(?i)(apontamento|refugo|erp|sync)"
    } | Format-Table TaskPath, TaskName, State -AutoSize
    throw "Não foi possível identificar com segurança uma ou duas tarefas atuais. Nenhuma tarefa foi alterada."
}

$backupRoot = Join-Path $env:ProgramData "GloboplastSync\task-backups"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$backupDir = Join-Path $backupRoot $timestamp
New-Item -ItemType Directory -Path $backupDir -Force | Out-Null

Write-Host "Tarefas atuais que serão ajustadas para 2 minutos:" -ForegroundColor Cyan
$candidates | Format-Table TaskPath, TaskName, State -AutoSize

foreach ($task in $candidates) {
    $fullName = Get-TaskFullName $task
    $originalText = Export-ScheduledTask -TaskName $task.TaskName -TaskPath $task.TaskPath
    $safeName = ($fullName -replace '[\\/:*?"<>|]', '_').Trim('_')
    $backupPath = Join-Path $backupDir "$safeName.xml"
    [IO.File]::WriteAllText($backupPath, $originalText, (New-Object Text.UTF8Encoding($false)))

    [xml]$document = $originalText
    $namespace = New-Object System.Xml.XmlNamespaceManager($document.NameTable)
    $namespace.AddNamespace("t", $document.DocumentElement.NamespaceURI)
    $repetitions = @($document.SelectNodes("//t:Repetition", $namespace))

    if ($repetitions.Count -eq 0) {
        throw "A tarefa $fullName não possui intervalo de repetição. Nenhuma recriação foi executada para ela."
    }

    foreach ($repetition in $repetitions) {
        $interval = $repetition.SelectSingleNode("t:Interval", $namespace)
        if ($null -eq $interval) {
            $interval = $document.CreateElement("Interval", $document.DocumentElement.NamespaceURI)
            $repetition.PrependChild($interval) | Out-Null
        }
        $interval.InnerText = "PT2M"
    }

    $temporaryPath = Join-Path $env:TEMP "globoplast-$safeName-$timestamp.xml"
    try {
        Save-Utf8Xml $document $temporaryPath
        & schtasks.exe /Create /TN $fullName /XML $temporaryPath /F | Out-Host
        if ($LASTEXITCODE -ne 0) {
            throw "Falha ao atualizar a tarefa $fullName. XML original: $backupPath"
        }
    }
    finally {
        Remove-Item $temporaryPath -Force -ErrorAction SilentlyContinue
    }
}

Write-Host "Validando intervalos gravados..." -ForegroundColor Cyan
foreach ($task in $candidates) {
    [xml]$current = Export-ScheduledTask -TaskName $task.TaskName -TaskPath $task.TaskPath
    $namespace = New-Object System.Xml.XmlNamespaceManager($current.NameTable)
    $namespace.AddNamespace("t", $current.DocumentElement.NamespaceURI)
    $intervals = @($current.SelectNodes("//t:Repetition/t:Interval", $namespace) | ForEach-Object { $_.InnerText })
    if ($intervals.Count -eq 0 -or @($intervals | Where-Object { $_ -ne "PT2M" }).Count -gt 0) {
        throw "A validação da tarefa $(Get-TaskFullName $task) falhou. Backup: $backupDir"
    }
    Write-Host "OK | $(Get-TaskFullName $task) | intervalo=PT2M"
}

Write-Host "Intervalo restaurado para 2 minutos." -ForegroundColor Green
Write-Host "Backup das tarefas anteriores: $backupDir"
