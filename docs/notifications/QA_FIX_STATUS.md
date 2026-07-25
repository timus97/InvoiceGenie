# QA fix status (2026-07-26)

All High and listed Medium/Low QA-NOTIFY stories addressed in code:

| Story | Status |
|-------|--------|
| 001 SKIPPED lock | Fixed — delete recoverable SKIPPED and re-enqueue |
| 002 force semantics | Fixed — force only auto event flags; UI force=false |
| 003 PII logs | Fixed — mask destination; log-payloads=false by default |
| 004 dual-write / claim | Fixed — claimDue FOR UPDATE SKIP LOCKED + short TX |
| 005 invoice 404 | Fixed |
| 006 status guard | Fixed — DRAFT etc. SKIPPED INVALID_STATUS |
| 007 qualifier | Fixed — auto qualify reminder/dunning |
| 008 preDueDays=0 | Fixed |
| 009 catch-up window | Fixed |
| 010 destination validation | Fixed |
| 011/019 customer exists | Fixed on pref upsert |
| 012 multi-instance claim | Fixed via SKIP LOCKED |
| 013 fail-closed SMTP/Meta | Fixed |
| 014 rate limit | Fixed (30/min tenant) |
| 015 UUID validation | Fixed |
| 017 history UX | Fixed filters + attempts |
| 018 WA templates | Seeded in V12 |
| 016 audit trail | Deferred (delivery attempts remain; full audit stream optional follow-up) |

Compile: SUCCESS after fixes.