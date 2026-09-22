const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8000";

export class ApiError extends Error {
  constructor(
    message: string,
    public readonly status: number,
  ) {
    super(message);
    this.name = "ApiError";
  }
}

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init.headers ?? {}) },
  });

  if (!response.ok) {
    const detail = await response.text();
    throw new ApiError(detail || `Request failed (${response.status})`, response.status);
  }

  return response.json() as Promise<T>;
}

export type DailyTradeEntryRow = {
  id: string;
  business_id: string;
  business_date: string;
  name: string;
  code: string;
  delta: number;
  gamma: number;
  theta: number;
  vega: number;
  pnl: number;
  locked: boolean;
  version: number;
  locked_by_display_name: string | null;
  updated_at: string | null;
};

export type DailyTradeEntryResponse = {
  id: string;
  business_id: string;
  business_date: string;
  name: string;
  code: string;
  delta: string;
  gamma: string;
  theta: string;
  vega: string;
  pnl: string;
  version: number;
  locked: boolean;
  locked_by_id: string | null;
  locked_by_display_name: string | null;
  locked_at: string | null;
  lock_expires_at: string | null;
  updated_at: string | null;
};

export type DailyTradeEntrySaveRow = {
  id: string;
  delta: number;
  gamma: number;
  theta: number;
  vega: number;
  pnl: number;
  expected_version: number;
};

export type DailyTradeEntrySaveResponse = {
  saved_at: string;
  business_date: string;
  rows: DailyTradeEntryRow[];
};
export type LockResponse = Pick<DailyTradeEntryRow, "id" | "locked" | "locked_by_display_name" | "version">;

function numericMetric(value: string, field: string): number {
  const metric = Number(value);
  if (!Number.isFinite(metric)) {
    throw new Error(`The server returned an invalid ${field} metric.`);
  }
  return metric;
}

function toDailyTradeEntryRow(row: DailyTradeEntryResponse): DailyTradeEntryRow {
  return {
    id: row.id,
    business_id: row.business_id,
    business_date: row.business_date,
    name: row.name,
    code: row.code,
    delta: numericMetric(row.delta, "delta"),
    gamma: numericMetric(row.gamma, "gamma"),
    theta: numericMetric(row.theta, "theta"),
    vega: numericMetric(row.vega, "vega"),
    pnl: numericMetric(row.pnl, "P&L"),
    version: row.version,
    locked: row.locked,
    locked_by_display_name: row.locked_by_display_name,
    updated_at: row.updated_at,
  };
}

export const authApi = {
  login: (username: string, password: string) =>
    apiRequest<{ access_token: string }>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),
};

export const tradeApi = {
  listEntry: async (token: string, businessDate: string, signal?: AbortSignal) => {
    const rows = await apiRequest<DailyTradeEntryResponse[]>(
      `/trades?business_date=${encodeURIComponent(businessDate)}`,
      { headers: { Authorization: `Bearer ${token}` }, signal },
    );
    return rows.map(toDailyTradeEntryRow);
  },
  saveEntry: async (token: string, businessDate: string, rows: DailyTradeEntrySaveRow[]) => {
    const response = await apiRequest<Omit<DailyTradeEntrySaveResponse, "rows"> & { rows: DailyTradeEntryResponse[] }>("/trades/entry/save", {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: JSON.stringify({ business_date: businessDate, rows }),
    });
    return { ...response, rows: response.rows.map(toDailyTradeEntryRow) };
  },
  lock: (token: string, id: string) =>
    apiRequest<LockResponse>(`/trades/${id}/lock`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    }),
  unlock: (token: string, id: string) =>
    apiRequest<LockResponse>(`/trades/${id}/unlock`, {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
    }),
};
