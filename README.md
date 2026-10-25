# SARtak client handover

SARtak is an Android plugin for search-and-rescue coordination in ATAK CIV.
This repository contains the source code, automated tests and build inputs for
OreFox to maintain the plugin. It supports HQ, Team Lead and Team Member
workflows for operations, teams, search grids, routes, search lines, alerts and
tracks.

Use this README for setup, build instructions and known issues. The tested APK,
User Guide, Technical Handover Guide and detailed validation evidence are
packaged separately from the source repository.

## Source and delivered version

| Item | Reference |
| --- | --- |
| Current handover source | [handover branch](https://github.com/GStaines199/T179-Capstone-Project/tree/handover) |
| Tested APK source | [4c21e7dabfac886b8403c270e31750a38852c35e](https://github.com/GStaines199/T179-Capstone-Project/tree/4c21e7dabfac886b8403c270e31750a38852c35e) |
| Original handover snapshot | [sartak-handover-2026-10-24](https://github.com/GStaines199/T179-Capstone-Project/tree/sartak-handover-2026-10-24) |
| Plugin version | `1.0 (4c21e7da) - [5.6.0]` |
| Tested APK | `ATAK-Plugin-SARtakPlugin-1.0-4c21e7da-5.6.0-civ-debug.apk` |
| APK SHA256 | `37c871a09e348ac37850d2e42524b8c80a950bfec914c7d83ef31d9838d5cde8` |

The repository's default branch is `main`; use `handover` for this delivery.
The original tag predates the latest documentation and the HQ access issue
noted below. Supply this README alongside any delivery of that snapshot or APK.
Later documentation commits do not change the tested APK. A new build reflects
the checked-out commit; check out `4c21e7da` to rebuild from the tested source.

## Build and install

### Requirements

- JDK 17, with `JAVA_HOME` pointing to the JDK directory.
- Android Studio and Android SDK API 36, including platform-tools.
- Internet access for the first Gradle and Maven dependency downloads.
- A compatible ATAK CIV 5.6 development installation; the plugin API is `5.6.0`.

The project uses Gradle 8.14.3, Android Gradle Plugin 8.13.0, Kotlin 2.2.0 and
Ditto Android SDK 5.0.0. The minimum Android API is 24.

### Get the source and configure the build

```powershell
git clone --branch handover https://github.com/GStaines199/T179-Capstone-Project.git SARtak
cd SARtak/plugin-examples/SARtakPlugin
Copy-Item .\local.properties.example .\local.properties
```

Set `sdk.dir` in `local.properties` to your Android SDK path. Keep the root ATAK
SDK files in place: the default build-plugin path resolves to
`../../atak-gradle-takdev.jar`. Use the optional `takdev.plugin` setting only
when deliberately supplying another SDK build plugin.

Open `plugin-examples/SARtakPlugin` in Android Studio. Prefer a Git checkout:
the ATAK build plugin uses Git to generate version information. A source ZIP
without `.git` metadata can fall back to SDK version defaults. For an archive,
initialise and commit a new local Git repository before building.

### Debug signing key

The default key is `$env:USERPROFILE\.android\debug.keystore`. Android Studio
normally creates it. If the key is absent, create it once:

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\.android" | Out-Null
keytool -genkeypair -v -keystore "$env:USERPROFILE\.android\debug.keystore" -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
```

Do not overwrite an existing key. Use the optional signing properties in the
configuration example when using another key. Private signing keys and
`local.properties` are local files and are excluded from source delivery.

### Build and test

Run from `plugin-examples/SARtakPlugin`:

```powershell
.\gradlew.bat :app:testCivDebugUnitTest :app:assembleCivDebug
```

The APK is generated in `app\build\outputs\apk\civ\debug`. Use `--offline`
only after the required dependencies have been downloaded.

### Install

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb devices
$apk = Get-ChildItem .\app\build\outputs\apk\civ\debug\*.apk | Sort-Object LastWriteTime -Descending | Select-Object -First 1
& $adb -s YOUR_DEVICE_SERIAL install -r $apk.FullName
```

Open ATAK, enable SARtak in Plugin Manager and open its tool. Debug or
self-signed APKs are not automatically approved for production ATAK. Production
deployment requires the appropriate ATAK plugin approval and signing process.

## First operation and configuration

1. Give each device a unique ATAK UID and a recognisable callsign.
2. Set the organiser's ATAK role to `HQ`. This role selection is not an
   independently authorised grant of operation permissions; see Known issues.
3. In SARtak, add/select the organisation's Ditto profile and create an operation.
4. Join other devices using the operation's QR/join code, and set their roles
   to `Team Lead` or `Team Member` as appropriate.
5. Confirm devices, map positions, team membership and grid changes on both ends.

Ditto profiles are configured on-device. A fresh installation has no preset
account; upgrades retain saved profiles. The build does not read or embed Ditto
credentials from `local.properties` or environment variables. Join codes carry
sync credentials and should be shared only with intended participants.

Stage development-profile devices with internet access before testing nearby
or offline sync. Connectivity depends on authentication, permissions and the
available transports. Successful operation without a TAK server does not by
itself establish fully offline or multi-hop operation.

## Known issues

**Unauthorised HQ access:** a device participating in an operation can assume
the HQ role and modify the operation without separate authorisation. This is
a known unresolved issue that should be fixed by restricting HQ privileges
and enforcing permission checks before operation changes are applied.
Selecting an ATAK role alone should not grant operation-management authority.

Other limitations and remaining checks are:

- Peer role and roster labels can disagree between devices.
- Raw GNSS storage performs synchronous SQLite work from a main-looper callback;
  its runtime latency has not been measured.
- Saving an incomplete Ditto profile closes the editor and loses its draft.
- Repeated join prompts and stale ended-line guidance need further investigation.
- Eight-hour endurance, battery targets, full physical-device regression and a
  clean-install workflow have not been fully verified on the delivered APK.

## Validation summary

The delivered APK was installed and loaded with ATAK CIV development build
`5.6.0.23 (51b827db)` on two Android 17/API 37 emulators and a Samsung SM-T636B
tablet running Android 13/API 33. Final validation recorded 856 JVM tests across
73 suites, with no failures, errors or skips. The separate functional/static
review selected 55 cases: 30 passed, 5 failed, 8 were partial, 1 was blocked and
11 were not tested. Unit-test success does not establish that every device
workflow passes.

Earlier physical field testing reported a practical nearby connection range
of approximately 30-50 metres in airplane mode. This is a team estimate rather
than a controlled measurement for the final APK. The stale indication around
100 metres was delayed and does not establish a reliable 100-metre range.
Refer to the separately supplied validation evidence for detailed results.

## Source and maintenance

| Location | Purpose |
| --- | --- |
| `plugin-examples/SARtakPlugin/app/src/main` | Application code, Android manifest and resources |
| `plugin-examples/SARtakPlugin/app/src/test` | Automated regression tests |
| `plugin-examples/SARtakPlugin/gradle`, `gradlew`, `gradlew.bat` | Build wrapper; launcher scripts are inside the plugin project |
| `plugin-examples/SARtakPlugin/local.properties.example` | Configuration template |
| `plugin-examples/SARtakPlugin/tools` | Device QA harness and performance/recovery notes |
| `main.jar`, `atak-gradle-takdev.jar` | ATAK API and build plugin |
| `mapping.txt`, `proguard-release-keep.txt` | SDK build/shrinker inputs |
| `LICENSE.txt`, `VERSION.txt` | Original SDK licence and version information |

The [plugin overview](plugin-examples/SARtakPlugin/README.md) describes the code
layout and roles. The [testing notes](plugin-examples/SARtakPlugin/tools/TESTING.md)
explain the debug-only QA harness; read them before commands that change device
state. Preserve the [performance and recovery notes](plugin-examples/SARtakPlugin/tools/PERFORMANCE_AND_RECOVERY.md)
when changing rendering or recovery behaviour.

Deliver a clean Git checkout or tracked-file export. Local caches, generated
build output, personal configuration, private keys and device data do not form
part of the source handover. Earlier files remain in Git history; cleaning the
current tree does not remove historical material or revoke previously shared
credentials. Rotate earlier development tokens if they should no longer grant
access. Original ATAK SDK and dependency licensing continues to apply.
