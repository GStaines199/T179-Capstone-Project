<#
.SYNOPSIS
Two-emulator SARtak demo with concurrent GPS movement and recording.
.EXAMPLE
.\demo.ps1 prepare
.\demo.ps1 record
.\demo.ps1 move -Distance 250
.\demo.ps1 stop
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('plan','prepare','status','record','record-stop','move','pause',
        'resume','lag','hold-member','catch-up','reset-position','stop','chapter',
        'open','lead','menu','self-test')]
    [string]$Command = 'menu',
    [string]$Config = (Join-Path $PSScriptRoot 'demo.json'),
    [string]$Name = '',
    [ValidateSet('hq','member','both')]
    [string]$Device = 'both',
    [ValidateSet('HOME','GRID','TEAM','OPERATION','ALERTS','DEVICES','TRACK')]
    [string]$Tab = 'HOME',
    [ValidateRange(0.1,10)]
    [double]$Speed = 1.2,
    [ValidateRange(0,10000)]
    [double]$Distance = 0
)
$ErrorActionPreference = 'Stop'
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) { throw 'Python 3 is required. Install Python and add it to PATH.' }
$arguments = @((Join-Path $PSScriptRoot 'sartak_demo.py'), $Command,
    '--config', $Config, '--device', $Device, '--tab', $Tab,
    '--speed', $Speed.ToString([Globalization.CultureInfo]::InvariantCulture),
    '--distance', $Distance.ToString([Globalization.CultureInfo]::InvariantCulture))
if ($Name) { $arguments += @('--name', $Name) }
& $python.Source @arguments
if ($LASTEXITCODE -ne 0) { throw "SARtak demo '$Command' failed. See the message above." }
