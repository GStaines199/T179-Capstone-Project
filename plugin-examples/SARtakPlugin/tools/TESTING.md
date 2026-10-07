# SARtak two-device test harness

`sartak-test.ps1` reduces repeated build/install/setup work during emulator QA.
Its role, callsign, and tab broadcasts are accepted only by debug builds.

## First-time setup

1. Start two ATAK 5.6 development emulators.
2. Build and install SARtak:

   ```powershell
   .\tools\sartak-test.ps1 build
   .\tools\sartak-test.ps1 install
   ```

3. Enable SARtak once in ATAK's Plugin Manager on each fresh emulator.
4. Create a repeatable leader/member fixture:

   ```powershell
   .\tools\sartak-test.ps1 setup-two-device
   ```

Use `-Serial emulator-5554,emulator-5556` when other Android devices are
connected.

## Common commands

```powershell
# Show ATAK/SARtak installation status.
.\tools\sartak-test.ps1 status

# Open the same SARtak tab on both devices.
.\tools\sartak-test.ps1 open-tab -Tab DEVICES

# Clear the active operation, team, cached peers, and managed map markers.
# Saved Ditto credentials and the saved-operation catalogue are retained.
.\tools\sartak-test.ps1 reset-fixture

# Target one device and change its SARtak/ATAK test role.
.\tools\sartak-test.ps1 set-role -Serial emulator-5554 -Role HQ

# Inject a real emulator GPS fix through Android's location path.
.\tools\sartak-test.ps1 move -Serial emulator-5556 `
  -Latitude -27.4702 -Longitude 153.0255

# Exercise offline/recovery behavior.
.\tools\sartak-test.ps1 disconnect -Serial emulator-5556
.\tools\sartak-test.ps1 reconnect -Serial emulator-5556

# Save paired screenshots, cropped panel views, and filtered logs.
.\tools\sartak-test.ps1 capture
.\tools\sartak-test.ps1 collect-logs
```

Artifacts are written to `qa/artifacts`. Run `clear-logs` before a focused
test so failures are easier to isolate.

## Limits

- ATAK must already be running with SARtak enabled before debug broadcasts can
  control the panel.
- Callsign changes update the debug instance immediately, but ATAK may require
  a restart before every core screen reflects the new value.
- `disconnect` disables the emulator's Wi-Fi/data services. It does not disable
  Bluetooth, so it is useful for Ditto nearby/offline tests.
- `setup-two-device` runs `reset-fixture` before assigning the test roles and
  callsigns, so an older operation cannot contaminate a new QA run.
- Operation/team creation remains UI-driven. The harness intentionally avoids
  inserting fake operation records, so tests still exercise production logic.
