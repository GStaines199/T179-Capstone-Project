# SARtak Handover

SARtak is an Android plugin for search-and-rescue coordination in ATAK CIV.
This branch contains the plugin source, tests, build tooling and one ATAK SDK
payload. It excludes coursework documents, duplicate sample projects, recorded
test evidence, demo scripts and local development environments.

## Start Here

1. Read [SETUP.md](SETUP.md) for prerequisites, building and installation.
2. Open `plugin-examples/SARtakPlugin` as the Android Studio project, not the
   repository root.
3. Read the [plugin overview](plugin-examples/SARtakPlugin/README.md).
4. Read [HANDOVER.md](HANDOVER.md) for cleanup decisions and remaining checks.

## Repository Layout

| Path | Purpose |
| --- | --- |
| `plugin-examples/SARtakPlugin/app/src/main` | SARtak runtime code and resources |
| `plugin-examples/SARtakPlugin/app/src/test` | Automated regression tests |
| `plugin-examples/SARtakPlugin/gradle` | Gradle wrapper |
| `plugin-examples/SARtakPlugin/tools` | Device QA harness and maintenance notes |
| `main.jar`, `atak-gradle-takdev.jar` | ATAK API and build plugin |
| `mapping.txt`, `proguard-release-keep.txt` | ATAK SDK build/shrinker inputs |
| `atak-javadoc.jar`, `espresso/` | SDK reference and optional test support |
| `LICENSE.txt`, `VERSION.txt` | Original SDK licence and version information |

The default plugin API target is `5.6.0`. Installing a debug APK requires an
appropriate ATAK development build; a matching version number alone does not
satisfy the production ATAK plugin signature checks.

Do not distribute personal Ditto credentials, private signing keys, device data
or `local.properties`. Configure Ditto profiles inside SARtak on the device.
