import { useRef, useState } from "react";
import { BrowserRouter, NavLink, Route, Routes } from "react-router-dom";
import { api } from "./api/client";
import { Explorer } from "./pages/Explorer";
import { Overview } from "./pages/Overview";
import { Recovery } from "./pages/Recovery";
import { Settings } from "./pages/Settings";

const CODES = [
  "insufficient_funds", "expired_card", "processing_error",
  "gateway_timeout", "do_not_honor", "fraudulent",
  "authentication_required", "3d_secure_failed",
];

function Navbar() {
  const [simulating, setSimulating] = useState(false);
  const [simResult, setSimResult] = useState<string | null>(null);
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null);

  async function handleSimulate() {
    setSimulating(true);
    setSimResult(null);
    const res = await api.simulate(200, 30);
    setSimResult(`Generated ${res.processed} events`);
    setSimulating(false);

    pollRef.current = setInterval(() => {
      window.dispatchEvent(new CustomEvent("paydecode:refresh"));
    }, 5000);
    setTimeout(() => {
      if (pollRef.current) clearInterval(pollRef.current);
    }, 30000);
  }

  async function handleFireOne() {
    const code = CODES[Math.floor(Math.random() * CODES.length)];
    await api.fireOne(code);
    window.dispatchEvent(new CustomEvent("paydecode:refresh"));
  }

  const navCls = ({ isActive }: { isActive: boolean }) =>
    `px-3 py-2 rounded text-sm font-medium transition-colors ${
      isActive ? "bg-navy-600 text-white" : "text-slate-400 hover:text-white hover:bg-navy-700"
    }`;

  return (
    <nav className="bg-navy-900 border-b border-navy-600 px-6 py-3 flex items-center justify-between sticky top-0 z-40">
      <div className="flex items-center gap-6">
        <span className="text-white font-bold text-lg tracking-tight">
          Pay<span className="text-accent-red">Decode</span>
        </span>
        <div className="flex gap-1">
          <NavLink to="/" className={navCls} end>Overview</NavLink>
          <NavLink to="/explorer" className={navCls}>Explorer</NavLink>
          <NavLink to="/recovery" className={navCls}>Recovery</NavLink>
          <NavLink to="/settings" className={navCls}>Settings</NavLink>
        </div>
      </div>

      <div className="flex items-center gap-3">
        {simResult && (
          <span className="text-green-400 text-xs">{simResult}</span>
        )}
        <button
          onClick={() => void handleFireOne()}
          className="text-xs px-3 py-1.5 rounded border border-navy-600 text-slate-300 hover:text-white hover:border-slate-500 transition-colors"
        >
          Fire Event
        </button>
        <button
          disabled={simulating}
          onClick={() => void handleSimulate()}
          className="text-xs px-3 py-1.5 rounded bg-accent-red hover:bg-red-500 text-white font-medium disabled:opacity-60 transition-colors"
        >
          {simulating ? "Simulating…" : "Simulate Traffic"}
        </button>
      </div>
    </nav>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <div className="min-h-screen bg-navy-900">
        <Navbar />
        <main className="max-w-7xl mx-auto px-6 py-6">
          <Routes>
            <Route path="/" element={<Overview />} />
            <Route path="/explorer" element={<Explorer />} />
            <Route path="/recovery" element={<Recovery />} />
            <Route path="/settings" element={<Settings />} />
          </Routes>
        </main>
      </div>
    </BrowserRouter>
  );
}
