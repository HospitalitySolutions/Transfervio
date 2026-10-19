import SwaggerParser from "@apidevtools/swagger-parser";
import Ajv from "ajv/dist/2020.js";
import addFormats from "ajv-formats";
import { readFileSync, writeFileSync } from "node:fs";
import assert from "node:assert/strict";
const root = new URL("../../", import.meta.url),
  file = new URL("docs/api/openapi.yaml", root);
const env = Object.fromEntries(
  readFileSync(new URL(".env.local", root), "utf8")
    .split(/\r?\n/)
    .filter((l) => /^[A-Z_]+=/.test(l))
    .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]),
);
const base = `http://localhost:${env.API_PORT || 8091}`;
const spec = await SwaggerParser.validate(
  file.pathname.replace(/^\/([A-Za-z]:)/, "$1"),
);
assert.equal(spec.openapi, "3.1.0");
const ajv = new Ajv({ strict: false, allErrors: true });
addFormats(ajv);
ajv.addFormat("int32", { type: "number", validate: Number.isInteger });
ajv.addFormat("int64", { type: "number", validate: Number.isInteger });
ajv.addFormat("double", { type: "number", validate: Number.isFinite });
let checks = 0;
async function check(route, path = route, token) {
  const response = await fetch(base + path, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  assert.equal(response.status, 200);
  const data = await response.json(),
    schema =
      spec.paths[route].get.responses["200"].content["application/json"].schema;
  const validate = ajv.compile(schema);
  assert.ok(
    validate(data),
    route + ": " + JSON.stringify(validate.errors?.slice(0, 3)),
  );
  checks++;
  return data;
}
const login = await fetch(base + "/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    email: "admin@calendra.test",
    password: env.BOOTSTRAP_ADMIN_PASSWORD,
  }),
});
assert.equal(login.status, 200);
const credentials = await login.json();
assert.ok(
  ajv.compile(
    spec.paths["/auth/login"].post.responses["200"].content["application/json"]
      .schema,
  )(credentials),
);
const token = credentials.token;
try {
  const live = await (await fetch(base + "/v3/api-docs")).json();
  for (const [path, item] of Object.entries(live.paths))
    for (const method of Object.keys(item).filter((m) =>
      ["get", "post", "put", "patch", "delete"].includes(m),
    ))
      assert.ok(
        spec.paths[path]?.[method],
        `Undocumented endpoint ${method} ${path}`,
      );
  for (const [path, item] of Object.entries(spec.paths))
    for (const method of Object.keys(item).filter((m) =>
      ["get", "post", "put", "patch", "delete"].includes(m),
    )) {
      assert.ok(live.paths[path]?.[method], `Stale endpoint ${method} ${path}`);
      if (path.startsWith("/v1/"))
        assert.deepEqual(item[method].security, [{ partnerBearer: [] }]);
    }
  await check("/api/runtime");
  const discovery = await check("/v1/export-schema", undefined, token);
  for (const [entity, fields] of Object.entries(discovery.entities)) {
    const name = entity === "clients" ? "ClientFields" : "AppointmentFields";
    assert.deepEqual(
      Object.keys(spec.components.schemas[name].properties).sort(),
      fields.map((f) => f.id).sort(),
    );
  }
  for (const path of [
    "/v1/me",
    "/v1/tenants",
    "/v1/api-keys",
    "/v1/invitations",
    "/v1/migrations",
    "/v1/events",
    "/v1/webhook-subscriptions",
    "/v1/webhook-deliveries",
  ])
    await check(path, path, token);
  const demo = JSON.parse(
    readFileSync(new URL(".local-data/demo.json", root), "utf8"),
  );
  await check(
    "/v1/tenants/{id}/properties",
    `/v1/tenants/${demo.tenant_id}/properties`,
    token,
  );
  await check(
    "/v1/tenants/{id}/catalog",
    `/v1/tenants/${demo.tenant_id}/catalog`,
    token,
  );
  await check(
    "/v1/migrations/{id}",
    `/v1/migrations/${demo.migration_id}`,
    token,
  );
  await check(
    "/v1/migrations/{id}/dry-run",
    `/v1/migrations/${demo.migration_id}/dry-run`,
    token,
  );
  for (const entity of ["clients", "appointments"])
    for (const fields of [
      "",
      entity === "clients"
        ? "&fields=first_name,email,notes"
        : "&fields=start,employee_id",
    ])
      await check(
        "/v1/migrations/{id}/export",
        `/v1/migrations/${demo.migration_id}/export?entity=${entity}${fields}`,
        token,
      );
  const publicBase =
    "/public/invitations/" +
    new URL(demo.review_url).pathname.split("/").at(-1);
  await check("/public/invitations/{token}", publicBase);
  await check("/public/invitations/{token}/preview", publicBase + "/preview");
  await check(
    "/public/invitations/{token}/source-mappings",
    publicBase + "/source-mappings",
  );
  const example =
    spec.paths["/v1/migrations/{id}/export"].get.responses["200"].content[
      "application/json"
    ];
  assert.ok(ajv.compile(example.schema)(example.example));
  const report = {
    passed: true,
    openapi: "3.1.0",
    paths: Object.keys(spec.paths).length,
    validatedResponseShapes: checks,
    routeCoverage: true,
    fieldRegistryConsistency: true,
  };
  writeFileSync(
    new URL(".local-data/contract-results.json", root),
    JSON.stringify(report, null, 2),
  );
  console.log(JSON.stringify(report, null, 2));
} finally {
  await fetch(base + "/v1/logout", {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });
}
