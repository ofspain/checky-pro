# crypto · T15 · Phase 0 — Repository Understanding

## 1. Architecture summary

`crypto-service` is a Spring Boot 3.5.4 / Java 21 module. Persistence is Postgres via Flyway-owned DDL
(`db/migration/`) plus JPA entities validated against that schema (`ddl-auto=validate`, never
mutating). Outbox pattern publishes to Kafka with idempotency keys `chain:txhash:eventtype` (L5).
Security is OAuth2 resource-server only. This task is the service's **first HTTP API surface** — every
prior task (T01-T14) built internal/adapter/persistence logic only; no `@RestController` exists
anywhere in this module yet.

## 2. Existing code this task touches

**Already exists, fully ready for this task:**
- **`watches` and `chain_cursors` tables** — already defined in `V1__chain_baseline.sql:7-21,63-70`
  (the VERBATIM baseline, `design.md` §4c). `watches` has `watch_id UUID NOT NULL UNIQUE` (no DB
  default — must be generated in application code), `invoice_uuid`, `chain`, `address`,
  `token_contract_address`, `expected_amount NUMERIC(78,0)`, `status` (CHECK'd to
  `REGISTERED`/`UNREGISTERED`/`EXPIRED`), `expires_at`, `created_at`, `unregistered_at`, and a partial
  index `idx_watches_chain_address ... WHERE status = 'REGISTERED'`. `chain_cursors` has `chain`,
  nullable `watch_id`, `last_block`, nullable `last_finalized_block`, `updated_at` — no unique
  constraint of its own.
- **Security is already fully wired for this exact endpoint.** `ResourceServerConfig.java:54` already
  maps `/internal/v1/**` to `hasAuthority("SCOPE_internal.crypto:write")` — its own Javadoc
  (`ResourceServerConfig.java:32-34`) explicitly names `DELETE /internal/v1/watches/{watchId}` as the
  reason the matcher is `/internal/v1/**` and not `/internal/v1/*`. **No new security configuration is
  needed** — this was pre-built (T03) anticipating this task.
- **RFC 9457 problem+json for 401/403** already exists (`ResourceServerConfig`'s hand-rolled
  `writeProblemJson`, a security-filter-level concern that runs before Spring MVC dispatch — separate
  from, and not reusable for, controller/domain-level errors this task will need, e.g. 404 on an
  unknown `watchId`).
- **VERBATIM internal API contract** (`design.md` §4c, lines 66-70):
  ```
  POST /internal/v1/watches      (scope internal.crypto:write)
    body: { invoiceUuid, chain, address, tokenContractAddress, expectedAmount, expiresAt }
    200:  { watchId, status: "REGISTERED" }
  DELETE /internal/v1/watches/{watchId}   -> 204
  ```
  Note the success status is `200`, not `201`, despite this being a creation endpoint — this is the
  spec's own literal text, not an assumption.

**Does NOT yet exist — this task's actual scope:**
- No `watch/` package anywhere in `services/crypto/src/main/java`.
- No grant migration for `watches`/`chain_cursors` to `crypto_app`. Confirmed via inspection of every
  existing migration (`V2` grants only `observations`/`attestations`/`quorum_decisions`; `V4` added
  `provider_health`; `V5` added `token_allowlist`) — `watches` and `chain_cursors` have **no grant at
  all** yet. This is the same class of gap T10 (`provider_health`) and T11 (`token_allowlist`) each
  found and closed with a new `V<n>__crypto_app_*_grant.sql` migration; very likely this task's own
  analog.
- No domain exception type, no `@RestControllerAdvice` for this module's own errors (404 on unknown
  `watchId`, validation failures), no request/response DTOs.

## 3. Established patterns to follow

- **Update-in-place entity precedent (T10's `ProviderHealth`).** `Watch` is this task's second
  update-in-place entity (after `ProviderHealth`): register (`INSERT`) then unregister
  (`UPDATE status, unregistered_at`) — needs named mutator methods, not raw setters, per T10's own
  convention.
- **DAO-owned business identifier decoupled from the surrogate PK.** `watches.watch_id` (the public
  `watchId` the API returns) is a separate `UUID` column from the `BIGINT IDENTITY` primary key — no DB
  default generates it, so `WatchService` must generate it explicitly (e.g. `UUID.randomUUID()`), not
  rely on `@GeneratedValue`.
- **Per-module `@RestControllerAdvice` + `ProblemDetail`, not a hand-rolled map** — checked
  `services/auth`'s own established pattern (the only sibling service with controllers;
  `services/auth/.../apikey/ApiKeyExceptionHandler.java`): one `@RestControllerAdvice` per feature
  module, mapping a small set of domain exceptions 1:1 to Spring's built-in `ProblemDetail`
  (`org.springframework.http.ProblemDetail`), each with a fixed status/type/title and no
  cause-varying detail. This is a different, controller-dispatch-level mechanism from
  `ResourceServerConfig`'s security-filter-level hand-rolled map (which runs *before* Spring MVC even
  engages, so it cannot be reused for domain 404s) — no actual conflict, just two separate layers each
  already precedented somewhere in this codebase.
- **Hand-written request/response DTOs, despite `agents.md`'s "Models are generated from `contracts/` —
  never hand-written" platform rule.** No OpenAPI-generator plugin exists anywhere in this multi-module
  build (confirmed via `pom.xml` search, root and both services), and `contracts/api/crypto-internal.yaml`
  does not exist in this repository at all (confirmed absent, same finding as T14). `services/auth`
  itself — the one service in this repo with a real, existing `contracts/api/auth.yaml` — still uses
  hand-written `dto.*` record classes for its controllers, not generated models. The literal spec rule
  is currently unsatisfiable platform-wide; the actual established precedent is hand-written DTOs. This
  mirrors T11's own resolution pattern (follow real precedent over a spec rule nothing in the codebase
  currently implements) and should be flagged, not silently followed-or-ignored, at Phase 2.
- **DB-role least-privilege, owner-vs-grantee split** — `crypto_app` never owns tables; grants are
  additive migrations (T02 Kimi Finding 2, carried through V2/V4/V5).
- **Money as `NUMERIC`/`BigDecimal`, decimal-string on the wire** — `expected_amount` will need this
  treatment in both the entity and the request/response DTOs.
- **Config-driven, not hardcoded, wherever the spec leaves room** — mirrors T11's allowlist-seeding
  redesign; not obviously applicable here since register/unregister needs no seed data, but worth
  checking in Phase 2 for anything like a default watch TTL or similar.

## 4. Testing conventions

- Pure-logic pieces get plain JUnit tests (no Spring context) — established across T09, T11-T14.
- Entity/repository persistence gets a Testcontainers (Postgres) integration test, following T10's
  `ProviderHealthRepositoryIntegrationTest` and T11's `TokenAllowlistRepositoryIntegrationTest`
  precedent, including the migration-grant integration test convention
  (`ChainBaselineMigrationIntegrationTest`'s `UNGRANTED_TABLES` list, which will need
  `watches`/`chain_cursors` removed from it once a grant migration is added, mirroring T10/T11's own
  precedent exactly).
- A REST controller is new territory for this service — `services/auth`'s own controller test
  conventions (`@WebMvcTest` or full `@SpringBootTest` with `MockMvc`, plus `spring-security-test`,
  already a crypto-service test dependency per `pom.xml`) are the only in-repo precedent to check in
  Phase 2, since no crypto-service controller test exists yet to follow directly.
- Fixed `Clock` convention applies wherever this task computes "now" (e.g. `created_at`,
  `unregistered_at`, and any expiry comparison against `expires_at`).

## 5. Known gaps / unknowns

- **How `WatchService` obtains the initial `ChainCursor.last_block` value at registration time is
  unclear.** `ChainAdapter` has no plain "get current block number" method — `getFinalityStatus`
  requires an existing, mined transaction hash, which a brand-new watch registration does not have.
  I do not know yet whether Phase 1/2 should scope `ChainCursor` creation as part of this task at all
  (the task statement says "Persist `Watch` and its `ChainCursor`," implying yes) with some
  to-be-determined initial value, or whether the *initial* cursor value genuinely requires new
  `ChainAdapter` surface this task would then also need to add (out of this task's named scope,
  `ChainAdapter` is frozen per every prior task's own convention) — flagged for Phase 1/2, not resolved
  here.
- **No `Watcher`/`WatcherRegistry` exists** (task 16, per `design.md`'s own `watch/` package listing)
  — this task's own scope is explicitly narrower (`WatchService` register/unregister +
  `WatchController` only, per the task statement's literal wording), so nothing this task builds
  should assume a running watcher exists yet.
- **No `ReorgDetector`, no `chain.tx.*` event emission anywhere in this codebase yet** — `ChainCursor`
  is data-only for this task; reorg walk-back (L6) is a future task's job.
- **Whether `expiresAt` validation (e.g. rejecting a past timestamp) is expected at registration time**
  is not stated anywhere in R18's literal text — a genuine Phase 1/2 design question, not yet resolved.
- **Whether `invoiceUuid` needs any cross-checking** — the platform rule "no cross-schema queries"
  (agents.md) strongly suggests it is stored opaquely with no validation against a Payment Service
  table this service cannot see; not yet confirmed against `requirements.md`'s full text (Phase 1's job).
- **`chain`/`address`/`tokenContractAddress` request-field validation** — this task's own `chain` field
  is very likely constrained to `ETHEREUM`/`TRON` like every other chain-scoped config in this service
  (`FinalityProperties`, `ProviderProperties`), but whether `address`/`tokenContractAddress` should be
  validated via the existing `AddressValidator`/`TokenValidator` (T11/T12) at registration time is not
  stated in the task's own scope line and is a genuine Phase 1/2 design question — those classes have
  no caller anywhere in this codebase yet, and R18's own text says only "register a watch... and begin
  watching the address," not "validate the address."
