import { useEffect, useState } from "react";
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { api, formatCurrency } from "../api/client";
import type { CodeBreakdown, RecoveryFunnel } from "../api/client";
import { RetryFunnelChart } from "../components/RetryFunnel";

export function Recovery() {
  const [funnel, setFunnel] = useState<RecoveryFunnel | null>(null);
  const [recoverableCodes, setRecoverableCodes] = useState<CodeBreakdown[]>([]);
  const [loading, setLoading] = useState(true);

  async function load() {
    const [f, codes] = await Promise.all([api.recovery(), api.byCode()]);
    setFunnel(f);
    setRecoverableCodes(codes.filter((c) => c.category === "RECOVERABLE" || c.category === "TECHNICAL"));
    setLoading(false);
  }

  useEffect(() => { void load(); }, []);

  if (loading) return <div className="flex items-center justify-center h-64 text-slate-400">Loading…</div>;

  const barData = recoverableCodes.map((c) => ({
    name: c.unified_code,
    amount: Math.round(c.lost_amount / 100),
  }));

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">Recovery</h1>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-navy-800 rounded-xl p-5 border border-navy-600">
          <h2 className="text-sm font-semibold text-slate-300 mb-5">Retry Funnel</h2>
          {funnel ? <RetryFunnelChart data={funnel} /> : <p className="text-slate-500 text-sm">No retry data yet</p>}
        </div>

        <div className="bg-navy-800 rounded-xl p-5 border border-navy-600">
          <h2 className="text-sm font-semibold text-slate-300 mb-4">Recoverable Revenue by Code</h2>
          {barData.length === 0 ? (
            <p className="text-slate-500 text-sm text-center py-8">No data yet</p>
          ) : (
            <ResponsiveContainer width="100%" height={240}>
              <BarChart data={barData} margin={{ top: 5, right: 10, left: 0, bottom: 40 }}>
                <CartesianGrid strokeDasharray="3 3" stroke="#035380" />
                <XAxis dataKey="name" tick={{ fill: "#94a3b8", fontSize: 10 }} angle={-30} textAnchor="end" />
                <YAxis tick={{ fill: "#94a3b8", fontSize: 11 }} />
                <Tooltip
                  contentStyle={{ background: "#01304f", border: "1px solid #035380", borderRadius: 8 }}
                  formatter={(v: number) => [`₹${v.toLocaleString()}`, "Lost (₹)"]}
                />
                <Bar dataKey="amount" fill="#60A5FA" radius={[4, 4, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      <div className="bg-navy-800 rounded-xl p-5 border border-navy-600">
        <h2 className="text-sm font-semibold text-slate-300 mb-4">Recovery Summary</h2>
        {funnel ? (
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-center">
            {[
              { label: "Scheduled", value: funnel.scheduled, color: "text-blue-400" },
              { label: "Executed", value: funnel.executed, color: "text-yellow-400" },
              { label: "Recovered", value: funnel.recovered, color: "text-green-400" },
              { label: "Recovered Revenue", value: formatCurrency(funnel.recovered_amount), color: "text-green-400" },
            ].map((item) => (
              <div key={item.label} className="bg-navy-900 rounded-lg p-4">
                <p className="text-slate-400 text-xs mb-1">{item.label}</p>
                <p className={`text-xl font-bold ${item.color}`}>{typeof item.value === "number" ? item.value.toLocaleString() : item.value}</p>
              </div>
            ))}
          </div>
        ) : null}
      </div>
    </div>
  );
}
