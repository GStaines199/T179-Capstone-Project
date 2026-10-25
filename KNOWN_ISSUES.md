# SARtak known issues

This file records unresolved limitations for the OreFox source handover.
The entry below was added on 25 October 2026 after the dated final-validation
run. It does not alter that run's test results or finding counts.

## HQ-001 Unauthorised HQ access

| Field | Recorded position |
| --- | --- |
| Status | Open - identified but unresolved |
| Affected delivery | Reported for the delivered implementation: tested source `4c21e7dabfac886b8403c270e31750a38852c35e`, APK `ATAK-Plugin-SARtakPlugin-1.0-4c21e7da-5.6.0-civ-debug.apk` |
| Team risk assessment | Likelihood 2 x impact 4 = 8 (Moderate) |
| Response | Reduce - further development required |
| Evidence | Project-team report and the bounded source inspection described below; no new device reproduction was performed for this documentation update |

### Behaviour and effect

A device with access to an operation can assume the HQ role and obtain
operation-management controls without a separate authorisation decision.
A participant may therefore make accidental or unauthorised changes to shared
operation information, disrupting coordination and reducing confidence in the
displayed state.

This finding concerns a participant that already has operation access. Access
by an unrelated device without the operation's configuration was not established
by this review. The full set of affected actions and their cross-device effects
still require device testing.

### Source references

- [AtakRoleResolver.java](plugin-examples/SARtakPlugin/app/src/main/java/com/atakmap/android/plugintemplate/runtime/AtakRoleResolver.java)
  resolves the device role from local ATAK preferences and identity metadata.
- [SARTakMapController.java](plugin-examples/SARtakPlugin/app/src/main/java/com/atakmap/android/plugintemplate/SARTakMapController.java)
  implements `canManageOperation()` and `canManageSearchArea()` by returning
  `isHqRole()`. The inspected checks do not independently verify that the
  device has been authorised to manage the current operation.
- `archiveOperation()` uses `canManageOperation()` as its permission gate.
  This is a source observation, not a newly executed archive test.

### Current operating guidance

Agree which devices should act as HQ and share operation access only with
intended, trusted participants. This procedure does not enforce permissions or
prevent a participating device from selecting HQ. The issue remains unresolved
in the submitted implementation.

### Required development work

- Define how an operation owner grants and revokes HQ authority for an
  identified participant.
- Check that authority when operation-management and search-area changes are
  applied, including synchronised changes. Hiding controls alone is insufficient.
- Define persistence and reconnection behaviour for grants and revocations,
  and add regression coverage before describing the issue as fixed.

### Proposed checks before closure

These checks are planned; they have not been executed as part of this update.
Use a disposable test operation and record the devices, build and results.

1. Join the same operation with an approved HQ device and a Team Member device.
2. Change the member's local ATAK role to HQ and attempt an operation change.
   After a fix, the change must be rejected unless authority was explicitly
   granted; verify that other devices retain the expected operation state.
3. Repeat after restart and disconnection/reconnection to check that role
   changes or restored state do not grant authority.
4. Verify that an explicitly authorised HQ can still make legitimate changes
   and that revoking its authority prevents further unauthorised changes.

### Delivery note

Include this file with the source and APK supplied to OreFox and draw attention
to HQ-001 during the handover. The original `sartak-handover-2026-10-24` tag
predates this disclosure; supply these notes alongside that historical snapshot
or use the later documentation commit. This update changes documentation only
and does not fix the implementation, rebuild the APK, move the original tag or
record client acceptance.
