# Synthetic fixtures

All records are fictional and use reserved `example.test` addresses. Recreate fixtures locally with `scripts/generate-fixtures.ps1`; the smaller `generate-xlsx-fixture.ps1` recreates only the workbook.

- `clients-sl.csv`: five clients with Slovenian headings.
- `clients-en.xlsx`: the same clients with English headings, no formulas or macros.
- `complete-migration.json`: five clients and three linked appointments.
- Screenshots 01–08: client-directory layouts; 09–16: appointment lists; 17–20: cropped calendar cards with ambiguous dates and missing duration.
- `ground-truth.json`: image SHA-256 to expected fields, mirrored into the API's mock resource.

The mock recognizes exact fixture bytes. Its 20-image/524-field check measures deterministic contract consistency, **not OCR accuracy**. Unknown images require review. Optional real vision requires explicit runtime and per-file authorization; fixture-only mode remains available. A separately authorized live test used an unseen generated synthetic screenshot and a bounded CSV mapping sample; see [current evidence](../docs/universal-migration-review.md). The large acceptance dataset (500 clients and 200 appointments) is generated in memory by `scripts/test-migration.mjs` and stored only in the local development database/object store.

`generate-xlsx-fixture.ps1 -Unfamiliar` creates an ignored local workbook for mapping tests. `generate-live-ai-fixture.ps1` generates an ignored unseen screenshot. The ordinary acceptance suite never runs live AI. Regenerating the committed fixtures also refreshes `synthetic-inputs.json`, the checksum allowlist used by fixture-only AI mode.
