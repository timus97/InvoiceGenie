import { Suspense } from "react";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { TableSkeleton } from "@/components/ui/skeleton";
import LedgerClient from "./ledger-client";

export default function LedgerPage() {
  return (
    <Suspense
      fallback={
        <div>
          <PageHeader title="Ledger" description="Loading..." />
          <Card>
            <TableSkeleton />
          </Card>
        </div>
      }
    >
      <LedgerClient />
    </Suspense>
  );
}