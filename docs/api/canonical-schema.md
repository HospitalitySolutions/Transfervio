# Canonical schema v1

The authoritative registry is `ExportSchema.java`; `/v1/export-schema` returns its machine-readable descriptors. Discovery exposes no customer records. MVP entity names are CLIENT and APPOINTMENT; query names are `clients` and `appointments`. Values in `data` remain strings for v1 compatibility, including integer durations. Selected absent values are JSON null; omitted fields are not requested or not authorized. A descriptor's `required` applies to a complete validated record, not to a projected response.

| CLIENT field | Type | Validation / meaning |
|---|---|---|
| source_id | string, optional | Original source identifier; at least one of source ID, email or phone is needed |
| first_name | string, optional | Given name; at least one name field is needed |
| last_name | string, optional | Family name |
| display_name | string, optional | Source display name, or combined given/family name |
| email | string, optional | Valid address when supplied, normalized to lowercase |
| phone | string, optional | 6–15 digits with optional leading +; common separators removed |
| language | string, optional | Source preference; never inferred when absent |
| notes | string, optional | Reviewed source notes, maximum 10,000 characters |

| APPOINTMENT field | Type | Validation / meaning |
|---|---|---|
| source_id | string, optional | Original appointment identifier |
| client_record_id | UUID string, required | Stable ID of an included client in the same migration |
| client_source_id | string, optional | Original client reference used for linking |
| start | datetime string, required | ISO 8601 with UTC offset; ambiguous dates/DST require review |
| end | datetime string, optional | Normalized when supplied; must agree with duration |
| timezone | string, required | Valid source or configured property IANA timezone |
| duration_minutes | integer string, required | 1–1440; can be derived from valid start/end |
| status | string, required | BOOKED, CONFIRMED, CANCELLED, COMPLETED, NO_SHOW |
| source_service | string, optional | Source label for vendor-side resolution |
| source_employee | string, optional | Source label for vendor-side resolution |
| service_id | string, optional | Partner catalog reference; must belong to the invited property's catalog when present |
| employee_id | string, optional | Partner catalog reference; must belong to the invited property's catalog when present |

Client identity resolution uses source ID/email/phone rather than guessed names. Source conflicts, duplicate candidates and staff overlaps require review. All included appointments require a property and a linked included client. Catalog resolution may be deferred to the destination vendor; missing service/staff IDs do not by themselves block approval. Optional exact source-label matches to the supplied property catalog remain supported.

`stable_record_id` identifies an approved canonical record independently of projections. `idempotency_key` is stable for that approved snapshot and record. Always deduplicate destination effects using this key. `relationships.client_record_id` retains the opaque client dependency even if the corresponding business field was not selected. Sharing no business fields for an entity still exposes these necessary technical identifiers and record counts.

Full exports preserve authorized fields present in the frozen canonical snapshot. Explicit projections normalize absent/empty selected values to null. Neither mode rewrites stored records. Provenance is limited to opaque upload ID, checksum, row number and extractor name; raw filenames, source IDs, table labels and text remain in protected review data.

## Compatibility and future profiles

The API path and `schema_version: 1` identify the canonical contract. Additive supported fields may be introduced within v1; changes to field meanings, types, identity or relationship semantics require a new major version. API document version 1.1.0 describes this extension of the existing v1 routes. Consumers should discover supported fields and tolerate additive metadata; explicit projections remain stable.

Future entities are registered independently (for example services, employees, locations, groups, memberships and packages). Their migration logic is outside this MVP. Saved export profiles are also a secondary milestone, not an implemented endpoint. A future profile may store preferred entities and field lists only. Resolving a profile must still intersect the current registry, credential permission and immutable customer-approved scope, and cannot contain destination transformations. Explicit `fields` can narrow that result; it must never expand authorization. Existing per-request selections will remain supported.
