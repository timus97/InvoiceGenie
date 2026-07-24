"use client";

import { useQuery } from "@tanstack/react-query";
import { toast } from "sonner";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { useTenant } from "@/components/tenant-provider";
import { exportAuditCsv, listAudit } from "@/lib/api/audit";

export default function AuditPage() {
  const { tenantId, ready } = useTenant();
  const list = useQuery({
    queryKey: ["audit", tenantId],
    enabled: ready,
    queryFn: ({ signal }) => listAudit(tenantId, 200, signal),
  });

  const onExport = async () => {
    try {
      const csv = await exportAuditCsv(tenantId, 500);
      const blob = new Blob([csv], { type: "text/csv" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = "audit-export.csv";
      a.click();
      URL.revokeObjectURL(url);
      toast.success("Audit CSV downloaded");
    } catch (e) {
      toast.error(e instanceof Error ? e.message : "Export failed");
    }
  };

  return (
    <div className="space-y-6">
      <PageHeader
        title="Audit log"
        description="Compliance trail of domain mutations"
        actions={<Button onClick={onExport}>Export CSV</Button>}
      />
      <Card className="overflow-x-auto p-0">
        <table className="w-full text-sm">
          <thead className="border-b bg-zinc-50 text-left dark:bg-zinc-900">
            <tr>
              <th className="px-4 py-2">When</th>
              <th className="px-4 py-2">Entity</th>
              <th className="px-4 py-2">Ref</th>
              <th className="px-4 py-2">Action</th>
            </tr>
          </thead>
          <tbody>
            {(list.data ?? []).map((a) => (
              <tr key={a.id} className="border-b last:border-0">
                <td className="px-4 py-2 text-xs text-zinc-500">{a.createdAt}</td>
                <td className="px-4 py-2">
                  {a.entityType}
                  {a.entityId ? (
                    <span className="ml-1 font-mono text-xs text-zinc-500">
                      {a.entityId.slice(0, 8)}
                    </span>
                  ) : null}
                </td>
                <td className="px-4 py-2">{a.entityRef ?? "—"}</td>
                <td className="px-4 py-2 font-medium">{a.action}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  );
}