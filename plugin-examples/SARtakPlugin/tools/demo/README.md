# Two-device SARtak demonstration

Run from this directory in PowerShell. Requires Python 3 and Android SDK Platform Tools; no additional Python packages. Both emulators must have ATAK and the SARtak debug APK loaded, unique ATAK UIDs, and their existing Ditto profiles. Device serials, names, start location and movement settings are in `demo.json`.

## First recorded take

```powershell
.\demo.ps1 prepare
.\demo-run.ps1
```

`prepare` launches ATAK, sets DURB / HQ and PYOTE / Team Member, opens SARtak, checks distinct UIDs, injects the starting GPS fixes, and starts continuous stationary GPS feeds. It retains profiles and saved operations. The guided run records both devices and waits for Enter between chapters while you perform operation/team/route/alert actions visibly in SARtak. These UI actions are not automatically clicked. GPS movement and video capture run concurrently without chat delays. You can freely interact with either emulator.

Preparation waits up to 60 seconds for each SARtak Home screen after launching ATAK, retries setup broadcasts while the plugin loads, and scrolls Home back to the identity when needed. It reads the identity by resource ID rather than assuming that every UID begins with ANDROID-. If readiness still fails, a credential-free startup diagnostic is saved under `runs/startup-SERIAL.json`; the error distinguishes a visible plugin from one whose UI never appeared. No reinstall or data reset is performed.

For a free-form take, use `.\demo.ps1` for the control menu, or `.\demo-start.ps1` to start recording. Finish with `.\demo-stop.ps1` to save recordings and stop GPS updates. `record-stop` saves recordings but keeps GPS active. Closing the menu does not stop the background worker.

## Scene and movement

- HQ/Lead: `emulator-5556`, DURB, start `-26.377529, 150.4900933`.
- Member: `emulator-5554`, PYOTE, 50 m away in the same actual 100 m UTM cell.
- The starting cell is UTM **56J E249500 N7080000**. Both positions are calculated before sending fixes. `demo.ps1 plan` prints exact coordinates and selection corners.
- The altitude of 350 m is a simulator setting, not a surveyed terrain height.
- Create a **new operation** named SARtak Field Demo using HQ's saved Ditto profile. Joining with its code also gives the member that operation's credentials; the existing saved profiles are retained. Do not show join codes/tokens in published footage.
- HQ plans a **4 x 4 km rectangle** around its position using Grid > Plan Search Area. Verify both devices receive it before continuing.
- Switch HQ to Team Lead, create Bravo, invite PYOTE, and accept on the member. Check both rosters.
- Select the current cell plus four columns east and four rows north, forming a **5 x 5-cell** route area. The two corner-centre coordinates are printed by `plan` and saved to `runs/plan.json`.
- Generate the matching north-first serpentine route in SARtak. The simulator's route is computed independently; it does not create a SARtak plan or overwrite grid data.
- Start the search line in SARtak before starting movement. The entire simulator route is about 2.7 km / 37 minutes at 1.2 m/s. The guided take demonstrates its first 550 m, about eight minutes of walking plus setup and narration.
- GPS speed and bearing are supplied through the emulator's real location input. Stationary feeds supply zero speed. Changing `-Speed` changes the reported physical pace; accelerated footage should be labelled accordingly.
- The app decides PARTIAL/COMPLETE status and synchronizes it normally. Exited cells should become PARTIAL; mark selected exited cells COMPLETE through the leader UI. The controller does not fabricate progress, sync, invitations or alerts.

## Individual controls

```powershell
.\demo.ps1 status
.\demo.ps1 open -Device hq -Tab OPERATION
.\demo.ps1 lead                         # change HQ role to Team Lead
.\demo.ps1 open -Tab GRID               # both devices
.\demo.ps1 move -Distance 230            # pause at 230 m along route
.\demo.ps1 lag                          # member walks at quarter speed
.\demo.ps1 hold-member                  # member stops, leader continues
.\demo.ps1 pause                        # both stop, GPS stays fresh
.\demo.ps1 resume
.\demo.ps1 catch-up                     # member at 1.7x until alongside
.\demo.ps1 move -Distance 550 -Speed 1.2 # continue to next checkpoint
.\demo.ps1 chapter -Name 'Hold acknowledged'
.\demo.ps1 reset-position               # GPS back to start; no app data reset
.\demo.ps1 record-stop
.\demo.ps1 stop
```

Distance is cumulative from the original start, not an additional distance. Use `move` with a new distance checkpoint after one is reached. `move` without `-Distance` walks the entire route. `pause` and `resume` control simulated people; they do not press SARtak's Pause/Resume buttons. During a hold-alert scene, send the alert in SARtak, pause GPS, acknowledge on the member, then resolve the actual alert before resuming GPS. This makes both the app state and the simulated people stop.

Resetting GPS does not clear a route or cell statuses. For another clean recording create a new operation in SARtak, join the member to that operation, and create a new team. Do not reuse an already searched operation expecting blank progress.

## Recordings

Videos are saved under `runs/YYYYMMDD-HHMMSS/`. Each emulator has numbered MP4 clips, a clip timing manifest and a concat list. `timeline.json` records commands and scene markers for editing. Recordings begin within separate ADB launches; use these timestamps to align them, not an assumption of frame-exact synchronization. No audio is captured: record narration separately.

Android screenrecord clips rotate every 170 seconds. Pulling/finalizing a clip introduces a short gap, so this recorder is suitable for edited demo footage. For a continuous live presentation, record the two visible emulator windows with a desktop recorder instead. If FFmpeg is available, concatenate an emulator's clips from inside the run folder:

```powershell
ffmpeg -f concat -safe 0 -i emulator-5556-concat.txt -c copy HQ-Lead.mp4
ffmpeg -f concat -safe 0 -i emulator-5554-concat.txt -c copy Member.mp4
```

The script leaves the original device clips intact if a PC transfer fails. They are named `/sdcard/sartak-demo-...mp4` and can be recovered with `adb -s SERIAL pull REMOTE_FILE LOCAL_FILE`. Recording errors are exposed by `status`; check them before a final take.

## Recovery

`runs/worker.log` and `demo.ps1 status` show GPS/recording errors. Only one background worker can own the controls, enforced by an OS file lock. If an emulator disconnects, the worker reports failures and retries the GPS feed when it returns. ADB access to an offline emulator does not affect the other device. A locked or unresponsive ATAK UI is still a real app issue; GPS success alone does not prove SARtak processed a fix.

Stop the worker before editing `demo.json`; then run `prepare` again. Keep the PC awake. `stop` signals only screenrecord processes started by this harness, finalizes/pulls the videos, sends a last stationary fix and exits. It does not force-stop ATAK or erase user data.

Outputs under `runs/` are gitignored. Only scripts/configuration/documentation belong in version control. Run `.\demo.ps1 self-test` for the geometry check; emulator GPS/recording still requires a rehearsal.
