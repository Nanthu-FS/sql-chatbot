import { useEffect, useState } from "react";
import { api, formatCurrency } from "../api/client";
import type { FailureItem, FailureListResponse } from "../api/client";
import { EventDrawer } from "../components/EventDrawer";

const CATEGORIES = ["", "RECOVERABLE", "HARD_DECLINE", "TECHNICAL", "FRAUD_RISK", "CUSTOMER_ACTION", "UNKNOWN"];
const CATEGORY_COLORS: Record<string, string> = {
  RECOVERABLE: "text-blue-400",
  HARD_DECLINE: "text-red-400",
  TECHNICAL: "text-yellow-400",
  FRAUD_RISK: "text-orange-400",
  CUSTOMER_ACTION: "text-purple-400",
  UNKNOWN: "text-gray-400",
};

export function Explorer() {
  const [data, setData] = useState<FailureListResponse | null>(null);
  const [selected, setSelected] = useState<FailureItem | null>(null);
  const [category, setCategory] = useState("");
  const [code, setCode] = useState("");
  const [from, setFrom] = useState("");
  const [to, setTo] = useState("");
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(false);

  async function load() {
    setLoading(true);
    const res = await api.failures({ category: category || undefined, code: code || undefined, from: from || undefined, to: to || undefined, page });
    setData(res);
    setLoading(false);
  }

  useEffect(() => { void load(); }, [category, code, from, to, page]);

  const totalPages = data ? Math.ceil(data.total / data.page_size) : 1;

  return (
    <div className="space-y-5">
      <h1 className="text-2xl font-bold text-white">Failure Explorer</h1>

      <div className="bg-navy-800 rounded-xl p-4 border border-navy-600 flex flex-wrap gap-3">
        <select
          className="bg-navy-900 text-slate-200 text-sm rounded px-3 py-2 border border-navy-600"
          value={category}
          onChange={(e) => { setCategory(e.target.value); setPage(1); }}
        >
          {CATEGORIES.map((c) => (
            <option key={c} value={c}>{c || "All Categories"}</option>
          ))}
        </select>
        <input
          className="bg-navy-900 text-slate-200 text-sm rounded px-3 py-2 border border-navy-600 w-48"
          placeholder="Unified code filter"
          value={code}
          onChange={(e) => { setCode(e.target.value); setPage(1); }}
        />
        <input
          type="date"
          className="bg-navy-900 text-slate-200 text-sm rounded px-3 py-2 border border-navy-600"
          value={from}
          onChange={(e) => { setFrom(e.target.value); setPage(1); }}
        />
        <input
          type="date"
          className="bg-navy-900 text-slate-200 text-sm rounded px-3 py-2 border border-navy-600"
          value={to}
          onChange={(e) => { setTo(e.target.value); setPage(1); }}
        />
        <button onClick={() => { setCategory(""); setCode(""); setFrom(""); setTo(""); setPage(1); }}
          className="text-sm text-slate-400 hover:text-white px-3">Clear</button>
      </div>

      <div className="bg-navy-800 rounded-xl border border-navy-600 overflow-hidden">
        {loading ? (
          <div className="py-12 text-center text-slate-400">Loading…</div>
        ) : data?.items.length === 0 ? (
          <div className="py-12 text-center text-slate-500">No failures found</div>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-slate-500 text-xs border-b border-navy-600 bg-navy-900">
                <th className="text-left px-4 py-3">Payment</th>
                <th className="text-left px-4 py-3">Gateway</th>
                <th className="text-left px-4 py-3">Code</th>
                <th className="text-left px-4 py-3">Category</th>
                <th className="text-right px-4 py-3">Amount</th>
                <th className="text-left px-4 py-3">Date</th>
              </tr>
            </thead>
            <tbody>
              {data?.items.map((item) => (
                <tr
                  key={item.id}
                  className="border-b border-navy-700 hover:bg-navy-700 cursor-pointer transition-colors"
                  onClick={() => setSelected(item)}
                >
                  <td className="px-4 py-3 font-mono text-xs text-slate-300">{item.external_payment_id}</td>
                  <td className="px-4 py-3 text-slate-400 capitalize">{item.gateway}</td>
                  <td className="px-4 py-3 font-mono text-xs text-slate-200">{item.unified_code}</td>
                  <td className={`px-4 py-3 font-medium text-xs ${CATEGORY_COLORS[item.category] ?? ""}`}>{item.category}</td>
                  <td className="px-4 py-3 text-right text-accent-red">{formatCurrency(item.amount, item.currency)}</td>
                  <td className="px-4 py-3 text-slate-400 text-xs">{new Date(item.occurred_at).toLocaleDateString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {data && totalPages > 1 && (
        <div className="flex items-center justify-between text-sm text-slate-400">
          <span>{data.total.toLocaleString()} total</span>
          <div className="flex gap-2">
            <button disabled={page <= 1} onClick={() => setPage(p => p - 1)}
              className="px-3 py-1 rounded bg-navy-800 border border-navy-600 disabled:opacity-40 hover:bg-navy-700">←</button>
            <span className="px-3 py-1">Page {page} of {totalPages}</span>
            <button disabled={page >= totalPages} onClick={() => setPage(p => p + 1)}
              className="px-3 py-1 rounded bg-navy-800 border border-navy-600 disabled:opacity-40 hover:bg-navy-700">→</button>
          </div>
        </div>
      )}

      <EventDrawer item={selected} onClose={() => setSelected(null)} />
    </div>
  );
}
