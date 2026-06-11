import type { FailureItem } from "../api/client";
import { formatCurrency } from "../api/client";

interface Props {
  item: FailureItem | null;
  onClose: () => void;
}

const CATEGORY_COLORS: Record<string, string> = {
  RECOVERABLE: "bg-blue-900 text-blue-300",
  HARD_DECLINE: "bg-red-900 text-red-300",
  TECHNICAL: "bg-yellow-900 text-yellow-300",
  FRAUD_RISK: "bg-orange-900 text-orange-300",
  CUSTOMER_ACTION: "bg-purple-900 text-purple-300",
  UNKNOWN: "bg-gray-700 text-gray-300",
};

export function EventDrawer({ item, onClose }: Props) {
  if (!item) return null;

  return (
    <div className="fixed inset-0 z-50 flex">
      <div className="flex-1 bg-black/50" onClick={onClose} />
      <div className="w-full max-w-lg bg-navy-800 border-l border-navy-600 overflow-y-auto shadow-2xl">
        <div className="flex items-center justify-between p-5 border-b border-navy-600">
          <h2 className="text-lg font-semibold text-white">Failure Detail</h2>
          <button onClick={onClose} className="text-slate-400 hover:text-white text-2xl leading-none">×</button>
        </div>

        <div className="p-5 space-y-5">
          <section>
            <h3 className="text-xs text-slate-500 uppercase tracking-wider mb-3">Payment</h3>
            <div className="grid grid-cols-2 gap-3 text-sm">
              <div>
                <p className="text-slate-400">ID</p>
                <p className="text-slate-100 font-mono text-xs">{item.external_payment_id}</p>
              </div>
              <div>
                <p className="text-slate-400">Amount</p>
                <p className="text-accent-red font-semibold">{formatCurrency(item.amount, item.currency)}</p>
              </div>
              <div>
                <p className="text-slate-400">Gateway</p>
                <p className="text-slate-100 capitalize">{item.gateway}</p>
              </div>
              <div>
                <p className="text-slate-400">Customer</p>
                <p className="text-slate-100 text-xs">{item.customer_email ?? "—"}</p>
              </div>
              <div>
                <p className="text-slate-400">Attempt #</p>
                <p className="text-slate-100">{item.attempt_number}</p>
              </div>
              <div>
                <p className="text-slate-400">Occurred</p>
                <p className="text-slate-100 text-xs">{new Date(item.occurred_at).toLocaleString()}</p>
              </div>
            </div>
          </section>

          <section>
            <h3 className="text-xs text-slate-500 uppercase tracking-wider mb-3">Decline</h3>
            <div className="space-y-2 text-sm">
              <div className="flex justify-between">
                <span className="text-slate-400">Gateway Code</span>
                <span className="font-mono text-slate-200 text-xs">{item.gateway_code}</span>
              </div>
              <div className="flex justify-between">
                <span className="text-slate-400">Unified Code</span>
                <span className="font-mono text-slate-200 text-xs">{item.unified_code}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-slate-400">Category</span>
                <span className={`text-xs px-2 py-1 rounded-full font-medium ${CATEGORY_COLORS[item.category] ?? "bg-gray-700 text-gray-300"}`}>
                  {item.category}
                </span>
              </div>
            </div>
          </section>
        </div>
      </div>
    </div>
  );
}
