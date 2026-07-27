import { describe, expect, it } from "vitest";
import {
  normalizeMetricsCounts,
  unwrapNotificationList,
} from "@/lib/api/notifications";

describe("unwrapNotificationList", () => {
  it("returns bare arrays as-is", () => {
    const rows = [{ id: "1", eventType: "X", channel: "EMAIL", status: "SENT" }];
    expect(unwrapNotificationList(rows as never)).toEqual(rows);
  });

  it("unwraps page envelope", () => {
    const rows = [{ id: "1", eventType: "X", channel: "EMAIL", status: "SENT" }];
    expect(
      unwrapNotificationList({ items: rows as never, nextCursor: null, count: 1 }),
    ).toEqual(rows);
  });

  it("returns empty for nullish", () => {
    expect(unwrapNotificationList(null)).toEqual([]);
    expect(unwrapNotificationList(undefined)).toEqual([]);
  });
});

describe("normalizeMetricsCounts", () => {
  it("reads array byStatus buckets", () => {
    const m = normalizeMetricsCounts({
      days: 7,
      total: 10,
      byStatus: [
        { key: "SENT", count: 5 },
        { key: "FAILED", count: 2 },
        { key: "PENDING", count: 3 },
      ],
    });
    expect(m).toEqual({ SENT: 5, FAILED: 2, PENDING: 3, SKIPPED: 0 });
  });
});
