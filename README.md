# InvoiceGenie — Multi-Tenant AR Backend

Production-grade **Accounts Receivable** backend: Java 17, Quarkus, DDD, Hexagonal Architecture, strict multi-tenant isolation. Designed for millions of invoices.

- **Design:** [docs/AR_BACKEND_DESIGN.md](docs/AR_BACKEND_DESIGN.md) — architecture, bounded contexts, package structure, tenant isolation, scale notes.
- **Run:** `mvn -pl ar-bootstrap quarkus:dev` (requires PostgreSQL; optional Kafka).
- **Build:** `mvn clean install`

No UI in this repo; backend only. Extensible for future AP and GL modules.
