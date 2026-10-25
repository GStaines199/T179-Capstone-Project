# SARtak client handover

SARtak is an Android plugin for search-and-rescue coordination in ATAK CIV.
This repository provides OreFox with the source code, automated tests and build
instructions needed to maintain the delivered plugin. It supports HQ, Team Lead
and Team Member workflows for operations, teams, search grids, routes, search
lines, alerts and tracks.

## Known HQ access issue

A device participating in an operation can assume the HQ role and modify
the operation without separate authorisation. This is a known unresolved
issue that should be fixed by restricting HQ privileges and checking
permissions before operation changes are applied. See
[KNOWN_ISSUES.md](KNOWN_ISSUES.md#unauthorised-hq-access).

## Start here

1. Download the tested APK and guides from the
   [handover release](https://github.com/GStaines199/T179-Capstone-Project/releases/tag/sartak-handover-2026-10-24).
2. Read [SETUP.md](SETUP.md) for prerequisites, building, installation and first
   operation setup. The User Guide covers day-to-day operation; the Technical
   Handover Guide explains architecture and maintenance. Both are release assets.
3. For development, open `plugin-examples/SARtakPlugin` in Android Studio.
4. Read the [plugin overview](plugin-examples/SARtakPlugin/README.md) and
   [handover scope notes](HANDOVER.md).

## Code and delivered version

| Item | Reference |
| --- | --- |
| Maintained handover tree | [handover branch](https://github.com/GStaines199/T179-Capstone-Project/tree/handover) |
| Tested APK source | [4c21e7dabfac886b8403c270e31750a38852c35e](https://github.com/GStaines199/T179-Capstone-Project/tree/4c21e7dabfac886b8403c270e31750a38852c35e) |
| Handover snapshot | [sartak-handover-2026-10-24](https://github.com/GStaines199/T179-Capstone-Project/tree/sartak-handover-2026-10-24) |
| Plugin version | `1.0 (4c21e7da) - [5.6.0]` |
| APK | `ATAK-Plugin-SARtakPlugin-1.0-4c21e7da-5.6.0-civ-debug.apk` |
| APK SHA256 | `37c871a09e348ac37850d2e42524b8c80a950bfec914c7d83ef31d9838d5cde8` |

The original handover tag includes the initial client README and predates the
25 October known-issue documentation. Use the maintained handover branch for the
updated documentation, and provide its current known-issue notes alongside
any delivery of the original tag or APK. The tested APK was built from the
earlier commit identified above; subsequent documentation updates do not
change runtime code or build inputs. The repository's default branch is
currently `main`; use the `handover` branch for the current source handover.

## Get the source and build

```powershell
git clone --branch handover https://github.com/GStaines199/T179-Capstone-Project.git SARtak
cd SARtak/plugin-examples/SARtakPlugin
Copy-Item .\local.properties.example .\local.properties
```

Set `sdk.dir` in your local file, then follow the signing-key preparation in
[SETUP.md](SETUP.md). Build and run the automated tests with:

```powershell
.\gradlew.bat :app:testCivDebugUnitTest :app:assembleCivDebug
```

Use JDK 17 and Android SDK API 36. The Android minimum API is 24. Keep the root
ATAK SDK files in place. Prefer a Git clone: a GitHub source ZIP has no `.git`
metadata, so its generated version information can differ; see [HANDOVER.md](HANDOVER.md).
A new build will also reflect the checked-out handover commit rather than the
older tested APK commit. To rebuild from the tested source, check out
`4c21e7da` and retain the current known-issue notes with the delivery.

## Tested environment and evidence

The delivered APK was installed and loaded with ATAK CIV development build
`5.6.0.23 (51b827db)` on two Android 17/API 37 emulators and a Samsung
SM-T636B tablet running Android 13/API 33. It uses the ATAK plugin API `5.6.0`.
Its Android debug signature is not production ATAK plugin approval; installing
it on a production ATAK build can require a different approval/signing process.

Final validation recorded 856 JVM tests across 73 suites, with no failures,
errors or skips. The separate functional/static review selected 55 cases:
30 passed, 5 failed, 8 were partial, 1 was blocked and 11 were not tested.
Unit-test success does not establish that all device workflows pass.

Known limitations and remaining checks include:

- **Unauthorised HQ access:** a participating device can assume HQ and
  modify the operation. This known issue should be fixed; see
  [KNOWN_ISSUES.md](KNOWN_ISSUES.md#unauthorised-hq-access).
- Peer role and roster labels can disagree between devices.
- Raw GNSS storage performs synchronous SQLite work from a main-looper callback;
  its runtime latency has not been measured.
- Saving an incomplete Ditto profile closes the editor and loses its draft.
- Repeated join prompts and stale ended-line guidance need further investigation.
- Eight-hour endurance, battery targets, full physical-device regression and a
  clean-install workflow have not been fully verified on the delivered APK.

Earlier field testing reported nearby connectivity in airplane mode over an
estimated practical range of 30-50 metres. This is a team estimate from earlier
testing, not a controlled range measurement for this APK. A stale indication at
around 100 metres was delayed and does not establish a reliable 100-metre range.

## Configuration and repository contents

Configure the organisation's Ditto profile inside SARtak. Fresh installations
contain no preset account; upgrades retain saved on-device profiles. The build
does not read or embed Ditto credentials from `local.properties` or environment
variables. Join codes carry sync credentials and should be shared only with
intended participants. Development/offline use still depends on authentication,
permissions and available nearby transports; follow [SETUP.md](SETUP.md).

| Path | Purpose |
| --- | --- |
| `plugin-examples/SARtakPlugin/app/src/main` | Runtime code and resources |
| `plugin-examples/SARtakPlugin/app/src/test` | Automated regression tests |
| `plugin-examples/SARtakPlugin/gradle` | Gradle wrapper |
| `plugin-examples/SARtakPlugin/tools` | Device QA harness and maintenance notes |
| `main.jar`, `atak-gradle-takdev.jar` | ATAK API and build plugin |
| `mapping.txt`, `proguard-release-keep.txt` | SDK build/shrinker inputs |
| `atak-javadoc.jar`, `espresso/` | SDK reference and optional test support |
| `LICENSE.txt`, `VERSION.txt` | Original SDK licence and version information |

The handover tree excludes coursework, duplicate samples, recorded device test
evidence and local development environments. Earlier history remains in this
repository. Do not commit personal credentials, private signing keys, device
data or `local.properties`. Previously distributed development tokens must be
rotated if they should no longer grant access; cleaning a branch does not revoke
them. ATAK SDK and dependency licensing continues to apply; inclusion here does
not grant additional redistribution rights.

The release and links record the delivered files. They do not record client
acceptance or a transfer of repository ownership.
