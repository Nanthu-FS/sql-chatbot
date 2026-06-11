import type { RecoveryFunnel } from "../api/client";
import { formatCurrency } from "../api/client";

interface Props {
  data: RecoveryFunnel;
}

export function RetryFunnelChart({ data }: Props) {
  const steps = [
    { label: "Scheduled", value: data.scheduled, color: "#60A5FA" },
    { label: "Executed", value: data.executed, color: "#FBBF24" },
    { label: "Recovered", value: data.recovered, color: "#4ADE80" },
  ];

  const max = steps[0].value || 1;

  return (
    <div className="space-y-3">
      {steps.map((step) => (
        <div key={step.label}>
          <div className="flex justify-between text-sm mb-1">
            <span className="text-slate-300">{step.label}</span>
            <span style={{ color: step.color }} className="font-semibold">
              {step.value.toLocaleString()}
            </span>
          </div>
          <div className="h-8 bg-navy-900 rounded overflow-hidden">
            <div
              className="h-full rounded transition-all"
              style={{
                width: `${(step.value / max) * 100}%`,
                background: step.color,
                opacity: 0.8,
              }}
            />
          </div>
        </div>
      ))}
      <div className="pt-2 border-t border-navy-600 flex justify-between text-sm">
        <span className="text-slate-400">Recovered Revenue</span>
        <span className="text-accent-green font-semibold">{formatCurrency(data.recovered_amount)}</span>
      </div>
      <div className="flex justify-between text-sm">
        <span className="text-slate-400">Recovery Rate</span>
        <span className="text-accent-green font-semibold">{data.recovery_rate_pct}%</span>
      </div>
    </div>
  );
}
