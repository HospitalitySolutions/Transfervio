# Synthetic fixtures

All records are fictional and use reserved `example.test` addresses. Recreate fixtures locally with `scripts/generate-fixtures.ps1`; the smaller `generate-xlsx-fixture.ps1` recreates only the workbook.

- `clients-sl.csv`: five clients with Slovenian headings.
- `clients-en.xlsx`: the same clients with English headings, no formulas or macros.
- `complete-migration.json`: five clients and three linked appointments.
- Screenshots 01–08: client-directory layouts; 09–16: appointment lists; 17–20: cropped calendar cards with ambiguous dates and missing duration.
- `ground-truth.json`: image SHA-256 to expected fields, mirrored into the API's mock resource.

The mock recognizes exact fixture bytes. Its 20-image/524-field check measures deterministic contract consistency, **not OCR accuracy**. Unknown images require review. Real vision is optional, synthetic-only, disabled by default and has not been exercised. The large acceptance dataset (500 clients and 200 appointments) is generated in memory by `scripts/test-migration.mjs` and stored only in the local development database/object store.
