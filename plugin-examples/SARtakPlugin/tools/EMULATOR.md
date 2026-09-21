# ATAK emulator setup (verified)

Verified on 2026-07-30 with ATAK CIV **4.6.0.5** (`atak-civ-sdk-4.6.0.5`), Android Emulator
**36.5.11**, Windows 11, AMD Ryzen 5 9600X + Radeon RX 9060 XT, WHPX acceleration.

ATAK boots to the 3D globe and the SARtakPlugin apk is detected as compatible.

## Quick start

```bash
powershell -ExecutionPolicy Bypass -File tools/atak-emulator.ps1 all
```

That creates the AVD, boots it, installs ATAK, launches it, writes the GL workaround file and
restarts ATAK. Individual steps: `create`, `start`, `provision`, `install`, `run`, `fixgl`,
`plugin`, `logcat`, `reset`.

## AVD configuration

| Setting | Value | Why |
| --- | --- | --- |
| API level | 30 (Android 11), Google APIs | ATAK 4.6 targets SDK 30 |
| ABI | **x86** | ATAK ships `arm64-v8a`, `armeabi-v7a`, `x86` — x86 is the only one that runs natively here |
| RAM | 3072 MB | A 32-bit x86 guest cannot use more; higher values destabilise it |
| VM heap | 512 MB | ATAK's map tiles OOM the 192 MB default |
| Cores | 4 | Leaves host cores for Gradle |
| Data partition | 12 GB | ATAK apk is ~200 MB, map/DTED caches grow fast |
| Resolution | 1280x800, 213 dpi, landscape | ATAK's UI is tablet-oriented; keeps software GL smooth |
| Camera / mic | off | Emulated camera and mic are a common emulator crash source |

## The two things that actually stop ATAK crashing

### 1. `-feature GLESDynamicVersion` (guest OpenGL ES 3.x)

Without it the guest is capped at **GLES 2.0** (`ro.opengles.version=131072`) no matter which GPU
mode you pick, and ATAK dies seconds after the splash screen:

```
E AndroidRuntime: FATAL EXCEPTION: GLThread
java.lang.IllegalArgumentException: eglChooseConfig failed
    at android.opengl.GLSurfaceView$BaseConfigChooser.chooseConfig
```

With the flag, `ro.opengles.version` becomes `196609` (ES 3.1) and EGL returns usable configs.
This is also written to `%USERPROFILE%\.android\advancedFeatures.ini` so the AVD behaves the same
when launched from Android Studio's Device Manager.

### 2. `/sdcard/atak/opengl.broken`

With ES3 available but this file missing, ATAK's own config chooser rejects every emulator config:

```
java.lang.IllegalArgumentException: No config chosen
```

The marker file selects ATAK's relaxed render path. Two details matter:

- Write it **after** ATAK's first launch. ATAK creates `/sdcard/atak` itself; pre-creating that
  directory from `adb` makes ATAK's storage migration fail
  (`E FileSystemUtils: failed migration of /storage/emulated/0/com.atakmap.map`).
- Restart ATAK after writing it.

```bash
adb shell "echo emulator > /sdcard/atak/opengl.broken"
```

## First-run wizard

ATAK's native code uses direct file paths, so it blocks on a **"File System Access Changes"**
dialog until all-files access is granted. The `install` step grants it headlessly:

```bash
adb shell appops set com.atakmap.app.civ MANAGE_EXTERNAL_STORAGE allow
```

Runtime permissions (location, storage, phone state, camera, mic) are granted the same way, so
ATAK goes straight to the map.

## GPU mode

Both of these were verified to render the map without crashing:

- `-gpu host` — real GPU, fastest. **Default.**
- `-gpu swiftshader_indirect` — software, slower but immune to GPU driver quirks
  (`tools/atak-emulator.ps1 start -Software`).

`-gpu guest` does **not** work with emulator 36.x: it logs *"the system image does not support
guest rendering"* and silently falls back. Older guides recommending `-gpu guest` predate this.

## Launching it day to day

Both settings live in files the emulator reads on every launch — `hw.gpu.mode=host` in the AVD's
`config.ini` and `GLESDynamicVersion = on` in `%USERPROFILE%\.android\advancedFeatures.ini` — so
**no command-line flags are needed**. Verified: a bare launch reports `ro.opengles.version=196609`
(ES 3.1) on the host GPU, and ATAK runs.

**Android Studio:** Device Manager > green arrow. Works as-is. Verified.

**Terminal (PowerShell), detached so it doesn't block the shell:**

```powershell
Start-Process "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -ArgumentList '-avd','ATAK_API30_x86'
```

**Terminal, blocking (Ctrl+C stops the emulator), handy when you want to watch its log:**

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd ATAK_API30_x86
```

**Via this script** (adds the flags explicitly, then waits for boot before returning):

```powershell
powershell -ExecutionPolicy Bypass -File tools\atak-emulator.ps1 start
```

To put `emulator` and `adb` on PATH permanently:

```powershell
[Environment]::SetEnvironmentVariable('Path', "$([Environment]::GetEnvironmentVariable('Path','User'));$env:LOCALAPPDATA\Android\Sdk\emulator;$env:LOCALAPPDATA\Android\Sdk\platform-tools", 'User')
```

Then `emulator -avd ATAK_API30_x86` works from any new terminal.

### Cold boot vs quick boot

The AVD uses quick boot (`fastboot.forceFastBoot=yes`), so it restores from a snapshot and starts
in seconds. That is fine for ATAK. Cold boot when the emulator gets into a bad state — Device
Manager > the `⋮` menu > **Cold Boot Now**, or from a terminal:

```powershell
& "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" -avd ATAK_API30_x86 -no-snapshot-load
```

Cold boot keeps your data (installed ATAK, plugins, `opengl.broken`). Only
`tools\atak-emulator.ps1 reset` wipes it, which means reinstalling ATAK afterwards.

## Plugin workflow

```bash
powershell -ExecutionPolicy Bypass -File tools/atak-emulator.ps1 plugin
```

Builds `assembleCivDebug`, installs the apk, restarts ATAK. ATAK registers the plugin but leaves
it unloaded until you enable it in **Settings > Tool Preferences > Plugins**. Confirm with:

```bash
adb logcat -d | findstr AtakPluginRegistry
```

**Reinstalling the apk resets that toggle.** ATAK treats an upgrade as an uninstall/reinstall and
sets `shouldLoad-<pkg>` back to `false`, so the plugin silently vanishes from the Tools menu after
every `install`. The descriptor still loads cleanly — it is not a load failure, and logcat shows
`!should load, skipping` rather than an error. Re-enable it in the plugin preferences, or headlessly
with ATAK stopped, then restart ATAK.

The plugin panel cannot be opened with `adb shell am broadcast`: `SHOW_PLUGIN` is registered on
ATAK's internal `AtakBroadcast` bus, not the system one, so the broadcast returns `result=0` and
nothing happens. Open it through the UI (Tools menu > SARtak).

To read the plugin's own state without the UI, the track log is a plain SQLite database — the
quickest way to confirm points are, or are not, being written:

```bash
adb shell "su 0 sqlite3 /data/data/com.atakmap.app.civ/databases/sar_database.db 'SELECT COUNT(*) FROM location_points;'"
```

The plugin flavor must match the installed ATAK flavor — the SDK apk is **CIV**
(`com.atakmap.app.civ`), so build the `civ` flavor, not the default `mil`.

## Troubleshooting

| Symptom | Fix |
| --- | --- |
| `eglChooseConfig failed` | Missing `-feature GLESDynamicVersion` |
| `No config chosen` | Missing `/sdcard/atak/opengl.broken`, or ATAK wasn't restarted after writing it |
| ATAK crash loop after a bad session | `tools/atak-emulator.ps1 reset` (wipes data), then `install`, `run`, `fixgl` |
| Emulator won't start | `emulator -accel-check` — WHPX must report "installed and usable" |
| Plugin not listed in ATAK | Wrong flavor (`mil` vs `civ`), or plugin `ATAK_VERSION` != installed ATAK version |
| Plugin gone from Tools after reinstalling it | `shouldLoad-<pkg>` reset to `false`; re-enable in plugin preferences |
