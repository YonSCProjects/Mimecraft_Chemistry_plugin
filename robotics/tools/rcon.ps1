<#
    A minimal RCON client, so either dev server can be driven from a terminal.

    The Minecraft MCP bridge in a Claude Code session is hard-wired to port 25575, which only one
    server can hold. The two teaching servers run at the same time - a class is split across them -
    so the second one needs this.

        .\rcon.ps1 "chemcraft"                      # defaults to 25575
        .\rcon.ps1 -Port 25576 "cc questions"       # the ChemCraft server
        .\rcon.ps1 -Port 25576 "cc whisper Dana chat היי"

    With no -Password it reads the one the MCP bridge already uses, out of .claude.json. That is
    done with a regex rather than ConvertFrom-Json on purpose: the file has keys differing only by
    case, which makes Windows PowerShell 5.1's parser throw, and its -AsHashtable escape hatch only
    exists in PowerShell 7. A regex works in both.
#>
param(
    [Parameter(Mandatory = $true, Position = 0)][string]$Command,
    [string]$RconHost = "127.0.0.1",
    [int]$Port = 25575,
    [string]$Password
)

$ErrorActionPreference = "Stop"

if (-not $Password) {
    $cfgPath = Join-Path $env:USERPROFILE ".claude.json"
    if (-not (Test-Path $cfgPath)) { throw "No -Password given and $cfgPath not found." }
    $raw = Get-Content $cfgPath -Raw
    $m = [regex]::Match($raw, '"RCON_PASSWORD"\s*:\s*"([^"]+)"')
    if (-not $m.Success) { throw "Could not read RCON_PASSWORD from $cfgPath." }
    $Password = $m.Groups[1].Value
}

# RCON packet: int32 size | int32 id | int32 type | body (UTF-8) | 0x00 | 0x00
# size counts everything after itself. type 3 = auth, 2 = command, 2/0 = response.
function Send-Packet($stream, [int]$id, [int]$type, [string]$body) {
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    $out = New-Object System.IO.MemoryStream
    $w = New-Object System.IO.BinaryWriter($out)
    $w.Write([int]($bytes.Length + 10)); $w.Write([int]$id); $w.Write([int]$type)
    $w.Write($bytes); $w.Write([byte]0); $w.Write([byte]0); $w.Flush()
    $buf = $out.ToArray()
    $stream.Write($buf, 0, $buf.Length); $stream.Flush()
}

# A NetworkStream read can return short; loop until the packet is whole.
function Read-Exact($stream, [int]$count) {
    $buf = New-Object byte[] $count
    $got = 0
    while ($got -lt $count) {
        $n = $stream.Read($buf, $got, $count - $got)
        if ($n -le 0) { throw "Connection closed after $got of $count bytes." }
        $got += $n
    }
    return ,$buf
}

function Read-Packet($stream) {
    $size = [BitConverter]::ToInt32((Read-Exact $stream 4), 0)
    $rest = Read-Exact $stream $size
    [pscustomobject]@{
        Id   = [BitConverter]::ToInt32($rest, 0)
        Type = [BitConverter]::ToInt32($rest, 4)
        # last two bytes are the terminators
        Body = [System.Text.Encoding]::UTF8.GetString($rest, 8, $size - 10)
    }
}

$client = New-Object System.Net.Sockets.TcpClient
try {
    $client.Connect($RconHost, $Port)
    $client.ReceiveTimeout = 15000
    $stream = $client.GetStream()

    Send-Packet $stream 1 3 $Password
    $auth = Read-Packet $stream
    if ($auth.Id -eq -1) { throw "RCON authentication failed on ${RconHost}:${Port}." }

    Send-Packet $stream 2 2 $Command
    $reply = Read-Packet $stream
    # Strip the section-sign colour codes the server sends, so output is readable in a terminal.
    ($reply.Body -replace '§.', '')
}
finally {
    $client.Close()
}
