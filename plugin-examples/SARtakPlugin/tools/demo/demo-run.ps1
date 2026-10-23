<#
.SYNOPSIS
Guided, recorded SARtak demo. UI confirmations are performed on the emulators.
.DESCRIPTION
Press Enter at chapter checkpoints after completing the visible actions.
GPS and screen recording run concurrently in the background.
#>
[CmdletBinding()]
param([string]$Config = (Join-Path $PSScriptRoot 'demo.json'))
$ErrorActionPreference = 'Stop'
$controller = Join-Path $PSScriptRoot 'demo.ps1'
$settings = Get-Content -LiteralPath $Config -Raw | ConvertFrom-Json
function Step([string]$Name, [string]$Instructions) {
    & $controller chapter -Config $Config -Name $Name
    Write-Host "`n$Name" -ForegroundColor Cyan
    Write-Host $Instructions
    Read-Host 'Press Enter when ready to continue' | Out-Null
}
& $controller prepare -Config $Config
& $controller record -Config $Config
try {
    Step '01 - Open SARtak' 'Both devices are stationary. Close/reopen the SARtak panel through ATAK Tools to capture the normal entry. Show HQ on the first device and Team Member on the second.'
    & $controller open -Config $Config -Device hq -Tab OPERATION
    Step '02 - Create a NEW operation' "On HQ create '$($settings.operationName)' with Training as search type. Use the existing Ditto profile. Join this new operation on the member with its join code; QR scanning can be recorded separately. Keep the join-code screen out of shared footage because it contains credentials."
    & $controller open -Config $Config -Device hq -Tab GRID
    Step '03 - Plan the search area' "On HQ select Plan Search Area, choose Box / Rectangle, and enter width $($settings.areaWidthKm) km and height $($settings.areaHeightKm) km. Verify the boundary and grids appear on both devices."
    & $controller open -Config $Config -Tab DEVICES
    Step '04 - Show connected devices' 'Show both callsigns, separate UIDs, Ditto connectivity and last updates. A device appearing here alone does not prove it has joined the team.'
    & $controller lead -Config $Config
    Step '05 - Create and join Bravo team' "On the lead create team '$($settings.teamName)'. Add the member, send an invite, and accept it on the member. Verify both rosters contain both devices and the pending invite disappears."
    & $controller open -Config $Config -Device hq -Tab GRID
    Step '06 - Select 25 cells and plan the route' 'Use two-corner route selection: start with the current cell and end four columns east and four rows north (5 x 5 cells). Generate the north-first serpentine route through those cells. Show the dotted route on both devices. Turn overlays off/on briefly, then leave them on.'
    Step '07 - Start the search line' 'On the leader start the search line pointing North. Verify it appears on the member. Enable track recording/visibility if desired. The next Enter starts both walking at 1.2 m/s.'
    & $controller move -Config $Config -Distance 230 -Speed $settings.walkingSpeedMps
    Step '08 - Walk and demonstrate cell progression' 'Both GPS feeds are moving concurrently. Follow the map as cells are exited and become PARTIAL. Mark an exited partial cell COMPLETE using the lead, leaving another PARTIAL, and verify the member receives those changes. Walking pauses automatically at 230 m; press Enter after showing progress.'
    & $controller move -Config $Config -Distance 320 -Speed $settings.walkingSpeedMps
    & $controller lag -Config $Config
    Step '09 - Member falls behind' 'The member walks at one-quarter leader speed. Allow the gap to develop and show the line-position warning/distance. Press Enter when ready for the hold alert.'
    & $controller open -Config $Config -Device hq -Tab ALERTS
    Step '10 - Send Hold Position' 'On the leader send Hold Position. Confirm the real popup arrives on the member. Press Enter immediately after it appears; this halts simulated movement. The script does not fake alerts or acknowledgements.'
    & $controller pause -Config $Config
    Step '11 - Acknowledge the hold' 'Acknowledge on the member. Show its acknowledgement on the leader and confirm the SARtak search line is paused. The GPS worker remains stationary with zero speed.'
    Step '12 - Resume search' 'Use SARtak to resolve the hold / resume the search. Confirm the remote popup/state clears and the line resumes. Press Enter to resume walking and allow the member to catch up.'
    & $controller move -Config $Config -Distance 550 -Speed $settings.walkingSpeedMps
    & $controller catch-up -Config $Config
    Step '13 - Catch up and round the first turn' 'Show the member recovering line position. Keep walking until the first rounded turn and the return lane are visible. GPS pauses at 550 m. You can press Enter sooner to finish the movement portion.'
    & $controller pause -Config $Config
    & $controller open -Config $Config -Device hq -Tab GRID
    Step '14 - Review search results' 'Stop the search line through SARtak. Show partial and complete cells, planned route, roster, Track tab and alerts. These are real app outcomes; do not mark a cell complete merely to hide a failed sync.'
    & $controller open -Config $Config -Tab HOME
    Step '15 - Closing shot' 'Show both devices in the same operation with the same team. Review readiness and final search progress. Press Enter to finish and save both recordings.'
} finally {
    & $controller pause -Config $Config
    & $controller record-stop -Config $Config
}
Write-Host 'Demo recorded. Stationary GPS feeds remain active; use demo.ps1 stop to shut down.' -ForegroundColor Green
