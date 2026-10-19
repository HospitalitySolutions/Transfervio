import { useEffect, useState, type FormEvent, type CSSProperties } from "react";
import { SourceMappingPanel } from "./SourceMappingPanel";
import {
  ArrowUpRight,
  ArrowRight,
  Plus,
  Search,
  LayoutDashboard,
  Building2,
  Link2,
  Palette,
  KeyRound,
  LogOut,
  ChevronRight,
  Check,
  CheckCircle2,
  UploadCloud,
  FileText,
  X,
  RefreshCw,
  ShieldCheck,
  Copy,
  AlertCircle,
  Clock3,
  Users,
  CalendarDays,
  Download,
  Database,
  Eye,
  LoaderCircle,
  MapPin,
  Layers3,
} from "lucide-react";
import {
  type Row,
  type Api,
  type Notify,
  request,
  human,
  when,
  Mark,
  Status,
  Modal,
  Empty,
  ReviewTable,
  EditRecord,
} from "./ui";
export default function App() {
  const match = location.pathname.match(/^\/i\/([^/]+)$/),
    [token, setToken] = useState(
      sessionStorage.getItem("transfervio-session") || "",
    ),
    [notice, setNotice] = useState<{ text: string; error: boolean }>();
  const notify: Notify = (text, error = false) => setNotice({ text, error });
  useEffect(() => {
    if (notice) {
      const t = setTimeout(() => setNotice(undefined), 7000);
      return () => clearTimeout(t);
    }
  }, [notice]);
  return (
    <>
      {match ? (
        <Portal invitation={match[1]} notify={notify} />
      ) : token ? (
        <Dashboard
          token={token}
          notify={notify}
          logout={() => {
            sessionStorage.removeItem("transfervio-session");
            setToken("");
          }}
        />
      ) : (
        <Login
          notify={notify}
          login={(t) => {
            sessionStorage.setItem("transfervio-session", t);
            setToken(t);
          }}
        />
      )}
      {notice && (
        <div className={`toast ${notice.error ? "error" : ""}`} role="status">
          {notice.error ? (
            <AlertCircle size={18} />
          ) : (
            <CheckCircle2 size={18} />
          )}
          <span>{notice.text}</span>
          <button aria-label="Dismiss" onClick={() => setNotice(undefined)}>
            <X size={16} />
          </button>
        </div>
      )}
    </>
  );
}
function Login({
  login,
  notify,
}: {
  login: (token: string) => void;
  notify: Notify;
}) {
  const [busy, setBusy] = useState(false);
  async function submit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    setBusy(true);
    try {
      login(
        (
          await request("/auth/login", {
            method: "POST",
            body: JSON.stringify(
              Object.fromEntries(new FormData(e.currentTarget)),
            ),
          })
        ).token,
      );
    } catch (e) {
      notify((e as Error).message, true);
    } finally {
      setBusy(false);
    }
  }
  return (
    <div className="login-layout">
      <section className="login-story">
        <Mark />
        <div>
          <span className="eyebrow">A GOOD MOVE STARTS HERE</span>
          <h1>
            A fresh start.
            <br />
            With your data
            <br />
            <em>intact.</em>
          </h1>
          <p>
            Help your customers bring their clients and appointments along. One
            invitation. A clear review. A confident next step.
          </p>
          <div className="story-flow">
            <span>
              <UploadCloud />
              Upload
            </span>
            <ArrowRight />
            <span>
              <Eye />
              Review
            </span>
            <ArrowRight />
            <span>
              <CheckCircle2 />
              Move forward
            </span>
          </div>
        </div>
        <small>Built for a smoother welcome.</small>
      </section>
      <section className="login-form">
        <div className="login-box">
          <span className="badge">● LOCAL WORKSPACE</span>
          <h2>Welcome back.</h2>
          <p>Sign in to your partner workspace.</p>
          <form onSubmit={submit}>
            <label>
              Email
              <input
                name="email"
                type="email"
                defaultValue="admin@calendra.test"
                required
                autoComplete="username"
              />
            </label>
            <label>
              Password
              <input
                name="password"
                type="password"
                required
                autoComplete="current-password"
              />
            </label>
            <button className="primary" disabled={busy}>
              {busy ? (
                "Signing in…"
              ) : (
                <>
                  Sign in
                  <ArrowRight size={18} />
                </>
              )}
            </button>
          </form>
          <div className="local-note">
            <ShieldCheck size={21} />
            <span>
              Local storage for your development data.
              <br />
              External AI and remote imports are disabled by default.
            </span>
          </div>
          <details>
            <summary>First time here?</summary>
            <p>
              Local partners: <strong>admin@calendra.test</strong> and{" "}
              <strong>admin@lime.test</strong>. Use the generated{" "}
              <code>BOOTSTRAP_ADMIN_PASSWORD</code> in your{" "}
              <code>.env.local</code> file.
            </p>
          </details>
        </div>
      </section>
    </div>
  );
}
function Dashboard({
  token,
  logout,
  notify,
}: {
  token: string;
  logout: () => void;
  notify: Notify;
}) {
  const [me, setMe] = useState<Row>(),
    [tab, setTab] = useState("Migrations"),
    [migrations, setMigrations] = useState<Row[]>([]),
    [tenants, setTenants] = useState<Row[]>([]),
    [invitations, setInvitations] = useState<Row[]>([]),
    [query, setQuery] = useState(""),
    [filter, setFilter] = useState("All migrations"),
    [dialog, setDialog] = useState(""),
    [selected, setSelected] = useState<Row>();
  const api: Api = (path, options = {}) => request(path, options, token);
  async function load() {
    try {
      const [p, ms, ts, is] = await Promise.all([
        api("/v1/me"),
        api("/v1/migrations"),
        api("/v1/tenants"),
        api("/v1/invitations"),
      ]);
      setMe(p);
      setMigrations(ms);
      setTenants(ts);
      setInvitations(is);
    } catch (e) {
      notify((e as Error).message, true);
    }
  }
  useEffect(() => {
    void load();
  }, [token]);
  const filtered = migrations.filter(
    (m) =>
      `${m.tenant_name} ${m.property_name || ""}`
        .toLowerCase()
        .includes(query.toLowerCase()) &&
      (filter === "All migrations" ||
        (filter === "Needs review"
          ? m.status === "REVIEW"
          : filter === "Imported"
            ? m.status === "IMPORTED"
            : !["IMPORTED", "REVIEW"].includes(m.status))),
  );
  return (
    <div className="workspace">
      <aside className="sidebar">
        <Mark />
        <div className="workspace-switch">
          <span className="avatar">{me?.name?.[0] || "P"}</span>
          <div>
            <strong>{me?.name || "Partner"}</strong>
            <small>Partner workspace</small>
          </div>
          <ChevronRight size={15} />
        </div>
        <span className="nav-label">WORKSPACE</span>
        <nav>
          {[
            ["Migrations", LayoutDashboard],
            ["Customers", Building2],
            ["Invitations", Link2],
            ["Branding", Palette],
            ["API keys", KeyRound],
          ].map(([name, Icon]: any) => (
            <button
              key={name}
              className={tab === name ? "active" : ""}
              onClick={() => setTab(name)}
            >
              <Icon size={19} />
              {name}
              {name === "Migrations" && <span>{migrations.length}</span>}
            </button>
          ))}
        </nav>
        <div className="sidebar-bottom">
          <div className="privacy-card">
            <ShieldCheck size={22} />
            <strong>Local by design</strong>
            <p>
              Your data is stored here.
              <br />
              You decide when it moves.
            </p>
            <span>● Local test destination</span>
          </div>
          <button
            className="profile"
            onClick={async () => {
              try {
                await api("/v1/logout", { method: "POST" });
              } finally {
                logout();
              }
            }}
          >
            <span className="avatar">{me?.name?.[0] || "P"}</span>
            <span>
              <strong>Partner admin</strong>
              <small>Sign out</small>
            </span>
            <LogOut size={17} />
          </button>
        </div>
      </aside>
      <main className="main">
        <header className="topbar">
          <span>
            Workspace
            <ChevronRight size={14} />
            <strong>{tab}</strong>
          </span>
          <div>
            <span className="local-pill">● Local environment</span>
            <span className="round-avatar">{me?.name?.[0] || "P"}</span>
          </div>
        </header>
        <div className="page">
          <div className="page-heading">
            <div>
              <span className="eyebrow">YOUR PARTNER WORKSPACE</span>
              <h1>
                {tab === "Migrations"
                  ? "Every move, in one place."
                  : tab === "Customers"
                    ? "A place for every customer."
                    : tab === "Invitations"
                      ? "Open the door to a fresh start."
                      : tab === "Branding"
                        ? "Make the welcome yours."
                        : "Connect your workflow."}
              </h1>
              <p>
                {tab === "Migrations"
                  ? "Invite customers, review their data, and follow every migration through."
                  : tab === "Customers"
                    ? "Keep customers, locations, and destination catalogs in the right place."
                    : tab === "Invitations"
                      ? "Create a secure upload link for a specific customer and location."
                      : tab === "Branding"
                        ? "A familiar experience, from the very first upload."
                        : "Manage server credentials for your partner integration."}
              </p>
            </div>
            {["Migrations", "Invitations"].includes(tab) ? (
              <button className="primary" onClick={() => setDialog("invite")}>
                <Plus size={17} />
                New invitation
              </button>
            ) : tab === "Customers" ? (
              <button className="primary" onClick={() => setDialog("customer")}>
                <Plus size={17} />
                Add customer
              </button>
            ) : null}
          </div>
          {tab === "Migrations" && (
            <>
              <div className="stats">
                {[
                  [
                    "Total migrations",
                    migrations.length,
                    Layers3,
                    "All customer moves",
                  ],
                  [
                    "Ready for review",
                    migrations.filter((m) => m.status === "REVIEW").length,
                    Eye,
                    "A little attention goes a long way",
                  ],
                  [
                    "Successfully imported",
                    migrations.filter((m) => m.status === "IMPORTED").length,
                    CheckCircle2,
                    "Confirmed by the destination",
                  ],
                ].map(([label, value, Icon, detail]: any) => (
                  <div className="stat" key={label}>
                    <div>
                      <span>{label}</span>
                      <span className="stat-icon">
                        <Icon size={16} />
                      </span>
                    </div>
                    <strong>{String(value).padStart(2, "0")}</strong>
                    <small>{detail}</small>
                  </div>
                ))}
              </div>
              <section className="table-card">
                <div className="table-toolbar">
                  <div className="tabs">
                    {[
                      "All migrations",
                      "Needs review",
                      "Imported",
                      "In progress",
                    ].map((f) => (
                      <button
                        key={f}
                        className={filter === f ? "selected" : ""}
                        onClick={() => setFilter(f)}
                      >
                        {f}
                      </button>
                    ))}
                  </div>
                  <label className="search">
                    <Search size={16} />
                    <input
                      placeholder="Find a customer…"
                      aria-label="Find a customer"
                      value={query}
                      onChange={(e) => setQuery(e.target.value)}
                    />
                  </label>
                </div>
                {filtered.length ? (
                  <div className="table-scroll">
                    <table>
                      <thead>
                        <tr>
                          <th>Customer / location</th>
                          <th>Status</th>
                          <th>Created</th>
                          <th>Destination</th>
                          <th />
                        </tr>
                      </thead>
                      <tbody>
                        {filtered.map((m) => (
                          <tr
                            key={m.id}
                            className="clickable"
                            onClick={() => {
                              setSelected(m);
                              setDialog("migration");
                            }}
                          >
                            <td>
                              <div className="customer-cell">
                                <span className="customer-icon">
                                  <Building2 size={19} />
                                </span>
                                <div>
                                  <strong>{m.tenant_name}</strong>
                                  <small>
                                    {m.property_name || "All locations"}
                                  </small>
                                </div>
                              </div>
                            </td>
                            <td>
                              <Status value={m.status} />
                            </td>
                            <td className="muted">{when(m.created_at)}</td>
                            <td>
                              <span className="destination-dot" />
                              Local mock
                            </td>
                            <td>
                              <button
                                className="icon-button"
                                aria-label={`Open migration for ${m.tenant_name}`}
                              >
                                <ArrowUpRight size={18} />
                              </button>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                ) : (
                  <Empty title="Your next welcome starts here.">
                    Create an invitation to help a customer bring their data
                    along.
                  </Empty>
                )}
                <footer className="table-footer">
                  <span>{filtered.length} migrations</span>
                  <span>
                    <ShieldCheck size={13} />
                    Private, scoped, and reviewable
                  </span>
                </footer>
              </section>
              <div className="guide-banner">
                <span className="guide-icon">
                  <ArrowUpRight size={28} />
                </span>
                <div>
                  <h3>From their old system. Into a new chapter.</h3>
                  <p>
                    Customers can upload exports or screenshots. You’ll review
                    the details before anything is imported.
                  </p>
                </div>
                <button
                  className="text-button"
                  onClick={() => setDialog("invite")}
                >
                  Create an invitation
                  <ArrowRight size={16} />
                </button>
              </div>
            </>
          )}
          {tab === "Customers" && (
            <section className="table-card">
              {tenants.length ? (
                <table>
                  <thead>
                    <tr>
                      <th>Customer</th>
                      <th>Your reference</th>
                      <th>Added</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {tenants.map((t) => (
                      <tr
                        key={t.id}
                        className="clickable"
                        onClick={() => {
                          setSelected(t);
                          setDialog("tenant");
                        }}
                      >
                        <td>
                          <div className="customer-cell">
                            <span className="customer-icon">
                              <Building2 size={18} />
                            </span>
                            <strong>{t.name}</strong>
                          </div>
                        </td>
                        <td>{t.external_tenant_id}</td>
                        <td>{when(t.created_at)}</td>
                        <td>
                          <ChevronRight size={16} />
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              ) : (
                <Empty title="Meet your first customer.">
                  Add a customer, then their locations and catalog.
                </Empty>
              )}
            </section>
          )}
          {tab === "Invitations" && (
            <section className="table-card">
              <div className="table-scroll">
                <table>
                  <thead>
                    <tr>
                      <th>Customer</th>
                      <th>Location</th>
                      <th>Expires</th>
                      <th>Status</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    {invitations.map((i) => (
                      <tr key={i.id}>
                        <td>
                          <strong>{i.tenant_name}</strong>
                        </td>
                        <td>{i.property_name || "All locations"}</td>
                        <td>{when(i.expires_at)}</td>
                        <td>
                          <Status
                            value={
                              i.revoked
                                ? "REVOKED"
                                : new Date(i.expires_at) < new Date()
                                  ? "EXPIRED"
                                  : "ACTIVE"
                            }
                          />
                        </td>
                        <td>
                          {!i.revoked && (
                            <button
                              className="text-button danger"
                              onClick={async () => {
                                try {
                                  await api(`/v1/invitations/${i.id}/revoke`, {
                                    method: "POST",
                                  });
                                  await load();
                                  notify("Invitation revoked.");
                                } catch (e) {
                                  notify((e as Error).message, true);
                                }
                              }}
                            >
                              Revoke
                            </button>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </section>
          )}
          {tab === "Branding" && me && (
            <Branding me={me} api={api} notify={notify} refresh={load} />
          )}
          {tab === "API keys" && <Keys api={api} notify={notify} />}
          <footer className="page-footer">
            <span>transfervio · A smoother way forward.</span>
            <span>
              <Database size={13} />
              Stored locally · Review required
            </span>
          </footer>
        </div>
      </main>
      {dialog === "invite" && (
        <InviteModal
          tenants={tenants}
          api={api}
          notify={notify}
          close={() => setDialog("")}
          refresh={load}
          addCustomer={() => setDialog("customer")}
        />
      )}
      {dialog === "customer" && (
        <Modal title="Add a customer" close={() => setDialog("")}>
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              try {
                await api("/v1/tenants", {
                  method: "POST",
                  body: JSON.stringify(
                    Object.fromEntries(new FormData(e.currentTarget)),
                  ),
                });
                await load();
                setDialog("");
                notify("Customer added.");
              } catch (e) {
                notify((e as Error).message, true);
              }
            }}
          >
            <label>
              Customer name
              <input name="name" required placeholder="e.g. Studio Aurora" />
            </label>
            <label>
              Your customer reference
              <input
                name="external_tenant_id"
                required
                placeholder="Your unique customer ID"
              />
            </label>
            <p className="hint">
              This reference belongs to your partner account.
            </p>
            <button className="primary">
              Add customer
              <Plus size={16} />
            </button>
          </form>
        </Modal>
      )}
      {dialog === "tenant" && selected && (
        <TenantModal
          tenant={selected}
          api={api}
          notify={notify}
          close={() => setDialog("")}
        />
      )}
      {dialog === "migration" && selected && (
        <MigrationModal
          migration={selected}
          api={api}
          notify={notify}
          close={() => {
            setDialog("");
            void load();
          }}
        />
      )}
    </div>
  );
}
function InviteModal({
  tenants,
  api,
  notify,
  close,
  refresh,
  addCustomer,
}: {
  tenants: Row[];
  api: Api;
  notify: Notify;
  close: () => void;
  refresh: () => Promise<void>;
  addCustomer: () => void;
}) {
  const [tenant, setTenant] = useState(tenants[0]?.id || ""),
    [properties, setProperties] = useState<Row[]>([]),
    [result, setResult] = useState<Row>(),
    [busy, setBusy] = useState(false);
  useEffect(() => {
    setProperties([]);
    if (tenant)
      api(`/v1/tenants/${tenant}/properties`)
        .then(setProperties)
        .catch((e) => notify(e.message, true));
  }, [tenant]);
  return (
    <Modal
      title={result ? "Your invitation is ready." : "Invite a customer"}
      close={close}
    >
      {result ? (
        <div>
          <div className="success-mark">
            <Check size={30} />
          </div>
          <p>
            This private, local link opens on this computer. Save it before
            closing; the raw link is shown only once.
          </p>
          <label>
            Upload link
            <input
              readOnly
              value={result.upload_url}
              onFocus={(e) => e.target.select()}
            />
          </label>
          <div className="button-row">
            <button
              className="primary"
              onClick={async () => {
                await navigator.clipboard.writeText(result.upload_url);
                notify("Invitation link copied.");
              }}
            >
              <Copy size={15} />
              Copy link
            </button>
            <a
              className="button secondary"
              href={result.upload_url}
              target="_blank"
              rel="noreferrer"
            >
              Open upload page
              <ArrowUpRight size={16} />
            </a>
          </div>
          <p className="hint">Expires {when(result.expires_at)}</p>
        </div>
      ) : tenants.length ? (
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            setBusy(true);
            const f = new FormData(e.currentTarget);
            try {
              setResult(
                await api("/v1/invitations", {
                  method: "POST",
                  body: JSON.stringify({
                    tenant_id: tenant,
                    property_id: f.get("property_id") || null,
                    expires_in_hours: Number(f.get("expires_in_hours")),
                    allowed_entities: f.get("property_id")
                      ? ["CLIENT", "APPOINTMENT"]
                      : ["CLIENT"],
                  }),
                }),
              );
              await refresh();
            } catch (e) {
              notify((e as Error).message, true);
            } finally {
              setBusy(false);
            }
          }}
        >
          <p>
            A branded upload page, securely bound to the customer and location
            you choose.
          </p>
          <label>
            Customer
            <select value={tenant} onChange={(e) => setTenant(e.target.value)}>
              {tenants.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Location
            <select name="property_id" key={tenant}>
              <option value="">All locations · clients only</option>
              {properties.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </label>
          <label>
            Link expires in
            <select name="expires_in_hours" defaultValue="168">
              <option value="24">24 hours</option>
              <option value="168">7 days</option>
              <option value="720">30 days</option>
            </select>
          </label>
          <div className="local-note">
            <ShieldCheck size={18} />
            <span>The customer cannot change the destination.</span>
          </div>
          <button className="primary" disabled={busy}>
            Create invitation
            <ArrowRight size={16} />
          </button>
        </form>
      ) : (
        <Empty title="Add a customer first.">
          <button className="primary" onClick={addCustomer}>
            Add customer
          </button>
        </Empty>
      )}
    </Modal>
  );
}
function TenantModal({
  tenant,
  api,
  notify,
  close,
}: {
  tenant: Row;
  api: Api;
  notify: Notify;
  close: () => void;
}) {
  const [properties, setProperties] = useState<Row[]>([]),
    [catalog, setCatalog] = useState<Row[]>([]),
    [selected, setSelected] = useState("");
  async function load() {
    setProperties(await api(`/v1/tenants/${tenant.id}/properties`));
    setCatalog(await api(`/v1/tenants/${tenant.id}/catalog`));
  }
  useEffect(() => {
    load().catch((e) => notify(e.message, true));
  }, []);
  return (
    <Modal title={tenant.name} close={close} wide>
      <div className="two-columns">
        <section>
          <h3>Locations</h3>
          {properties.map((p) => (
            <button
              className={`location-card ${selected === p.id ? "chosen" : ""}`}
              key={p.id}
              onClick={() => setSelected(p.id)}
            >
              <MapPin size={18} />
              <span>
                <strong>{p.name}</strong>
                <small>{p.timezone}</small>
              </span>
              <ChevronRight size={15} />
            </button>
          ))}
          <form
            onSubmit={async (e) => {
              e.preventDefault();
              const form = e.currentTarget;
              try {
                const p = await api(`/v1/tenants/${tenant.id}/properties`, {
                  method: "POST",
                  body: JSON.stringify(Object.fromEntries(new FormData(form))),
                });
                setSelected(p.id);
                form.reset();
                await load();
                notify("Location saved.");
              } catch (e) {
                notify((e as Error).message, true);
              }
            }}
          >
            <label>
              Location name
              <input name="name" required placeholder="Ljubljana studio" />
            </label>
            <label>
              Location reference
              <input
                name="external_property_id"
                required
                placeholder="location-001"
              />
            </label>
            <label>
              Timezone
              <input name="timezone" required defaultValue="Europe/Ljubljana" />
            </label>
            <button className="secondary">
              <Plus size={15} />
              Add location
            </button>
          </form>
        </section>
        <section>
          <h3>Destination catalog</h3>
          <p className="hint">
            Choose a location, then register its allowed services and employees.
          </p>
          {selected ? (
            <>
              <div className="catalog-list">
                {catalog
                  .filter((c) => c.property_id === selected)
                  .map((c) => (
                    <div key={c.id}>
                      <span className="badge">{human(c.kind)}</span>
                      <strong>{c.name}</strong>
                      <small>{c.external_id}</small>
                    </div>
                  ))}
              </div>
              <form
                onSubmit={async (e) => {
                  e.preventDefault();
                  const form = e.currentTarget;
                  try {
                    await api(`/v1/tenants/${tenant.id}/catalog`, {
                      method: "PUT",
                      body: JSON.stringify({
                        property_id: selected,
                        entries: [Object.fromEntries(new FormData(form))],
                      }),
                    });
                    form.reset();
                    await load();
                    notify("Catalog entry saved.");
                  } catch (e) {
                    notify((e as Error).message, true);
                  }
                }}
              >
                <label>
                  Type
                  <select name="kind">
                    <option value="SERVICE">Service</option>
                    <option value="EMPLOYEE">Employee</option>
                  </select>
                </label>
                <label>
                  Name
                  <input name="name" required placeholder="Consultation" />
                </label>
                <label>
                  Destination ID
                  <input
                    name="external_id"
                    required
                    placeholder="service-001"
                  />
                </label>
                <button className="secondary">
                  <Plus size={15} />
                  Add catalog entry
                </button>
              </form>
            </>
          ) : (
            <Empty title="Choose a location">
              Catalog entries stay within their location.
            </Empty>
          )}
        </section>
      </div>
    </Modal>
  );
}
function Branding({
  me,
  api,
  notify,
  refresh,
}: {
  me: Row;
  api: Api;
  notify: Notify;
  refresh: () => Promise<void>;
}) {
  const [name, setName] = useState(me.brand_name),
    [color, setColor] = useState(me.primary_color),
    [support, setSupport] = useState(me.support_text),
    [logo, setLogo] = useState(me.logo_data);
  return (
    <div className="branding-grid">
      <section className="card">
        <h2>Your brand</h2>
        <form
          onSubmit={async (e) => {
            e.preventDefault();
            try {
              await api("/v1/branding", {
                method: "PUT",
                body: JSON.stringify({
                  brand_name: name,
                  primary_color: color,
                  support_text: support,
                  logo_data: logo,
                }),
              });
              await refresh();
              notify("Branding saved.");
            } catch (e) {
              notify((e as Error).message, true);
            }
          }}
        >
          <label>
            Brand name
            <input
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
            />
          </label>
          <label>
            Primary color
            <input
              type="color"
              value={color}
              onChange={(e) => setColor(e.target.value)}
            />
          </label>
          <label>
            Support message
            <textarea
              value={support}
              onChange={(e) => setSupport(e.target.value)}
              required
            />
          </label>
          <label>
            Logo · PNG or JPG
            <input
              type="file"
              accept="image/png,image/jpeg"
              onChange={(e) => {
                const f = e.target.files?.[0];
                if (!f) return;
                if (f.size > 120000) {
                  notify("Use a logo smaller than 120 KB.", true);
                  return;
                }
                const reader = new FileReader();
                reader.onload = () => setLogo(reader.result as string);
                reader.readAsDataURL(f);
              }}
            />
          </label>
          <button className="primary">
            Save branding
            <Check size={16} />
          </button>
        </form>
      </section>
      <section
        className="brand-preview"
        style={{ "--accent": color } as CSSProperties}
      >
        <span className="eyebrow">UPLOAD PAGE PREVIEW</span>
        <div className="card">
          <Mark name={name} logo={logo} />
          <h2>
            Your next chapter
            <br />
            starts here.
          </h2>
          <p>
            Upload your export or screenshots.
            <br />
            No special template required.
          </p>
          <div className="mini-drop">
            <UploadCloud size={30} />
            <strong>Bring your data along</strong>
          </div>
          <p className="hint">{support}</p>
        </div>
      </section>
    </div>
  );
}
function Keys({ api, notify }: { api: Api; notify: Notify }) {
  const [keys, setKeys] = useState<Row[]>([]),
    [raw, setRaw] = useState("");
  async function load() {
    setKeys(await api("/v1/api-keys"));
  }
  useEffect(() => {
    load().catch((e) => notify(e.message, true));
  }, []);
  return (
    <section className="card">
      <h2>Partner API keys</h2>
      <p>
        For your server integration. Keys expire after 90 days and are shown
        only once.
      </p>
      <form
        className="inline-form"
        onSubmit={async (e) => {
          e.preventDefault();
          const form = e.currentTarget;
          try {
            setRaw(
              (
                await api("/v1/api-keys", {
                  method: "POST",
                  body: JSON.stringify(Object.fromEntries(new FormData(form))),
                })
              ).token,
            );
            form.reset();
            await load();
          } catch (e) {
            notify((e as Error).message, true);
          }
        }}
      >
        <input
          name="label"
          aria-label="Key label"
          required
          placeholder="Key label, e.g. Local bridge"
        />
        <button className="primary">
          <Plus size={16} />
          Create key
        </button>
      </form>
      {raw && (
        <div className="key-reveal">
          <label>
            Copy this key now.
            <input value={raw} readOnly onFocus={(e) => e.target.select()} />
          </label>
          <button
            className="secondary"
            onClick={async () => {
              await navigator.clipboard.writeText(raw);
              setRaw("");
              notify("API key copied.");
            }}
          >
            <Copy size={15} />
            Copy and dismiss
          </button>
        </div>
      )}
      <table>
        <thead>
          <tr>
            <th>Label</th>
            <th>Expires</th>
            <th>Status</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {keys.map((k) => (
            <tr key={k.id}>
              <td>{k.label}</td>
              <td>{when(k.expires_at)}</td>
              <td>
                <Status value={k.revoked ? "REVOKED" : "ACTIVE"} />
              </td>
              <td>
                {!k.revoked && (
                  <button
                    className="text-button danger"
                    onClick={async () => {
                      try {
                        await api(`/v1/api-keys/${k.id}/revoke`, {
                          method: "POST",
                        });
                        await load();
                        notify("Key revoked.");
                      } catch (e) {
                        notify((e as Error).message, true);
                      }
                    }}
                  >
                    Revoke
                  </button>
                )}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}
function MigrationModal({
  migration,
  api,
  notify,
  close,
}: {
  migration: Row;
  api: Api;
  notify: Notify;
  close: () => void;
}) {
  const [preview, setPreview] = useState<Row>(),
    [dryRun, setDryRun] = useState<Row>(),
    [busy, setBusy] = useState(false);
  async function load() {
    setPreview(await api(`/v1/migrations/${migration.id}`));
  }
  useEffect(() => {
    load().catch((e) => notify(e.message, true));
    const t = setInterval(() => void load().catch(() => {}), 4000);
    return () => clearInterval(t);
  }, []);
  return (
    <Modal
      title={`${migration.tenant_name} · ${migration.property_name || "All locations"}`}
      close={close}
      wide
    >
      {preview ? (
        <>
          <div className="detail-heading">
            <Status value={preview.migration.status} />
            <span>
              {preview.files.length} files · {preview.records.length} records
            </span>
            <button
              className="icon-button"
              aria-label="Refresh migration"
              onClick={() => void load()}
            >
              <RefreshCw size={16} />
            </button>
          </div>
          <div className="file-chips">
            {preview.files.map((f: Row) => (
              <span key={f.id}>
                <FileText size={14} />
                {f.original_name}
              </span>
            ))}
          </div>
          <ReviewTable preview={preview} />
          {["READY", "PARTIAL", "IMPORTED"].includes(
            preview.migration.status,
          ) && (
            <div className="delivery-actions">
              <div>
                <h3>Review the destination changes</h3>
                <p>The local mock writes only to this computer.</p>
              </div>
              <button
                className="secondary"
                onClick={async () => {
                  try {
                    setDryRun(
                      await api(`/v1/migrations/${migration.id}/dry-run`),
                    );
                  } catch (e) {
                    notify((e as Error).message, true);
                  }
                }}
              >
                <Eye size={17} />
                Preview delivery
              </button>
            </div>
          )}
          {dryRun && (
            <div className="dry-run">
              <h3>Dry-run · {dryRun.proposed_changes.length} records</h3>
              <p>{dryRun.note}</p>
              <div className="dry-list">
                {dryRun.proposed_changes.map((r: Row) => (
                  <div key={r.record_id}>
                    {r.entity === "CLIENT" ? (
                      <Users size={15} />
                    ) : (
                      <CalendarDays size={15} />
                    )}
                    <strong>{r.data.display_name || r.data.start}</strong>
                    <span className="badge">{human(r.action)}</span>
                  </div>
                ))}
              </div>
              <div className="button-row">
                <button
                  className="primary"
                  disabled={busy}
                  onClick={async () => {
                    setBusy(true);
                    try {
                      await api(
                        `/v1/migrations/${migration.id}/mock-delivery`,
                        { method: "POST", body: "{}" },
                      );
                      await load();
                      setDryRun(undefined);
                      notify(
                        "Local destination returned record-level receipts.",
                      );
                    } catch (e) {
                      notify((e as Error).message, true);
                    } finally {
                      setBusy(false);
                    }
                  }}
                >
                  Deliver to local mock
                  <ArrowRight size={16} />
                </button>
                <button
                  className="secondary"
                  onClick={() => {
                    const url = URL.createObjectURL(
                        new Blob([JSON.stringify(dryRun, null, 2)], {
                          type: "application/json",
                        }),
                      ),
                      a = document.createElement("a");
                    a.href = url;
                    a.download = "transfervio-dry-run.json";
                    a.click();
                    URL.revokeObjectURL(url);
                  }}
                >
                  <Download size={15} />
                  Download preview
                </button>
              </div>
            </div>
          )}
          {preview.delivery_results.length > 0 && (
            <div className="receipt-summary">
              <CheckCircle2 size={20} />
              <span>
                {
                  preview.delivery_results.filter(
                    (r: Row) => r.outcome !== "FAILED",
                  ).length
                }{" "}
                confirmed ·{" "}
                {
                  preview.delivery_results.filter(
                    (r: Row) => r.outcome === "FAILED",
                  ).length
                }{" "}
                failed
              </span>
              <small>Based on destination acknowledgments.</small>
            </div>
          )}
        </>
      ) : (
        <div className="loading">
          <LoaderCircle className="spin" />
          Loading migration…
        </div>
      )}
    </Modal>
  );
}
function Portal({
  invitation,
  notify,
}: {
  invitation: string;
  notify: Notify;
}) {
  const [landing, setLanding] = useState<Row>(),
    [preview, setPreview] = useState<Row>(),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(""),
    [confirmed, setConfirmed] = useState(false),
    [editing, setEditing] = useState<Row>(),
    [sharing, setSharing] = useState<Record<string, string[]> | undefined>();
  const base = `/public/invitations/${invitation}`;
  async function load() {
    const [l, p] = await Promise.all([
      request(base),
      request(base + "/preview"),
    ]);
    setLanding(l);
    setPreview(p);
  }
  useEffect(() => {
    load().catch((e) => setError(e.message));
    const t = setInterval(() => load().catch((e) => setError(e.message)), 5000);
    return () => clearInterval(t);
  }, [invitation]);
  async function upload(files: FileList | File[]) {
    setBusy("Uploading");
    try {
      const data = new FormData();
      Array.from(files).forEach((f) => data.append("files", f));
      await request(base + "/uploads", { method: "POST", body: data });
      await load();
      notify("Files stored securely on this computer.");
    } catch (e) {
      notify((e as Error).message, true);
    } finally {
      setBusy("");
    }
  }
  if (error)
    return (
      <div className="portal-error">
        <ShieldCheck size={42} />
        <h1>This invitation is unavailable.</h1>
        <p>
          It may have expired or been revoked. Ask your partner for a new
          invitation.
        </p>
      </div>
    );
  if (!landing || !preview)
    return (
      <div className="loading full">
        <LoaderCircle className="spin" />
        Opening your invitation…
      </div>
    );
  const status = preview.migration.status,
    editable = ["UPLOADING", "REVIEW", "FAILED"].includes(status),
    issues = preview.records.filter(
      (r: Row) => !r.excluded && r.issues.length,
    ).length,
    approved = ["READY", "PARTIAL", "IMPORTED"].includes(status),
    step = approved
      ? 4
      : status === "REVIEW"
        ? 2
        : status === "PROCESSING"
          ? 1
          : 0;
  return (
    <div
      className="portal"
      style={{ "--accent": landing.brand.primary_color } as CSSProperties}
    >
      <header className="portal-header">
        <Mark name={landing.brand.brand_name} logo={landing.brand.logo_data} />
        <span>
          <ShieldCheck size={17} />
          Private migration workspace
        </span>
      </header>
      <main>
        {landing.external_ai_enabled && (
          <div className="external-notice">
            <AlertCircle size={18} />
            OpenAI is available
            {landing.ai_synthetic_only
              ? " for verified synthetic fixtures"
              : " for authorized uploads"}
            . Each file needs your explicit permission. Images or bounded column
            samples leave this computer only when you request AI extraction or
            suggestions.
          </div>
        )}
        <div className="portal-intro">
          <span className="eyebrow">
            WELCOME, {landing.tenant.toUpperCase()}
          </span>
          <h1>
            Your next chapter
            <br />
            starts here.
          </h1>
          <p>
            Bring your clients and appointments with you.
            <br />
            We’ll help you check every detail along the way.
          </p>
          <div className="scope-pill">
            <MapPin size={15} />
            {landing.property}
            <span>·</span>
            <Clock3 size={14} />
            Link expires {when(landing.migration.expires_at)}
          </div>
        </div>
        <div className="stepper">
          {[
            "Upload",
            "Extract",
            "Review & validate",
            "Approve",
            "Delivery",
          ].map((s, i) => (
            <div className={i <= step ? "current" : ""} key={s}>
              <span>{i < step ? <Check size={14} /> : i + 1}</span>
              <strong>{s}</strong>
              {i < 4 && <i />}
            </div>
          ))}
        </div>
        {approved ? (
          <section className="approval-banner">
            <CheckCircle2 size={35} />
            <div>
              <h2>
                {status === "IMPORTED"
                  ? "Your migration is complete."
                  : "Reviewed. Approved. Ready for the next step."}
              </h2>
              <p>
                {status === "IMPORTED"
                  ? "The destination has confirmed every included record."
                  : "Your approved data is frozen. Your partner can now preview delivery and import it."}
              </p>
            </div>
            <Status value={status} />
          </section>
        ) : (
          <section className="upload-section">
            <h2>
              {preview.files.length
                ? "Your files, together."
                : "Bring your data along."}
            </h2>
            <p>
              Upload your export or screenshots. No special template required.
            </p>
            {editable && (
              <label
                className={`dropzone ${busy ? "disabled" : ""}`}
                onDragOver={(e) => e.preventDefault()}
                onDrop={(e) => {
                  e.preventDefault();
                  if (!busy) void upload(e.dataTransfer.files);
                }}
              >
                <input
                  aria-label="Upload files"
                  type="file"
                  multiple
                  accept=".csv,.xlsx,.json,.png,.jpg,.jpeg"
                  disabled={!!busy}
                  onChange={(e) => {
                    if (e.target.files?.length) void upload(e.target.files);
                    e.target.value = "";
                  }}
                />
                <span className="upload-symbol">
                  <UploadCloud size={27} />
                </span>
                <strong>
                  {busy || "Drop your files here, or browse files"}
                </strong>
                <small>
                  CSV, Excel, JSON, PNG or JPG · Up to 10 MB per file · 20 files
                  maximum
                </small>
              </label>
            )}
            <div className="file-list">
              {preview.files.map((f: Row) => (
                <div key={f.id}>
                  <FileText size={20} />
                  <span>
                    <strong>{f.original_name}</strong>
                    <small>
                      {(f.size_bytes / 1024).toFixed(1)} KB ·{" "}
                      {f.deleted_at
                        ? "Retention period ended"
                        : "Stored privately"}
                    </small>
                  </span>
                  <CheckCircle2 size={17} />
                </div>
              ))}
            </div>
            {editable && preview.files.length > 0 && (
              <SourceMappingPanel
                base={base}
                files={preview.files}
                schema={landing.export_schema}
                externalAi={landing.external_ai_enabled}
                syntheticOnly={landing.ai_synthetic_only}
                changed={load}
                notify={notify}
              />
            )}
            {status === "PROCESSING" ? (
              <div className="processing">
                <LoaderCircle className="spin" />
                <span>
                  Extracting and validating your files. You can keep this page
                  open.
                </span>
              </div>
            ) : (
              preview.files.length > 0 && (
                <div className="upload-footer">
                  <span>
                    <ShieldCheck size={15} />
                    Uploading never starts an import.
                  </span>
                  <button
                    className="primary"
                    disabled={!!busy}
                    onClick={async () => {
                      setBusy("Processing");
                      try {
                        await request(base + "/process", { method: "POST" });
                        await load();
                      } catch (e) {
                        notify((e as Error).message, true);
                      } finally {
                        setBusy("");
                      }
                    }}
                  >
                    {status === "REVIEW"
                      ? "Re-extract all files"
                      : "Extract & validate"}
                    <ArrowRight size={16} />
                  </button>
                </div>
              )
            )}
            {status === "FAILED" && (
              <p className="error-text">
                {preview.jobs.find((j: Row) => j.error_message)
                  ?.error_message ||
                  "Extraction could not finish. Check source mappings and file formats."}
              </p>
            )}
          </section>
        )}
        {preview.records.length > 0 && (
          <section className="review-section">
            <div className="section-heading">
              <div>
                <span className="eyebrow">TAKE A MOMENT TO CHECK</span>
                <h2>Your data, ready for a closer look.</h2>
                <p>
                  Review the source, resolve flagged fields, and choose what to
                  include.
                </p>
              </div>
              <span className={`validation-pill ${issues ? "attention" : ""}`}>
                {issues ? (
                  <AlertCircle size={16} />
                ) : (
                  <CheckCircle2 size={16} />
                )}{" "}
                {issues
                  ? `${issues} records need attention`
                  : "All included records validated"}
              </span>
            </div>
            <ReviewTable
              preview={preview}
              sourceBase={base}
              edit={status === "REVIEW" ? setEditing : undefined}
            />
            {status === "REVIEW" && (
              <div className="approve-section">
                <div>
                  <details className="sharing-fields">
                    <summary>Choose fields your partner may retrieve</summary>
                    <p>
                      Technical record IDs and client relationships always
                      remain available. Unselected business fields will not be
                      exported.
                    </p>
                    {Object.entries(
                      landing.export_schema as Record<string, Row[]>,
                    ).map(([entity, fields]) => (
                      <fieldset key={entity}>
                        <legend>{entity}</legend>
                        {fields
                          .filter(
                            (f) =>
                              !landing.migration.export_fields?.[entity] ||
                              landing.migration.export_fields[entity].includes(
                                f.id,
                              ),
                          )
                          .map((f) => {
                            const current =
                              sharing?.[entity] ||
                              landing.migration.export_fields?.[entity] ||
                              fields.map((x) => x.id);
                            return (
                              <label className="checkbox" key={f.id}>
                                <input
                                  type="checkbox"
                                  checked={current.includes(f.id)}
                                  onChange={(e) => {
                                    const defaults = Object.fromEntries(
                                      Object.entries(
                                        landing.export_schema as Record<
                                          string,
                                          Row[]
                                        >,
                                      ).map(([key, values]) => [
                                        key,
                                        landing.migration.export_fields?.[
                                          key
                                        ] || values.map((x) => x.id),
                                      ]),
                                    );
                                    setSharing({
                                      ...defaults,
                                      ...sharing,
                                      [entity]: e.target.checked
                                        ? [...current, f.id]
                                        : current.filter(
                                            (x: string) => x !== f.id,
                                          ),
                                    });
                                    setConfirmed(false);
                                  }}
                                />
                                {f.display_name}
                              </label>
                            );
                          })}
                      </fieldset>
                    ))}
                  </details>
                  <label className="checkbox">
                    <input
                      type="checkbox"
                      checked={confirmed}
                      onChange={(e) => setConfirmed(e.target.checked)}
                    />
                    <span>
                      I have reviewed the included records and approve them for
                      delivery to {landing.property}.
                    </span>
                  </label>
                  <p>
                    Approval freezes this version. Your partner handles delivery
                    separately.
                  </p>
                </div>
                <button
                  className="primary"
                  disabled={!confirmed || issues > 0 || !!busy}
                  onClick={async () => {
                    setBusy("Approving");
                    try {
                      await request(base + "/approve", {
                        method: "POST",
                        body: JSON.stringify({
                          revision: preview.migration.revision,
                          ...(sharing ? { export_fields: sharing } : {}),
                        }),
                      });
                      await load();
                      notify("Your migration is approved.");
                    } catch (e) {
                      notify((e as Error).message, true);
                    } finally {
                      setBusy("");
                    }
                  }}
                >
                  Approve migration
                  <Check size={16} />
                </button>
              </div>
            )}
          </section>
        )}
        <footer className="portal-footer">
          <span>{landing.brand.support_text}</span>
          <span>
            <ShieldCheck size={14} />
            Stored on this computer ·{" "}
            {landing.external_ai_enabled
              ? "AI requires per-file consent"
              : "Mock extraction"}
          </span>
        </footer>
      </main>
      {editing && (
        <EditRecord
          record={editing}
          preview={preview}
          catalog={landing.catalog}
          close={() => setEditing(undefined)}
          save={async (patch) => {
            await request(base + "/mappings", {
              method: "PATCH",
              body: JSON.stringify({
                revision: preview.migration.revision,
                records: [patch],
              }),
            });
            await load();
            setEditing(undefined);
            notify("Record saved and revalidated.");
          }}
          notify={notify}
        />
      )}
    </div>
  );
}
