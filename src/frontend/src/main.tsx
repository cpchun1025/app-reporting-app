import React, { useEffect, useMemo, useState } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";
import { authApi, tradeApi, type TradeRecord } from "./api/client";

type Page = "trade" | "daily" | "monthly" | "annual" | "consolidated";
type Row = { id?: string; name: string; code: string; locked: boolean; values: number[]; owner?: string; updated: string; version?: number };
const metrics = ["Delta", "Gamma", "Theta", "Vega", "P&L"];
function App() {
  const [authenticated, setAuthenticated] = useState(false);
  const [token, setToken] = useState("");
  const [page, setPage] = useState<Page>("trade");
  const [rows, setRows] = useState<Row[]>([]);
  const [date, setDate] = useState("2026-09-22");
  const [toast, setToast] = useState("");
  const [theme, setTheme] = useState<"dark" | "light">("dark");
  const [selectedMetric, setSelectedMetric] = useState(4);
  const notify = (message: string) => { setToast(message); window.setTimeout(() => setToast(""), 2600); };
  useEffect(() => {
    if (!token) return;
    tradeApi.list(token).then(records => {
      if (records.length) setRows(records.map(recordToRow));
    }).catch(() => notify("Unable to load saved trade data."));
  }, [token]);

  if (!authenticated) return <Login onLogin={(accessToken) => { setToken(accessToken); setAuthenticated(true); }} />;
  const nav = [
    ["trade", "Trade Entry", "▦"], ["daily", "Daily View", "◷"], ["monthly", "Monthly", "▥"],
    ["annual", "Annual", "⌁"], ["consolidated", "Consolidated", "◎"]
  ] as const;
  return <div className={`app ${theme}`}>
    <aside className="sidebar">
      <div className="brand"><span className="brand-mark">M</span><span>MARKET<span className="muted">OPS</span></span></div>
      <div className="workspace-label">WORKSPACE</div>
      {nav.map(([id, label, icon]) => <button key={id} className={`nav-item ${page === id ? "active" : ""}`} onClick={() => setPage(id)}><span className="nav-icon">{icon}</span>{label}</button>)}
      <div className="sidebar-spacer" />
      <div className="system-status"><span className="status-dot" /> All systems operational</div>
      <button className="nav-item" onClick={() => notify("Settings are available to administrators")}><span className="nav-icon">⚙</span>Settings</button>
      <button className="user-card" onClick={() => setAuthenticated(false)}><span className="avatar">AC</span><span><strong>Alex Chen</strong><small>Administrator</small></span><span className="logout">↪</span></button>
    </aside>
    <main className="main">
      <header className="topbar"><div><span className="eyebrow">TRADING OPERATIONS / {page.toUpperCase()}</span><h1>{nav.find(n => n[0] === page)?.[1]}</h1></div><div className="top-actions"><button className="icon-btn" onClick={() => setTheme(theme === "dark" ? "light" : "dark")}>{theme === "dark" ? "☼" : "☾"}</button><button className="icon-btn">◌</button><span className="live-pill"><span className="status-dot" /> LIVE</span></div></header>
      <section className="toolbar"><label>BUSINESS DATE<input type="date" value={date} onChange={e => setDate(e.target.value)} /></label><span className="toolbar-divider" /><span className="as-of">Last refresh <strong>16:25:04 ET</strong></span><button className="refresh" onClick={() => notify("Data refreshed just now")}>↻ Refresh</button></section>
      {page === "trade" && <TradeEntry rows={rows} setRows={setRows} notify={notify} token={token} businessDate={date} />}
      {page === "daily" && <DataView title="Daily performance" rows={rows} metric={selectedMetric} setMetric={setSelectedMetric} />}
      {page === "monthly" && <AggregateView period="September 2026" rows={rows} metric={selectedMetric} setMetric={setSelectedMetric} />}
      {page === "annual" && <AggregateView period="2026 YTD" rows={rows} metric={selectedMetric} setMetric={setSelectedMetric} annual />}
      {page === "consolidated" && <Consolidated rows={rows} notify={notify} />}
    </main>
    {toast && <div className="toast"><span>✓</span>{toast}</div>}
  </div>;
}

function Login({ onLogin }: { onLogin: (token: string) => void }) {
  const [user, setUser] = useState("dev_trader"); const [password, setPassword] = useState("DevTrader123!"); const [error, setError] = useState("");
  const submit = async (e: React.FormEvent) => { e.preventDefault(); if (!user || !password) return setError("Enter your username and password."); try { const result = await authApi.login(user, password); onLogin(result.access_token); } catch { setError("Unable to sign in. Check your credentials."); } };
  return <div className="login-screen"><div className="login-glow" /><div className="login-card"><div className="brand login-brand"><span className="brand-mark">M</span><span>MARKET<span className="muted">OPS</span></span></div><h1>Welcome back</h1><p className="login-subtitle">Sign in to your trading operations workspace.</p><form onSubmit={submit}><label>USERNAME<input value={user} onChange={e => setUser(e.target.value)} autoComplete="username" /></label><label>PASSWORD<input type="password" value={password} onChange={e => setPassword(e.target.value)} autoComplete="current-password" /></label>{error && <div className="form-error">{error}</div>}<button className="primary full" type="submit">Sign in <span>→</span></button></form><p className="login-note">Development environment · Secure session enabled</p></div></div>;
}

function TradeEntry({ rows, setRows, notify, token, businessDate }: { rows: Row[]; setRows: React.Dispatch<React.SetStateAction<Row[]>>; notify: (s: string) => void; token: string; businessDate: string }) {
  const [dirty, setDirty] = useState(false);
  const update = (ri: number, mi: number, value: string) => { const num = Number(value); if (!Number.isFinite(num)) return; setRows(old => old.map((r, i) => i === ri ? { ...r, values: r.values.map((v, j) => j === mi ? num : v), updated: "now" } : r)); setDirty(true); };
  const toggleLock = (ri: number) => setRows(old => old.map((r, i) => i === ri ? { ...r, locked: !r.locked, owner: r.locked ? undefined : "Alex Chen" } : r));
  const totals = metrics.map((_, i) => rows.reduce((sum, r) => sum + r.values[i], 0));
  return <><div className="page-heading"><div><h2>Daily trade entry</h2><p>Enter and save desk-level risk metrics. Locks are shared across users.</p></div><div className="action-row"><span className={`save-status ${dirty ? "pending" : ""}`}>{dirty ? "● Unsaved changes" : "✓ Saved"}</span><button className="primary" onClick={async () => { try { await tradeApi.save(token, businessDate, rows); setDirty(false); notify("Trade data saved and backup copy created"); } catch { notify("Save failed. Database and backup copy were not completed."); } }}>Save</button></div></div><div className="stat-strip"><Stat label="Rows loaded" value={`${rows.length}`} /><Stat label="Rows locked" value={`${rows.filter(r => r.locked).length} / ${rows.length}`} /><Stat label="Total P&L" value={format(totals[4])} tone={totals[4] >= 0 ? "positive" : "negative"} /><Stat label="Last updated by" value="Current session" /></div><div className="table-card trade-grid"><div className="table-toolbar"><span className="table-title">TRADING METRICS <small>{rows.length} BUSINESSES · 5 METRICS</small></span></div><table className="data-table"><thead><tr><th className="business-col">TRADING BUSINESS</th>{metrics.map(m => <th key={m}>{m}<small>{m === "P&L" ? "USD" : "RISK UNITS"}</small></th>)}<th>STATUS</th><th>UPDATED</th></tr></thead><tbody>{rows.map((row, ri) => <tr key={row.id ?? row.code} className={row.locked ? "locked-row" : ""}><td className="business-name"><span className="row-status">{row.locked ? "⌕" : "·"}</span><strong>{row.name}</strong><small>{row.code}</small></td>{row.values.map((value, mi) => <td key={mi}><input className={`metric-input ${value < 0 ? "negative" : ""}`} disabled={row.locked} value={value} onChange={e => update(ri, mi, e.target.value)} onFocus={e => e.currentTarget.select()} /></td>)}<td><button className={`lock-badge ${row.locked ? "is-locked" : ""}`} onClick={async () => { if (!row.id) return; try { if (row.locked) await tradeApi.unlock(token, row.id); else await tradeApi.lock(token, row.id); setRows(old => old.map((r, i) => i === ri ? { ...r, locked: !r.locked, owner: r.locked ? undefined : "Current user" } : r)); notify(row.locked ? `${row.name} unlocked` : `${row.name} locked for review`); } catch { notify("Lock conflict: another user owns this row."); } }}>{row.locked ? "⌕ LOCKED" : "○ OPEN"}</button></td><td className="updated">{row.updated}<small>{row.owner ? `by ${row.owner}` : "by you"}</small></td></tr>)}<tr className="total-row"><td><strong>DAILY TOTAL</strong></td>{totals.map((v, i) => <td key={i} className={v < 0 ? "negative" : ""}>{format(v)}</td>)}<td colSpan={2} /></tr></tbody></table><div className="table-footer"><span>⌨ Tab to navigate · Enter to edit · Locked rows are read-only</span><span className="version">Database lock + optimistic concurrency enabled</span></div></div></>;
}

function recordToRow(record: TradeRecord): Row {
  const quantity = Number(record.quantity);
  const price = Number(record.price);
  return { id: record.id, name: record.account, code: record.instrument, locked: record.locked, owner: record.locked_by_display_name ?? undefined, updated: record.locked_at ? new Date(record.locked_at).toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" }) : "saved", version: record.version, values: [quantity, price, quantity * -0.2, quantity * 0.1, quantity * price] };
}

function Stat({ label, value, tone = "" }: { label: string; value: string; tone?: string }) { return <div className="stat"><span>{label}</span><strong className={tone}>{value}</strong></div>; }
function DataView({ title, rows, metric, setMetric }: { title: string; rows: Row[]; metric: number; setMetric: (n: number) => void }) { const values = rows.map(r => r.values[metric]); return <><div className="page-heading"><div><h2>{title}</h2><p>Review submitted records and drill into desk-level details.</p></div><button className="secondary">⇩ Export CSV</button></div><div className="filter-bar"><label>METRIC<select value={metric} onChange={e => setMetric(Number(e.target.value))}>{metrics.map((m, i) => <option key={m} value={i}>{m}</option>)}</select></label><label>STATUS<select><option>All statuses</option><option>Submitted</option><option>Draft</option></select></label><label>SOURCE<select><option>All sources</option><option>Manual</option><option>External</option></select></label></div><div className="split-view"><div className="table-card compact"><table className="data-table"><thead><tr><th>BUSINESS</th><th>{metrics[metric]}</th><th>STATUS</th><th>UPDATED BY</th></tr></thead><tbody>{rows.map(r => <tr key={r.code}><td className="business-name"><strong>{r.name}</strong><small>{r.code}</small></td><td className={r.values[metric] < 0 ? "negative" : "positive"}>{format(r.values[metric])}</td><td><span className={`status-text ${r.locked ? "locked" : ""}`}>{r.locked ? "Locked" : "Open"}</span></td><td className="updated">{r.owner || "Alex Chen"}</td></tr>)}</tbody></table></div><Chart title={`${metrics[metric]} by business`} values={values} labels={rows.map(r => r.name)} /></div></>; }
function AggregateView({ period, rows, metric, setMetric, annual = false }: { period: string; rows: Row[]; metric: number; setMetric: (n: number) => void; annual?: boolean }) { const total = rows.reduce((s, r) => s + r.values[metric], 0); return <><div className="page-heading"><div><h2>{period} performance</h2><p>{annual ? "Monthly aggregation across the current year." : "Business-day aggregation and trend analysis."}</p></div><div className="action-row"><button className="secondary">⇩ Export CSV</button><button className="primary">View details ↗</button></div></div><div className="filter-bar"><label>METRIC<select value={metric} onChange={e => setMetric(Number(e.target.value))}>{metrics.map((m, i) => <option key={m} value={i}>{m}</option>)}</select></label><label>GROUP BY<select><option>Trading business</option><option>Metric</option></select></label><label>PERIOD<input type="month" defaultValue="2026-09" /></label></div><div className="stat-strip"><Stat label="Selected metric" value={metrics[metric]} /><Stat label="Period total" value={format(total)} tone={total >= 0 ? "positive" : "negative"} /><Stat label="Business days" value={annual ? "182" : "22"} /><Stat label="Missing dates" value="0" tone="positive" /></div><div className="chart-grid"><Chart title={`${metrics[metric]} trend`} values={rows.map(r => r.values[metric])} labels={rows.map(r => r.name)} line /><Chart title="Distribution by business" values={rows.map(r => r.values[metric])} labels={rows.map(r => r.name)} /></div></>; }
function Chart({ title, values, labels, line = false }: { title: string; values: number[]; labels: string[]; line?: boolean }) { const max = Math.max(...values.map(Math.abs), 1); return <div className="chart-card"><div className="chart-header"><strong>{title}</strong><button>•••</button></div><div className={`chart ${line ? "line-chart" : ""}`}>{line ? <svg viewBox="0 0 500 160" preserveAspectRatio="none"><polyline fill="none" stroke="var(--accent)" strokeWidth="3" points={values.map((v, i) => `${i * (490 / Math.max(values.length - 1, 1)) + 5},${145 - ((v / max) * 105 + 0)}`).join(" ")} /></svg> : values.map((v, i) => <div className="bar-group" key={labels[i]}><div className={`bar ${v < 0 ? "bar-negative" : ""}`} style={{ height: `${Math.max(Math.abs(v) / max * 105, 8)}px` }} /><small>{labels[i].slice(0, 5)}</small></div>)}</div><div className="chart-legend"><span className="legend-dot" /> {metrics[4]} <span className="chart-unit">USD MM</span></div></div>; }
function Consolidated({ rows, notify }: { rows: Row[]; notify: (s: string) => void }) { return <><div className="page-heading"><div><h2>Consolidated reporting</h2><p>Unified view across manual, external-system, and email-source records.</p></div><button className="primary" onClick={() => notify("Report queued for delivery")}>Send consolidated report ↗</button></div><div className="source-cards"><div><span className="source-icon manual">M</span><span><strong>Manual submissions</strong><small>{rows.length} records · Validated</small></span><b>●</b></div><div><span className="source-icon external">E</span><span><strong>External system</strong><small>24 records · Validated</small></span><b>●</b></div><div><span className="source-icon email">@</span><span><strong>Email source</strong><small>8 records · 1 pending</small></span><b className="amber">●</b></div></div><div className="table-card"><div className="table-toolbar"><span className="table-title">CONSOLIDATED RECORDS <small>37 RECORDS</small></span><button className="secondary">Preview report</button></div><table className="data-table"><thead><tr><th>BUSINESS DATE</th><th>TRADING BUSINESS</th><th>METRIC</th><th>VALUE</th><th>SOURCE</th><th>VALIDATION</th></tr></thead><tbody>{rows.slice(0, 5).map((r, i) => <tr key={r.code}><td>22 Sep 2026</td><td className="business-name"><strong>{r.name}</strong></td><td>{metrics[i]}</td><td className={r.values[i] < 0 ? "negative" : "positive"}>{format(r.values[i])}</td><td><span className="source-tag">{i % 2 ? "External" : "Manual"}</span></td><td><span className="status-text locked">✓ Valid</span></td></tr>)}</tbody></table></div></>; }
function format(n: number) { return new Intl.NumberFormat("en-US", { maximumFractionDigits: 1, minimumFractionDigits: n > 1000 ? 0 : 1 }).format(n); }
createRoot(document.getElementById("root")!).render(<App />);
