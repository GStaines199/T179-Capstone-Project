# SARtak performance and recovery (emulator results)

Sprint tasks covered (Performance and Stability):

1. Profile ATAK while a large SARtak search area is displayed.
2. Confirm that large grids only render map content that is currently needed.
3. Test recovery after ATAK is closed, restarted or interrupted by Android.

Tested 2026-10-03 on the `ATAK_API30_x86` emulator (ATAK 5.6.0, 4 vCPU),
using George's branch at `48c4faf` as the baseline. Physical devices were not
used; their numbers will differ, but every problem found here is per map item
and gets worse on slower phones, not better.

## How to reproduce

Debug builds log two tags:

```
adb logcat -s SARtakPerf SARtakRecovery
```

- `SARtakPerf` logs every overlay render: time per overlay, how many map items
  each SARtak overlay holds, and how many of those are off screen.
- `SARtakRecovery` logs a one-line snapshot of the operation, team, search
  area, cell progress, route, search line, overlays and tracking when SARtak
  starts (`restored`), once sync is up (`synced`) and when it shuts down
  (`disposing`). Comparing `disposing` with the next `synced` shows exactly
  what a restart lost.

Stress setup used for every run: HQ plans a 20 km x 20 km box (40,401 cells),
5,000 cells are marked complete and 400 partial (a few hours of lane
searching), and a Team Leader generates the 603-cell Auto Route.

## 1. Profile with a large search area

Same scripted session on both builds: open SARtak, zoom through every level,
then pan 30 times. Render times are main-thread time spent in SARtak overlay
code.

| | Baseline | Fixed |
|---|---|---|
| Total main-thread render time | 18,544 ms | 303 ms |
| Slowest single render | 7,524 ms | 104 ms (first render after a cold start) |
| Renders over one frame (16 ms) | 6 | 1 |
| "ATAK isn't responding" (ANR) | 1 | 0 |
| Grid overlay map items (peak) | 498 | 7 |
| Route overlay map items (peak) | 500 | 1 |
| Biggest frame drop | 462 frames skipped | none after ATAK's own startup |

Individual baseline renders measured in isolation: 4,454 ms at 15 m/px and
9,803 ms at 8 m/px, which is what triggered the ANR.

Steady-state CPU of the ATAK process once the large area is loaded and
everything has settled (60 one-second samples; 400% would be all 4 cores),
map at 8 m/px with the whole planned area in reach:

| | Baseline (2 runs) | Fixed | Fixed, grid overlay off |
|---|---|---|---|
| ATAK CPU, average | 28.2% / 28.8% | 18.4% | 18.4% |
| SARtak map items | 654 grid + 500 route | 7 grid + 1 route | 1 route |
| Slowest render | 9,383 / 9,790 ms, ANR both runs | 56 ms | - |

At 1 m/px the fixed build measured 17.7% / 19.5% with the grid on and 18.0% /
19.1% with it off, so the overlay no longer adds measurable CPU on top of ATAK.
CPU averaged over the scripted session above is not comparable between the
builds: the baseline spent much of it frozen behind the ANR dialog.

Memory (PSS) was within noise between builds (Java heap 67-92 MB, native heap
~215 MB, both dominated by ATAK itself).

### Root cause

Every SARtak cell, grid line and route dash was an ATAK `DrawingShape`.
Creating one builds a centre marker, and that marker queries terrain
elevation through native code on the UI thread (stack from the ANR trace:
`DrawingShape.setClosed -> EditablePolyline.setShapeMarker -> getCenter ->
ElevationManager.getElevation`). That is ~15 ms per item on the emulator, and
the overlays rebuilt every item whenever anything changed.

## 2. Does a large grid only render what is needed?

Baseline: no.

- The visible-cell list was cut off at 600 cells from the bottom-left corner,
  so on a 20 km area the 100 m grid was drawn in a small patch, the selected
  cell was not drawn at mid zoom, and searched cells outside those 600 never
  appeared at all.
- The zoomed-out summary box and the "Planned area: x complete" counts were
  computed from the same 600 cells, so they were wrong for large areas.
- The route drew up to 500 dash items and the team areas up to 600 cell items
  regardless of what was on screen; up to 388 of the 500 route items were off
  screen in the profile run.

Fixed:

- Map items are plain ATAK `Polyline`s (no centre marker, no elevation
  query), and an item is only recreated when it actually changes.
- The 100 m grid is two polylines covering the visible area plus half a
  screen of margin, so short pans need no work at all.
- Searched cells are merged into rectangles (5,401 cells became 3 items) and
  only rectangles in that window are drawn.
- Nothing walks every cell of the planned area; counts come from the stored
  statuses, so they cover the whole area exactly (UI now reads "5000
  complete, 401 partial, 0 in progress, 40401 cells total").
- The route is one dashed polyline; team areas are merged rectangles (603
  assigned cells became 1 item).

## 3. Recovery after ATAK is closed, restarted or interrupted

State before each test: Team Leader, operation active, team created, 20 km
area, 5,400 cell statuses, 603-cell route, grid overlay, labels and team
areas on, search line active. "Baseline" here is the build just before the
recovery changes; the startup and persistence code it runs is unchanged from
George's branch.

| Scenario | How | Baseline | Fixed |
|---|---|---|---|
| A. Quit ATAK | Tools > Quit, relaunch | Line, grid overlay and team areas lost | Everything restored |
| B. Killed by Android | `kill -9` (what the low-memory killer does), relaunch | Same 3 lost | Everything restored |
| C. Force stop | `am force-stop`, relaunch | Not run (same startup path as A and B) | Everything restored |
| D. Interrupted | Another app in front for 60 s, then back | Not run | Same process, nothing rebuilt or lost |
| E. Killed while line paused | Pause, `kill -9`, relaunch | Not run (line is always lost) | Restored as PAUSED |
| F. Killed after ending line | End line, `kill -9`, relaunch | Not run | Line stays ended |
| G. Killed right after marking a cell | Mark Complete, `kill -9` 0.5 s later | Not run (unchanged code) | Status kept (5000 -> 5001 complete) |
| H. Screen off 30 s | Sleep, wake | Not run | Same process, overlays still drawn |
| I. Plugin reinstalled | Uninstall and reinstall the SARtak APK while ATAK runs | Grid overlay and team areas lost (line not active at the time) | Everything restored, active line included |

"Everything" means the `synced` snapshot after restart matched the
`disposing` snapshot (or the known state) field for field: operation, role,
team, area, cell counts, selected cell, route, search line state, overlay
visibility and track recording. No crashes in any scenario.

What was wrong in the baseline:

- The leader's search line existed only in memory and in sync messages that
  expire after 30 s (ATAK's cold start alone takes about that long), so a
  leader whose ATAK restarted came back to "Line not started" while the team
  was still searching. It is now saved locally, scoped to the operation and
  team, and restored under the leader's control; ending the line clears it.
- Overlay switches (grid, grid labels, route, team areas) were not saved, so
  the search area vanished from the map after every restart until someone
  found the switch again.

## Not covered here

- Physical-device numbers, battery drain and long (2-4 h) runs.
- Team member restart while the leader keeps searching: needs a second
  device. Members already re-read the leader's line from sync, and the leader
  republishes every 5 s.
