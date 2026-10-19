import { readFileSync, writeFileSync } from "node:fs";
import assert from "node:assert/strict";
import { fileURLToPath } from "node:url";
import { TransfervioClient } from "../packages/api-client-example/client.mjs";
import { consume } from "../packages/api-client-example/consumer.mjs";
const root = new URL("../", import.meta.url),
  env = Object.fromEntries(
    readFileSync(new URL(".env.local", root), "utf8")
      .split(/\r?\n/)
      .filter((l) => /^[A-Z_]+=/.test(l))
      .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]),
  );
const base = `http://localhost:${env.API_PORT || 8091}`;
let checks = 0;
async function api(
  path,
  { token, body, form, method = "GET", status = 200 } = {},
) {
  const r = await fetch(base + path, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { "Content-Type": "application/json" } : {}),
    },
    body: form || (body ? JSON.stringify(body) : undefined),
  });
  assert.equal(
    r.status,
    status,
    `${method} ${path.replace(/invitations\/[^/]+/, "invitations/[redacted]")}: ${r.status}`,
  );
  checks++;
  const text = await r.text();
  return text ? JSON.parse(text) : null;
}
const token = (
  await api("/auth/login", {
    method: "POST",
    body: {
      email: "admin@calendra.test",
      password: env.BOOTSTRAP_ADMIN_PASSWORD,
    },
  })
).token;
const demo = JSON.parse(
  readFileSync(new URL(".local-data/demo.json", root), "utf8"),
);
const runtime = await api("/api/runtime");
assert.equal(runtime.ai_provider, "mock");
const schema = await api("/v1/export-schema", { token });
assert.equal(schema.schema_version, 1);
assert.equal(schema.entities.clients.length, 8);
assert.equal(schema.entities.appointments.length, 12);
await api("/v1/export-schema", { status: 401 });
await api("/v1/export-schema?entity=services", { token, status: 400 });
const endpoint = `/v1/migrations/${demo.bulk_migration_id}/export`;
const full = await api(endpoint + "?entity=clients&limit=2", { token });
const subset = await api(
  endpoint + "?entity=clients&fields=first_name,email,notes&limit=2",
  { token },
);
assert.deepEqual(
  subset.records.map((r) => r.stable_record_id),
  full.records.map((r) => r.stable_record_id),
);
assert.deepEqual(
  subset.records.map((r) => r.idempotency_key),
  full.records.map((r) => r.idempotency_key),
);
assert.equal(subset.total, 500);
assert.equal(subset.next_cursor, 2);
for (const r of subset.records) {
  assert.deepEqual(Object.keys(r.data), ["first_name", "email", "notes"]);
  assert.equal(r.data.notes, null);
  assert.ok(
    r.provenance.every((p) => !("filename" in p) && !("source_id" in p)),
  );
}
assert.deepEqual(
  await api(endpoint + "?entity=clients&limit=2", { token }),
  full,
);
await api(endpoint + "?entity=clients&fields=tenant_id", {
  token,
  status: 400,
});
await api(endpoint + "?entity=clients&fields=", { token, status: 400 });
await api(endpoint + "?cursor=2147483647", { token, status: 400 });
const appointments = await api(
  endpoint + "?entity=appointments&fields=start&limit=2",
  { token },
);
assert.ok(
  appointments.records.every(
    (r) =>
      Object.keys(r.data).join(",") === "start" &&
      r.relationships.client_record_id,
  ),
);
const key = await api("/v1/api-keys", {
  token,
  method: "POST",
  body: {
    label: "Synthetic field scope",
    export_fields: { clients: ["email"], appointments: [] },
  },
});
const limited = await api(endpoint + "?limit=2", { token: key.token });
assert.ok(
  limited.records.every((r) => Object.keys(r.data).every((f) => f === "email")),
);
await api(endpoint + "?fields=phone", { token: key.token, status: 403 });
assert.deepEqual(
  (
    await api("/v1/export-schema?entity=clients", { token: key.token })
  ).entities.clients.map((f) => f.id),
  ["email"],
);
const summary = await api(`/v1/migrations/${demo.bulk_migration_id}`, {
  token: key.token,
});
assert.ok(!summary.records && !summary.files);
await api(`/v1/migrations/${demo.bulk_migration_id}/dry-run`, {
  token: key.token,
  status: 403,
});
await api("/v1/invitations", {
  token: key.token,
  method: "POST",
  body: {
    tenant_id: demo.tenant_id,
    property_id: demo.property_id,
    allowed_entities: ["CLIENT"],
    expires_in_hours: 1,
  },
  status: 403,
});
await api(`/v1/api-keys/${key.id}/revoke`, { token, method: "POST" });
async function invite(extra = {}) {
  const i = await api("/v1/invitations", {
    token,
    method: "POST",
    body: {
      tenant_id: demo.tenant_id,
      property_id: demo.property_id,
      allowed_entities: ["CLIENT", "APPOINTMENT"],
      expires_in_hours: 24,
      ...extra,
    },
  });
  return {
    ...i,
    base:
      "/public/invitations/" + new URL(i.upload_url).pathname.split("/").at(-1),
  };
}
async function upload(i, name, bytes) {
  const form = new FormData();
  form.append("files", new Blob([bytes]), name);
  return (await api(i.base + "/uploads", { method: "POST", form }))[0];
}
async function process(i) {
  await api(i.base + "/process", { method: "POST" });
  for (let n = 0; n < 30; n++) {
    await new Promise((r) => setTimeout(r, 600));
    const p = await api(i.base + "/preview");
    if (p.migration.status === "REVIEW") return p;
    assert.notEqual(p.migration.status, "FAILED");
  }
  throw Error("Extraction timeout");
}
const restricted = await invite({
  export_fields: { clients: ["first_name", "email"], appointments: [] },
});
await upload(
  restricted,
  "consent.json",
  JSON.stringify({
    clients: [
      {
        id: "scope-c1",
        first_name: "Synthetic",
        email: "synthetic@example.test",
        notes: "Never export this note",
      },
    ],
    appointments: [
      {
        id: "scope-a1",
        client_source_id: "scope-c1",
        start: "2026-11-02T12:00",
        duration: "30",
        status: "CONFIRMED",
        source_service: "Vendor-resolved label",
        source_employee: "Vendor-resolved person",
      },
    ],
  }),
);
let p = await process(restricted);
assert.ok(p.records.every((r) => r.issues.length === 0));
await api(restricted.base + "/approve", {
  method: "POST",
  body: {
    revision: p.migration.revision,
    export_fields: { clients: ["notes"] },
  },
  status: 403,
});
await api(restricted.base + "/approve", {
  method: "POST",
  body: {
    revision: p.migration.revision,
    export_fields: { clients: ["first_name"], appointments: [] },
  },
});
const shared = await api(`/v1/migrations/${restricted.migration_id}/export`, {
  token,
});
assert.deepEqual(shared.records[0].data, { first_name: "Synthetic" });
await api(`/v1/migrations/${restricted.migration_id}/export?fields=email`, {
  token,
  status: 403,
});
assert.equal(
  (await api(`/v1/migrations/${restricted.migration_id}`, { token })).migration
    .status,
  "READY",
);
const consumerKey = await api("/v1/api-keys", {
  token,
  method: "POST",
  body: { label: "Synthetic vendor consumer" },
});
for (const results of [
  [],
  [null],
  [{ record_id: shared.records[0].stable_record_id }],
])
  await api(`/v1/migrations/${restricted.migration_id}/delivery-results`, {
    token: consumerKey.token,
    method: "POST",
    body: { results },
    status: 400,
  });
const vendor = new TransfervioClient(base, consumerKey.token),
  journal = fileURLToPath(new URL(".local-data/universal-vendor.json", root));
assert.deepEqual(
  (
    await vendor.export(restricted.migration_id, "clients", {
      fields: ["first_name"],
      limit: 1,
    })
  )[0].data,
  { first_name: "Synthetic" },
);
assert.equal(
  (await consume(vendor, restricted.migration_id, journal)).acknowledgments,
  0,
);
const imported = await consume(vendor, restricted.migration_id, journal, {
  execute: true,
});
assert.equal(imported.created, 2);
const retry = await consume(vendor, restricted.migration_id, journal, {
  execute: true,
});
assert.equal(retry.created, 0);
assert.equal(retry.already_exists, 2);
assert.equal(
  (await api(`/v1/migrations/${restricted.migration_id}`, { token })).migration
    .status,
  "IMPORTED",
);
await api(`/v1/api-keys/${consumerKey.id}/revoke`, { token, method: "POST" });
const cases = [
  [
    "unfamiliar.csv",
    "Roster number,Given label,Electronic endpoint\nu1,Synthetic,synthetic@example.test",
    {
      "Roster number": "source_id",
      "Given label": "first_name",
      "Electronic endpoint": "email",
    },
    1,
  ],
  [
    "unfamiliar.xlsx",
    readFileSync(new URL(".local-data/unfamiliar.xlsx", root)),
    {
      "Roster number": "source_id",
      "Given label": "first_name",
      "Family label": "last_name",
      "Electronic endpoint": "email",
      "Contact number": "phone",
    },
    5,
  ],
  [
    "unfamiliar.json",
    JSON.stringify({
      clients: [
        { id: "known-1", name: "Known Synthetic", email: "known@example.test" },
      ],
      attendees: [
        {
          person: {
            ref: "u1",
            given: "Synthetic",
            mail: "synthetic@example.test",
          },
        },
      ],
    }),
    {
      "person.ref": "source_id",
      "person.given": "first_name",
      "person.mail": "email",
    },
    2,
  ],
];
cases.push([
  "ambiguous.csv",
  "id,name,email,e-mail\nc1,Synthetic,first@example.test,second@example.test",
  { id: "source_id", name: "display_name", email: "email", "e-mail": "" },
  1,
]);
for (const [name, bytes, fields, count] of cases) {
  const i = await invite(),
    file = await upload(i, name, bytes);
  const plans = await api(i.base + "/source-mappings");
  const plan = plans.find((p) => !p.approved);
  assert.ok(
    plan,
    "Unfamiliar tables must require confirmation even beside known tables",
  );
  await api(i.base + "/process", { method: "POST", status: 409 });
  await api(i.base + `/uploads/${file.id}/ai-consent`, {
    method: "PUT",
    body: { authorized: true, synthetic: true },
    status: 403,
  });
  await api(i.base + `/source-mappings/${file.id}`, {
    method: "PUT",
    body: {
      table: plan.table_name,
      entity: "CLIENT",
      fields: { ...fields, [Object.keys(fields)[0]]: "tenant_id" },
      approved: true,
    },
    status: 400,
  });
  await api(i.base + `/source-mappings/${file.id}`, {
    method: "PUT",
    body: {
      table: plan.table_name,
      entity: "CLIENT",
      fields,
      approved: true,
    },
  });
  p = await process(i);
  assert.equal(p.records.length, count);
  assert.ok(p.records.every((r) => r.issues.length === 0 && r.data.email));
  await api(i.base + "/approve", {
    method: "POST",
    body: { revision: p.migration.revision },
  });
  await api(i.base + `/source-mappings/${file.id}`, {
    method: "PUT",
    body: {
      table: plan.table_name,
      entity: "CLIENT",
      fields,
      approved: true,
    },
    status: 409,
  });
}
await api("/v1/logout", { token, method: "POST" });
const report = {
  passed: true,
  httpAssertions: checks,
  selectiveExport: true,
  stableIdentities: true,
  missingVsOmitted: true,
  credentialScopes: true,
  approvedSharingScope: true,
  safeMetadata: true,
  csvXlsxJsonMapping: true,
  mixedKnownAndUnfamiliarTables: true,
  genericConsumerRetry: "zero new records",
  optionalVendorResolution: true,
  externalAiCalls: 0,
};
writeFileSync(
  new URL(".local-data/universal-results.json", root),
  JSON.stringify(report, null, 2),
);
console.log(JSON.stringify(report, null, 2));
