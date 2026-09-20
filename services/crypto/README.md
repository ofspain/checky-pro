# Crypto Service

Java 21 · Spring Boot · virtual threads · web3j (EVM) · Tron gRPC. **CODEOWNERS-protected.**

The only component that talks to blockchains. Adapter layer (per-chain, common interface)
plus watcher layer (long-running subscriptions/polling). Every fact requires 2-of-3 quorum
across independent RPC providers. Per-chain finality policy; reorgs emit `chain.tx.reorged`.
Exposes internal `POST /attest` — the sole path to `kms:Sign` on the attestation key.

Owns: `chain` Postgres schema (watches, provider health, raw observation log).
Publishes: `chain.tx.seen|confirmed|finalized|reorged`, `chain.provider.degraded`.

`sidecars/` — TypeScript chain adapters for thin-Java-tooling chains (translation only:
no quorum authority, no key access, no business state).

## Local development database

Uses `services/auth`'s Postgres container (`docker compose -f services/auth/compose.local.yaml up
-d postgres`), a new `chain` schema, and a separate, unprivileged `crypto_app` runtime role (never
the migration owner — table owners bypass grants in Postgres, so this split is what makes the
service's own least-privilege access real).

```
mvn -pl services/crypto flyway:migrate
docker exec -it auth-postgres-1 psql -U checky -d checky \
  -c "ALTER ROLE crypto_app PASSWORD 'crypto-app-local-only';"
```

The migration itself never commits a password — real environments provision `crypto_app`'s
credential out-of-band (External Secrets Operator), same as every other credential this service
uses. The line above is the one-time local-only step, matching `spring.datasource.password`'s own
`crypto-app-local-only` default in `application.properties`.
