import { useEffect, useState } from "react";
import { api } from "../api/client";
import type { UnmappedCode } from "../api/client";

const UNIFIED_CODES = [
  "INSUFFICIENT_FUNDS", "EXPIRED_CARD", "CARD_LIMIT_EXCEEDED",
  "STOLEN_CARD", "ACCOUNT_CLOSED", "INVALID_CARD", "DO_NOT_HONOR",
  "GATEWAY_TIMEOUT", "API_ERROR", "NETWORK_ERROR", "PROCESSING_ERROR",
  "BANK_FRAUD_BLOCK", "3DS_FAILED", "RISK_THRESHOLD",
  "AUTHENTICATION_REQUIRED", "CARD_NOT_SUPPORTED", "UNKNOWN",
];

export function Settings() {
  const [unmapped, setUnmapped] = useState<UnmappedCode[]>([]);
  const [selected, setSelected] = useState<UnmappedCode | null>(null);
  const [unifiedCode, setUnifiedCode] = useState("");
  const [saving, setSaving] = useState(false);
  const [success, setSuccess] = useState("");

  async function load() {
    const data = await api.unmapped();
    setUnmapped(data);
  }

  useEffect(() => { void load(); }, []);

  async function handleSave() {
    if (!selected || !unifiedCode) return;
    setSaving(true);
    await api.createMapping({ gateway: selected.gateway, gateway_code: selected.gateway_code, unified_code: unifiedCode });
    setSaving(false);
    setSuccess(`Mapped "${selected.gateway_code}" → ${unifiedCode}`);
    setSelected(null);
    setUnifiedCode("");
    await load();
    setTimeout(() => setSuccess(""), 3000);
  }

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-bold text-white">Settings / Mappings</h1>

      {success && (
        <div className="bg-green-900 border border-green-700 text-green-200 rounded-lg px-4 py-3 text-sm">
          {success}
        </div>
      )}

      <div className="bg-navy-800 rounded-xl border border-navy-600 overflow-hidden">
        <div className="px-5 py-4 border-b border-navy-600">
          <h2 className="text-sm font-semibold text-slate-300">Unmapped Gateway Codes</h2>
          <p className="text-xs text-slate-500 mt-1">These resolved to UNKNOWN — select one to map it.</p>
        </div>
        {unmapped.length === 0 ? (
          <p className="text-slate-500 text-sm p-5">No unmapped codes — great!</p>
        ) : (
          <table className="w-full text-sm">
            <thead>
              <tr className="text-slate-500 text-xs border-b border-navy-600 bg-navy-900">
                <th className="text-left px-4 py-3">Gateway</th>
                <th className="text-left px-4 py-3">Raw Code</th>
                <th className="text-right px-4 py-3">Occurrences</th>
                <th className="px-4 py-3"></th>
              </tr>
            </thead>
            <tbody>
              {unmapped.map((u) => (
                <tr
                  key={`${u.gateway}:${u.gateway_code}`}
                  className={`border-b border-navy-700 transition-colors ${selected?.gateway_code === u.gateway_code ? "bg-navy-700" : "hover:bg-navy-700"}`}
                >
                  <td className="px-4 py-3 text-slate-400 capitalize">{u.gateway}</td>
                  <td className="px-4 py-3 font-mono text-xs text-slate-200">{u.gateway_code}</td>
                  <td className="px-4 py-3 text-right text-slate-300">{u.occurrence_count}</td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => { setSelected(u); setUnifiedCode(""); }}
                      className="text-xs text-blue-400 hover:text-blue-300 border border-blue-800 px-2 py-1 rounded"
                    >Map</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {selected && (
        <div className="bg-navy-800 rounded-xl p-5 border border-navy-600">
          <h3 className="text-sm font-semibold text-slate-300 mb-4">
            Map: <span className="font-mono text-white">{selected.gateway_code}</span> ({selected.gateway})
          </h3>
          <div className="flex gap-3 items-end">
            <div className="flex-1">
              <label className="text-xs text-slate-400 block mb-1">Map to unified code</label>
              <select
                className="w-full bg-navy-900 text-slate-200 text-sm rounded px-3 py-2 border border-navy-600"
                value={unifiedCode}
                onChange={(e) => setUnifiedCode(e.target.value)}
              >
                <option value="">Select code…</option>
                {UNIFIED_CODES.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
            </div>
            <button
              disabled={!unifiedCode || saving}
              onClick={() => void handleSave()}
              className="px-4 py-2 bg-blue-600 hover:bg-blue-500 disabled:opacity-40 text-white text-sm rounded font-medium"
            >
              {saving ? "Saving…" : "Save Mapping"}
            </button>
            <button onClick={() => setSelected(null)} className="px-4 py-2 text-slate-400 hover:text-white text-sm">Cancel</button>
          </div>
        </div>
      )}
    </div>
  );
}
