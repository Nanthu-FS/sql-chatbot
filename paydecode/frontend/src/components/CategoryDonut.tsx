import { Cell, Legend, Pie, PieChart, ResponsiveContainer, Tooltip } from "recharts";
import type { CategoryBreakdown } from "../api/client";
import { formatCurrency } from "../api/client";

const COLORS: Record<string, string> = {
  RECOVERABLE: "#60A5FA",
  HARD_DECLINE: "#FF6B6B",
  TECHNICAL: "#FBBF24",
  FRAUD_RISK: "#F97316",
  CUSTOMER_ACTION: "#A78BFA",
  UNKNOWN: "#6B7280",
};

interface Props {
  data: CategoryBreakdown[];
}

export function CategoryDonut({ data }: Props) {
  const chartData = data.map((d) => ({
    name: d.category,
    value: d.failure_count,
    amount: d.lost_amount,
  }));

  return (
    <ResponsiveContainer width="100%" height={280}>
      <PieChart>
        <Pie
          data={chartData}
          cx="50%"
          cy="50%"
          innerRadius={60}
          outerRadius={100}
          paddingAngle={2}
          dataKey="value"
        >
          {chartData.map((entry) => (
            <Cell key={entry.name} fill={COLORS[entry.name] ?? "#6B7280"} />
          ))}
        </Pie>
        <Tooltip
          contentStyle={{ background: "#01304f", border: "1px solid #035380", borderRadius: 8 }}
          formatter={(value: number, _name: string, props: { payload?: { amount?: number } }) => [
            `${value} failures (${formatCurrency(props.payload?.amount ?? 0)})`,
            "",
          ]}
        />
        <Legend
          formatter={(value) => <span style={{ color: "#94a3b8", fontSize: 12 }}>{value}</span>}
        />
      </PieChart>
    </ResponsiveContainer>
  );
}
