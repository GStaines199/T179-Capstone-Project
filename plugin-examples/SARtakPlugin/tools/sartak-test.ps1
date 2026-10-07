param(
    [Parameter(Position = 0)]
    [ValidateSet('status', 'build', 'install', 'launch', 'setup-two-device',
        'reset-fixture', 'open-tab', 'set-role', 'set-callsign', 'move', 'disconnect',
        'reconnect', 'capture', 'clear-logs', 'collect-logs')]
    [string]$Command = 'status',
    [string[]]$Serial,
    [ValidateSet('HOME', 'GRID', 'TEAM', 'OPERATION', 'ALERTS', 'DEVICES', 'TRACK')]
    [string]$Tab = 'HOME',
    [ValidateSet('HQ', 'TEAM_LEAD', 'TEAM_MEMBER')]
    [string]$Role = 'TEAM_MEMBER',
    [string]$Callsign,
    [double]$Latitude = -27.4698,
    [double]$Longitude = 153.0251,
    [double]$Altitude = 10,
    [string]$OutputDirectory = (Join-Path $PSScriptRoot '..\qa\artifacts')
)

$ErrorActionPreference = 'Stop'
$AtakPackage = 'com.atakmap.app.civ'
$PluginPackage = 'com.atakmap.android.plugintemplate.plugin'
$ActionPrefix = 'com.atakmap.android.plugintemplate'
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$Adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe'

if (-not (Test-Path -LiteralPath $Adb)) {
    $candidate = Get-Command adb -ErrorAction SilentlyContinue
    if ($candidate) { $Adb = $candidate.Source }
}
if (-not (Test-Path -LiteralPath $Adb)) {
    throw 'adb was not found. Install Android SDK Platform Tools or add adb to PATH.'
}

function Invoke-Adb {
    param([string]$Device, [Parameter(ValueFromRemainingArguments)] [string[]]$Arguments)
    if ($Device) { & $Adb -s $Device @Arguments }
    else { & $Adb @Arguments }
    if ($LASTEXITCODE -ne 0) { throw "adb failed for ${Device}: $($Arguments -join ' ')" }
}

function Get-ConnectedDevices {
    $devices = @(& $Adb devices | Select-Object -Skip 1 |
        ForEach-Object { if ($_ -match '^([^\s]+)\s+device$') { $Matches[1] } })
    if ($Serial) {
        foreach ($requested in $Serial) {
            if ($devices -notcontains $requested) { throw "Device $requested is not connected." }
        }
        return @($Serial)
    }
    return @($devices | Sort-Object)
}

function Require-Devices([int]$Minimum = 1) {
    $devices = @(Get-ConnectedDevices)
    if ($devices.Count -lt $Minimum) {
        throw "Expected at least $Minimum connected emulator/device(s); found $($devices.Count)."
    }
    return $devices
}

function Get-LatestApk {
    $apk = Get-ChildItem -Path (Join-Path $RepoRoot 'app\build\outputs\apk\civ\debug') `
        -Filter '*.apk' -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $apk) { throw 'No Civ debug APK found. Run the build command first.' }
    return $apk.FullName
}

function Send-DebugBroadcast {
    param([string]$Device, [string]$Action, [string]$ExtraName, [string]$ExtraValue)
    $args = @('shell', 'am', 'broadcast', '-a', "$ActionPrefix.$Action", '-p', $AtakPackage)
    if ($ExtraName) { $args += @('--es', $ExtraName, $ExtraValue) }
    Invoke-Adb -Device $Device -Arguments $args | Out-Null
}

function Start-Atak([string]$Device) {
    Invoke-Adb -Device $Device -Arguments @('shell', 'monkey', '-p',
        $AtakPackage, '-c', 'android.intent.category.LAUNCHER', '1') | Out-Null
}

function Open-Tab([string]$Device, [string]$Name) {
    Send-DebugBroadcast $Device 'DEBUG_OPEN_TAB' 'tab' $Name
}

function Set-TestRole([string]$Device, [string]$Name) {
    Send-DebugBroadcast $Device 'DEBUG_SET_ROLE' 'role' $Name
}

function Set-TestCallsign([string]$Device, [string]$Name) {
    Send-DebugBroadcast $Device 'DEBUG_SET_CALLSIGN' 'callsign' $Name
}

function Save-Screenshot([string]$Device, [string]$Directory) {
    New-Item -ItemType Directory -Force -Path $Directory | Out-Null
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $remote = "/sdcard/sartak-$stamp.png"
    $local = Join-Path $Directory "$stamp-$Device.png"
    Invoke-Adb -Device $Device -Arguments @('shell', 'screencap', '-p', $remote) | Out-Null
    Invoke-Adb -Device $Device -Arguments @('pull', $remote, $local) | Out-Null
    Invoke-Adb -Device $Device -Arguments @('shell', 'rm', $remote) | Out-Null

    Add-Type -AssemblyName System.Drawing
    $image = [System.Drawing.Bitmap]::FromFile($local)
    try {
        $width = [int]$image.Width
        $height = [int]$image.Height
        $left = [int][Math]::Floor($width / 2.0)
        $rectangle = [System.Drawing.Rectangle]::new($left, 0,
            [int]($width - $left), $height)
        $panel = $image.Clone($rectangle, $image.PixelFormat)
        try { $panel.Save((Join-Path $Directory "$stamp-$Device-panel.png")) }
        finally { $panel.Dispose() }
    } finally { $image.Dispose() }
    Write-Host "Captured $Device -> $local"
}

switch ($Command) {
    'status' {
        $devices = @(Get-ConnectedDevices)
        Write-Host "ADB: $Adb"
        Write-Host "Connected: $($devices.Count)"
        foreach ($device in $devices) {
            $atak = (& $Adb -s $device shell pm path $AtakPackage 2>$null) -join ''
            $plugin = (& $Adb -s $device shell pm path $PluginPackage 2>$null) -join ''
            Write-Host "  $device | ATAK $([bool]$atak) | SARtak $([bool]$plugin)"
        }
    }
    'build' {
        Push-Location $RepoRoot
        try { & .\gradlew.bat :app:assembleCivDebug; if ($LASTEXITCODE -ne 0) { throw 'Gradle build failed.' } }
        finally { Pop-Location }
        Write-Host "Built $(Get-LatestApk)"
    }
    'install' {
        $devices = @(Require-Devices)
        $apk = Get-LatestApk
        foreach ($device in $devices) {
            Invoke-Adb -Device $device -Arguments @('install', '-r', $apk) | Out-Host
            Write-Host "Installed SARtak on $device"
        }
        Write-Warning 'After a first install, enable SARtak once in ATAK Plugin Manager.'
    }
    'launch' {
        foreach ($device in @(Require-Devices)) { Start-Atak $device }
    }
    'setup-two-device' {
        $devices = @(Require-Devices 2)
        if ($devices.Count -ne 2) { throw 'Specify exactly two devices with -Serial when more are connected.' }
        Start-Atak $devices[0]; Start-Atak $devices[1]
        Start-Sleep -Seconds 8
        Send-DebugBroadcast $devices[0] 'DEBUG_RESET_FIXTURE' $null $null
        Send-DebugBroadcast $devices[1] 'DEBUG_RESET_FIXTURE' $null $null
        Set-TestRole $devices[0] 'TEAM_LEAD'
        Set-TestCallsign $devices[0] 'QA-LEAD'
        Set-TestRole $devices[1] 'TEAM_MEMBER'
        Set-TestCallsign $devices[1] 'QA-MEMBER'
        Invoke-Adb -Device $devices[0] -Arguments @('emu', 'geo', 'fix',
            "$Longitude", "$Latitude", "$Altitude") | Out-Null
        Invoke-Adb -Device $devices[1] -Arguments @('emu', 'geo', 'fix',
            "$($Longitude + 0.00015)", "$Latitude", "$Altitude") | Out-Null
        Open-Tab $devices[0] 'TEAM'; Open-Tab $devices[1] 'TEAM'
        Write-Host "Configured $($devices[0]) as QA-LEAD and $($devices[1]) as QA-MEMBER."
    }
    'reset-fixture' {
        foreach ($device in @(Require-Devices)) {
            Send-DebugBroadcast $device 'DEBUG_RESET_FIXTURE' $null $null
            Write-Host "Reset active SARtak operation/team fixture on $device"
        }
    }
    'open-tab' { foreach ($device in @(Require-Devices)) { Open-Tab $device $Tab } }
    'set-role' { foreach ($device in @(Require-Devices)) { Set-TestRole $device $Role } }
    'set-callsign' {
        if (-not $Callsign) { throw '-Callsign is required.' }
        foreach ($device in @(Require-Devices)) { Set-TestCallsign $device $Callsign }
    }
    'move' {
        foreach ($device in @(Require-Devices)) {
            Invoke-Adb -Device $device -Arguments @('emu', 'geo', 'fix',
                "$Longitude", "$Latitude", "$Altitude") | Out-Null
            Write-Host "Moved $device to $Latitude, $Longitude"
        }
    }
    'disconnect' {
        foreach ($device in @(Require-Devices)) {
            Invoke-Adb -Device $device -Arguments @('shell', 'svc', 'wifi', 'disable') | Out-Null
            Invoke-Adb -Device $device -Arguments @('shell', 'svc', 'data', 'disable') | Out-Null
        }
    }
    'reconnect' {
        foreach ($device in @(Require-Devices)) {
            Invoke-Adb -Device $device -Arguments @('shell', 'svc', 'wifi', 'enable') | Out-Null
            Invoke-Adb -Device $device -Arguments @('shell', 'svc', 'data', 'enable') | Out-Null
        }
    }
    'capture' {
        foreach ($device in @(Require-Devices)) { Save-Screenshot $device $OutputDirectory }
    }
    'clear-logs' { foreach ($device in @(Require-Devices)) { Invoke-Adb -Device $device -Arguments @('logcat', '-c') | Out-Null } }
    'collect-logs' {
        New-Item -ItemType Directory -Force -Path $OutputDirectory | Out-Null
        $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
        foreach ($device in @(Require-Devices)) {
            $path = Join-Path $OutputDirectory "$stamp-$device-logcat.txt"
            & $Adb -s $device logcat -d -v threadtime | Select-String -Pattern `
                'SARtak|plugintemplate|Ditto|AndroidRuntime|FATAL EXCEPTION' |
                Set-Content -Path $path -Encoding utf8
            Write-Host "Saved $path"
        }
    }
}
