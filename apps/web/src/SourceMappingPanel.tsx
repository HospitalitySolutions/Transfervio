import { useEffect, useState } from "react";
import { request, type Row, type Notify } from "./ui";

export function SourceMappingPanel({
  base,
  files,
  schema,
  externalAi,
  syntheticOnly,
  changed,
  notify,
}: {
  base: string;
  files: Row[];
  schema: Record<string, Row[]>;
  externalAi: boolean;
  syntheticOnly: boolean;
  changed: () => Promise<void>;
  notify: Notify;
}) {
  const [plans, setPlans] = useState<Row[]>([]),
    [busy, setBusy] = useState(false),
    [error, setError] = useState("");
  async function load() {
    try {
      setPlans(await request(base + "/source-mappings"));
      setError("");
    } catch (e) {
      setError((e as Error).message);
    }
  }
  useEffect(() => {
    void load();
  }, [base, files.map((f) => f.id).join(",")]);
  async function action(fn: () => Promise<unknown>) {
    setBusy(true);
    try {
      await fn();
      await load();
      await changed();
    } catch (e) {
      notify((e as Error).message, true);
    } finally {
      setBusy(false);
    }
  }
  function edit(index: number, patch: Row) {
    setPlans(
      plans.map((p, i) =>
        i === index ? { ...p, ...patch, approved: false } : p,
      ),
    );
  }
  return (
    <section
      className="source-mappings"
      aria-label="Source mapping and AI permission"
    >
      {externalAi && (
        <div className="ai-consent">
          <h3>Permission for external AI</h3>
          <p>
            Authorize only files you have the right to share with OpenAI. Images
            are sent in full; structured files send at most 12 sampled rows.
            Permission alone does not send data.{" "}
            {syntheticOnly &&
              "This runtime accepts verified synthetic fixtures only."}
          </p>
          {files
            .filter((f) => !f.deleted_at)
            .map((f) => (
              <label className="checkbox" key={f.id}>
                <input
                  type="checkbox"
                  disabled={busy}
                  checked={!!f.ai_authorized}
                  aria-label={`Authorize AI for ${f.original_name}`}
                  onChange={(e) => {
                    const authorized = e.target.checked;
                    void action(() =>
                      request(`${base}/uploads/${f.id}/ai-consent`, {
                        method: "PUT",
                        body: JSON.stringify({
                          authorized,
                          synthetic: syntheticOnly,
                        }),
                      }),
                    );
                  }}
                />
                I authorize external AI processing of {f.original_name}.
              </label>
            ))}
        </div>
      )}
      {error && <p className="error-text">{error}</p>}
      {plans.length > 0 && (
        <details open={plans.some((p) => !p.approved)}>
          <summary>
            Source column mappings · {plans.filter((p) => !p.approved).length}{" "}
            need confirmation
          </summary>
          <p>
            Check unfamiliar columns, choose the entity and explicitly ignore
            unrelated fields. Confirmed mappings apply to every row; AI never
            runs per row.
          </p>
          {plans.map((plan, index) => {
            const entity =
                plan.entity === "CLIENT" ? "clients" : "appointments",
              allowed = schema[entity] || [];
            return (
              <div
                className="source-plan"
                key={plan.upload_id + plan.table_name}
              >
                <h3>
                  {plan.filename} · {plan.table_name}
                </h3>
                <p>
                  {plan.row_count} rows ·{" "}
                  {plan.approved ? "Mapping confirmed" : "Review mapping"}
                </p>
                <label>
                  Entity
                  <select
                    aria-label={`Entity for ${plan.filename}`}
                    value={plan.entity}
                    disabled={busy}
                    onChange={(e) =>
                      edit(index, {
                        entity: e.target.value,
                        field_map: Object.fromEntries(
                          plan.columns.map((c: string) => [c, ""]),
                        ),
                      })
                    }
                  >
                    <option value="CLIENT">Clients</option>
                    <option value="APPOINTMENT">Appointments</option>
                  </select>
                </label>
                {plan.columns.map((column: string) => (
                  <div className="source-map-row" key={column}>
                    <div>
                      <strong>{column}</strong>
                      <small>
                        {plan.samples?.[0]?.[column] || "No sample value"}
                      </small>
                    </div>
                    <label>
                      <span className="sr-only">Map {column}</span>
                      <select
                        aria-label={`Map ${column}`}
                        value={plan.field_map[column] || ""}
                        disabled={busy}
                        onChange={(e) =>
                          edit(index, {
                            field_map: {
                              ...plan.field_map,
                              [column]: e.target.value,
                            },
                          })
                        }
                      >
                        <option value="">Ignore this column</option>
                        {allowed.map((f) => (
                          <option key={f.id} value={f.id}>
                            {f.display_name}
                          </option>
                        ))}
                      </select>
                    </label>
                  </div>
                ))}
                <div className="button-row">
                  <button
                    className="secondary"
                    disabled={busy || plan.approved}
                    onClick={() =>
                      void action(() =>
                        request(`${base}/source-mappings/${plan.upload_id}`, {
                          method: "PUT",
                          body: JSON.stringify({
                            table: plan.table_name,
                            entity: plan.entity,
                            fields: plan.field_map,
                            approved: true,
                          }),
                        }),
                      )
                    }
                  >
                    Confirm source mapping
                  </button>
                  {externalAi && (
                    <button
                      className="secondary"
                      disabled={
                        busy ||
                        !files.find((f) => f.id === plan.upload_id)
                          ?.ai_authorized
                      }
                      onClick={() =>
                        void action(() =>
                          request(
                            `${base}/source-mappings/${plan.upload_id}/suggest`,
                            {
                              method: "POST",
                              body: JSON.stringify({ table: plan.table_name }),
                            },
                          ),
                        )
                      }
                    >
                      {busy ? "Working…" : "Suggest mapping with AI"}
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </details>
      )}
    </section>
  );
}
