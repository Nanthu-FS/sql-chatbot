import { useMemo } from "react";
import {
  Area,
  AreaChart,
  CartesianGrid,
  Legend,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from "recharts";
import type { TimeseriesPoint } from "../api/client";

const COLORS: Record<string, string> = {
  RECOVERABLE: "#60A5FA",
  HARD_DECLINE: "#FF6B6B",
  TECHNICAL: "#FBBF24",
  FRAUD_RISK: "#F97316",
  CUSTOMER_ACTION: "#A78BFA",
  UNKNOWN: "#6B7280",
};

const CATEGORIES = ["RECOVERABLE", "HARD_DECLINE", "TECHNICAL", "FRAUD_RISK", "CUSTOMER_ACTION", "UNKNOWN"];

interface Props {
  data: TimeseriesPoint[];
}

export function TimeseriesChart({ data }: Props) {
  const chartData = useMemo(() => {
    const buckets: Record<string, Record<string, number>> = {};
    for (const point of data) {
      if (!buckets[point.bucket]) buckets[point.bucket] = {};
      buckets[point.bucket][point.category] = point.failure_count;
    }
    return Object.entries(buckets)
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([bucket, cats]) => ({ bucket, ...cats }));
  }, [data]);

  const presentCategories = useMemo(() => {
    const seen = new Set(data.map((d) => d.category));
    return CATEGORIES.filter((c) => seen.has(c));
  }, [data]);

  return (
    <ResponsiveContainer width="100%" height={260}>
      <AreaChart data={chartData} margin={{ top: 5, right: 10, left: 0, bottom: 5 }}>
        <CartesianGrid strokeDasharray="3 3" stroke="#035380" />
        <XAxis dataKey="bucket" tick={{ fill: "#94a3b8", fontSize: 11 }} />
        <YAxis tick={{ fill: "#94a3b8", fontSize: 11 }} />
        <Tooltip
          contentStyle={{ background: "#01304f", border: "1px solid #035380", borderRadius: 8 }}
          labelStyle={{ color: "#e2e8f0" }}
        />
        <Legend formatter={(v) => <span style={{ color: "#94a3b8", fontSize: 12 }}>{v}</span>} />
        {presentCategories.map((cat) => (
          <Area
            key={cat}
            type="monotone"
            dataKey={cat}
            stackId="1"
            stroke={COLORS[cat]}
            fill={COLORS[cat]}
            fillOpacity={0.6}
          />
        ))}
      </AreaChart>
    </ResponsiveContainer>
  );
}
