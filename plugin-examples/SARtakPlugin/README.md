# SARtak ATAK Plugin

SARtak adds search-and-rescue coordination tools to ATAK 5.8 CIV. The plugin
uses ATAK for the base map, self location, role/callsign identity, and CoT
markers, then adds SARtak-specific operation, grid, team, search-line, track,
and Ditto sync views.

## Current Capabilities

- Operation setup with a local operation profile.
- Operation join code and QR display.
- Optional Ditto SDK sync for SARtak operation data.
- Search grid overlay snapped to stable 100 m UTM cells.
- Manual grid cell status marking.
- Team setup, invites, requests, and member removal.
- Search line sync between joined devices.
- Devices tab showing known ATAK/Ditto devices.
- Local track and grid/team persistence scoped to the active operation.

## Generic Deployment Model

The plugin APK can be installed on any compatible ATAK 5.8 CIV device. A normal
volunteer device should not need your personal Ditto credentials compiled into
its build.

There are two setup roles:

1. Operation organiser / team leader build
   - Opens SARtak `Home` -> `Ditto Setup`.
   - Adds one or more Ditto credential profiles.
   - Selects the profile for the current organisation/search.
   - Creates the SARtak operation.
   - Shares the generated QR/code with other devices.

2. Volunteer / joining device
   - Installs the same SARtak APK.
   - Opens SARtak in ATAK.
   - Joins the operation by entering the QR/code contents.
   - Receives the operation sync profile from that code.
   - Saves the imported Ditto profile locally for future use.

The join code currently contains the Ditto development sync details for the
operation. This is acceptable for prototype testing, but not a production
security model.

## Ditto Account Setup

For current prototype testing:

1. Create a Ditto Portal account.
2. Create a Ditto database/app.
3. Open the database/app `Connect` tab.
4. Copy the SDK connection values:
   - Database ID
   - Auth URL
   - Development or Online Playground token
5. In SARtak, open `Home` -> `Ditto Setup` -> `Add New Ditto Profile`.
6. Enter:
   - Profile name
   - Database ID
   - Auth URL
   - Development or Online Playground token
7. Select that profile before creating an operation.

Developers can still put these values in `local.properties` as a build-time
fallback, but normal users should use the in-plugin Ditto Setup section.

## Offline Behaviour

With the current Ditto development setup, devices should be staged while they
still have internet access so Ditto can authenticate. After that, devices in
the same operation can continue syncing locally when nearby, depending on
available transports such as LAN/Wi-Fi, Wi-Fi Aware, or Bluetooth LE.

For a stronger field deployment model, SARtak should eventually support a
proper organisation-owned Ditto setup or Ditto offline shared-key operation
profiles instead of relying on one developer's portal credentials.

## Build Setup

Create `local.properties` from `local.properties.example` and set:

- `sdk.dir`
- `takdev.plugin`
- optional Ditto values for operation creation

Then build:

```powershell
.\gradlew.bat :app:assembleCivDebug
```

The debug APK is generated under:

```text
app\build\outputs\apk\civ\debug
```

## Install

Use the full path to `adb` if PowerShell cannot find it:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r "app\build\outputs\apk\civ\debug\ATAK-Plugin-SARtakPlugin-1.0-<git>-5.8.0-civ-debug.apk"
```

## Files That Must Stay Local

Do not commit:

- `local.properties`
- Ditto tokens
- private keystores
- generated APKs/AABs
- local Gradle/build output

The root `.gitignore` already excludes these.

## Current Limitations

- QR generation is implemented, but in-app camera scanning is not yet
  implemented. Join code paste/import remains available.
- Ditto development credentials are suitable for testing, not production.
- True no-internet first-time onboarding would need Ditto offline shared-key or
  a managed operational credential flow.
- ATAK CoT and Ditto sync are both used; reliability should still be tested on
  physical devices, not only emulators.
