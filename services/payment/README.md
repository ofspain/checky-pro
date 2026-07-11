# Payment & Verification Service

Java 21 · Spring Boot · JPA/Flyway. **The core domain.**

Owns: invoices, payment records, the verification state machine
(`CREATED → WATCHING → SEEN → CONFIRMING → FINALIZED → ATTESTED`, reorg-reversible),
hash-chain ledger, receipt issuance (signed at finality only), tax-ready history,
`payments` Postgres schema.

Consumes: `chain.tx.*` events. Publishes: `invoice.created`, `payment.seen`,
`payment.finalized`, `receipt.issued`. All writes go through the outbox (`libs/java/outbox`).
