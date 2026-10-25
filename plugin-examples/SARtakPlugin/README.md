# SARtak ATAK Plugin

SARtak extends ATAK with search-and-rescue coordination. ATAK supplies the map,
device location, callsign/role identity and CoT integration. SARtak adds operation,
team, grid, route, line, alert, tracking and Ditto-sync workflows.

## Roles and Workflows

**Known issue:** a participating device can assume HQ and modify the
operation without separate authorisation. This should be fixed by
restricting HQ privileges and enforcing operation permissions. See
[the known-issue notes](../../KNOWN_ISSUES.md#unauthorised-hq-access).

- **HQ:** creates/manages operations, defines operation-wide search areas,
  reviews teams and progress, and can archive operations while retaining data.
- **Team Lead:** joins an operation, creates a team, handles membership, selects
  team search cells/routes and controls the search line.
- **Team Member:** joins an operation/team, follows the shared search line and
  receives/sends team alerts.

Devices can discover shared operation data through a configured Ditto profile.
ATAK CoT/server communication is a separate transport; do not assume visible
markers prove Ditto connectivity. The Devices tab exposes connection diagnostics.

## Code Map

| Location under `app/src/main` | Responsibility |
| --- | --- |
| `java/.../PluginTemplateDropDownReceiver.java` | Main plugin UI and interactions |
| `java/.../PluginTemplateMapComponent.java` | ATAK lifecycle/integration |
| `java/.../grid/` | UTM cells, overlays, line/route guidance and progress |
| `java/.../runtime/` | Identity, operations, membership, sync, alerts and health |
| `java/.../database/` | Local SQLite storage and repositories |
| `res/`, `assets/`, `AndroidManifest.xml` | UI resources and plugin registration |

Java package paths retain the template name `com.atakmap.android.plugintemplate`;
those files are SARtak implementation, not an unused duplicate template.
Automated regression tests are in `app/src/test`.

## Setup and Maintenance

See [../../SETUP.md](../../SETUP.md) for building and installation and
[../../HANDOVER.md](../../HANDOVER.md) for handover scope. Use
`local.properties.example` as the single configuration template.

- Configure profiles via SARtak's Ditto Setup, not another developer's files.
- APKs have no bundled Ditto account or build-time credential fallback. A fresh
  installation starts without a profile; upgrades retain on-device profiles.
- QR/join codes include development sync details; share them only with intended
  participants. Development tokens are not a production security model.
- Nearby/offline operation depends on authentication, permissions and available
  transports. Validate on physical devices in the intended field environment.
- `tools/sartak-test.ps1` is optional debug-only QA tooling. Read
  `tools/TESTING.md` before using reset/setup commands because they change state.
- Preserve `tools/PERFORMANCE_AND_RECOVERY.md` when changing grid rendering.
- The default ATAK API is 5.6.0. Changing metadata alone does not validate another
  version or bypass ATAK's signature checks.
