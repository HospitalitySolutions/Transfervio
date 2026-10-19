import { useState, type ReactNode } from "react";
import {
  ArrowUpRight,
  AlertCircle,
  CheckCircle2,
  FileText,
  Users,
  CalendarDays,
  X,
  ChevronRight,
  Check,
} from "lucide-react";
export type Row = Record<string, any>;
export type Api = (path: string, options?: RequestInit) => Promise<any>;
export type Notify = (text: string, error?: boolean) => void;
export async function request(
  path: string,
  options: RequestInit = {},
  token?: string,
): Promise<any> {
  const response = await fetch(path, {
    ...options,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(options.body && !(options.body instanceof FormData)
        ? { "Content-Type": "application/json" }
        : {}),
      ...options.headers,
    },
  });
  const text = await response.text();
  const data = text ? JSON.parse(text) : undefined;
  if (!response.ok)
    throw new Error(data?.message || `Request failed (${response.status})`);
  return data;
}
export const human = (s: string) =>
  s
    .toLowerCase()
    .replaceAll("_", " ")
    .replace(/^./, (c) => c.toUpperCase());
export const when = (s: string) =>
  new Date(s).toLocaleDateString(undefined, {
    day: "numeric",
    month: "short",
    year: "numeric",
  });
export function Mark({
  name = "transfervio",
  logo,
}: {
  name?: string;
  logo?: string;
}) {
  return (
    <div className="wordmark">
      {logo ? (
        <img src={logo} alt="" />
      ) : (
        <span className="brand-icon">
          <ArrowUpRight size={22} />
        </span>
      )}
      <span>{name}</span>
    </div>
  );
}
export function Status({ value }: { value: string }) {
  return (
    <span className={`status ${value.toLowerCase()}`}>
      <i />
      {human(value)}
    </span>
  );
}
export function Modal({
  title,
  children,
  close,
  wide = false,
}: {
  title: string;
  children: ReactNode;
  close: () => void;
  wide?: boolean;
}) {
  return (
    <div
      className="modal-backdrop"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) close();
      }}
    >
      <section
        className={`modal ${wide ? "wide" : ""}`}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <header>
          <h2>{title}</h2>
          <button
            className="icon-button"
            aria-label="Close dialog"
            onClick={close}
          >
            <X size={20} />
          </button>
        </header>
        {children}
      </section>
    </div>
  );
}
export function Empty({
  title,
  children,
}: {
  title: string;
  children: ReactNode;
}) {
  return (
    <div className="empty">
      <FileText size={30} />
      <h3>{title}</h3>
      <p>{children}</p>
    </div>
  );
}
export function ReviewTable({
  preview,
  edit,
  sourceBase,
}: {
  preview: Row;
  edit?: (r: Row) => void;
  sourceBase?: string;
}) {
  const [entity, setEntity] = useState("CLIENT"),
    [page, setPage] = useState(0),
    [onlyIssues, setOnlyIssues] = useState(false),
    [source, setSource] = useState<Row>();
  const all = preview.records.filter(
      (r: Row) => r.entity === entity && (!onlyIssues || r.issues.length > 0),
    ),
    rows = all.slice(page * 25, (page + 1) * 25);
  return (
    <>
      <section className="table-card">
        <div className="table-toolbar">
          <div className="tabs">
            {[
              ["CLIENT", "Clients", Users],
              ["APPOINTMENT", "Appointments", CalendarDays],
            ].map(([type, label, Icon]: any) => (
              <button
                key={type}
                className={entity === type ? "selected" : ""}
                onClick={() => {
                  setEntity(type);
                  setPage(0);
                }}
              >
                <Icon size={15} />
                {label}
                <span>
                  {preview.records.filter((r: Row) => r.entity === type).length}
                </span>
              </button>
            ))}
          </div>
          <label className="checkbox">
            <input
              type="checkbox"
              checked={onlyIssues}
              onChange={(e) => {
                setOnlyIssues(e.target.checked);
                setPage(0);
              }}
            />
            Needs attention
          </label>
        </div>
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                {entity === "CLIENT" ? (
                  <>
                    <th>Client</th>
                    <th>Email / phone</th>
                  </>
                ) : (
                  <>
                    <th>Date & time</th>
                    <th>Service / employee</th>
                  </>
                )}
                <th>Validation</th>
                <th>Source</th>
                {edit && <th />}
              </tr>
            </thead>
            <tbody>
              {rows.map((r: Row) => (
                <tr key={r.id} className={r.excluded ? "excluded" : ""}>
                  {entity === "CLIENT" ? (
                    <>
                      <td>
                        <strong>
                          {r.data.display_name ||
                            [r.data.first_name, r.data.last_name]
                              .filter(Boolean)
                              .join(" ") ||
                            "Name missing"}
                        </strong>
                        <small>{r.data.source_id || "No source ID"}</small>
                      </td>
                      <td>
                        {r.data.email || "—"}
                        <small>{r.data.phone || "—"}</small>
                      </td>
                    </>
                  ) : (
                    <>
                      <td>
                        <strong>{r.data.start || "Date missing"}</strong>
                        <small>
                          {r.data.timezone || "Timezone missing"} ·{" "}
                          {r.data.duration_minutes || "?"} min
                        </small>
                      </td>
                      <td>
                        {r.data.source_service ||
                          r.data.service_id ||
                          "Service missing"}
                        <small>
                          {r.data.source_employee ||
                            r.data.employee_id ||
                            "Employee missing"}
                        </small>
                      </td>
                    </>
                  )}
                  <td>
                    {r.excluded ? (
                      <Status value="EXCLUDED" />
                    ) : r.issues.length ? (
                      <span
                        className="issue-label"
                        title={r.issues.map((i: Row) => i.message).join("\n")}
                      >
                        <AlertCircle size={14} />
                        {r.issues.length} issues
                      </span>
                    ) : (
                      <span className="valid-label">
                        <CheckCircle2 size={14} />
                        Validated
                      </span>
                    )}
                  </td>
                  <td>
                    <button
                      className="text-button"
                      onClick={() => setSource(r)}
                    >
                      <FileText size={14} />
                      {r.provenance.length} sources
                    </button>
                  </td>
                  {edit && (
                    <td>
                      <button className="text-button" onClick={() => edit(r)}>
                        Review
                        <ChevronRight size={14} />
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
          {!rows.length && (
            <Empty title="Nothing here yet.">
              Records appear after extraction.
            </Empty>
          )}
        </div>
        <footer className="table-footer">
          <span>{all.length} records</span>
          <div>
            <button
              className="text-button"
              disabled={page === 0}
              onClick={() => setPage(page - 1)}
            >
              Previous
            </button>
            <span>
              {page + 1} / {Math.max(1, Math.ceil(all.length / 25))}
            </span>
            <button
              className="text-button"
              disabled={(page + 1) * 25 >= all.length}
              onClick={() => setPage(page + 1)}
            >
              Next
            </button>
          </div>
        </footer>
      </section>
      {source && (
        <Modal title="Source provenance" close={() => setSource(undefined)}>
          {source.provenance.map((p: Row, i: number) => (
            <div className="provenance" key={i}>
              <FileText size={20} />
              <div>
                <strong>{p.filename}</strong>
                <p>
                  Row {p.row} · {human(p.extractor)}
                  {p.duplicate_match ? " · Matched duplicate" : ""}
                </p>
                <small>Source reference: {p.source_id || "Not provided"}</small>
                {sourceBase && (
                  <a
                    href={`${sourceBase}/uploads/${p.upload_id}`}
                    target="_blank"
                    rel="noreferrer"
                  >
                    Download original file
                  </a>
                )}
              </div>
            </div>
          ))}
        </Modal>
      )}
    </>
  );
}
export function EditRecord({
  record,
  preview,
  catalog,
  save,
  close,
  notify,
}: {
  record: Row;
  preview: Row;
  catalog: Row[];
  save: (patch: Row) => Promise<void>;
  close: () => void;
  notify: Notify;
}) {
  const client = record.entity === "CLIENT",
    fields = client
      ? [
          "source_id",
          "first_name",
          "last_name",
          "display_name",
          "email",
          "phone",
          "language",
          "notes",
        ]
      : [
          "source_id",
          "client_record_id",
          "start",
          "timezone",
          "duration_minutes",
          "status",
          "service_id",
          "employee_id",
        ];
  const [busy, setBusy] = useState(false);
  return (
    <Modal
      title={client ? "Review client" : "Review appointment"}
      close={close}
      wide
    >
      <form
        onSubmit={async (e) => {
          e.preventDefault();
          setBusy(true);
          const form = new FormData(e.currentTarget),
            values: Row = {};
          fields.forEach((f) => (values[f] = form.get(f)));
          try {
            await save({
              record_id: record.id,
              fields: values,
              excluded: form.has("excluded"),
              acknowledge_source: form.has("acknowledge_source"),
            });
          } catch (e) {
            notify((e as Error).message, true);
          } finally {
            setBusy(false);
          }
        }}
      >
        {record.issues.length > 0 && (
          <ul className="issues">
            {record.issues.map((i: Row, n: number) => (
              <li key={n}>
                <AlertCircle size={15} />
                {i.message}
              </li>
            ))}
          </ul>
        )}
        <div className="edit-grid">
          {fields.map((field) => (
            <label key={field}>
              {human(field)}
              {field === "client_record_id" ? (
                <select name={field} defaultValue={record.data[field] || ""}>
                  <option value="">Choose a client</option>
                  {preview.records
                    .filter((r: Row) => r.entity === "CLIENT" && !r.excluded)
                    .map((r: Row) => (
                      <option key={r.id} value={r.id}>
                        {r.data.display_name || r.data.first_name} ·{" "}
                        {r.data.source_id}
                      </option>
                    ))}
                </select>
              ) : field === "service_id" || field === "employee_id" ? (
                <select name={field} defaultValue={record.data[field] || ""}>
                  <option value="">Choose {field.split("_")[0]}</option>
                  {catalog
                    .filter((c) => c.kind === field.split("_")[0].toUpperCase())
                    .map((c) => (
                      <option key={c.external_id} value={c.external_id}>
                        {c.name}
                      </option>
                    ))}
                </select>
              ) : field === "status" ? (
                <select name={field} defaultValue={record.data[field] || ""}>
                  <option value="">Choose status</option>
                  {[
                    "BOOKED",
                    "CONFIRMED",
                    "CANCELLED",
                    "COMPLETED",
                    "NO_SHOW",
                  ].map((s) => (
                    <option key={s}>{s}</option>
                  ))}
                </select>
              ) : (
                <input
                  name={field}
                  defaultValue={record.data[field] || ""}
                  placeholder={
                    field === "start" ? "2026-11-02T10:00" : undefined
                  }
                />
              )}
            </label>
          ))}
        </div>
        <label className="checkbox">
          <input
            name="excluded"
            type="checkbox"
            defaultChecked={record.excluded}
          />
          Exclude this record from the migration
        </label>
        <label className="checkbox">
          <input name="acknowledge_source" type="checkbox" />I checked the
          source and resolved extraction or duplicate conflicts.
        </label>
        <div className="modal-actions">
          <button type="button" className="secondary" onClick={close}>
            Cancel
          </button>
          <button className="primary" disabled={busy}>
            Save & revalidate
            <Check size={16} />
          </button>
        </div>
      </form>
    </Modal>
  );
}
