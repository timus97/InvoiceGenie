"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import clsx from "clsx";
import {
  LayoutDashboard,
  Users,
  FileText,
  Wallet,
  Landmark,
  CalendarClock,
  StickyNote,
  BookOpen,
  Settings,
  Building2,
  ArrowLeftRight,
  ScrollText,
  Webhook,
} from "lucide-react";

const NAV = [
  { href: "/", label: "Dashboard", icon: LayoutDashboard },
  { href: "/customers", label: "Customers", icon: Users },
  { href: "/invoices", label: "Invoices", icon: FileText },
  { href: "/payments", label: "Payments", icon: Wallet },
  { href: "/cheques", label: "Cheques", icon: Landmark },
  { href: "/aging", label: "Aging", icon: CalendarClock },
  { href: "/credit-notes", label: "Credit notes", icon: StickyNote },
  { href: "/ledger", label: "Ledger", icon: BookOpen },
  { href: "/tenants", label: "Tenants", icon: Building2 },
  { href: "/exchange-rates", label: "FX rates", icon: ArrowLeftRight },
  { href: "/audit", label: "Audit", icon: ScrollText },
  { href: "/webhooks", label: "Webhooks", icon: Webhook },
  { href: "/settings", label: "Settings", icon: Settings },
] as const;

export function Sidebar() {
  const pathname = usePathname();
  const appName = process.env.NEXT_PUBLIC_APP_NAME ?? "InvoiceGenie AR";

  return (
    <aside className="flex w-60 shrink-0 flex-col border-r border-zinc-200 bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-950">
      <div className="border-b border-zinc-200 px-4 py-5 dark:border-zinc-800">
        <div className="text-xs font-semibold uppercase tracking-wider text-indigo-600 dark:text-indigo-400">
          InvoiceGenie
        </div>
        <div className="mt-0.5 text-sm font-medium text-zinc-900 dark:text-zinc-100">
          {appName}
        </div>
      </div>
      <nav className="flex-1 space-y-0.5 p-3">
        {NAV.map(({ href, label, icon: Icon }) => {
          const active =
            href === "/"
              ? pathname === "/"
              : pathname === href || pathname.startsWith(`${href}/`);
          return (
            <Link
              key={href}
              href={href}
              className={clsx(
                "flex items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition-colors",
                active
                  ? "bg-indigo-600 text-white shadow-sm"
                  : "text-zinc-600 hover:bg-zinc-200/70 hover:text-zinc-900 dark:text-zinc-400 dark:hover:bg-zinc-900 dark:hover:text-zinc-100",
              )}
            >
              <Icon className="h-4 w-4 shrink-0" />
              {label}
            </Link>
          );
        })}
      </nav>
      <div className="border-t border-zinc-200 p-3 text-xs text-zinc-500 dark:border-zinc-800 dark:text-zinc-500">
        Proxied to Quarkus <code className="font-mono">/api/v1</code>
      </div>
    </aside>
  );
}