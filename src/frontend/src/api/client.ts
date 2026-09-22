const API_URL = import.meta.env.VITE_API_URL ?? "http://localhost:8000";

export async function apiRequest<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_URL}${path}`, {
    ...init,
    headers: { "Content-Type": "application/json", ...(init.headers ?? {}) },
  });
  if (!response.ok) {
    const detail = await response.text();
    throw new Error(detail || `Request failed (${response.status})`);
  }
  return response.json() as Promise<T>;
}

export const authApi = {
  login: (username: string, password: string) =>
    apiRequest<{ access_token: string }>("/auth/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    }),
};

export type TradeRecord = {
  id: string;
  account: string;
  instrument: string;
  quantity: string;
  price: string;
  version: number;
  locked: boolean;
  locked_by_display_name: string | null;
  locked_at: string | null;
};

export const tradeApi = {
  list: (token: string) => apiRequest<TradeRecord[]>("/trades", { headers: { Authorization: `Bearer ${token}` } }),
  save: (token: string, businessDate: string, rows: unknown[]) =>
    apiRequest<{ snapshot_filename: string; row_count: number }>("/trades/entry/save", {
      method: "POST",
      headers: { Authorization: `Bearer ${token}` },
      body: JSON.stringify({ business_date: businessDate, rows }),
    }),
  lock: (token: string, id: string) =>
    apiRequest(`/trades/${id}/lock`, { method: "POST", headers: { Authorization: `Bearer ${token}` } }),
  unlock: (token: string, id: string) =>
    apiRequest(`/trades/${id}/unlock`, { method: "POST", headers: { Authorization: `Bearer ${token}` } }),
};
