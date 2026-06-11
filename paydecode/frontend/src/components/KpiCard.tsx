
interface KpiCardProps {
  label: string;
  value: string;
  sub?: string;
  color?: "red" | "green" | "yellow" | "blue";
}

const colorMap = {
  red: "text-accent-red",
  green: "text-accent-green",
  yellow: "text-accent-yellow",
  blue: "text-accent-blue",
};

export function KpiCard({ label, value, sub, color = "blue" }: KpiCardProps) {
  return (
    <div className="bg-navy-800 rounded-xl p-5 border border-navy-600">
      <p className="text-slate-400 text-sm mb-1">{label}</p>
      <p className={`text-2xl font-bold ${colorMap[color]}`}>{value}</p>
      {sub && <p className="text-slate-500 text-xs mt-1">{sub}</p>}
    </div>
  );
}
