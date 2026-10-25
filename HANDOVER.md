# Handover Scope

This branch starts from local `main` commit `b7e3349` and performs packaging
cleanup followed by removal of the build-time Ditto credential fallback.
Runtime profiles now come only from on-device setup or imported operations.
The full project history and removed material remain on `main`; this is not a
history purge.

## Known HQ Access Issue

A participating device can assume the HQ role and modify the operation
without separate authorisation. This is a known unresolved issue that
should be fixed by restricting HQ privileges and enforcing permission
checks for operation changes. Include
[KNOWN_ISSUES.md](KNOWN_ISSUES.md#unauthorised-hq-access) with the handover
and bring this limitation to OreFox's attention.

The original `sartak-handover-2026-10-24` tag predates this note. Supply
the updated issue notes alongside that snapshot or use the later
documentation commit.

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

Include the current [known-issue record](KNOWN_ISSUES.md), particularly
the unresolved HQ access issue, in the files supplied to OreFox. Record any client
acknowledgement separately; this note is not evidence of acceptance.

1. Build and run the unit tests using `SETUP.md` and the intended SDK.
2. Build a generic APK. Ditto build-time fallback inputs are no longer supported;
   existing private local.properties files are ignored and are not distributed.
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

## Packaging Verification - 23 October 2026

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

## Personal Configuration Removal

- Removed Ditto BuildConfig fields and all runtime fallback to developer
  local.properties credentials. Local/environment Ditto settings cannot seed a
  profile or be compiled into the APK by this build.
- Fresh installations have no preset Ditto profile. HQ must configure a profile
  on-device; joining devices can import the operation's QR/join code as before.
- Existing on-device profiles/operations are deliberately preserved on upgrade.
  Removing the fallback does not erase a profile already stored on a device.
- Checked tracked text files for personal filesystem paths, device identities,
  credential literals and matches to this machine's private Ditto values. No
  actual account values were found in tracked source. Removed personal branch
  naming from the maintenance notes; test identities and SDK debug passwords are
  generic test/development values.
- The ignored local.properties and private signing keys remain local and are
  not part of the branch/export. Only the standard credential-free example is
  distributed. Do not copy local caches or old APKs into a delivery.
- This does not purge Git history or revoke credentials in previously produced
  APKs/join codes. Rotate previously shared development tokens in Ditto before
  wider delivery if those tokens should no longer grant access.

Credential-removal verification: clean debug build and all 856 tests passed
(73 suites, no failures/errors/skips), using the existing private local build
configuration. Generated BuildConfig contains no Ditto credential fields. The
rebuilt APK's 58 non-native/non-signature entries were checked in UTF-8/UTF-16
against the local database ID, token and auth URL; no values matched. No APK was
installed and no device profiles were erased during this change.
