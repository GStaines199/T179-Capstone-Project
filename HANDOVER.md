# Handover Scope

This branch starts from local `main` commit `b7e3349` and performs packaging
cleanup only. SARtak runtime code and resources are unchanged. The full project
history and removed material remain on `main`; this is not a history purge.

## Removed from the Handover Tree

- Word field-test checklists, meeting notes, the Figma design file and SDK PDF
  guides; no coursework or meeting documents are required to build SARtak.
- `atak-civ/`: duplicate SDK payload with its own sample projects.
- `plugin-examples/helloworld/` and `plugin-examples/plugintemplate/`: standalone
  samples, not dependencies of SARtak.
- Tracked IDE settings and the tracked SARtak Python `.venv`.
- `qa/`: historical screenshots, UI dumps and log captures; tests themselves stay.
- `tools/demo/`: recording and scripted walking demonstrations, kept on `main`.
- The legacy ATAK 4.6 emulator setup script/guide and redundant
  `template.local.properties`; use the current setup guide and example instead.
- The historical root crash dump and field-test Markdown checklist.

Ignored machine-local files may still exist in an existing checkout. They are not
part of a clean clone/export. Do not zip the whole working directory: that could
include local credentials, keys, generated APKs, caches and device data.

## Kept Deliberately

- Complete SARtak main sources, resources, manifest and regression tests.
- Gradle wrapper/build files and one credential-free local configuration example.
- Root ATAK `main.jar`, build-plugin JAR, mapping file and shrinker rules.
  The build plugin explicitly resolves these root SDK inputs.
- Original SDK licence/version files. This repository includes SDK artifacts,
  not the full ATAK application's source or an installable ATAK distribution.
- Minimal Markdown build/maintenance notes and the two-device QA harness.

## Optional Files / Owner Decisions

These have been retained rather than silently dropping useful support:

| File or directory | Reason to retain / decision |
| --- | --- |
| `atak-javadoc.jar` | API reference, not required for the debug compile. Remove only if the recipient does not need SDK reference material. |
| `espresso/` | Original SDK instrumentation support; current SARtak unit tests do not have matching instrumented tests. Can be omitted for a strictly runtime/build-only delivery. |
| `app/src/test/` | Not installed on users' devices, but important for maintaining the handover. Recommended to keep. |
| `tools/sartak-test.ps1`, `tools/TESTING.md` | Optional debug-only device QA; recommended to keep for the receiving developers. |
| `tools/PERFORMANCE_AND_RECOVERY.md` | Maintenance context for performance-sensitive rendering and recovery. Recommended to keep. |

Confirm which SDK artifacts the recipient is entitled to receive under the
applicable distribution arrangements. No new distribution permission or release
ATAK approval is implied by this cleanup.

## Before Delivery

1. Build and run the unit tests using `SETUP.md` and the intended SDK.
2. Build a generic APK without private Ditto build-time fallback values. Existing
   local.properties files are not modified by this cleanup.
3. Verify operation join/restart, team membership, map positions, grid progress,
   search lines and alerts on the target physical devices.
4. Confirm production credentials, access policy and ATAK signing separately.
5. Deliver a clean tracked-tree export, not this machine's entire working folder.
   Review Git history before sharing the repository itself; removed documents or
   previously committed secrets are not erased by deleting files on this branch.

The ATAK build plugin uses Git to generate version metadata. Build from a clean
Git checkout where possible. For a source-only archive, initialise a new local
Git repository and commit the imported files before building; an archive without
Git metadata falls back to SDK version defaults and prints Git warnings.

## Verification - 23 October 2026

- Exported only the staged handover files to a new temporary directory.
- Used JDK 17 and the installed Android SDK, without local.properties, private
  Ditto fallback values, existing project build output or the duplicate SDK tree.
- Ran `:app:testCivDebugUnitTest :app:assembleCivDebug --offline` successfully:
  49 tasks executed; 853 tests across 73 suites, zero failures/errors/skips.
- Confirmed runtime sources/resources and retained build dependency files are
  unchanged from the starting main revision.
- Existing manifest/deprecation/native-symbol warnings remain. The source-only
  snapshot also printed the Git-metadata warnings described above.
- No emulator/physical-device workflows were exercised during this packaging
  change. The temporary validation APK was not installed on any device and is
  not part of the handover source tree.
