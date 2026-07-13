# Auth Service

Themistra's identity issuer — OIDC/OAuth2 via Spring Authorization Server (Java 21, Spring Boot).
Owns accounts, MFA (TOTP), roles/role templates, merchant API keys, refresh-token families,
the append-only auth audit trail, and the `auth` Postgres schema.

**Design docs (read before touching code):**
- [`docs/architecture/target-design.md`](docs/architecture/target-design.md) — the architecture
- [`docs/architecture/auth-decisions.md`](docs/architecture/auth-decisions.md) — decision log (D-001…)
- [`docs/architecture/reference-analysis.md`](docs/architecture/reference-analysis.md) / [`gap-analysis.md`](docs/architecture/gap-analysis.md) — why it looks this way

## Layout

```
src/main/java/com/themistra/auth/
├── account/   registration, verification, suspension
├── authn/     login flow, MFA step, lockout, password policy/reset
├── mfa/       TOTP enrollment, recovery codes
├── token/     SAS config, claims, JWKS rotation, refresh families
├── authz/     roles + role templates
├── apikey/    merchant keys + key→JWT exchange
├── audit/     append-only audit writer
├── events/    outbox publishing (contracts/events/auth)
├── admin/     operator endpoints
└── common/    RFC 9457 errors, validated config records
```

Package-by-feature; module boundaries ArchUnit-enforced. Modules expose services, never entities.

## Run locally

```bash
docker compose -f services/auth/compose.local.yaml up -d   # Postgres + Kafka
mvn -pl services/auth spring-boot:run                       # from repo root
```

Flyway migrates the `auth` schema on boot. OIDC discovery: http://localhost:8080/.well-known/openid-configuration

## Build & test

```bash
mvn -pl services/auth verify          # unit + integration (Testcontainers: needs Docker)
docker build -f services/auth/Dockerfile -t auth-service .   # from repo root
```

## Deploy

`deploy/k8s/` manifests (Deployment/HPA/PDB/NetworkPolicy/ExternalSecret) are applied by the CDK
service stack with `${...}` placeholders stamped by CI. Secrets come exclusively from
Secrets Manager via External Secrets — nothing sensitive lives in this repo.

## Ground rules

- Kafka: `auth.user.lifecycle`, `auth.email.requested`, `auth.security.audit` — outbox only.
- Auth never sends email; it emits events for the Notification service.
- Migrations are DDL + reference data only (D-005).
- Every new endpoint is authenticated unless added to the CI-enforced public list.
- Every architectural change lands in `docs/architecture/auth-decisions.md`.
