[CmdletBinding()]
param([string]$Config = (Join-Path $PSScriptRoot 'demo.json'))
& (Join-Path $PSScriptRoot 'demo.ps1') record -Config $Config
