import clsx from "clsx";

const STYLES: Record<string, string> = {
  ACTIVE: "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-200",
  BLOCKED: "bg-amber-100 text-amber-900 dark:bg-amber-900/40 dark:text-amber-200",
  DELETED: "bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300",
  DRAFT: "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-200",
  ISSUED: "bg-blue-100 text-blue-800 dark:bg-blue-900/40 dark:text-blue-200",
  PARTIALLY_PAID: "bg-indigo-100 text-indigo-800 dark:bg-indigo-900/40 dark:text-indigo-200",
  PAID: "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-200",
  OVERDUE: "bg-orange-100 text-orange-900 dark:bg-orange-900/40 dark:text-orange-200",
  WRITTEN_OFF: "bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300",
  RECEIVED: "bg-sky-100 text-sky-800 dark:bg-sky-900/40 dark:text-sky-200",
  DEPOSITED: "bg-violet-100 text-violet-800 dark:bg-violet-900/40 dark:text-violet-200",
  CLEARED: "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-200",
  BOUNCED: "bg-rose-100 text-rose-800 dark:bg-rose-900/40 dark:text-rose-200",
  APPLIED: "bg-emerald-100 text-emerald-800 dark:bg-emerald-900/40 dark:text-emerald-200",
  EXPIRED: "bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300",
  CANCELLED: "bg-zinc-200 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-300",
};

export function StatusBadge({ status }: { status: string }) {
  return (
    <span
      className={clsx(
        "inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium tracking-wide",
        STYLES[status] ?? "bg-zinc-100 text-zinc-700 dark:bg-zinc-800 dark:text-zinc-200",
      )}
    >
      {status.replaceAll("_", " ")}
    </span>
  );
}