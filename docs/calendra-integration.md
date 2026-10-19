# Calendra integration boundary

Transfervio now exposes a vendor-neutral pull API. It contains no Calendra destination adapter, and `DESTINATION_MODE=calendra` is rejected. The local mock exists only to test Transfervio's review, receipt and retry behavior. `IMPORT_WRITE_ENABLED=true` fails startup.

Calendra must own its connector, field transformations, service/staff/location resolution, destination persistence and verified acknowledgments. The generic [vendor guide](api/README.md) and executable consumer demonstrate that contract without depending on Calendra routes or DTOs. `scripts/run-mock-smoke.ps1 -DryRun` previews only the local mock. The old `run-calendra-smoke.ps1` name remains a compatibility wrapper and cannot write remotely.

The original read-only assessment inspected local Calendra revision `c6be8d69043592fd82228cecca850b126a848686` on 18 October 2026. Booking creation had notification, entitlement and billing effects requiring explicit handling. That assessment is not proof of any deployed API's current behavior. No Calendra code, database or settings were changed by this universal API work.

An actual connector test requires a separately approved Calendra-owned implementation, an authenticated authorized non-production API, and a synthetic tenant/property. Run the connector locally so it can reach `http://localhost:8091`. Verify client-first imports, catalog resolution, stable idempotency, partial failures, duplicate-free retries and suppression of notifications, reminders, entitlements and billing against the destination itself. Mock receipts do not prove those effects.

The newer specification describes a possible future production import path, but no production write is authorized in this session. A production path would require separate review, explicit authorization and tenant-scoped safeguards. Actual Calendra import acceptance remains pending; local universal API and generic consumer acceptance are independent of that gate.
