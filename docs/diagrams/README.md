# InvoiceGenie Diagrams

Editable source: [InvoiceGenie-Architecture.drawio](./InvoiceGenie-Architecture.drawio)

Open in [diagrams.net](https://app.diagrams.net/) or the draw.io desktop app. The live session was created via the draw.io MCP integration.

## Pages

| # | Page | PNG | Contents |
|---|------|-----|----------|
| 1 | **Architecture** | [01-Architecture.png](./01-Architecture.png) | Hexagonal layers: clients → driving adapters → application → domain → driven adapters → bootstrap/kernel |
| 2 | **Module Design** | [02-Module-Design.png](./02-Module-Design.png) | Maven module dependency graph (depends-on arrows) |
| 3 | **Invoice Flow Design** | [03-Invoice-Flow-Design.png](./03-Invoice-Flow-Design.png) | Create/issue invoice sequence, outbox worker, invoice lifecycle states |
| 4 | **Domain Data Design** | [04-Domain-Data-Design.png](./04-Domain-Data-Design.png) | Aggregates, tables, relationships, multi-tenancy strip |
| 5 | **Payment Design** | [05-Payment-Design.png](./05-Payment-Design.png) | Record payment, FIFO/manual allocation, cheque lifecycle |

## How to edit

1. Open `InvoiceGenie-Architecture.drawio` in draw.io.
2. Or reload via draw.io MCP: `load_diagram` → path to this file.
3. Prefer editing tabs rather than recreating from scratch so page structure is preserved.

## Related docs

- [ONBOARDING.md](../ONBOARDING.md) — narrative blueprint
- [SCHEMA.md](../SCHEMA.md) — SQL schema detail
