// Explicitly authorized synthetic-only live acceptance; never invoked by CI or run-local-tests.
import { readFileSync, writeFileSync } from "node:fs";
import assert from "node:assert/strict";
const root = new URL("../", import.meta.url),
  env = Object.fromEntries(
    readFileSync(new URL(".env.local", root), "utf8")
      .split(/\r?\n/)
      .filter((l) => /^[A-Z_]+=/.test(l))
      .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]),
  );
if (!process.argv.includes("--authorized-synthetic-test"))
  throw Error("Explicit synthetic test authorization flag required");
const base = `http://localhost:${env.API_PORT || 8091}`;
let count = 0;
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
  const t = await r.text();
  let data = t ? JSON.parse(t) : null;
  assert.equal(
    r.status,
    status,
    `AI acceptance HTTP ${r.status}: ${data?.code || data?.message || ""}`,
  );
  count++;
  return data;
}
assert.equal((await api("/api/runtime")).ai_provider, "openai");
const token = (
  await api("/auth/login", {
    method: "POST",
    body: {
      email: "admin@calendra.test",
      password: env.BOOTSTRAP_ADMIN_PASSWORD,
    },
  })
).token;
try {
  const tenant = await api("/v1/tenants", {
    token,
    method: "POST",
    body: {
      external_tenant_id: "synthetic-ai-acceptance",
      name: "Synthetic AI acceptance",
    },
  });
  const invitation = await api("/v1/invitations", {
      token,
      method: "POST",
      body: {
        tenant_id: tenant.id,
        allowed_entities: ["CLIENT"],
        expires_in_hours: 24,
      },
    }),
    prefix =
      "/public/invitations/" +
      new URL(invitation.upload_url).pathname.split("/").at(-1);
  const form = new FormData();
  form.append(
    "files",
    new Blob([readFileSync(new URL(".local-data/unseen-synthetic.png", root))]),
    "unseen-synthetic.png",
  );
  form.append(
    "files",
    new Blob([
      "Contact reference,Given label,Family label,Mailbox address\nlive-client-2,Milo,Example,milo@example.test",
    ]),
    "unseen-synthetic.csv",
  );
  const files = await api(prefix + "/uploads", { method: "POST", form });
  const csv = files.find((f) => f.name.endsWith(".csv")),
    image = files.find((f) => f.name.endsWith(".png"));
  const plan = (await api(prefix + "/source-mappings"))[0];
  await api(prefix + `/source-mappings/${csv.id}/suggest`, {
    method: "POST",
    body: { table: plan.table_name },
    status: 403,
  });
  for (const file of files)
    await api(prefix + `/uploads/${file.id}/ai-consent`, {
      method: "PUT",
      body: { authorized: true, synthetic: true },
    });
  const suggestion = await api(prefix + `/source-mappings/${csv.id}/suggest`, {
    method: "POST",
    body: { table: plan.table_name },
  });
  assert.equal(suggestion.approved, false);
  assert.equal(suggestion.fields["Mailbox address"], "email");
  assert.equal(suggestion.entity, "CLIENT");
  // Confirm only the known fictional ground-truth mapping; never approve arbitrary source output.
  await api(prefix + `/source-mappings/${csv.id}`, {
    method: "PUT",
    body: {
      table: plan.table_name,
      entity: "CLIENT",
      fields: {
        "Contact reference": "source_id",
        "Given label": "first_name",
        "Family label": "last_name",
        "Mailbox address": "email",
      },
      approved: true,
    },
  });
  await api(prefix + `/uploads/${image.id}/ai-consent`, {
    method: "PUT",
    body: { authorized: false, synthetic: true },
  });
  await api(prefix + "/process", { method: "POST", status: 403 });
  await api(prefix + `/uploads/${image.id}/ai-consent`, {
    method: "PUT",
    body: { authorized: true, synthetic: true },
  });
  async function extract() {
    await api(prefix + "/process", { method: "POST" });
    for (let n = 0; n < 100; n++) {
      await new Promise((r) => setTimeout(r, 1500));
      const p = await api(prefix + "/preview");
      if (p.migration.status === "REVIEW") return p;
      if (p.migration.status === "FAILED")
        throw Error(
          "Live AI: " +
            p.jobs.map((j) => j.error_code + ": " + j.error_message).join("; "),
        );
    }
    throw Error("Live AI timeout");
  }
  let preview = await extract();
  assert.equal(preview.records.length, 2);
  const nora = preview.records.find(
    (r) => r.data.email === "nora@example.test",
  );
  assert.ok(nora);
  assert.equal(nora.data.first_name, "Nora");
  assert.equal(nora.data.last_name, "Example");
  assert.ok(nora.issues.some((i) => i.code === "AI_REVIEW_REQUIRED"));
  const responses = preview.ai_usage.reduce(
    (n, r) => n + Number(r.responses),
    0,
  );
  assert.equal(responses, 2);
  preview = await extract();
  assert.equal(
    preview.ai_usage.reduce((n, r) => n + Number(r.responses), 0),
    responses,
  );
  await api(prefix + "/mappings", {
    method: "PATCH",
    body: {
      revision: preview.migration.revision,
      records: preview.records.map((r) => ({
        record_id: r.id,
        acknowledge_source: true,
      })),
    },
  });
  preview = await api(prefix + "/preview");
  assert.ok(preview.records.every((r) => r.issues.length === 0));
  await api(prefix + "/approve", {
    method: "POST",
    body: { revision: preview.migration.revision },
  });
  const exported = await api(
    `/v1/migrations/${invitation.migration_id}/export?fields=first_name,last_name,email`,
    { token },
  );
  assert.equal(exported.records.length, 2);
  const report = {
    passed: true,
    syntheticOnly: true,
    previouslyUnseenImage: true,
    boundedMappingSuggestion: true,
    consentEnforced: true,
    acceptedProviderResponses: responses,
    cachedReprocessing: true,
    humanReviewGate: true,
    clients: 2,
    httpAssertions: count,
    usage: preview.ai_usage,
  };
  writeFileSync(
    new URL(".local-data/live-ai-results.json", root),
    JSON.stringify(report, null, 2),
  );
  console.log(JSON.stringify(report, null, 2));
} finally {
  await api("/v1/logout", { token, method: "POST" });
}
