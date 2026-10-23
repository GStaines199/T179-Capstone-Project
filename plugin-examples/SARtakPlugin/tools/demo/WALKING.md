# Independent walking controls

Run these commands in this folder. No preparation, recording, role changes, operation changes, or app launch is needed. The first start reads the selected emulator's **current GPS position and altitude**, then moves from there. The old demo GPS feed is stopped first if it is still running, so it cannot keep pulling the devices back to its route. An active old demo recording must be finished first.

Device names: **lead** (or **hq**) = emulator-5556 / DURB; **member** = emulator-5554 / PYOTE; **both** selects both. Serials come from demo.json. Default direction is north and default speed is **1.2 m/s (4.3 km/h)**. A previously controlled device retains its speed and direction on resume. Each device moves in a straight line along its own heading; this does not automatically follow a SARtak planned route or its curves.

```powershell
# Start both from where they currently are, heading north at walking pace.
.\walk.ps1 start -Device both

# Stop/start either device independently.
.\walk.ps1 stop -Device member
.\walk.ps1 resume -Device member
.\walk.ps1 stop -Device lead
.\walk.ps1 resume -Device lead

# Set an exact speed (metres per second).
.\walk.ps1 speed -Device lead -Speed 1.5
.\walk.ps1 speed -Device member -Speed 0.7

# Increase/decrease speed by 0.2 m/s; optional -Step changes the increment.
.\walk.ps1 faster -Device lead
.\walk.ps1 slower -Device member
.\walk.ps1 faster -Device both -Step 0.5

# Change heading: 0 north, 90 east, 180 south, 270 west.
.\walk.ps1 direction -Device both -Bearing 180
.\walk.ps1 start -Device member -Speed 1.2 -Bearing 0

# Halt both; continue supplying fresh stationary GPS fixes.
.\walk.ps1 stop -Device both

# Read status and live device GPS without changing anything.
.\walk.ps1 status

# Send a last stationary fix and end the walking worker.
.\walk.ps1 shutdown
```

Stopped devices remain at the stopping position. Speed and direction commands do not resume a stopped device. Stopping a device that was never started here leaves it untouched. Start/resume reads the current fix again, so moving it manually while stopped is respected. Avoid manually setting another GPS route while it is walking or supplying stationary fixes; use shutdown first when handing location control back to the emulator UI.

Two devices can walk together at different speeds, producing a real gap for the SARtak search-line demo. These controls simulate the people only: use SARtak itself to pause/resume the search line, send alerts, and acknowledge them. When an alert should stop people, use `stop` as well. Keep using `walk.ps1` for movement rather than restarting the earlier `demo.ps1` route worker.

The controller runs in the background, supplying fixes about once per second to each device it controls. Closing the terminal does not stop it; use stop/shutdown. If ADB disconnects, movement on that device is paused and its error is shown by status. Reconnect the emulator and resume to take a fresh GPS reading. Local state/logs are in the gitignored runs/walk-* files. No Ditto credentials are read and no SARtak operation, team, grid or UI state is changed.
