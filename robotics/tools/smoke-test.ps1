param(
    [Parameter(Mandatory=$true)][string]$ServerDir,
    [Parameter(Mandatory=$true)][string]$JavaExe,
    [int]$BootTimeoutSec = 300,
    [int]$SettleSec = 8
)

$log = Join-Path $ServerDir "logs\latest.log"
if (Test-Path $log) { Remove-Item $log -Force -ErrorAction SilentlyContinue }

$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName               = $JavaExe
$psi.Arguments              = "-Xms1G -Xmx2G -XX:+UseG1GC -jar paper.jar --nogui"
$psi.WorkingDirectory       = $ServerDir
$psi.UseShellExecute        = $false
$psi.RedirectStandardInput  = $true
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError  = $true

$p = [System.Diagnostics.Process]::Start($psi)

# Drain both pipes asynchronously - a full pipe would deadlock the server.
$outTask = $p.StandardOutput.ReadToEndAsync()
$errTask = $p.StandardError.ReadToEndAsync()

# Windows PowerShell writes a UTF-8 BOM on the first line it sends, and the server reads it as
# part of the command ("<BOM>stop<--[HERE]"). Burn it on a deliberate no-op line instead of on
# something that matters. Cost one run to find.
Start-Sleep -Seconds 2
try { $p.StandardInput.WriteLine(""); $p.StandardInput.Flush() } catch { }

$deadline = (Get-Date).AddSeconds($BootTimeoutSec)
$booted = $false
while ((Get-Date) -lt $deadline -and -not $p.HasExited) {
    Start-Sleep -Milliseconds 1000
    if (Test-Path $log) {
        $c = Get-Content $log -Raw -ErrorAction SilentlyContinue
        if ($c -and $c -match 'Done \(') { $booted = $true; break }
    }
}

if ($p.HasExited) {
    "RESULT: server exited during boot (code $($p.ExitCode))"
} elseif (-not $booted) {
    "RESULT: boot timed out after $BootTimeoutSec s"
} else {
    "RESULT: booted"
    Start-Sleep -Seconds $SettleSec      # let the robot engine tick

    foreach ($cmd in @("robocraft selftest", "robocraft progress", "plugins")) {
        try { $p.StandardInput.WriteLine($cmd); $p.StandardInput.Flush() } catch { }
        Start-Sleep -Milliseconds 1500
    }
    Start-Sleep -Seconds 2
}

if (-not $p.HasExited) {
    try { $p.StandardInput.WriteLine("stop"); $p.StandardInput.Flush() } catch { }
    if (-not $p.WaitForExit(120000)) { "WARN: stop timed out, killing"; $p.Kill() }
}

Set-Content -Path (Join-Path $ServerDir "console.out") -Value $outTask.Result
$err = $errTask.Result
if ($err) { Set-Content -Path (Join-Path $ServerDir "console.err") -Value $err }

"exit code: $($p.ExitCode)"
