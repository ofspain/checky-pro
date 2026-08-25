<!-- MODEL: Claude Sonnet — Phase 0 (Repository Understanding). -->

# crypto · T01 · Phase 0 — Repository Understanding

Task statement: update `SECURITY-THREAT-MODEL.md` for threats #1–#6; add `services/crypto` to the
root `<modules>`; create `pom.xml` mirroring `services/auth` plus web3j (EVM), the Tron gRPC client,
and the AWS KMS SDK (KMS only, scoped to the `attest` module); enable Java 21 virtual threads.

---

## 1. Architecture summary

The Crypto Service is the **only** component in this platform that talks to blockchains and the
**only** holder of the path to `kms:Sign` on the attestation key (`package.md` §0). Its entire
reason to exist is arbitrating truth about on-chain events without ever trusting a single source:
every verification fact (tx existence, amount, token, confirmations, finality) is fetched from N
independent RPC providers and treated as true only on 2-of-3 agreement (L1); disagreement is held,
never resolved in anyone's favor (L2); every provider's raw answer is logged verbatim before any
decision is made (L3, the "defensible core" per `design.md` §5). Finality is a per-chain policy
object, not a global constant (L4) — Ethereum requires the beacon-chain `finalized` checkpoint, Tron
requires a solidified block. Reorgs are first-class, walking the watcher cursor backward (L6). Token
identity is `<chain, contractAddress>` only, matched against a signed allowlist — never a symbol
(L7). `POST /internal/v1/attest` is the sole path to KMS signing, gated on quorum + finality +
screening (L10–L12), enforced both by ArchUnit (package ban) and IAM (only this service's role may
call `kms:Sign`).

Twelve planned feature modules under `com.themistra.crypto` (package-by-feature, matching auth's own
convention): `adapter`, `provider`, `quorum`, `finality`, `watch`, `reorg`, `token`, `screening`,
`attest`, `observation`, `events`, `common` (`design.md` §6). One Postgres schema (`chain`), eight
tables in the first migration (`watches`, `observations`, `quorum_decisions`, `provider_health`,
`chain_cursors`, `token_allowlist`, `screening_results`, `attestations`, plus `outbox`/`shedlock`).
Kafka outbox for every emitted `chain.*` event, same pattern as auth-service's own outbox (D-009/D-018
there), but implemented fresh here — this service does not depend on auth-service's code (services
never depend on each other's source; only `libs/` and `contracts/`, per `agents.md`).

## 2. Existing code this task touches

**`services/crypto/` currently contains only**: `README.md` (a one-paragraph service description)
and `sidecars/README.md` (an empty, reserved directory for future TypeScript chain sidecars — none
ship at launch, per `package.md` §2). No Java source, no `pom.xml`, no `src/` tree exists yet. This
task is a genuine greenfield start for this service, not an extension of anything already built.

**Root `pom.xml`** (`/pom.xml`) currently lists exactly one module: `services/auth`. This task adds
`services/crypto` as the second entry. Confirmed by direct read: the parent is
`org.springframework.boot:spring-boot-starter-parent:3.5.4`, `java.version`/
`maven.compiler.release` are both `21`, and `spring-boot-maven-plugin` is the only
plugin-management entry — all inherited automatically by any new child module, nothing to
re-declare.

**`libs/java/{outbox,kafka-core,security-core}`** are empty scaffolds (`.gitkeep` only, no code) —
confirmed by direct listing. Auth-service's own outbox implementation deliberately stayed local to
that service rather than populating `libs/java/outbox` (its own D-018: "extraction to
`libs/java/outbox` deferred... building a shared library against a single consumer risks guessing at
an abstraction shape"). With a second Java service about to exist, this is the first point where
that rule-of-three question becomes live — **not this task's decision to make** (T01 is skeleton/POM
only), but worth flagging: crypto-service's own outbox (§4c's `outbox` table + `EventTopics`
mapping) is specified as its own local implementation too, mirroring auth's pattern, not as a
`libs/java/outbox` dependency.

**`SECURITY-THREAT-MODEL.md`** (repo root) is a genuine stub today — confirmed by direct read: 8
threats listed, all with `Status: designed`, none yet marked implemented/verified. Its own header
states plainly: "must be completed before the first line of crypto-service code" and "every feature
PR either updates this document or states why no update is needed." Threats #1–#6 (the ones this
task's own statement names) map directly to this service's core mechanisms: #1 rogue RPC provider →
quorum (L1); #2 fake USDT contract → signed token allowlist (L7); #3 reorg after "confirmed" →
per-chain finality + reorg-safe cursors (L4, L6); #4 stolen server credentials → KMS-only signing,
key never leaves KMS (L11); #5 insider alters historical verification → the append-only observation
log (L3) + S3 snapshot (mentioned but not one of the six named in this task's own statement — #5 is
present in the table but the task statement explicitly scopes to "#1–#6", which includes #5); #6
address poisoning → prefix/suffix similarity flagging (L9). Threats #7 (webhook spoofing) and #8
(merchant account takeover) belong to Payment/Auth respectively, not this service — consistent with
the task statement's own "#1–#6" scoping excluding them.

**Auth-service's `pom.xml`** (`services/auth/pom.xml`, read in full across this session's earlier
work) is the literal template this task's own statement says to mirror. Its dependency shape:
`spring-boot-starter-web`, `-validation`, `-oauth2-authorization-server`, `-oauth2-resource-server`
(+ `spring-security-oauth2-resource-server` explicitly), `-data-jpa`; `flyway-core` +
`flyway-database-postgresql`; `postgresql` driver; `spring-kafka`; **`software.amazon.awssdk:kms`
(already present in auth's own pom, for `MfaSeedEncryption`'s narrow D-025 KMS exception)**;
`-actuator`; `micrometer-registry-prometheus`; `shedlock-spring` + `-provider-jdbc-template`;
`bucket4j_jdk17-core`; test-scope: `-test`, `spring-security-test`, `-testcontainers`, Testcontainers
`postgresql`/`kafka`/`junit-jupiter`, `archunit-junit5`, `awaitility`. **This means the "AWS KMS SDK"
half of this task's own instruction is largely already satisfied by mirroring auth's pom as-is** —
the genuinely new dependencies this task must add beyond a straight mirror are **web3j** (EVM/
Ethereum client library) and a **Tron gRPC client** (TronGrid or `java-tron`'s gRPC stubs, per
`design.md` §6's `tron/TronAdapter.java` comment). Not every auth-service dependency necessarily
belongs in crypto's pom without judgment — e.g. `spring-boot-starter-oauth2-authorization-server`
is auth-specific (crypto-service is a resource server validating auth's tokens, per L13/`agents.md`,
not an issuer itself) — a Phase 1/5 design question, not decided here.

## 3. Established patterns to follow

- **Package-by-feature, ArchUnit-enforced module boundaries** — identical convention to auth-service
  (`com.themistra.auth.*` → `com.themistra.crypto.*`), confirmed by `agents.md`'s own explicit
  wording ("mirroring the auth service") and `design.md` L15.
- **Validated `@ConfigurationProperties`, flat `application.properties`, fail-fast on missing config
  in non-local profiles** — the exact convention this session spent all of T38 confirming auth-service
  applies uniformly; `agents.md` states the identical rule for crypto-service.
- **Flyway DDL-only migrations, JPA for simple find/save** — same as auth; `V1__chain_baseline.sql`
  is fully specified verbatim in `design.md` §4c (not this task's own file to write — that's T02).
- **Transactional outbox, `EventTopics` aggregate→topic mapping** — same shape as auth's `events`
  module, fully specified verbatim in `design.md` §4c (T04's own scope, not T01's).
- **RFC 9457 `application/problem+json` errors, exhaustive CI-enforced public-endpoint allowlist** —
  identical convention; crypto's own allowlist is narrower still (actuator + one well-known path
  only — no public write endpoints at all, unlike auth's registration/reset endpoints).
- **One genuinely new pattern this service introduces that auth-service never needed**: Java 21
  virtual threads for the watcher layer (`design.md` §6, `watch/Watcher.java`) — this task's own
  statement explicitly says "enable Java 21 virtual threads," which for a Spring Boot 3.5.4/Java 21
  app is a `spring.threads.virtual.enabled=true` property plus (per `agents.md`'s own language
  callout) using them specifically for the long-running per-address watcher subscriptions, not
  request-handling threads generally.

## 4. Testing conventions

Unit (plain JUnit, fixed `Clock`, **scripted fake `ChainAdapter`s** — never a real provider call, in
tests or CI, per `agents.md` and `package.md` §8's own opening line) → ArchUnit + contract →
integration (Testcontainers: Postgres + Kafka, fake providers). This mirrors auth-service's own
tier structure exactly, with one crypto-specific addition: the fake-provider-adapter discipline is a
named, explicit rule here (auth-service has no external-API-dependent equivalent at this strictness
— its closest analogue, the HIBP breach-check call, is a real network call in its own unit tests'
fail-open path, not a universally-scripted fake).

## 5. Known gaps / unknowns

- **`services/crypto` has no `src/main/java` tree yet** — confirmed directly, not assumed. This
  task's own scope (skeleton + POM) is exactly what creates the first of it; no existing code this
  task could accidentally regress.
- **Several `design.md` OPEN decisions (O1–O6) and `package.md` §11 open questions (Q1–Q7) block
  real deployment but not this task specifically** — e.g. Q1 (which 3 real RPC providers per chain)
  and Q7 (KMS key algorithm) are explicitly marked as blockers for *real* deployment, not for
  fake-provider tests or for T01's own skeleton/POM work. Not this task's job to resolve; noted so a
  later phase doesn't mistake T01 for the task that should propose them.
- **I do not know the exact web3j version or Tron gRPC client artifact coordinates to pin** — neither
  `design.md` nor `agents.md` names a specific version for either; Phase 1/5 will need to resolve
  current stable coordinates the same way T31 (auth-service, Bucket4j) once caught a wrong artifact-ID
  guess by verifying against the actual Maven Central listing before trusting a remembered version
  number — the same discipline applies here for two libraries this session has not touched before.
- **I do not know whether `spring-boot-starter-oauth2-authorization-server` belongs in crypto's
  pom** — crypto-service validates auth-issued tokens (a resource server, per R27/`agents.md`) but
  never issues its own; including the full SAS starter (as opposed to just
  `spring-boot-starter-oauth2-resource-server`) would pull in issuer-side machinery this service
  never uses. Flagged for Phase 1/5, not decided here.

---

**Phase 0 complete — repository understanding written.** Proceed to Phase 1 (Specification
Extraction) on approval.
