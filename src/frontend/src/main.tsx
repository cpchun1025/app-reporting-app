import React, { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";
import {
  ApiError,
  authApi,
  tradeApi,
  type DailyTradeEntryRow,
  type DailyTradeEntrySaveRow,
} from "./api/client";

type Page = "trade" | "daily" | "monthly" | "annual" | "consolidated";
type MetricKey = "delta" | "gamma" | "theta" | "vega" | "pnl";
type LoadState = "idle" | "loading" | "ready" | "empty" | "error";

const metrics: ReadonlyArray<{ key: MetricKey; label: string; unit: string }> = [
  { key: "delta", label: "Delta", unit: "RISK UNITS" },
  { key: "gamma", label: "Gamma", unit: "RISK UNITS" },
  { key: "theta", label: "Theta", unit: "RISK UNITS" },
  { key: "vega", label: "Vega", unit: "RISK UNITS" },
  { key: "pnl", label: "P&L", unit: "USD" },
];

function localDate(): string {
  const now = new Date();
  const year = now.getFullYear();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function App() {
  const [authenticated, setAuthenticated] = useState(false);
  const [token, setToken] = useState("");
  const [page, setPage] = useState<Page>("trade");
  const [rows, setRows] = useState<DailyTradeEntryRow[]>([]);
  const [date, setDate] = useState(localDate);
  const [loadState, setLoadState] = useState<LoadState>("idle");
  const [loadError, setLoadError] = useState("");
  const [lastRefresh, setLastRefresh] = useState<Date | null>(null);
  const [theme, setTheme] = useState<"dark" | "light">("dark");
  const [selectedMetric, setSelectedMetric] = useState<MetricKey>("pnl");
  const [toast, setToast] = useState("");
  const [dirty, setDirty] = useState(false);
  const requestSequence = useRef(0);
  const loadController = useRef<AbortController | null>(null);

  const notify = useCallback((message: string) => {
    setToast(message);
    window.setTimeout(() => setToast(""), 2600);
  }, []);

  const loadEntry = useCallback(async (businessDate: string) => {
    if (!token) return;
    loadController.current?.abort();
    const controller = new AbortController();
    loadController.current = controller;
    const sequence = ++requestSequence.current;
    setLoadState("loading");
    setLoadError("");

    try {
      const response = await tradeApi.listEntry(token, businessDate, controller.signal);
      if (sequence !== requestSequence.current) return;
      setRows(response);
      setDirty(false);
      setLastRefresh(new Date());
      setLoadState(response.length ? "ready" : "empty");
    } catch (error) {
      if (controller.signal.aborted || sequence !== requestSequence.current) return;
      setLoadError(error instanceof Error ? error.message : "Unable to load daily trade entry data.");
      setLoadState("error");
    }

  }, [token]);

  useEffect(() => {
    void loadEntry(date);
    return () => loadController.current?.abort();
  }, [date, loadEntry]);

  const changeDate = (nextDate: string) => {
    if (nextDate === date) return;
    if (dirty && !window.confirm("You have unsaved changes. Change the business date and discard them?")) return;
    setDate(nextDate);
  };

  if (!authenticated) {
    return <Login onLogin={accessToken => { setToken(accessToken); setAuthenticated(true); }} />;
  }

  const nav = [
    ["trade", "Trade Entry", "▦"], ["daily", "Daily View", "◷"], ["monthly", "Monthly", "▥"],
    ["annual", "Annual", "⌁"], ["consolidated", "Consolidated", "◎"],
  ] as const;
  const refreshLabel = lastRefresh
    ? new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit", second: "2-digit" }).format(lastRefresh)
    : "Not refreshed";

  return <div className={`app ${theme}`}>
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark">M</span><span>MARKET<span className="muted">OPS</span></span></div>
      <div className="workspace-label">WORKSPACE</div>
      {nav.map(([id, label, icon]) => <button key={id} className={`nav-item ${page === id ? "active" : ""}`} onClick={() => setPage(id)}><span className="nav-icon">{icon}</span>{label}</button>)}
      <div className="sidebar-spacer" />
      <div className="system-status"><span className="status-dot" /> All systems operational</div>
      <button className="user-card" onClick={() => setAuthenticated(false)}><span className="avatar">U</span><span><strong>Signed-in user</strong><small>Authenticated session</small></span><span className="logout">↪</span></button>
    </aside>
    <main className="main">
      <header className="topbar"><div><span className="eyebrow">TRADING OPERATIONS / {page.toUpperCase()}</span><h1>{nav.find(item => item[0] === page)?.[1]}</h1></div><div className="top-actions"><button className="icon-btn" aria-label="Toggle color theme" onClick={() => setTheme(theme === "dark" ? "light" : "dark")}>{theme === "dark" ? "☼" : "☾"}</button><span className="live-pill"><span className="status-dot" /> LIVE</span></div></header>
      <section className="toolbar"><label>BUSINESS DATE<input type="date" value={date} onChange={event => changeDate(event.target.value)} /></label><span className="toolbar-divider" /><span className="as-of">Last refresh <strong>{refreshLabel}</strong></span><button className="refresh" disabled={loadState === "loading"} onClick={() => void loadEntry(date)}>↻ {loadState === "loading" ? "Refreshing" : "Refresh"}</button></section>
      {page === "trade" && <TradeEntry rows={rows} setRows={setRows} token={token} businessDate={date} loadState={loadState} loadError={loadError} dirty={dirty} setDirty={setDirty} onRefresh={() => void loadEntry(date)} notify={notify} />}
      {page === "daily" && <DataView rows={rows} metric={selectedMetric} setMetric={setSelectedMetric} state={loadState} />}
      {page === "monthly" && <AggregateView period={`${date.slice(0, 7)} performance`} rows={rows} metric={selectedMetric} setMetric={setSelectedMetric} />}
      {page === "annual" && <AggregateView period={`${date.slice(0, 4)} YTD`} rows={rows} metric={selectedMetric} setMetric={setSelectedMetric} />}
      {page === "consolidated" && <Consolidated rows={rows} businessDate={date} state={loadState} />}
    </main>
    {toast && <div className="toast"><span>✓</span>{toast}</div>}
  </div>;
}

function Login({ onLogin }: { onLogin: (token: string) => void }) {
  const [user, setUser] = useState("dev_trader");
  const [password, setPassword] = useState("DevTrader123!");
  const [error, setError] = useState("");
  const submit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!user || !password) return setError("Enter your username and password.");
    try {
      const result = await authApi.login(user, password);
      onLogin(result.access_token);
    } catch {
      setError("Unable to sign in. Check your credentials.");
    }
  };
  return <div className="login-screen"><div className="login-glow" /><div className="login-card"><div className="brand login-brand"><span className="brand-mark">M</span><span>MARKET<span className="muted">OPS</span></span></div><h1>Welcome back</h1><p className="login-subtitle">Sign in to your trading operations workspace.</p><form onSubmit={submit}><label>USERNAME<input value={user} onChange={event => setUser(event.target.value)} autoComplete="username" /></label><label>PASSWORD<input type="password" value={password} onChange={event => setPassword(event.target.value)} autoComplete="current-password" /></label>{error && <div className="form-error">{error}</div>}<button className="primary full" type="submit">Sign in <span>→</span></button></form><p className="login-note">Development environment · Secure session enabled</p></div></div>;
}

function TradeEntry({ rows, setRows, token, businessDate, loadState, loadError, dirty, setDirty, onRefresh, notify }: {
  rows: DailyTradeEntryRow[];
  setRows: React.Dispatch<React.SetStateAction<DailyTradeEntryRow[]>>;
  token: string;
  businessDate: string;
  loadState: LoadState;
  loadError: string;
  dirty: boolean;
  setDirty: (dirty: boolean) => void;
  onRefresh: () => void;
  notify: (message: string) => void;
}) {
  const [saving, setSaving] = useState(false);
  const [conflict, setConflict] = useState("");
  const totals = useMemo(() => metrics.reduce<Record<MetricKey, number>>((result, metric) => ({ ...result, [metric.key]: rows.reduce((sum, row) => sum + row[metric.key], 0) }), { delta: 0, gamma: 0, theta: 0, vega: 0, pnl: 0 }), [rows]);
  const update = (rowIndex: number, key: MetricKey, rawValue: string) => {
    const value = Number(rawValue);
    if (!Number.isFinite(value)) return;
    setRows(current => current.map((row, index) => index === rowIndex ? { ...row, [key]: value } : row));
    setDirty(true);
    setConflict("");
  };
  const save = async () => {
    setSaving(true);
    setConflict("");
    const payload: DailyTradeEntrySaveRow[] = rows.map(({ id, delta, gamma, theta, vega, pnl, version }) => ({ id, delta, gamma, theta, vega, pnl, expected_version: version }));
    try {
      const response = await tradeApi.saveEntry(token, businessDate, payload);
      const savedRows = new Map(response.rows.map(row => [row.id, row]));
      setRows(current => current.map(row => savedRows.get(row.id) ?? row));
      setDirty(false);
      notify(`Saved ${response.rows.length} trade entry rows.`);
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        setConflict("A row changed elsewhere. Refresh to review the latest versions before saving.");
      } else {
        notify(error instanceof Error ? error.message : "Save failed. Please try again.");
      }
    } finally {
      setSaving(false);
    }
  };

  return <><div className="page-heading"><div><h2>Daily trade entry</h2><p>Enter and save desk-level risk metrics.</p></div><div className="action-row"><span className={`save-status ${dirty ? "pending" : ""}`}>{saving ? "● Saving changes" : dirty ? "● Unsaved changes" : "✓ Saved"}</span><button className="primary" disabled={saving || loadState !== "ready" || !dirty} onClick={() => void save()}>{saving ? "Saving…" : "Save"}</button></div></div>
    {conflict && <StateMessage tone="conflict" message={conflict} actionLabel="Refresh data" onAction={onRefresh} />}
    {loadState === "loading" && <StateMessage tone="loading" message="Loading daily trade entry data…" />}
    {loadState === "error" && <StateMessage tone="error" message={loadError} actionLabel="Try again" onAction={onRefresh} />}
    {loadState === "empty" && <StateMessage tone="empty" message="No trade-entry rows are available for this business date." />}
    {loadState === "ready" && <><div className="stat-strip"><Stat label="Rows loaded" value={`${rows.length}`} /><Stat label="Rows locked" value={`${rows.filter(row => row.locked).length} / ${rows.length}`} /><Stat label="Total P&L" value={format(totals.pnl)} tone={totals.pnl >= 0 ? "positive" : "negative"} /><Stat label="Business date" value={businessDate} /></div><div className="table-card trade-grid"><div className="table-toolbar"><span className="table-title">TRADING METRICS <small>{rows.length} BUSINESSES · 5 METRICS</small></span></div><table className="data-table"><thead><tr><th className="business-col">TRADING BUSINESS</th>{metrics.map(metric => <th key={metric.key}>{metric.label}<small>{metric.unit}</small></th>)}<th>STATUS</th><th>UPDATED</th></tr></thead><tbody>{rows.map((row, rowIndex) => <tr key={row.id} className={row.locked ? "locked-row" : ""}><td className="business-name"><span className="row-status">{row.locked ? "⌕" : "·"}</span><strong>{row.name}</strong><small>{row.code}</small></td>{metrics.map(metric => <td key={metric.key}><input className={`metric-input ${row[metric.key] < 0 ? "negative" : ""}`} aria-label={`${row.name} ${metric.label}`} disabled={row.locked} value={row[metric.key]} onChange={event => update(rowIndex, metric.key, event.target.value)} onFocus={event => event.currentTarget.select()} /></td>)}        <td><button className={`lock-badge ${row.locked ? "is-locked" : ""}`} onClick={async () => { try { const updated = row.locked ? await tradeApi.unlock(token, row.id) : await tradeApi.lock(token, row.id); setRows(current => current.map(item => item.id === updated.id ? { ...item, locked: updated.locked, locked_by_display_name: updated.locked_by_display_name, version: updated.version } : item)); notify(row.locked ? `${row.name} unlocked` : `${row.name} locked`); } catch (error) { if (error instanceof ApiError && error.status === 409) setConflict("This row is locked or stale. Refresh before trying again."); else notify("Lock operation failed."); } }}>{row.locked ? "⌕ LOCKED" : "○ OPEN"}</button></td><td className="updated">{formatUpdated(row.updated_at)}<small>{row.locked_by_display_name ? `by ${row.locked_by_display_name}` : `v${row.version}`}</small></td></tr>)}<tr className="total-row"><td><strong>DAILY TOTAL</strong></td>{metrics.map(metric => <td key={metric.key} className={totals[metric.key] < 0 ? "negative" : ""}>{format(totals[metric.key])}</td>)}<td colSpan={2} /></tr></tbody></table><div className="table-footer"><span>Tab to navigate · Locked rows are read-only</span><span className="version">Row versions are saved with each entry</span></div></div></>}</>;
}

function StateMessage({ tone, message, actionLabel, onAction }: { tone: "loading" | "error" | "empty" | "conflict"; message: string; actionLabel?: string; onAction?: () => void }) {
  return <div className={`state-message ${tone}`} role={tone === "error" || tone === "conflict" ? "alert" : "status"}><span>{message}</span>{actionLabel && onAction && <button className="secondary" onClick={onAction}>{actionLabel}</button>}</div>;
}

function Stat({ label, value, tone = "" }: { label: string; value: string; tone?: string }) { return <div className="stat"><span>{label}</span><strong className={tone}>{value}</strong></div>; }

function DataView({ rows, metric, setMetric, state }: { rows: DailyTradeEntryRow[]; metric: MetricKey; setMetric: (metric: MetricKey) => void; state: LoadState }) {
  return <><div className="page-heading"><div><h2>Daily performance</h2><p>Review saved desk-level risk metrics.</p></div></div><MetricSelector metric={metric} setMetric={setMetric} /><DataTable rows={rows} metric={metric} state={state} /></>;
}

function AggregateView({ period, rows, metric, setMetric }: { period: string; rows: DailyTradeEntryRow[]; metric: MetricKey; setMetric: (metric: MetricKey) => void }) {
  const total = rows.reduce((sum, row) => sum + row[metric], 0);
  return <><div className="page-heading"><div><h2>{period}</h2><p>Current business-date risk data.</p></div></div><MetricSelector metric={metric} setMetric={setMetric} /><div className="stat-strip"><Stat label="Selected metric" value={metrics.find(item => item.key === metric)?.label ?? metric} /><Stat label="Period total" value={format(total)} tone={total >= 0 ? "positive" : "negative"} /><Stat label="Rows" value={`${rows.length}`} /><Stat label="Data status" value={rows.length ? "Available" : "No data"} /></div></>;
}

function MetricSelector({ metric, setMetric }: { metric: MetricKey; setMetric: (metric: MetricKey) => void }) {
  return <div className="filter-bar"><label>METRIC<select value={metric} onChange={event => setMetric(event.target.value as MetricKey)}>{metrics.map(item => <option key={item.key} value={item.key}>{item.label}</option>)}</select></label></div>;
}

function DataTable({ rows, metric, state }: { rows: DailyTradeEntryRow[]; metric: MetricKey; state: LoadState }) {
  if (state !== "ready") return null;
  return <div className="table-card compact"><table className="data-table"><thead><tr><th>BUSINESS</th><th>{metrics.find(item => item.key === metric)?.label}</th><th>STATUS</th><th>VERSION</th></tr></thead><tbody>{rows.map(row => <tr key={row.id}><td className="business-name"><strong>{row.name}</strong><small>{row.code}</small></td><td className={row[metric] < 0 ? "negative" : "positive"}>{format(row[metric])}</td><td><span className={`status-text ${row.locked ? "locked" : ""}`}>{row.locked ? "Locked" : "Open"}</span></td><td className="updated">v{row.version}</td></tr>)}</tbody></table></div>;
}

function Consolidated({ rows, businessDate, state }: { rows: DailyTradeEntryRow[]; businessDate: string; state: LoadState }) {
  return <><div className="page-heading"><div><h2>Consolidated reporting</h2><p>Current daily trade-entry records.</p></div></div>{state === "ready" ? <div className="table-card"><div className="table-toolbar"><span className="table-title">CONSOLIDATED RECORDS <small>{rows.length} RECORDS</small></span></div><table className="data-table"><thead><tr><th>BUSINESS DATE</th><th>TRADING BUSINESS</th><th>METRIC</th><th>VALUE</th><th>VERSION</th></tr></thead><tbody>{rows.flatMap(row => metrics.map(metric => <tr key={`${row.id}-${metric.key}`}><td>{businessDate}</td><td className="business-name"><strong>{row.name}</strong><small>{row.code}</small></td><td>{metric.label}</td><td className={row[metric.key] < 0 ? "negative" : "positive"}>{format(row[metric.key])}</td><td>v{row.version}</td></tr>))}</tbody></table></div> : <StateMessage tone={state === "error" ? "error" : state === "loading" ? "loading" : "empty"} message={state === "loading" ? "Loading current records…" : state === "error" ? "Current records could not be loaded." : "No current records are available."} />}</>;
}

function format(n: number) { return new Intl.NumberFormat("en-US", { maximumFractionDigits: 4, minimumFractionDigits: 0 }).format(n); }
function formatUpdated(value: string | null) { return value ? new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" }).format(new Date(value)) : "—"; }

createRoot(document.getElementById("root")!).render(<App />);
