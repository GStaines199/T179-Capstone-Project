<#
.SYNOPSIS
    Create / start / provision an Android emulator that runs ATAK CIV 4.6 reliably.

.DESCRIPTION
    ATAK is an OpenGL-heavy app that ships 32-bit native libs (armeabi-v7a, arm64-v8a, x86).
    Verified working combination (ATAK CIV 4.6.0.5, emulator 36.5.11, Windows 11):

      * API 30 (Android 11), Google APIs, x86 ABI  -> matches ATAK's x86 .so files
      * -feature GLESDynamicVersion                -> exposes GLES 3.x to the guest.
                                                      Without it the guest is capped at ES 2.0 and
                                                      ATAK dies on its GL thread with
                                                      "IllegalArgumentException: eglChooseConfig failed".
      * /sdcard/atak/opengl.broken present         -> ATAK's own EGL config chooser otherwise rejects
                                                      every emulator config ("No config chosen").
                                                      This file selects its relaxed/safe render path.
      * tablet geometry, landscape, 1280x800       -> ATAK's UI is laid out for tablets, and software
                                                      GL stays smooth at this resolution.

    GPU: -gpu host (default here) is fastest and verified. If your GPU driver misbehaves,
    -Software switches to SwiftShader, which is also verified but slower.

    Usage:
      .\atak-emulator.ps1 create      # write the AVD (safe to re-run; -Force to overwrite)
      .\atak-emulator.ps1 start       # launch the emulator with the correct GPU flags
      .\atak-emulator.ps1 provision   # wait for boot, lock landscape, kill animations
      .\atak-emulator.ps1 install     # install / reinstall ATAK from the SDK apk
      .\atak-emulator.ps1 run         # start ATAK on the emulator
      .\atak-emulator.ps1 fixgl       # write /sdcard/atak/opengl.broken and restart ATAK
      .\atak-emulator.ps1 plugin      # install this plugin (civDebug) and restart ATAK
      .\atak-emulator.ps1 logcat      # tail ATAK + plugin logs
      .\atak-emulator.ps1 reset       # wipe emulator data (clean slate after a crash)
      .\atak-emulator.ps1 all         # create + start + provision + install + run
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('create', 'start', 'provision', 'install', 'run', 'fixgl', 'plugin', 'logcat', 'reset', 'all')]
    [string]$Command = 'all',

    [string]$AvdName = 'ATAK_API30_x86',
    [string]$AtakApk = "$env:USERPROFILE\Downloads\atak-civ-sdk-4.6.0.5\atak-civ\atak.apk",

    # Use SwiftShader instead of the host GPU (slower, but immune to GPU driver quirks).
    [switch]$Software,
    [switch]$Force
)

$ErrorActionPreference = 'Stop'

$Sdk      = if ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { "$env:LOCALAPPDATA\Android\Sdk" }
$Emulator = Join-Path $Sdk 'emulator\emulator.exe'
$Adb      = Join-Path $Sdk 'platform-tools\adb.exe'
$SysImage = 'system-images\android-30\google_apis\x86\'
$AvdHome  = "$env:USERPROFILE\.android\avd"
$AvdDir   = Join-Path $AvdHome "$AvdName.avd"
$AtakPkg  = 'com.atakmap.app.civ'

function Get-LaunchArgs {
    param([string[]]$Extra = @())
    $gpu = if ($Software) { 'swiftshader_indirect' } else { 'host' }
    @(
        '-avd', $AvdName,
        '-gpu', $gpu,
        '-feature', 'GLESDynamicVersion',  # REQUIRED: ES3 for ATAK's map view
        '-no-boot-anim',                   # shaves ~15 s off boot
        '-cores', '4',
        '-memory', '3072'
    ) + $Extra
}

function Assert-Sdk {
    if (-not (Test-Path $Emulator)) { throw "emulator.exe not found at $Emulator" }
    if (-not (Test-Path $Adb))      { throw "adb.exe not found at $Adb" }
    if (-not (Test-Path (Join-Path $Sdk $SysImage))) {
        throw "Missing system image. In Android Studio > SDK Manager > SDK Platforms > Android 11, tick 'Google APIs Intel x86 Atom System Image'."
    }
}

function Set-EmulatorFeatures {
    # Emulator-wide override, read on every launch including Android Studio's Device Manager
    # play button. Without GLESDynamicVersion the guest is capped at GLES 2.0 and ATAK crashes,
    # so this must exist even when the emulator is not started by this script.
    $featureFile = "$env:USERPROFILE\.android\advancedFeatures.ini"
    $existing = if (Test-Path $featureFile) { Get-Content $featureFile -Raw } else { '' }
    if ($existing -match '(?m)^\s*GLESDynamicVersion\s*=\s*on\s*$') { return }
    if ($existing -match '(?m)^\s*GLESDynamicVersion\s*=') {
        ($existing -replace '(?m)^\s*GLESDynamicVersion\s*=.*$', 'GLESDynamicVersion = on') |
            Out-File -FilePath $featureFile -Encoding ascii
    } else {
        @"
$existing
# Expose the host GPU's OpenGL ES 3.x to the guest instead of capping it at ES 2.0.
# ATAK 4.6's map view asks EGL for an ES3 config and dies with "eglChooseConfig failed" without it.
GLESDynamicVersion = on
"@.TrimStart() | Out-File -FilePath $featureFile -Encoding ascii
    }
    Write-Host "Enabled GLESDynamicVersion in $featureFile" -ForegroundColor Green
}

function Invoke-Create {
    Assert-Sdk
    Set-EmulatorFeatures
    if ((Test-Path $AvdDir) -and -not $Force) {
        Write-Host "AVD '$AvdName' already exists. Use -Force to recreate." -ForegroundColor Yellow
        return
    }
    if ($Force -and (Test-Path $AvdDir)) { Remove-Item $AvdDir -Recurse -Force }
    New-Item -ItemType Directory -Force -Path $AvdDir | Out-Null

    @"
avd.ini.encoding=UTF-8
path=$AvdDir
path.rel=avd\$AvdName.avd
target=android-30
"@ | Out-File -FilePath (Join-Path $AvdHome "$AvdName.ini") -Encoding ascii

    # ---- Tuned for ATAK: 32-bit x86 guest, software GL, tablet landscape ----
    # hw.ramSize 3072: a 32-bit guest cannot use more, and >3 GB destabilises it.
    # vm.heapSize 512: ATAK loads large map tiles; the 192 MB default OOMs.
    # disk.dataPartition.size 12G: ATAK apk is ~200 MB and map/DTED caches grow fast.
    # hw.gpu.mode host: real GPU. Stored in the AVD so Android Studio's Device Manager
    #   play button uses the same fast path as this script.
    # camera/audio off: emulated camera + mic are a common source of emulator crashes.
    @"
AvdId=$AvdName
PlayStore.enabled=false
abi.type=x86
avd.ini.displayname=ATAK Tablet API 30 (x86)
avd.ini.encoding=UTF-8
disk.dataPartition.size=12G
fastboot.forceChosenSnapshotBoot=no
fastboot.forceColdBoot=no
fastboot.forceFastBoot=yes
hw.accelerometer=yes
hw.arc=false
hw.audioInput=no
hw.audioOutput=no
hw.battery=yes
hw.camera.back=none
hw.camera.front=none
hw.cpu.arch=x86
hw.cpu.ncore=4
hw.dPad=no
hw.gps=yes
hw.gpu.enabled=yes
hw.gpu.mode=host
hw.initialOrientation=landscape
hw.keyboard=yes
hw.lcd.density=213
hw.lcd.height=800
hw.lcd.width=1280
hw.mainKeys=no
hw.ramSize=3072
hw.sdCard=yes
hw.sensors.orientation=yes
hw.sensors.proximity=no
hw.trackBall=no
image.sysdir.1=$SysImage
runtime.network.latency=none
runtime.network.speed=full
sdcard.size=2048M
showDeviceFrame=no
skin.dynamic=yes
tag.display=Google APIs
tag.id=google_apis
vm.heapSize=512
"@ | Out-File -FilePath (Join-Path $AvdDir 'config.ini') -Encoding ascii

    Write-Host "Created AVD '$AvdName'." -ForegroundColor Green
}

function Invoke-Start {
    Assert-Sdk
    if ((& $Adb devices) -match 'emulator-\d+\s+device') {
        Write-Host 'An emulator is already running.' -ForegroundColor Yellow
        return
    }
    Write-Host "Starting $AvdName (1-3 min on first boot)..." -ForegroundColor Cyan
    Start-Process -FilePath $Emulator -ArgumentList (Get-LaunchArgs)
    Invoke-WaitForBoot
}

function Invoke-WaitForBoot {
    Write-Host 'Waiting for boot...' -NoNewline
    & $Adb wait-for-device | Out-Null
    for ($i = 0; $i -lt 120; $i++) {
        if ((& $Adb shell getprop sys.boot_completed 2>$null).Trim() -eq '1') {
            Write-Host ' booted.' -ForegroundColor Green
            return
        }
        Write-Host '.' -NoNewline
        Start-Sleep -Seconds 3
    }
    throw 'Emulator did not finish booting in 6 minutes.'
}

function Invoke-Provision {
    Assert-Sdk
    Invoke-WaitForBoot

    # Keep the screen on and stay in landscape; rotation mid-session can crash ATAK's map view.
    & $Adb shell settings put system accelerometer_rotation 0 | Out-Null
    & $Adb shell settings put system user_rotation 1         | Out-Null
    & $Adb shell settings put system screen_off_timeout 2147483647 | Out-Null
    & $Adb shell settings put global window_animation_scale 0     | Out-Null
    & $Adb shell settings put global transition_animation_scale 0 | Out-Null
    & $Adb shell settings put global animator_duration_scale 0    | Out-Null

    Write-Host 'Provisioned: landscape locked, animations off, screen stays on.' -ForegroundColor Green
}

function Invoke-FixGl {
    # ATAK's EGL config chooser rejects every emulator config unless this marker file
    # exists, and then dies with "No config chosen" on its GL thread.
    # Write it *after* ATAK's first run: ATAK creates /sdcard/atak itself, and pre-creating
    # that directory from adb makes ATAK's storage migration fail.
    Assert-Sdk
    $sdcard = (& $Adb shell ls /sdcard 2>$null) -join ' '
    if ($sdcard -notmatch '(^|\s)atak(\s|$)') {
        Write-Host 'ATAK has not created /sdcard/atak yet - launch ATAK once and let it settle.' -ForegroundColor Yellow
        return
    }
    & $Adb shell "echo emulator > /sdcard/atak/opengl.broken"
    if (((& $Adb shell ls /sdcard/atak) -join ' ') -notmatch 'opengl.broken') {
        throw 'Failed to create /sdcard/atak/opengl.broken'
    }
    & $Adb shell am force-stop $AtakPkg | Out-Null
    Start-Sleep -Seconds 2
    Invoke-Run
    Write-Host 'opengl.broken in place; ATAK restarted on its safe render path.' -ForegroundColor Green
}

function Invoke-Install {
    Assert-Sdk
    if (-not (Test-Path $AtakApk)) {
        throw "ATAK apk not found at $AtakApk. Pass -AtakApk <path to atak.apk> from the ATAK CIV SDK."
    }
    Invoke-WaitForBoot
    Write-Host 'Installing ATAK (200 MB, ~1-2 min)...' -ForegroundColor Cyan
    & $Adb install -r -g $AtakApk
    if ($LASTEXITCODE -ne 0) { throw 'ATAK install failed.' }

    # Grant up front so the first-run permission wizard cannot stall the app.
    $perms = @(
        'android.permission.ACCESS_FINE_LOCATION', 'android.permission.ACCESS_COARSE_LOCATION',
        'android.permission.READ_EXTERNAL_STORAGE', 'android.permission.WRITE_EXTERNAL_STORAGE',
        'android.permission.READ_PHONE_STATE', 'android.permission.RECORD_AUDIO',
        'android.permission.CAMERA', 'android.permission.ACCESS_BACKGROUND_LOCATION'
    )
    foreach ($p in $perms) { & $Adb shell pm grant $AtakPkg $p 2>$null | Out-Null }

    # ATAK's native code uses direct file paths, so it demands all-files access and blocks on a
    # "File System Access Changes" dialog until it is granted. appops grants it without the dialog.
    & $Adb shell appops set $AtakPkg MANAGE_EXTERNAL_STORAGE allow | Out-Null

    Write-Host 'ATAK installed; permissions and all-files access granted.' -ForegroundColor Green
}

function Invoke-Run {
    Assert-Sdk
    & $Adb shell monkey -p $AtakPkg -c android.intent.category.LAUNCHER 1 | Out-Null
    Write-Host 'ATAK launched. Accept the EULA / device-setup wizard on first run.' -ForegroundColor Green
}

function Invoke-Plugin {
    Assert-Sdk
    Push-Location (Split-Path $PSScriptRoot -Parent)
    try {
        & .\gradlew.bat assembleCivDebug
        if ($LASTEXITCODE -ne 0) { throw 'Plugin build failed.' }
        $apk = Get-ChildItem 'app\build\outputs\apk\civ\debug\*.apk' | Select-Object -First 1
        if (-not $apk) { throw 'No civ/debug apk found under app\build\outputs\apk.' }
        & $Adb install -r $apk.FullName
        if ($LASTEXITCODE -ne 0) { throw "Plugin install failed for $($apk.Name)" }
        Write-Host "Installed $($apk.Name)" -ForegroundColor Green
    } finally { Pop-Location }
    & $Adb shell am force-stop $AtakPkg | Out-Null
    Invoke-Run
    Write-Host 'In ATAK: Settings > Tool Preferences > Plugins to enable/load the plugin.' -ForegroundColor Cyan
}

function Invoke-Logcat {
    Assert-Sdk
    & $Adb logcat -v time ATAK:V ATAKMapEngine:V SARtak:V AndroidRuntime:E DEBUG:E '*:S'
}

function Invoke-Reset {
    Assert-Sdk
    Write-Host 'Wiping emulator data...' -ForegroundColor Yellow
    & $Adb emu kill 2>$null | Out-Null
    Start-Sleep -Seconds 3
    Start-Process -FilePath $Emulator -ArgumentList (Get-LaunchArgs @('-wipe-data', '-no-snapshot-load'))
    Invoke-WaitForBoot
    Invoke-Provision
}

switch ($Command) {
    'create'    { Invoke-Create }
    'start'     { Invoke-Start }
    'provision' { Invoke-Provision }
    'install'   { Invoke-Install }
    'run'       { Invoke-Run }
    'fixgl'     { Invoke-FixGl }
    'plugin'    { Invoke-Plugin }
    'logcat'    { Invoke-Logcat }
    'reset'     { Invoke-Reset }
    'all'       {
        Invoke-Create; Invoke-Start; Invoke-Provision; Invoke-Install; Invoke-Run
        # ATAK's first run builds /sdcard/atak; it may crash on GL before opengl.broken exists.
        Start-Sleep -Seconds 25
        Invoke-FixGl
    }
}
