param(
    [string]$Source,
    [string]$Output,
    [string]$DestinationRoot,
    [switch]$AllLegacy,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'

function Resolve-WorldFolder {
    param([string]$Path, [switch]$AllowPrompt)
    if (-not (Test-Path -LiteralPath $Path)) { throw "Path does not exist: $Path" }
    if (-not (Test-Path -LiteralPath $Path -PathType Container)) { throw "Not a directory: $Path" }
    $item = Get-Item -LiteralPath $Path -ErrorAction Stop
    if (Test-Path -LiteralPath (Join-Path $item.FullName 'level.dat')) { return $item }
    $candidates = @(Get-ChildItem -LiteralPath $item.FullName -Directory -ErrorAction SilentlyContinue |
        Where-Object { Test-Path -LiteralPath (Join-Path $_.FullName 'level.dat') })
    if ($candidates.Count -eq 0) {
        throw "No level.dat or world folders found in: $Path"
    }
    if ($candidates.Count -eq 1) {
        Write-Host "Selected the only world in this saves directory: $($candidates[0].Name)" -ForegroundColor Yellow
        return $candidates[0]
    }
    Write-Host 'Multiple worlds found. Select one to convert:' -ForegroundColor Yellow
    for ($i = 0; $i -lt $candidates.Count; $i++) {
        Write-Host ("  [{0}] {1}" -f ($i + 1), $candidates[$i].Name)
    }
    if (-not $AllowPrompt) {
        throw 'Use -Source to specify a world folder containing level.dat.'
    }
    while ($true) {
        $answer = (Read-Host 'Enter a number or the full world folder path').Trim('"').Trim("'")
        if ($answer -match '^\d+$') {
            $n = [int]$answer
            if ($n -ge 1 -and $n -le $candidates.Count) { return $candidates[$n - 1] }
        } elseif ($answer) {
            return Resolve-WorldFolder -Path $answer -AllowPrompt
        }
        Write-Host 'Invalid selection. Please try again.' -ForegroundColor Red
    }
}

function Get-LevelDisplayName {
    param([string]$WorldFolder)
    try {
        $bytes = [System.IO.File]::ReadAllBytes((Join-Path $WorldFolder 'level.dat'))
        if ($bytes.Length -gt 2 -and $bytes[0] -eq 0x1F -and $bytes[1] -eq 0x8B) {
            $stream = New-Object System.IO.MemoryStream(, $bytes)
            $gz = New-Object System.IO.Compression.GZipStream($stream, [System.IO.Compression.CompressionMode]::Decompress)
            $buffer = New-Object System.IO.MemoryStream
            $gz.CopyTo($buffer)
            $gz.Dispose()
            $stream.Dispose()
            $bytes = $buffer.ToArray()
            $buffer.Dispose()
        }
        $key = [System.Text.Encoding]::ASCII.GetBytes('LevelName')
        for ($i = 0; $i -le $bytes.Length - $key.Length - 3; $i++) {
            $found = $true
            for ($j = 0; $j -lt $key.Length; $j++) {
                if ($bytes[$i + $j] -ne $key[$j]) { $found = $false; break }
            }
            if (-not $found) { continue }
            $len = ($bytes[$i + $key.Length] -shl 8) -bor $bytes[$i + $key.Length + 1]
            if ($len -le 0 -or $len -gt 128) { continue }
            $start = $i + $key.Length + 2
            if ($start + $len -gt $bytes.Length) { continue }
            $name = [System.Text.Encoding]::UTF8.GetString($bytes, $start, $len).Trim()
            if ($name) { return $name }
        }
    } catch { }
    return $null
}


Write-Host 'World cleaner' -ForegroundColor Cyan
Write-Host 'WARNING: Conversion has risks. BACK UP YOUR WORLD before continuing.' -ForegroundColor Red
Write-Host 'This tool creates a separate copy. It does not modify the original world.' -ForegroundColor Red
Write-Host 'Keep enough free disk space for the complete exported world and your backups.' -ForegroundColor Red
Write-Host 'All processing takes place locally on your computer. No world data is uploaded.' -ForegroundColor Yellow
Write-Host 'Close Minecraft and the server before conversion. Keep your old modpack and original world.'
if ($AllLegacy) {
    Write-Host 'Removal mode: all legacy Firefighting blocks, items and related connections.' -ForegroundColor Yellow
} else {
    Write-Host 'Removal mode: legacy Powertrain components, shared brackets and related connections.' -ForegroundColor Yellow
}
Write-Host 'Identified constraint-provider blocks may also be removed. Entire Sable substructures are not deleted.' -ForegroundColor Yellow

try {
    # Select the export location BEFORE asking for or inspecting a source world.
    if ($Output) {
        $Output = [System.IO.Path]::GetFullPath($Output.Trim().Trim('"').Trim("'"))
        if (Test-Path -LiteralPath $Output) { throw 'Output already exists. Choose a new output folder.' }
        $DestinationRoot = Split-Path -Parent $Output
    } elseif (-not $DestinationRoot) {
        Write-Host 'Step 1 - Select the export directory (an existing folder for the new copy).'
        $DestinationRoot = Read-Host 'Export directory path'
    }
    $DestinationRoot = $DestinationRoot.Trim().Trim('"').Trim("'")
    if (-not (Test-Path -LiteralPath $DestinationRoot -PathType Container)) {
        throw 'Export directory does not exist. Create it first, then run this tool again.'
    }
    $DestinationRoot = (Get-Item -LiteralPath $DestinationRoot).FullName
    Write-Host "Export directory: $DestinationRoot" -ForegroundColor Cyan

    $sourceProvided = $PSBoundParameters.ContainsKey('Source')
    if (-not $Source) {
        Write-Host 'Step 2 - Select the source world folder (containing level.dat), or its saves directory.'
        $Source = Read-Host 'Source world path'
    }
    $Source = $Source.Trim().Trim('"').Trim("'")
    $item = Resolve-WorldFolder -Path $Source -AllowPrompt:(-not $sourceProvided)
    $Source = $item.FullName
    $displayName = Get-LevelDisplayName $Source
    $baseName = if ($displayName) { $displayName } else { $item.Name }
    $baseName = ($baseName -replace '[\\/:*?"<>|]', '_').Trim()
    if (-not $baseName -or $baseName -match '^(saves?|world)$') { $baseName = $item.Name }
    if (-not $Output) {
        $mode = if ($AllLegacy) { 'all' } else { 'powertrain' }
        $worldName = 'CLEANUP-v2-' + $mode + '-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + $baseName
        $Output = Join-Path $DestinationRoot $worldName
    }
    $Output = [System.IO.Path]::GetFullPath($Output)
    Write-Host "Source world: $Source" -ForegroundColor Cyan
    if ($displayName -and $displayName -ne $item.Name) {
        Write-Host "World display name: $displayName (folder: $($item.Name))" -ForegroundColor DarkGray
    }
    Write-Host "Output folder: $Output" -ForegroundColor Cyan
    Write-Host ('Select this world in Minecraft: ' + [System.IO.Path]::GetFileName($Output)) -ForegroundColor Cyan
    if ($DryRun) {
        Write-Host 'Dry run complete. No copy was created and the original world was not modified.' -ForegroundColor Yellow
        Write-Host 'You can now close this window.'
        exit 0
    }
    $cleanup = Join-Path $PSScriptRoot 'tools\world_cleanup\clean_world.py'
    $pythonArgs = @($cleanup, $Source, '--output', $Output)
    if ($AllLegacy) { $pythonArgs += '--all-legacy' }
    $log = Join-Path $PSScriptRoot 'cleanup-last-run.log'
    $pyPrefix = @()
    if (Get-Command py -ErrorAction SilentlyContinue) {
        $pyExe = 'py'
        $pyPrefix = @('-3')
    } elseif (Get-Command python -ErrorAction SilentlyContinue) {
        $pyExe = 'python'
    } else {
        throw 'Python 3.10 or newer is required. Install it and run this tool again.'
    }
    $logLines = New-Object System.Collections.Generic.List[string]
    $logLines.Add('Started: ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'))
    $logLines.Add('Source: ' + $Source)
    $logLines.Add('Output: ' + $Output)
    $logLines.Add('Arguments: ' + (@($pyExe) + $pyPrefix + $pythonArgs -join ' '))
    # Capture stderr without letting PS 5.1 terminate before writing the log.
    # Never name this variable $output: it aliases the destination parameter.
    $env:PYTHONIOENCODING = 'utf-8'
    $ErrorActionPreference = 'Continue'
    $pyStdout = & $pyExe @pyPrefix @pythonArgs 2>&1
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    foreach ($line in @($pyStdout)) {
        $text = [string]$line
        $logLines.Add($text)
        Write-Host $text
    }
    $logLines.Add('Exit code: ' + $exitCode)
    Set-Content -LiteralPath $log -Value $logLines -Encoding UTF8
    if ($exitCode -ne 0) {
        Write-Host "Conversion failed (exit code $exitCode). Original unchanged; no completed output was published." -ForegroundColor Red
        Write-Host "Review the details above. Full log: $log" -ForegroundColor Red
        Write-Host 'You can now close this window.'
        exit $exitCode
    }
    $receipt = Join-Path $PSScriptRoot 'LAST_SUCCESSFUL_CLEANUP.txt'
    @(
        'Latest successful conversion. Previous copies are not updated automatically.',
        ('Completed: ' + (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')),
        ('Minecraft world name: ' + [System.IO.Path]::GetFileName($Output)),
        ('Output folder: ' + $Output),
        ('Source: ' + $Source),
        'This copy has not been verified by loading it in Minecraft.'
    ) | Set-Content -LiteralPath $receipt -Encoding UTF8
    Write-Host 'Conversion complete. Select this world in Minecraft:' -ForegroundColor Green
    Write-Host ([System.IO.Path]::GetFileName($Output)) -ForegroundColor Green
    Write-Host "Full path: $Output"
    Write-Host "Result receipt: $receipt"
    Write-Host 'Conversion finished. Please close this window.' -ForegroundColor Green
} catch {
    Write-Host ('ERROR: ' + $_.Exception.Message) -ForegroundColor Red
    Write-Host 'Conversion did not complete. Keep the original world and check the paths, permissions and available disk space.' -ForegroundColor Red
    Write-Host 'You can now close this window.'
    exit 2
}
