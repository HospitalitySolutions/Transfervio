# Transfervio — Universal Migration API MVP

**Repository:** `HospitalitySolutions/Transfervio`

**Local development:** `C:\DEVELOPMENT\Projects\transfervio`

**Architecture:** Vendor-initiated integration, standardized export API, no destination-specific adapters in Transfervio.

## 1. Core architecture

Transfervio is an independent AI-powered migration platform.

It accepts information exported from other software products, interprets and normalizes that information, and provides approved structured records to destination software vendors through a standard API.

Vendors integrate with Transfervio and retrieve data themselves.

### Transfervio is responsible for:

- Accepting CSV, XLSX, JSON, PNG and JPEG files.
- Recognizing data using deterministic parsing and AI.
- Mapping source information to a universal canonical schema.
- Identifying clients, appointments and supported related information.
- Validating, normalizing and deduplicating data.
- Providing human review and correction.
- Maintaining immutable approved migration snapshots.
- Exposing standardized, versioned APIs.
- Allowing selective retrieval of entities and fields.
- Recording vendor import acknowledgments and migration status.

### Destination vendors are responsible for:

- Connecting to the Transfervio API.
- Authenticating with their partner credentials.
- Selecting which entities and fields they need.
- Mapping canonical Transfervio fields into their internal model.
- Resolving destination-specific services, staff and locations.
- Validating and persisting the data in their own systems.
- Handling duplicates and destination-specific business rules.
- Returning verified import results to Transfervio.

**Do not implement Calendra-specific or other vendor-specific destination adapters inside the Transfervio backend.**

## 2. Preserve and extend existing functionality

Before implementing, inspect the latest repository, local changes and current tests.

Existing components to review:

- `RecordValidator.java`
- `StructuredParser.java`
- `OpenAiVision.java`
- `ImageExtractor.java`
- `MigrationService.java`
- `DeliveryService.java`
- `DeliveryController.java`
- `PartnerController.java`
- `docs/api/openapi.yaml`

Preserve existing partner → tenant → property isolation, invitations, approvals, paginated exports, idempotency keys, receipts and mock delivery.

Do not rewrite working components unnecessarily.

## 3. Real AI extraction

Extend the existing OpenAI integration.

### Images

Support arbitrary valid PNG/JPEG screenshots when authorized external AI mode is explicitly enabled.

Keep existing synthetic-only mode for deterministic tests.

Use structured output, preserve provenance and flag uncertainty.

Improve diagnostics for invalid model, authentication, quota, rate-limit, timeout and structured-output errors.

### Structured files

Continue deterministic parsing for recognizable CSV, XLSX and JSON files.

For unfamiliar structures, use AI to suggest how source fields correspond to Transfervio canonical fields.

Examples:

- `Ime` → `first_name`
- `Priimek` → `last_name`
- `Elektronski naslov` → `email`
- `Termin` → `start`

A user must be able to correct ambiguous mappings.

For large files, infer mappings using bounded representative samples and apply them deterministically to all rows.

Real customer data must only be sent to external AI when appropriately authorized. Never silently transmit it.

## 4. Universal canonical schema

Maintain a versioned schema independent of destination software.

### MVP entities

**CLIENT**

Supported initial fields:

- `source_id`
- `first_name`
- `last_name`
- `display_name`
- `email`
- `phone`
- `language`
- `notes`

**APPOINTMENT**

Supported initial fields:

- `source_id`
- `client_record_id`
- `client_source_id`
- `start`
- `end`
- `timezone`
- `duration_minutes`
- `status`
- `source_service`
- `source_employee`
- `service_id`
- `employee_id`

Preserve stable technical identifiers independently of user-selected data fields.

### Extensibility

Design the schema registry so future entities can be introduced, including:

- Services
- Employees
- Locations
- Groups
- Memberships
- Packages
- Other industry-specific entities

Do not implement their full migration logic in this MVP unless existing functionality requires it.

Document data types, nullability, validation rules, relationship requirements and versioning.

## 5. Selective API exports

Extend the existing endpoint:

`GET /v1/migrations/{id}/export`

Support requesting a specific entity and only selected fields.

### Example: minimal client export

`GET /v1/migrations/{id}/export?entity=clients&fields=first_name,last_name,email`

### Example: additional client fields

`GET /v1/migrations/{id}/export?entity=clients&fields=first_name,last_name,email,phone,language`

### Example: appointments

`GET /v1/migrations/{id}/export?entity=appointments&fields=start,duration_minutes,status,service_id,employee_id`

### Requirements

1. No `fields` parameter preserves existing full-export behavior.
2. When `fields` is supplied, return only those requested business fields.
3. Unknown fields produce a documented 400 error.
4. Unauthorized fields cannot be retrieved.
5. Field selection must not modify stored canonical records.
6. Stable record IDs, idempotency keys, and required technical references remain available independently of field selection.
7. Do not expose unnecessary provenance or source PII in technical metadata.
8. Distinguish omitted fields from selected fields whose values are missing.
9. Preserve pagination and consistent snapshot results.
10. Apply field authorization server-side using an explicit field registry.

Do not introduce vendor-specific property names or transformations in the export endpoint.

## 6. Field discovery API

Introduce:

`GET /v1/export-schema`

Support entity-specific schema discovery.

Example:

`GET /v1/export-schema?entity=clients`

Return field identifiers, display names, data types, descriptions and optional/required semantics.

Vendors should be able to discover supported fields programmatically without hardcoding the full schema.

Keep schema discovery separate from access to actual customer records.

## 7. Export profiles

Support per-request field selection in MVP.

Design the API so reusable vendor export profiles can be introduced without breaking existing consumers.

Saved profiles are a secondary milestone unless their implementation is straightforward.

Profiles may define preferred entities and fields but must never expand a partner's authorization or customer's approved data-sharing scope.

Do not build destination field mapping into these profiles.

## 8. Vendor API integration

Reuse the existing partner API.

The vendor workflow must be:

1. Authenticate as a partner.
2. Register or synchronize an authorized tenant.
3. Synchronize relevant properties and catalog entries.
4. Generate a migration invitation.
5. Customer uploads and reviews data.
6. Customer approves the migration.
7. Vendor discovers the ready migration.
8. Vendor retrieves the desired approved entities and fields.
9. Vendor maps and imports data into its own application.
10. Vendor returns per-record delivery acknowledgments.

Preserve:

- Tenant and property isolation
- Partner API keys
- Versioned contracts
- Pagination
- Immutable approved snapshots
- Stable record identifiers
- Idempotency keys
- Delivery receipts
- Event polling
- Signed webhook support

Never mark a migration `IMPORTED` merely because data was exported. Import completion requires verified vendor acknowledgments.

## 9. OpenAPI documentation

Publish a complete, accurate OpenAPI 3.1 specification for the implemented endpoints.

Document:

- Authentication
- Tenant/property management
- Invitations
- Migration lifecycle
- Field discovery
- Selective exports
- Pagination
- Entity schemas
- Stable identifiers
- Relationship handling
- Webhooks and event polling
- Delivery acknowledgments
- Retry and failure behavior
- Validation/error responses
- Versioning and compatibility

Provide example requests and responses.

Include a short vendor integration guide and a functioning example consumer.

The API and examples must be usable by any third-party vendor without Calendra-specific assumptions.

## 10. Local testing

Keep Transfervio running locally on Windows.

Run acceptance tests for:

- Synthetic screenshot extraction
- Authorized AI extraction
- CSV/XLSX/JSON field mapping
- Client and appointment normalization
- Review and approval
- Complete export
- Selected-field export
- Unknown/unauthorized fields
- Pagination
- Tenant isolation
- Stable IDs
- Import receipts
- Partial failures and retries
- API schema consistency

Use the existing mock destination only for Transfervio's own integration tests.

### Calendra integration testing

Calendra must implement its own client of the Transfervio partner API.

For the initial test, run a Calendra-owned connector locally so it can access Transfervio at `http://localhost:8091`.

A separate, explicitly approved Calendra integration may then forward synthetic records through an authenticated production Calendra import API.

Do not introduce Calendra-specific destination mapping in Transfervio.

Do not enable production Calendra writes without a separately reviewed, tenant-scoped import path and explicit authorization.

## 11. CI and Git workflow

Develop on a feature branch.

Preserve local changes.

Use only self-hosted GitHub Actions runners for all CI, testing and deployment workflows.

Never fall back to GitHub-hosted runners.

Keep API keys, uploaded records, database dumps and other secrets out of Git.

Run relevant backend, frontend, contract, integration and browser tests.

Do not merge to main or deploy infrastructure without explicit approval.

## 12. Definition of done

The MVP is complete when:

1. Authorized previously unseen source data can be extracted.
2. The user can review and approve normalized records.
3. Transfervio provides a stable, documented canonical schema.
4. Vendors can retrieve all or selected supported entities and fields.
5. Different field selections return consistent stable identities.
6. Vendors can build an integration using only the OpenAPI documentation.
7. Import results can be acknowledged reliably.
8. Retries do not incorrectly duplicate or downgrade successful delivery results.
9. No destination-specific mapping exists in the Transfervio core.
10. All critical local tests pass.

The platform must remain suitable for future integration with Calendra, Lime Booking, hotel PMS systems and other SaaS products without redesigning the core API.
