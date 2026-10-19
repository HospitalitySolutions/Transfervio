# Calendra integration: local contract stub and dry-run

## Current boundary

No Calendra API is called. No Calendra repository file, database, production setting or service was changed. `DESTINATION_MODE=mock` is the default. `DESTINATION_MODE=calendra` selects a contract-stub preview only. `IMPORT_WRITE_ENABLED=true` fails startup. `run-calendra-smoke.ps1 -ConfirmWrite` refuses before making a request.

The local mock consumes the same approved canonical snapshot exported to partners. Clients are created before dependent appointments. Each record has an idempotency key derived from partner, tenant, property, migration and stable record ID. The mock persists destination identities in Transfervio's own database and returns per-record acknowledgments. It has no notification, entitlement or financial code paths.

```powershell
.\scripts\run-calendra-smoke.ps1 -DryRun
# Or select an approved migration explicitly:
.\scripts\run-calendra-smoke.ps1 -MigrationId <uuid> -DryRun
```

The report is saved only to `.local-data/calendra-dry-run.json`. The synthetic acceptance test uses five clients and three appointments, deliberately fails one delivery, retries, and confirms a repeated full delivery creates zero new records.

## Read-only source review

Inspected local Calendra revision `c6be8d69043592fd82228cecca850b126a848686` on 18 October 2026. This is evidence about the checked-out source, not a guarantee about any deployed API.

| Source | Finding | Integration consequence |
|---|---|---|
| `backend/src/main/java/com/example/app/security/SecurityConfig.java:385` | JWT authentication followed by unit, location, CRM isolation and tenant permission filters | Existing staff browser identity is not an approved machine integration identity |
| `backend/src/main/java/com/example/app/client/ClientController.java:188` | Client request includes names, email/phone, assigned staff/location IDs, language, notification settings and billing-related fields | Use a deliberately restricted import DTO; do not send unrelated fields |
| `backend/src/main/java/com/example/app/client/ClientController.java:545` | Creation takes an authenticated `User`, applies its company context, reconciles workspace accounts and logs activity | Need explicit service-account scopes and tenant mapping, not a copied login token |
| `backend/src/main/java/com/example/app/session/SessionBookingController.java:213` | Booking request includes client IDs, consultant, service chain, start/end, location, space, group, consumables and payees | Canonical appointments need approved service/staff/location mappings; avoid implicit defaults |
| `backend/src/main/java/com/example/app/session/SessionBookingController.java:557` | Booking POST delegates to `SessionBookingCreationService` with authenticated user and optional waitlist/overbook inputs | No reviewed batch import or dry-run contract is established |
| `backend/src/main/java/com/example/app/session/SessionBookingCreationService.java:594` | Creation reserves future entitlements, invokes booking confirmation, applies consumables, reconciles entitlements, publishes booking events and queues open-bill sync | Current creation route cannot be assumed free of notifications or financial side effects |

No dedicated machine-to-machine migration API or idempotent batch import contract was established by this inspection. In particular, `suppressNotifications` on a client does not prove that all booking side effects are suppressed.

## Contract required before real testing

Separately authorize a Calendra change if needed. A reviewable import endpoint should require a scoped service credential and an exact staging/local base URL plus synthetic tenant/property allowlist; reject production, redirects, arbitrary URLs and cross-scope IDs. It should expose catalogs and conflict validation, preserve per-record idempotency, create clients first, return itemized outcomes, and explicitly suppress notifications, reminders, entitlement consumption, consumables, invoices and billing drafts. Tests must verify those code paths rather than assume flags are sufficient.

Then verify five synthetic clients and three appointments in the actual non-production CRM/calendar, retry, and confirm zero duplicates. This external acceptance step is **not completed**: no approved test API, machine credential or test tenant was supplied. Local mock acceptance is complete independently.
