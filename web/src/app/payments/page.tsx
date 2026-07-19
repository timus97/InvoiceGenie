import { Suspense } from "react";
import { PageHeader } from "@/components/ui/page-header";
import { Card } from "@/components/ui/card";
import { TableSkeleton } from "@/components/ui/skeleton";
import PaymentsClient from "./payments-client";

export default function PaymentsPage() {
  return (
    <Suspense
      fallback={
        <div>
          <PageHeader title="Payments" description="Loading..." />
          <Card>
            <TableSkeleton />
          </Card>
        </div>
      }
    >
      <PaymentsClient />
    </Suspense>
  );
}