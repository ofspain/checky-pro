# Payment & Verification Service

Java 21 · Spring Boot · JPA/Flyway. **The core domain.**

Owns: invoices, payment records, the verification state machine
(`CREATED → WATCHING → SEEN → CONFIRMING → FINALIZED → ATTESTED`, reorg-reversible),
hash-chain ledger, receipt issuance (signed at finality only), tax-ready history,
`payments` Postgres schema.

Consumes: `chain.tx.*` events. Publishes: `invoice.created`, `payment.seen`,
`payment.finalized`, `receipt.issued`. All writes go through the outbox (`libs/java/outbox`).

## Local development database

Uses `services/auth`'s Postgres container (`docker compose -f services/auth/compose.local.yaml up
-d postgres`), a new `payments` schema, and a separate, unprivileged `payment_app` runtime role
(never the migration owner — table owners bypass grants in Postgres, so this split is what makes
the service's own least-privilege access real).

```
mvn -pl services/payment flyway:migrate
docker exec -it auth-postgres-1 psql -U checky -d checky \
  -c "ALTER ROLE payment_app PASSWORD 'payment-app-local-only';"
```

The migration itself never commits a password — real environments provision `payment_app`'s
credential out-of-band (External Secrets Operator), same as every other credential this service
uses. The line above is the one-time local-only step; `application.properties` (task 3) will set
`spring.datasource.password` to match it.
