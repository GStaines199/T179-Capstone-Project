# SARtak Field Test Checklist - 3 Physical Devices

Date:
Location:
Tester:
Device 1 callsign / role:
Device 2 callsign / role:
Device 3 callsign / role:

Use this checklist to confirm SARtak works on real devices outside the emulator. Mark each item as Pass / Fail / Needs work and write notes.

## 1. Device Setup

- [ ] All devices have the same ATAK version installed.
- [ ] All devices have the same SARtak plugin APK installed.
- [ ] SARtak plugin appears as compatible and loaded in ATAK.
- [ ] Each device has a unique ATAK callsign.
- [ ] Each device has the correct ATAK role set:
  - Team Lead for leader device.
  - Team Member for member devices.
- [ ] Each device has GPS/location enabled.
- [ ] Each device can open the SARtak sidebar.
- [ ] SARtak Home tab shows correct GPS status.
- [ ] SARtak Home tab does not show fake location data.

Notes:


## 2. Operation Setup / QR Code

- [ ] Team Lead device can create a SARtak operation.
- [ ] Team Lead device can generate/show an operation QR code.
- [ ] Member device 1 can scan the operation QR code.
- [ ] Member device 2 can scan the operation QR code.
- [ ] After scanning, both member devices join the same operation.
- [ ] If QR scanning fails, paste join code works as fallback.
- [ ] App does not crash after scanning QR code.
- [ ] If ATAK closes/crashes after QR scan, note exact behaviour.

Notes:


## 3. Ditto / Mesh Sync

- [ ] Leader device shows Ditto active.
- [ ] Member device 1 shows Ditto active.
- [ ] Member device 2 shows Ditto active.
- [ ] Devices tab shows all 3 devices.
- [ ] Devices tab shows last update time for each device.
- [ ] Devices tab shows whether each device is connected through Ditto / ATAK / both.
- [ ] Devices continue to appear after waiting 30-60 seconds.
- [ ] Turning one device screen off does not immediately break sync.
- [ ] Restarting ATAK restores device visibility.

Notes:


## 4. Team Creation / Join Flow

- [ ] Team Lead device can create a team.
- [ ] Team name and team colour display correctly.
- [ ] Member device 1 can see the active team.
- [ ] Member device 2 can see the active team.
- [ ] Member device can request to join the team.
- [ ] Leader receives join request popup/card.
- [ ] Leader can accept join request.
- [ ] Accepted member sees they are now in the team.
- [ ] Leader sees accepted member in the team roster.
- [ ] Leader can invite a member.
- [ ] Invited member receives invite popup/card.
- [ ] Member can accept invite.
- [ ] Leader roster updates after invite acceptance.
- [ ] Leaving team removes member from active roster.
- [ ] Removing member from leader device updates member device.
- [ ] Removed/left member can request to rejoin.

Notes:


## 5. Map Markers / Colours

- [ ] All 3 devices appear on the ATAK map.
- [ ] Markers appear as live ATAK-style team/person markers where possible.
- [ ] Unassigned members use the unassigned/default colour.
- [ ] Members in the same SARtak team share the correct team colour/outline.
- [ ] Team leaders have distinct team colours.
- [ ] Marker colours do not flicker between ATAK colour and SARtak colour.
- [ ] Member device only shows expected devices for its role/team visibility mode.
- [ ] Leader device can switch visibility modes:
  - Just me.
  - My team.
  - Other team leaders.
  - Everyone.
- [ ] Hide/show callsigns works correctly.

Notes:


## 6. Search Grid

- [ ] Selecting current cell uses the current GPS position.
- [ ] Selected cell snaps to the correct 100 m grid square.
- [ ] Grid labels are readable using UTM + cell reference format.
- [ ] Grid labels can be hidden/shown.
- [ ] Grid overlay can be hidden/shown.
- [ ] Marking a cell partial updates locally.
- [ ] Marking a cell complete updates locally.
- [ ] Clearing a cell updates locally.
- [ ] Grid status syncs to other connected devices.
- [ ] Grid does not flicker heavily while panning/zooming.

Notes:


## 7. Search Lanes / Search Line

- [ ] Lane count includes the team leader plus all team members.
- [ ] Lanes remain inside the selected 100 m cell.
- [ ] Leader can start search line.
- [ ] Search line appears on member devices.
- [ ] Leader can pause search line.
- [ ] Search line pause syncs to member devices.
- [ ] Leader can resume search line.
- [ ] Search line resume syncs to member devices.
- [ ] Search line colour displays correctly.
- [ ] Distance-from-line values look realistic.
- [ ] Devices that are stationary do not show unrealistic pace values.

Notes:


## 8. Alerts

- [ ] Alerts tab appears on all devices.
- [ ] Leader can send Hold Position alert.
- [ ] Member devices receive popup alert.
- [ ] Member devices must acknowledge alert.
- [ ] Leader sees acknowledgement count update.
- [ ] Hold Position pauses the search line.
- [ ] Leader can clear Hold Position alert.
- [ ] Cleared alert disappears from member devices.
- [ ] Member can send Request Team Leader alert.
- [ ] Leader receives Request Team Leader alert.
- [ ] Alert includes sender callsign and location when available.
- [ ] Member can send Emergency Stop alert.
- [ ] All team devices receive Emergency Stop alert.
- [ ] Emergency Stop pauses search line.
- [ ] Reopening ATAK does not re-trigger old acknowledged alerts incorrectly.

Notes:


## 9. Tracks / Movement

- [ ] Track tab opens correctly.
- [ ] Track recording can be toggled.
- [ ] Track visibility can be toggled.
- [ ] Moving device creates visible track data.
- [ ] Stationary device does not produce unrealistic speed/pace.
- [ ] Clear track history works.
- [ ] Track data persists after closing/reopening ATAK where expected.

Notes:


## 10. Shared Markers / Photos

- [ ] Creating an ATAK marker on one device appears on other devices if supported.
- [ ] Quick Pic/photo marker appears locally.
- [ ] Quick Pic/photo marker syncs to other connected devices if supported.
- [ ] Marker title/location are preserved.
- [ ] Marker does not duplicate repeatedly.
- [ ] Removing or updating marker behaves as expected.

Notes:


## 11. Offline / Range Test

- [ ] Test devices with mobile data off.
- [ ] Test devices with Wi-Fi off, if safe/appropriate.
- [ ] Nearby devices still sync through available peer-to-peer method.
- [ ] Move one device farther away and note when sync becomes stale.
- [ ] Bring device back close and confirm sync recovers.
- [ ] If one device reconnects after being offline, team/grid/alert state catches up.

Notes:


## 12. Failure / Recovery

- [ ] Turn GPS off on one device.
- [ ] SARtak changes GPS status appropriately.
- [ ] ATAK does not show fabricated SARtak GPS data.
- [ ] Turn GPS back on and confirm SARtak updates.
- [ ] Close and reopen ATAK on one device.
- [ ] Device rejoins operation/team state correctly.
- [ ] Reboot one device and confirm recovery.
- [ ] App does not freeze during normal use.
- [ ] App does not crash during QR scanning.
- [ ] App does not crash during invite/request/alert workflows.

Notes:


## 13. Performance / Stability

- [ ] ATAK map remains responsive while SARtak is open.
- [ ] SARtak tab switching is responsive.
- [ ] Map panning/zooming is responsive.
- [ ] No long freezes when Ditto sync is active.
- [ ] Battery drain seems reasonable during test period.
- [ ] Device temperature remains acceptable.
- [ ] No repeated duplicate notifications.
- [ ] No repeated duplicate team/member cards.

Notes:


## 14. Evidence To Capture

Take screenshots or short videos of:

- [ ] Plugin loaded and compatible in ATAK.
- [ ] Home tab showing operation/GPS/Ditto status.
- [ ] Devices tab showing all 3 devices.
- [ ] Team creation on leader device.
- [ ] Member join request and leader acceptance.
- [ ] All team members visible on map.
- [ ] Grid selected and snapped to current GPS cell.
- [ ] Grid status change syncing between devices.
- [ ] Search line started on leader and visible on members.
- [ ] Alert popup appearing on member device.
- [ ] Alert acknowledgement count updating on leader device.
- [ ] Offline/nearby sync working without TAK server.
- [ ] Any bug/crash/unexpected behaviour.

Notes:


## Bugs Found

1.

2.

3.

4.

5.


## Overall Result

- [ ] Ready for demo
- [ ] Mostly working, minor fixes needed
- [ ] Major issues found

Summary:

