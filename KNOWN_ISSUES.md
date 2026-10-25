# SARtak known issues

## Unauthorised HQ access

A device participating in an operation can assume the HQ role and modify the
operation without separate authorisation. This can allow accidental or
unauthorised changes to shared operation information.

This is a known unresolved issue in the handover build and should be fixed by
restricting HQ privileges to authorised users and enforcing permission checks
before operation changes are applied. Selecting an ATAK role alone should not
grant authority to manage an operation.

Include this note with the source and APK supplied to OreFox. The existing
`sartak-handover-2026-10-24` tag predates the note, so supply the updated
notes alongside that snapshot or use the later documentation commit.
