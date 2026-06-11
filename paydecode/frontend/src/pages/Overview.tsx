import { useEffect, useState } from "react";
import { api, formatCurrency } from "../api/client";
import type { CategoryBreakdown, CodeBreakdown, Summary, TimeseriesPoint } from "../api/client";
import { CategoryDonut } from "../components/CategoryDonut";
import { KpiCard } from "../components/KpiCard";
import { TimeseriesChart } from "../components/TimeseriesChart";

export function Overview() {
  const [summary, setSummary] = useState<Summary | null>(null);
  const [categories, setCategories] = useState<CategoryBreakdown[]>([]);
  const [codes, setCodes] = useState<CodeBreakdown[]>([]);
  const [timeseries, setTimeseries] = useState<TimeseriesPoint[]>([]);
  const [loading, setLoading] = useState(true);

  async function load() {
    const [s, cats, cds, ts] = await Promise.all([
      api.summary(),
      api.byCategory(),
      api.byCode(),
      api.timeseries("day"),
    ]);
    setSummary(s);
    setCategories(cats);
    setCodes(cds);
    setTimeseries(ts);
    setLoading(false);
  }

  useEffect(() => {
    void load();
  }, []);

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64 text-slate-400">Loading…</div>
    );
  }

  const top5 = codes.slice(0, 5);

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">Overview</h1>

      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <KpiCard
          label="Lost Revenue"
          value={formatCurrency(summary?.total_failed_amount ?? 0)}
          color="red"
        />
        <KpiCard
          label="Total Failures"
          value={(summary?.failure_count ?? 0).toLocaleString()}
          color="yellow"
        />
        <KpiCard
          label="Recovery Rate"
          value={`${summary?.recovery_rate_pct ?? 0}%`}
          color="green"
        />
        <KpiCard
          label="Recovered Revenue"
          value={formatCurrency(summary?.recovered_amount ?? 0)}
          sub={`Top: ${summary?.top_failure_category ?? "—"}`}
          color="blue"
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-navy-800 rounded-xl p-5 border border-navy-600">
          <h2 className="text-sm font-semibold text-slate-300 mb-4">Failures Over Time</h2>
          {timeseries.length === 0 ? (
            <p className="text-slate-500 text-sm text-center py-8">No data yet — run the simulator</p>
          ) : (
            <TimeseriesChart data={timeseries} />
          )}
        </div>

        <div className="bg-navy-800 rounded-xl p-5 border border-navy-600">
          <h2 className="text-sm font-semibold text-slate-300 mb-4">By Category</h2>
          {categories.length === 0 ? (
            <p className="text-slate-500 text-sm text-center py-8">No data yet</p>
          ) : (
            <CategoryDonut data={categories} />
          )}
        </div>
      </div>

      <div className="bg-navy-800 rounded-xl p-5 border border-navy-600">
        <h2 className="text-sm font-semibold text-slate-300 mb-4">Top 5 Decline Reasons</h2>
        {top5.length === 0 ? (
          <p className="text-slate-500 text-sm">No data yet</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-slate-500 text-xs border-b border-navy-600">
                <th className="text-left pb-2">Code</th>
                <th className="text-left pb-2">Category</th>
                <th className="text-right pb-2">Failures</th>
                <th className="text-right pb-2">Lost Revenue</th>
              </tr>
            </thead>
            <tbody>
              {top5.map((row) => (
                <tr key={row.unified_code} className="border-b border-navy-700 hover:bg-navy-700 transition-colors">
                  <td className="py-2 font-mono text-xs text-slate-200">{row.unified_code}</td>
                  <td className="py-2 text-slate-400">{row.category}</td>
                  <td className="py-2 text-right text-slate-200">{row.failure_count.toLocaleString()}</td>
                  <td className="py-2 text-right text-accent-red font-medium">{formatCurrency(row.lost_amount)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}
