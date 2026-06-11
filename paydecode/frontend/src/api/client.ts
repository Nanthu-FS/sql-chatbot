const BASE = "";

export interface Summary {
  total_failed_amount: number;
  failure_count: number;
  recovery_rate_pct: number;
  top_failure_category: string | null;
  recovered_amount: number;
}

export interface CategoryBreakdown {
  category: string;
  failure_count: number;
  lost_amount: number;
}

export interface CodeBreakdown {
  unified_code: string;
  category: string;
  failure_count: number;
  lost_amount: number;
}

export interface TimeseriesPoint {
  bucket: string;
  category: string;
  failure_count: number;
  lost_amount: number;
}

export interface RecoveryFunnel {
  scheduled: number;
  executed: number;
  recovered: number;
  recovered_amount: number;
  recovery_rate_pct: number;
}

export interface FailureItem {
  id: number;
  payment_id: number;
  external_payment_id: string;
  gateway: string;
  amount: number;
  currency: string;
  customer_email: string | null;
  gateway_code: string;
  unified_code: string;
  category: string;
  occurred_at: string;
  attempt_number: number;
}

export interface FailureListResponse {
  items: FailureItem[];
  total: number;
  page: number;
  page_size: number;
}

export interface UnmappedCode {
  gateway: string;
  gateway_code: string;
  occurrence_count: number;
}

async function get<T>(path: string): Promise<T> {
  const res = await fetch(`${BASE}${path}`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json() as Promise<T>;
}

async function post<T>(path: string, body?: unknown): Promise<T> {
  const res = await fetch(`${BASE}${path}`, {
    method: "POST",
    headers: body ? { "Content-Type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json() as Promise<T>;
}

function dateParams(from?: string, to?: string) {
  const p = new URLSearchParams();
  if (from) p.set("from", from);
  if (to) p.set("to", to);
  return p.toString() ? `?${p}` : "";
}

export const api = {
  summary: (from?: string, to?: string) =>
    get<Summary>(`/api/analytics/summary${dateParams(from, to)}`),

  byCategory: (from?: string, to?: string) =>
    get<CategoryBreakdown[]>(`/api/analytics/by-category${dateParams(from, to)}`),

  byCode: (from?: string, to?: string) =>
    get<CodeBreakdown[]>(`/api/analytics/by-code${dateParams(from, to)}`),

  timeseries: (bucket: "day" | "week" = "day", from?: string, to?: string) => {
    const p = new URLSearchParams({ bucket });
    if (from) p.set("from", from);
    if (to) p.set("to", to);
    return get<TimeseriesPoint[]>(`/api/analytics/timeseries?${p}`);
  },

  recovery: () => get<RecoveryFunnel>("/api/analytics/recovery"),

  failures: (params: {
    category?: string;
    code?: string;
    from?: string;
    to?: string;
    page?: number;
  }) => {
    const p = new URLSearchParams();
    if (params.category) p.set("category", params.category);
    if (params.code) p.set("code", params.code);
    if (params.from) p.set("from", params.from);
    if (params.to) p.set("to", params.to);
    if (params.page) p.set("page", String(params.page));
    return get<FailureListResponse>(`/api/failures?${p}`);
  },

  unmapped: () => get<UnmappedCode[]>("/api/failures/unmapped"),

  createMapping: (body: { gateway: string; gateway_code: string; unified_code: string }) =>
    post("/api/failures/mappings", body),

  simulate: (count: number, days: number) =>
    post<{ processed: number; duplicates: number; errors: number }>(
      `/api/simulator/generate?count=${count}&days=${days}`
    ),

  fireOne: (code: string) =>
    post<{ event_id: string; duplicate: boolean; unified_code: string }>(
      `/api/simulator/fire-one?code=${encodeURIComponent(code)}`
    ),
};

export function formatCurrency(amount: number, currency = "INR"): string {
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency,
    maximumFractionDigits: 0,
  }).format(amount / 100);
}
