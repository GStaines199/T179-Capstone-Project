<#
.SYNOPSIS
Walk either emulator from its CURRENT GPS position. Never prepares/resets ATAK.
.EXAMPLE
.\walk.ps1 start -Device both
.\walk.ps1 faster -Device lead
.\walk.ps1 stop -Device member
.\walk.ps1 direction -Device both -Bearing 90
.\walk.ps1 shutdown
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('start','resume','stop','speed','faster','slower','direction','status','shutdown')]
    [string]$Command = 'status',
    [ValidateSet('lead','hq','member','both')]
    [string]$Device = 'both',
    [ValidateRange(0.1,10)]
    [double]$Speed,
    [ValidateRange(0,359.999)]
    [double]$Bearing,
    [ValidateRange(0.1,5)]
    [double]$Step = 0.2,
    [string]$Config = (Join-Path $PSScriptRoot 'demo.json')
)
$ErrorActionPreference = 'Stop'
$python = Get-Command python -ErrorAction SilentlyContinue
if (-not $python) { throw 'Python 3 is required.' }
$arguments = @((Join-Path $PSScriptRoot 'walk.py'), $Command,
    '--device', $Device, '--config', $Config,
    '--step', $Step.ToString([Globalization.CultureInfo]::InvariantCulture))
foreach ($parameter in @('Speed','Bearing')) {
    if ($PSBoundParameters.ContainsKey($parameter)) {
        $value = Get-Variable -Name $parameter -ValueOnly
        $arguments += @(('--' + $parameter.ToLowerInvariant()),
            $value.ToString([Globalization.CultureInfo]::InvariantCulture))
    }
}
& $python.Source @arguments
if ($LASTEXITCODE -ne 0) { throw "Walking command '$Command' failed. See the message above." }
