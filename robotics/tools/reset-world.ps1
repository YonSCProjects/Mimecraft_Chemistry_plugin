<#
.SYNOPSIS
  Move a RoboCraft server from the flat first-term world to real terrain, keeping everything.

.DESCRIPTION
  Refuses to run while the server is up. Then:
    1. moves world/ to world-flat-<stamp>/            (nothing is deleted)
    2. moves players.yml, placements.yml, robots.yml, plots.yml into
       plugins/RoboCraft/backup-<stamp>-reset/        (fresh start for every student)
    3. server.properties: level-type=minecraft\:normal, generator-settings cleared,
       level-seed=<Seed>                              (the same seed on every class server, so
                                                       every class sees the same land)
    4. config.yml: plot.ground-y: auto, board.offset-y: 1, pad-x/pad-z/border added if absent
  Start the server yourself afterwards. The first join carves that student's pad; or run
  `rc pads 16` from the console first so no first join waits on it.

.EXAMPLE
  .\reset-world.ps1 -ServerDir C:\26.2_RoboCraft_Tair -Seed RoboCraft2026
#>
param(
    [Parameter(Mandatory=$true)][string]$ServerDir,
    [string]$Seed = 'RoboCraft2026'
)
$ErrorActionPreference = 'Stop'
$props = Join-Path $ServerDir 'server.properties'
if (-not (Test-Path $props)) { throw "not a server folder: $ServerDir" }
$port = [int]((Select-String -Path $props -Pattern '^server-port=(\d+)').Matches[0].Groups[1].Value)
if (Get-NetTCPConnection -State Listen -LocalPort $port -EA SilentlyContinue) { throw "server on port $port is RUNNING - stop it first" }

$stamp = Get-Date -Format 'yyyy-MM-dd-HHmm'
$pd = Join-Path $ServerDir 'plugins\RoboCraft'
$cfg = Join-Path $pd 'config.yml'
if (-not (Test-Path $cfg)) { throw "no plugins\RoboCraft\config.yml in $ServerDir" }

# 1. the world - moved, never deleted
$world = Join-Path $ServerDir 'world'
if (Test-Path $world) {
    Move-Item $world (Join-Path $ServerDir "world-flat-$stamp")
    "world/ -> world-flat-$stamp/"
} else { "no world/ folder (never started?)" }

# 2. student data - fresh start, everything kept
$bak = Join-Path $pd "backup-$stamp-reset"
New-Item -ItemType Directory -Path $bak -Force | Out-Null
foreach ($f in 'players.yml','placements.yml','robots.yml','plots.yml','questions.jsonl') {
    $p = Join-Path $pd $f
    if (Test-Path $p) { Move-Item $p (Join-Path $bak $f); "$f -> backup-$stamp-reset/" }
}
Copy-Item $cfg (Join-Path $bak 'config.yml')

# 3. server.properties
$t = [IO.File]::ReadAllText($props)
$t = [regex]::Replace($t, '(?m)^level-type=.*$', 'level-type=minecraft\:normal')
$t = [regex]::Replace($t, '(?m)^generator-settings=.*$', 'generator-settings=')
if ($t -match '(?m)^level-seed=') { $t = [regex]::Replace($t, '(?m)^level-seed=.*$', "level-seed=$Seed") }
else { $t = $t.TrimEnd() + "`nlevel-seed=$Seed`n" }
[IO.File]::WriteAllText($props, $t)
"server.properties: level-type normal, seed '$Seed'"

# 4. config.yml - only the keys that change meaning; everything else stays exactly as it was
$c = [IO.File]::ReadAllText($cfg, [Text.Encoding]::UTF8)
$c = [regex]::Replace($c, '(?m)^(\s*)ground-y:\s*-?\d+\s*$', '${1}ground-y: auto')
$c = [regex]::Replace($c, '(?m)^(board:\s*\r?\n(?:.*\r?\n)*?\s*)offset-y:\s*-?\d+', '${1}offset-y: 1')
if ($c -notmatch '(?m)^\s*pad-x:') {
    $c = [regex]::Replace($c, '(?m)^(\s*)ground-y: auto\s*$', "`${1}ground-y: auto`n`${1}pad-x: 44`n`${1}pad-z: 24`n`${1}border: 2400")
}
[IO.File]::WriteAllText($cfg, $c, (New-Object Text.UTF8Encoding $false))
"config.yml: ground-y auto, board.offset-y 1, pad 44x24, border 2400"
"DONE. Start the server; the plugin generates the world and carves each pad on first join (or run  rc pads 16  from the console first)."
