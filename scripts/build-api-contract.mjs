// Enrich Spring's route/DTO inventory with explicit wire response contracts. Local metadata only.
import { readFileSync, writeFileSync } from "node:fs";
const root = new URL("../", import.meta.url),
  env = Object.fromEntries(
    readFileSync(new URL(".env.local", root), "utf8")
      .split(/\r?\n/)
      .filter((l) => /^[A-Z_]+=/.test(l))
      .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]),
  );
const base = `http://localhost:${env.API_PORT || 8091}`;
const spec = await (await fetch(base + "/v3/api-docs")).json();
const login = await fetch(base + "/auth/login", {
  method: "POST",
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify({
    email: "admin@calendra.test",
    password: env.BOOTSTRAP_ADMIN_PASSWORD,
  }),
});
if (!login.ok) throw Error("Local documentation login failed");
const { token } = await login.json();
let discovery;
try {
  const r = await fetch(base + "/v1/export-schema", {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!r.ok) throw Error("Schema discovery failed");
  discovery = await r.json();
} finally {
  await fetch(base + "/v1/logout", {
    method: "POST",
    headers: { Authorization: `Bearer ${token}` },
  });
}
const ref = (name) => ({ $ref: "#/components/schemas/" + name }),
  arr = (items) => ({ type: "array", items }),
  str = { type: "string" },
  uuid = { type: "string", format: "uuid" },
  bool = { type: "boolean" },
  integer = { type: "integer" },
  nullable = (s) => ({ ...s, type: [s.type, "null"] }),
  obj = (properties, required = []) => ({
    type: "object",
    additionalProperties: false,
    properties,
    ...(required.length ? { required } : {}),
  });
const S = spec.components.schemas;
S.Error = obj({ message: str, code: str, retryable: bool }, ["message"]);
S.ExportFieldPolicy = {
  type: "object",
  additionalProperties: false,
  properties: Object.fromEntries(
    Object.entries(discovery.entities).map(([entity, fields]) => [
      entity,
      {
        type: "array",
        uniqueItems: true,
        items: { type: "string", enum: fields.map((f) => f.id) },
      },
    ]),
  ),
  description:
    "Absent entity means all its registry fields. An empty entity list permits no business fields. Intersected with approved sharing scope. Never includes destination field transformations.",
};
for (const [entity, fields] of Object.entries(discovery.entities))
  S[entity === "clients" ? "ClientFields" : "AppointmentFields"] = obj(
    Object.fromEntries(
      fields.map((f) => [
        f.id,
        {
          type: ["string", "null"],
          ...(f.data_type === "uuid" ? { format: "uuid" } : {}),
          description:
            f.description +
            " " +
            f.validation +
            " " +
            (f.required
              ? "Required in validated canonical records, but may be omitted by export projection."
              : "Optional."),
        },
      ]),
    ),
  );
S.SafeProvenance = obj({
  upload_id: uuid,
  sha256: str,
  row: integer,
  extractor: str,
});
S.Relationships = obj({ client_record_id: uuid });
S.ExportRecord = obj(
  {
    stable_record_id: uuid,
    entity: { type: "string", enum: ["CLIENT", "APPOINTMENT"] },
    approved: { type: "boolean", const: true },
    idempotency_key: str,
    data: { anyOf: [ref("ClientFields"), ref("AppointmentFields")] },
    relationships: ref("Relationships"),
    provenance: arr(ref("SafeProvenance")),
  },
  [
    "stable_record_id",
    "entity",
    "approved",
    "idempotency_key",
    "data",
    "relationships",
    "provenance",
  ],
);
S.ExportPage = obj(
  {
    schema_version: { type: "integer", const: 1 },
    migration_id: uuid,
    tenant_id: uuid,
    property_id: nullable(uuid),
    records: arr(ref("ExportRecord")),
    next_cursor: nullable(integer),
    total: integer,
  },
  [
    "schema_version",
    "migration_id",
    "tenant_id",
    "property_id",
    "records",
    "next_cursor",
    "total",
  ],
);
S.FieldDescriptor = obj(
  {
    id: str,
    display_name: str,
    data_type: { type: "string", enum: ["string", "uuid", "datetime"] },
    nullable: bool,
    required: bool,
    description: str,
    validation: str,
  },
  [
    "id",
    "display_name",
    "data_type",
    "nullable",
    "required",
    "description",
    "validation",
  ],
);
S.SchemaDiscovery = obj(
  {
    schema_version: { type: "integer", const: 1 },
    entities: obj({
      clients: arr(ref("FieldDescriptor")),
      appointments: arr(ref("FieldDescriptor")),
    }),
    technical_fields: arr(str),
    compatibility: str,
  },
  ["schema_version", "entities", "technical_fields", "compatibility"],
);
S.Credential = obj(
  { id: uuid, token: str, expires_at: { type: "string", format: "date-time" } },
  ["id", "token", "expires_at"],
);
S.CredentialMetadata = obj({
  id: uuid,
  label: str,
  created_at: str,
  expires_at: nullable(str),
  revoked: bool,
  export_fields: ref("ExportFieldPolicy"),
});
S.Brand = obj({
  id: uuid,
  name: str,
  brand_name: str,
  primary_color: str,
  support_text: str,
  logo_data: str,
});
S.TenantResource = obj(
  {
    id: uuid,
    partner_id: uuid,
    external_tenant_id: str,
    name: str,
    created_at: str,
  },
  ["id", "partner_id", "external_tenant_id", "name"],
);
S.PropertyResource = obj(
  {
    id: uuid,
    tenant_id: uuid,
    partner_id: uuid,
    external_property_id: str,
    name: str,
    timezone: str,
  },
  ["id", "tenant_id", "partner_id", "external_property_id", "name", "timezone"],
);
S.CatalogResource = obj({
  id: uuid,
  tenant_id: uuid,
  partner_id: uuid,
  property_id: uuid,
  kind: { type: "string", enum: ["SERVICE", "EMPLOYEE"] },
  external_id: str,
  name: str,
});
S.Migration = obj(
  {
    id: uuid,
    partner_id: uuid,
    tenant_id: uuid,
    property_id: nullable(uuid),
    status: {
      type: "string",
      enum: [
        "UPLOADING",
        "PROCESSING",
        "REVIEW",
        "READY",
        "PARTIAL",
        "IMPORTED",
        "FAILED",
      ],
    },
    revision: integer,
    created_at: str,
    approved_at: nullable(str),
    export_fields: ref("ExportFieldPolicy"),
    tenant_name: str,
    property_name: nullable(str),
    invitation_id: uuid,
    allowed_entities: arr(str),
    expires_at: str,
  },
  ["id", "partner_id", "tenant_id", "property_id", "status", "revision"],
);
S.InvitationCreated = obj(
  { invitation_id: uuid, migration_id: uuid, upload_url: str, expires_at: str },
  ["invitation_id", "migration_id", "upload_url", "expires_at"],
);
S.InvitationResource = obj({
  id: uuid,
  migration_id: uuid,
  expires_at: str,
  revoked: bool,
  allowed_entities: arr(str),
  tenant_id: uuid,
  property_id: nullable(uuid),
  tenant_name: str,
  property_name: nullable(str),
  status: str,
});
S.UploadResult = obj({ id: uuid, name: str, duplicate: bool }, [
  "id",
  "duplicate",
]);
S.UploadResource = obj({
  id: uuid,
  original_name: str,
  content_type: str,
  size_bytes: integer,
  sha256: str,
  created_at: str,
  deleted_at: nullable(str),
  ai_authorized: bool,
});
S.Issue = obj({ code: str, field: str, message: str, severity: str }, [
  "code",
  "field",
  "message",
  "severity",
]);
S.SourceProvenance = {
  type: "object",
  properties: {
    upload_id: uuid,
    filename: str,
    sha256: str,
    row: integer,
    source_id: str,
    extractor: str,
    table: str,
    parser_issues: arr(str),
    reviewed: bool,
    duplicate_match: bool,
    source_region: arr({ type: "number" }),
    uncertain_fields: arr(str),
  },
  additionalProperties: false,
};
S.ReviewRecord = obj({
  id: uuid,
  entity: str,
  data: { type: "object", additionalProperties: { type: "string" } },
  provenance: arr(ref("SourceProvenance")),
  issues: arr(ref("Issue")),
  excluded: bool,
});
S.DeliveryResult = obj({
  migration_id: uuid,
  record_id: uuid,
  outcome: {
    type: "string",
    enum: ["IMPORTED", "ALREADY_EXISTS", "UPDATED", "FAILED"],
  },
  destination_id: nullable(str),
  error_code: nullable(str),
  attempt: integer,
  updated_at: str,
});
S.Job = obj({
  kind: str,
  status: str,
  attempts: integer,
  error_code: nullable(str),
  error_message: nullable(str),
});
S.AiUsage = obj({
  model: str,
  purpose: str,
  responses: integer,
  input_tokens: integer,
  output_tokens: integer,
  estimated_cost_usd: { type: ["number", "null"] },
});
S.MigrationPreview = obj(
  {
    migration: ref("Migration"),
    files: arr(ref("UploadResource")),
    records: arr(ref("ReviewRecord")),
    delivery_results: arr(ref("DeliveryResult")),
    jobs: arr(ref("Job")),
    ai_usage: arr(ref("AiUsage")),
  },
  ["migration", "delivery_results", "jobs"],
);
S.Landing = obj({
  brand: ref("Brand"),
  migration: ref("Migration"),
  tenant: str,
  property: str,
  catalog: arr(obj({ kind: str, external_id: str, name: str })),
  ai_provider: str,
  external_ai_enabled: bool,
  ai_synthetic_only: bool,
  export_schema: obj({
    clients: arr(ref("FieldDescriptor")),
    appointments: arr(ref("FieldDescriptor")),
  }),
});
S.ApprovalResult = obj({ sha256: str, status: str, created_at: str }, [
  "sha256",
]);
S.SourcePlan = obj({
  upload_id: uuid,
  table_name: str,
  entity: { type: "string", enum: ["CLIENT", "APPOINTMENT"] },
  columns: arr(str),
  field_map: { type: "object", additionalProperties: str },
  approved: bool,
  suggested_by: str,
  updated_at: str,
  filename: str,
  row_count: integer,
  samples: arr({ type: "object", additionalProperties: str }),
});
S.MappingSuggestion = obj({
  entity: str,
  fields: { type: "object", additionalProperties: str },
  approved: { type: "boolean", const: false },
  sample_rows: integer,
});
S.EventPayload = obj({
  event_id: uuid,
  type: { type: "string", const: "migration.ready" },
  migration_id: uuid,
  export_url: str,
  snapshot_sha256: str,
});
S.Event = obj({
  id: uuid,
  event_type: str,
  payload: ref("EventPayload"),
  created_at: str,
});
S.WebhookSubscription = obj({
  id: uuid,
  target_url: str,
  active: bool,
  created_at: str,
});
S.WebhookCreated = obj({ id: uuid, signing_secret: str, target_url: str });
S.WebhookDelivery = obj({
  event_id: uuid,
  subscription_id: uuid,
  status: str,
  attempts: integer,
  available_at: str,
  lease_until: nullable(str),
  last_http_status: nullable(integer),
});
S.DryRun = obj({
  migration_id: uuid,
  destination: str,
  dry_run: { const: true, type: "boolean" },
  writes_enabled: { const: false, type: "boolean" },
  tenant_id: uuid,
  property_id: nullable(uuid),
  proposed_changes: arr(
    obj({
      record_id: uuid,
      entity: str,
      action: str,
      data: { type: "object", additionalProperties: str },
      idempotency_key: str,
    }),
  ),
  notifications: integer,
  billing_operations: integer,
  note: str,
});
S.MockResult = obj({
  receipts: arr(ref("Receipt")),
  destination: str,
  notifications: integer,
  billing_operations: integer,
});
S.Runtime = obj({
  product: str,
  runtime: str,
  ai_provider: str,
  destination_mode: str,
  import_write_enabled: bool,
  first_started_at: str,
});
for (const name of ["Invitation", "Key", "Approval"])
  S[name].properties.export_fields = ref("ExportFieldPolicy");
S.Receipt.required = ["record_id", "outcome"];
S.Receipt.properties.outcome = {
  type: "string",
  enum: ["IMPORTED", "ALREADY_EXISTS", "UPDATED", "FAILED"],
};
S.Receipts.required = ["results"];
S.Receipts.properties.results.minItems = 1;
S.Receipts.properties.results.maxItems = 1000;
S.Mapping.required = ["table", "entity", "fields", "approved"];
S.Mapping.properties.entity = {
  type: "string",
  enum: ["CLIENT", "APPOINTMENT"],
};
S.Consent.required = ["authorized", "synthetic"];
const responses = new Map();
function response(path, method, schema, summary, description = "") {
  responses.set(method + " " + path, { schema, summary, description });
}
response(
  "/auth/login",
  "post",
  ref("Credential"),
  "Create an eight-hour partner administrator session",
  "Synthetic local bootstrap accounts only; no public signup. Wrong credentials return 401.",
);
response(
  "/api/runtime",
  "get",
  ref("Runtime"),
  "Read non-secret runtime policy",
);
response("/v1/me", "get", ref("Brand"), "Read authenticated partner branding");
response(
  "/v1/api-keys",
  "get",
  arr(ref("CredentialMetadata")),
  "List partner API keys",
  "Administrator session required. Hashes and raw credentials are never returned.",
);
response(
  "/v1/api-keys",
  "post",
  ref("Credential"),
  "Issue a 90-day partner API key",
  "Administrator session required. Token is returned once. Optional export_fields restricts this key.",
);
for (const method of ["get", "post"])
  response(
    "/v1/tenants",
    method,
    method === "get" ? arr(ref("TenantResource")) : ref("TenantResource"),
    method === "get"
      ? "List owned tenants"
      : "Upsert a tenant by partner-scoped external ID",
  );
for (const method of ["get", "post"])
  response(
    "/v1/tenants/{id}/properties",
    method,
    method === "get" ? arr(ref("PropertyResource")) : ref("PropertyResource"),
    method === "get"
      ? "List tenant properties"
      : "Upsert a property with an IANA timezone",
  );
response(
  "/v1/tenants/{id}/catalog",
  "get",
  arr(ref("CatalogResource")),
  "List optional tenant destination catalogs",
  "Catalog IDs are validated if explicitly supplied. Missing destination references do not prevent approval; vendors resolve source labels.",
);
response(
  "/v1/invitations",
  "get",
  arr(ref("InvitationResource")),
  "List owned invitations",
  "Raw capability tokens are never listed.",
);
response(
  "/v1/invitations",
  "post",
  ref("InvitationCreated"),
  "Create a scoped invitation",
  "Tenant/property and allowed entities are fixed server-side. Optional export_fields caps sharing. Field-restricted API keys cannot create full-access public capabilities. Save upload_url securely; it is returned once.",
);
response(
  "/v1/migrations",
  "get",
  arr(ref("Migration")),
  "List owned migrations",
);
response(
  "/v1/migrations/{id}",
  "get",
  ref("MigrationPreview"),
  "Read migration status and receipts",
  "API keys receive migration, jobs and delivery_results only. Administrator sessions additionally receive source review data. Customer records are available to vendors through authorized approved exports.",
);
response(
  "/v1/export-schema",
  "get",
  ref("SchemaDiscovery"),
  "Discover supported canonical fields",
  "No customer data. Optional entity=clients|appointments. Only fields allowed by this credential are returned; per-migration approval may further narrow them.",
);
response(
  "/v1/migrations/{id}/export",
  "get",
  ref("ExportPage"),
  "Retrieve immutable approved records with optional field selection",
  "entity defaults to clients. Omit fields for full authorized data. A supplied nonempty comma list projects business fields only; selected missing/empty values become null, unselected keys are omitted. Unknown fields: 400; known unauthorized fields: 403. Scope is the intersection of the registry, API credential and immutable customer approval. Stable IDs, idempotency keys and opaque client relationships remain available independently. Provenance excludes filenames and source IDs. Export never marks IMPORTED. Offset cursor/limit (1–200) paginate the same immutable snapshot; next_cursor=null ends the entity. Client pages must be handled before dependent appointments.",
);
response(
  "/v1/migrations/{id}/dry-run",
  "get",
  ref("DryRun"),
  "Preview only the local mock destination",
  "Administrator session required. No vendor adapter, external write or business-rule guarantee.",
);
response(
  "/v1/migrations/{id}/mock-delivery",
  "post",
  ref("MockResult"),
  "Exercise local mock receipts for testing",
  "Administrator session required. This route is a local test helper, not a vendor connector.",
);
response(
  "/public/invitations/{token}",
  "get",
  ref("Landing"),
  "Resolve a branded scoped invitation",
);
response(
  "/public/invitations/{token}/uploads",
  "post",
  arr(ref("UploadResult")),
  "Upload private local source files",
  "Up to 20 unique files, 10 MiB each, 50 MiB per request. UTF-8 CSV/JSON, XLSX without evaluated macros/formulas, valid PNG/JPEG. Repeated checksum uploads are idempotent. Uploads never trigger external AI or destination imports.",
);
response(
  "/public/invitations/{token}/preview",
  "get",
  ref("MigrationPreview"),
  "Review normalized records, issues and safe job diagnostics",
);
response(
  "/public/invitations/{token}/approve",
  "post",
  ref("ApprovalResult"),
  "Freeze the reviewed migration snapshot",
  "Requires current revision and no included record errors. Optional export_fields narrows invitation sharing; it cannot expand it. Repeated approval returns the existing digest/timestamp. READY means approved; only receipts can complete imports.",
);
response(
  "/public/invitations/{token}/source-mappings",
  "get",
  arr(ref("SourcePlan")),
  "Inspect deterministic source column mappings",
  "Returns bounded local samples for the invitation holder. Unknown columns require explicit mapping or ignore. This GET makes no AI calls.",
);
response(
  "/public/invitations/{token}/source-mappings/{upload}/suggest",
  "post",
  ref("MappingSuggestion"),
  "Request a reviewable AI mapping suggestion",
  "Requires runtime opt-in and consent for this exact upload. Sends at most 12 representative rows, 120 characters per value, within a 16,000-character sample budget. Suggestions are never approved automatically. Provider failures return safe typed diagnostics.",
);
response(
  "/v1/events",
  "get",
  arr(ref("Event")),
  "Poll migration.ready events",
  "cursor is an offset, default 0. Returns up to 100 events ordered by creation and ID. Persist event IDs, advance by returned count, and deduplicate retries. Events contain IDs/export references, not source PII.",
);
response(
  "/v1/webhook-subscriptions",
  "get",
  arr(ref("WebhookSubscription")),
  "List local webhook subscriptions",
);
response(
  "/v1/webhook-subscriptions",
  "post",
  ref("WebhookCreated"),
  "Create a signed local webhook subscription",
  "Administrator session required. Exact configured loopback receiver only; no redirects. Signing secret is returned once.",
);
response(
  "/v1/webhook-deliveries",
  "get",
  arr(ref("WebhookDelivery")),
  "Inspect up to 200 webhook delivery attempts",
);
const operationDescriptions = {
  "post /v1/migrations/{id}/delivery-results":
    "Return itemized verified vendor receipts. Successful outcomes require a destination_id; acknowledge clients before their dependent appointments. Success is monotonic: retry failure cannot downgrade success, and conflicting destination IDs return 409. An unknown record or invalid receipt returns 400. Partial acknowledgments remain PARTIAL; only all included approved records succeeding produces IMPORTED. This API records the authenticated vendor assertion and cannot verify its database.",
  "put /public/invitations/{token}/source-mappings/{upload}":
    "Map every source column to one entity field or an empty string to ignore it. Unknown targets and duplicate target mappings return 400. Confirming a mapping applies it deterministically to all rows after reprocessing; changing it invalidates prior unapproved review records. Approved migrations return 409.",
  "put /public/invitations/{token}/uploads/{upload}/ai-consent":
    "Explicitly grant or revoke external processing permission for this immutable upload checksum. No network call occurs here. Runtime AI opt-in is still required; synthetic-only mode accepts only verified fixtures. Revoked/expired invitations or cross-migration uploads cannot grant consent.",
  "post /public/invitations/{token}/process":
    "Queue durable extraction after mapping confirmation. Image extraction in OpenAI mode additionally requires per-upload consent. The worker retries transient failures up to three times; authentication, model, quota and schema failures stop with actionable diagnostics. Mock mode has no external calls. Processing blocks edits.",
  "patch /public/invitations/{token}/mappings":
    "Correct canonical fields using the current review revision, exclude records or acknowledge source review. Revalidates dates, identity, relationships and scope. AI records require source acknowledgment. No field can alter partner, tenant or property scope.",
  "delete /v1/migrations/{id}":
    "Administrator session required. Deletes this migration and its local originals, rows, snapshots, cached AI outputs, receipts and mock effects. Backups are separate. PROCESSING migrations return 409.",
  "put /v1/tenants/{id}/catalog":
    "Upsert optional SERVICE/EMPLOYEE catalog entries within the specified owned property. No vendor-specific transformation or adapter runs here.",
};
for (const [path, item] of Object.entries(spec.paths))
  for (const [method, operation] of Object.entries(item)) {
    if (!["get", "post", "put", "patch", "delete"].includes(method)) continue;
    operation.security = path.startsWith("/v1/") ? [{ partnerBearer: [] }] : [];
    const rich = responses.get(method + " " + path);
    if (rich) {
      operation.summary = rich.summary;
      operation.description = rich.description;
      operation.responses["200"] = {
        description: "Success",
        content: { "application/json": { schema: rich.schema } },
      };
    } else {
      operation.summary = operation.summary || operation.operationId;
      operation.description =
        operationDescriptions[method + " " + path] ||
        operation.description ||
        "Scoped partner operation. Cross-partner resources return 404.";
    }
    if (operationDescriptions[method + " " + path])
      operation.description = operationDescriptions[method + " " + path];
    if (path.startsWith("/public/"))
      operation.description =
        (operation.description || "") +
        " The opaque invitation capability in the path must be valid, unexpired and not revoked. Never log this URL.";
    if (path.includes("/uploads/{id}") && method === "get")
      operation.responses["200"] = {
        description:
          "Original bytes; 410 after retention expiry. Scoped attachment download.",
        content: {
          "text/csv": { schema: { type: "string", format: "binary" } },
          "application/json": { schema: { type: "string", format: "binary" } },
          "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": {
            schema: { type: "string", format: "binary" },
          },
          "image/png": { schema: { type: "string", format: "binary" } },
          "image/jpeg": { schema: { type: "string", format: "binary" } },
        },
      };
    for (const [code, description] of Object.entries({
      400: "Invalid entity, field, mapping or request",
      401: "Missing, expired or revoked partner credential",
      403: "Administrator privilege, field authorization or AI consent required",
      404: "Missing, expired, revoked or out-of-scope resource",
      409: "State/revision conflict, unresolved validation or conflicting receipt",
      410: "Original file retention period ended",
      413: "Upload size limit exceeded",
      415: "Unsupported or mismatched file type",
      429: "Local request rate limit exceeded",
      500: "Safe generic operation failure",
      502: "AI provider authentication/model/quota/rate-limit/timeout/structured-output error",
    }))
      operation.responses[code] = {
        description,
        content: { "application/json": { schema: ref("Error") } },
      };
    if (path.endsWith("/export"))
      for (const parameter of operation.parameters || []) {
        if (parameter.name === "fields")
          parameter.description =
            "Optional comma-separated business field IDs. Blank, unknown or wrong-entity fields produce 400; unauthorized known fields produce 403.";
        if (parameter.name === "entity")
          parameter.schema.enum = ["clients", "appointments"];
        if (parameter.name === "limit") {
          parameter.schema.minimum = 1;
          parameter.schema.maximum = 200;
        }
        if (parameter.name === "cursor") parameter.schema.minimum = 0;
      }
  }
spec.openapi = "3.1.0";
spec.info = {
  title: "Transfervio Universal Migration API",
  version: "1.1.0",
  description:
    "Vendor-initiated migration. Transfervio normalizes and reviews sources, freezes approvals and exports canonical v1 records. Vendors own field mapping, destination catalog resolution, persistence, business rules and verified receipts. There are no destination-specific adapters. Source and canonical storage stay local in development; external AI requires explicit runtime and per-upload permission. Schema changes are additive within v1; breaking field meaning/type/identity changes require a new major API/schema version. Saved export profiles are not implemented; future profiles may only narrow the same authorization intersection.",
};
spec.servers = [
  { url: "http://localhost:8091", description: "Local development only" },
];
spec.components.securitySchemes = {
  partnerBearer: {
    type: "http",
    scheme: "bearer",
    description:
      "Partner API key or eight-hour administrator session. Secrets are issued once and must stay on the vendor server. Administrative operations require a session.",
  },
};
spec.webhooks = {
  "migration.ready": {
    post: {
      summary: "Signed migration.ready event to the registered local receiver",
      description:
        "Verify HMAC-SHA256 over timestamp.eventId.exactBody with the subscription secret. Enforce a five-minute timestamp window and persist event IDs with your effect to reject replay. Headers X-Transfervio-Timestamp, X-Transfervio-Event, X-Transfervio-Signature (sha256=<hex>). Non-2xx responses are retried up to five attempts with backoff; return 2xx for already completed events. No PII is included.",
      parameters: [
        "X-Transfervio-Timestamp",
        "X-Transfervio-Event",
        "X-Transfervio-Signature",
      ].map((name) => ({ name, in: "header", required: true, schema: str })),
      requestBody: {
        required: true,
        content: { "application/json": { schema: ref("EventPayload") } },
      },
      responses: {
        204: { description: "Accepted or already processed" },
        400: { description: "Invalid signature, timestamp or payload" },
      },
    },
  },
};
spec.paths["/v1/migrations/{id}/export"].get.responses["200"].content[
  "application/json"
].example = {
  schema_version: 1,
  migration_id: "00000000-0000-0000-0000-000000000001",
  tenant_id: "00000000-0000-0000-0000-000000000002",
  property_id: null,
  records: [
    {
      stable_record_id: "00000000-0000-0000-0000-000000000003",
      entity: "CLIENT",
      approved: true,
      idempotency_key: "opaque-stable-key",
      data: {
        first_name: "Synthetic",
        email: "synthetic@example.test",
        phone: null,
      },
      relationships: {},
      provenance: [],
    },
  ],
  next_cursor: null,
  total: 1,
};
writeFileSync(
  new URL("apps/api/src/main/resources/api-contract.json", root),
  JSON.stringify(spec, null, 2) + "\n",
);
console.log(
  `Prepared OpenAPI 3.1 contract: ${Object.keys(spec.paths).length} paths and ${Object.keys(S).length} schemas.`,
);
