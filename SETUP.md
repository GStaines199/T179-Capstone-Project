# Build and Run SARtak

## Prerequisites

- JDK 17, with `JAVA_HOME` pointing to the JDK directory, not its `bin` folder.
- Android Studio / Android SDK, including Android API 36 and platform-tools.
- Internet access for the first Gradle and Maven dependency downloads.
- For development testing: a compatible ATAK CIV 5.6 development installation.
  The plugin API requirement is `5.6.0`, not the full ATAK patch version.

The checked-in wrapper uses Gradle 8.14.3. The build pins Android Gradle Plugin
8.13.0, Kotlin 2.2.0 and Ditto Android SDK 5.0.0. The Android minimum API is 24.
The ATAK SDK payload is at the repository root; keep the directory layout intact.

## Local Configuration

Open a terminal in `plugin-examples/SARtakPlugin`:

```powershell
Copy-Item .\local.properties.example .\local.properties
```

Edit `sdk.dir` to your Android SDK path. The default build-plugin path resolves
to the checked-in `atak-gradle-takdev.jar`; uncomment `takdev.plugin` only to use
another SDK payload. Never commit `local.properties`.

The default debug signing key is `$env:USERPROFILE\.android\debug.keystore`.
Android Studio normally creates it. If it is absent, create it once:

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\.android"
keytool -genkeypair -v -keystore "$env:USERPROFILE\.android\debug.keystore" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

Do not overwrite an existing key. Set the optional signing properties only when
using a different key. Never include private keys in a handover archive.

## Build and Test

```powershell
.\gradlew.bat :app:testCivDebugUnitTest :app:assembleCivDebug
```

The APK is generated in `app\build\outputs\apk\civ\debug`. Use `--offline`
only after the required Gradle/dependency versions have been downloaded.

## Install

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
$apk = Get-ChildItem .\app\build\outputs\apk\civ\debug\*.apk | Sort-Object LastWriteTime -Descending | Select-Object -First 1
& $adb -s YOUR_DEVICE_SERIAL install -r $apk.FullName
```

Open ATAK, enable SARtak in Plugin Manager, and open its tool. Debug builds and
self-signed release builds are not automatically approved for store/release
ATAK. Production deployment requires the appropriate ATAK plugin approval and
signing process; this branch does not provide that approval or a private key.

## First Operation

1. Give every device a unique ATAK UID and a recognisable callsign.
2. Set the organiser's ATAK role to `HQ`.
3. In SARtak, add/select the organisation's Ditto profile and create an operation.
4. Join from other devices using its QR/join code. Set their ATAK roles to
   `Team Lead` or `Team Member` as appropriate.
5. Confirm devices, map positions, team membership and grid changes on both ends.

Ditto profiles can be configured on-device; personal credentials are not needed
at compile time. Join codes contain sync credentials and must be treated as
sensitive. Stage development-profile devices with internet access before testing
nearby/offline sync. No-TAK-server testing alone does not prove fully offline or
multi-hop connectivity.

For repeatable device checks see `tools/TESTING.md`. For large-grid and recovery
notes see `tools/PERFORMANCE_AND_RECOVERY.md`. No device state is reset by building.
