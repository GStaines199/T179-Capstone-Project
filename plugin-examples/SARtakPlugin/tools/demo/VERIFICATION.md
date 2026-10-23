# Demo harness verification - 19 October 2026

Executor: Codex using ADB against emulator-5556 and emulator-5554. Application code was not changed. This verifies the demo tools and initial preparation, not the complete operation/team/search workflow.

| Check | Method | Result | Evidence |
| --- | --- | --- | --- |
| Python and PowerShell scripts parse | Executed syntax checks | PASS | Python py_compile and PowerShell parser on all scripts |
| Start separation and common 100 m cell | Executed geometry assertions | PASS | demo.ps1 self-test; UTM 56J E249500 N7080000 |
| Distinct ATAK identities | Executed on-device UI inspection | PASS | DURB ANDROID-713b442f971e0e4d; PYOTE ANDROID-f1679e14883fbe4c |
| HQ/member roles and callsigns | Executed on-device UI inspection | PASS | runs/prepared/20261019-162547-emulator-5556.png and 20261019-162546-emulator-5554.png |
| Existing saved Ditto profiles retained | Executed on-device UI inspection | PASS | HQ SARtak_Test; member SAR Operation; neither has an active operation |
| GPS injection, movement and pause | Executed controller commands | PASS | Worker positions changed during rehearsal; pause supplied zero speed; GPS errors empty |
| Member lag/hold controls | Executed controller commands | PASS | Rehearsal state showed member distance behind HQ; hold mode retained stationary member |
| Resume after distance checkpoint | Executed controller commands | PASS | Corrected checkpoint behavior; subsequent resume returned moving=true |
| Both emulator recordings finalize | Executed recording/pull and MP4 container checks | PASS | runs/20261019-162055/ |
| Automatic recording rollover | Executed with six-second rehearsal clips | PASS | runs/20261019-162441/; three clips per emulator with nonzero MP4 durations and valid ftyp/moov/mdat boxes |
| Preparation with Home scrolled down | Executed on both emulators after scrolling HQ Home | PASS | Updated readiness wait scrolled the panel, found identity by resource ID, and verified both roles/callsigns |
| Preparation after fresh ATAK launch | Executed after force-stopping ATAK on both emulators | PASS | Setup waited through plugin loading, then verified DURB/HQ and PYOTE/Team Member without reinstalling or clearing data |
| New operation, route matching, invites, search progression, alerts and acknowledgements | Not executed in this preparation | NOT TESTED | Follow demo-run.ps1 for the first complete recorded take |

The rollover test inspected MP4 structure and duration, not frame-by-frame playback. Recordings are separate device captures with timestamps; they are not frame-synchronized and have short rollover gaps. Full-route simulated walking was not run. Final state: both emulators stationary at their configured starts, HQ/member roles, saved profiles intact, no active operation, recorder stopped, stationary GPS worker active with no reported errors.

Generated evidence and videos live in the gitignored runs directory and are not part of the shared source files.
