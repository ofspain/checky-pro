# payment · T04 · Phase 3 — Design Challenge Findings

Artifact: `.ai/prompts/payment/T04/artifacts/03-design-challenge.md`
Consumes: `.ai/prompts/payment/T04/artifacts/02-task-implementation-brief.md`
Authoritative standing rules: `spec/payment-service/agents.md`, `spec/payment-service/design.md` §4c
Scoped to T04 only. Findings only — no redesign, no implementation. Produced directly (no
external Kimi artifact landed on disk this round), held to the same adversarial standard used
throughout this pipeline.

---

### 1. No `Clock` bean exists anywhere in `services/payment` yet — the TIB's own Dependencies section omits it

- **Issue:** `OutboxPublisher`/`OutboxRelay` both need an injectable `Clock` (for `createdAt`/
  `publishedAt`), per `agents.md`'s own testing convention ("use `java.time` with an injectable
  `Clock`"). Verified directly: `find services/payment/src/main -iname "*Clock*"` returns
  nothing. `services/crypto`'s own `ClockConfig` (a one-bean `@Configuration` class,
  `Clock.systemUTC()`) is the real, already-built precedent — but the TIB's own "Dependencies"
  and "Files to Create" sections never mention it.
- **Severity:** High — the code as planned would not compile; `OutboxPublisher`'s constructor
  has nowhere to get a `Clock` from.
- **Evidence:**
  - `02-task-implementation-brief.md` Dependencies, Files to Create (no `Clock`/`ClockConfig`
    anywhere)
  - `services/crypto/src/main/java/com/themistra/crypto/common/ClockConfig.java` (the real,
    minimal template)
- **Recommended brief amendment:** add `services/payment/src/main/java/com/themistra/payment/
  common/ClockConfig.java` to Files to Create, mirroring crypto's own exactly (one `@Bean Clock
  clock() { return Clock.systemUTC(); }`).

---

### 2. No protection against a duplicate `OutboxEvent` row if a caller invokes `publish(...)` twice for the same logical event

- **Issue:** Unlike `crypto-service`'s own `OutboxPublisher` (a `UNIQUE idempotency_key` column
  catches a double-publish as a `DataIntegrityViolationException`), payment's own `outbox` table
  has no uniqueness beyond its surrogate `id` (Phase 0's own confirmed finding) — and that
  schema is immutable (T02). Nothing in this task's own scope catches an application bug that
  calls `publish(...)` twice for the same real business event (e.g. a retried method, a
  double-dispatch in some future task's own code).
- **Severity:** Medium — not exploitable by *this* task (no call site exists yet), but every
  later task that calls `OutboxPublisher.publish(...)` inherits this gap with no guardrail at
  this layer. Each of the four named events (`invoice.created`, `payment.seen`,
  `payment.finalized`, `receipt.issued`) is designed to fire exactly once per aggregate, so a
  duplicate would be a real correctness bug, not a harmless retry.
- **Evidence:**
  - `00-repository-understanding.md`'s own finding (no `idempotency_key` column, confirmed
    against `V1__payments_baseline.sql`)
  - `services/crypto/src/main/java/com/themistra/crypto/events/OutboxEvent.java:43`
    (`@Column(... unique = true)` on crypto's own `idempotency_key`)
- **Recommended brief amendment:** no schema change is possible in this task (immutable
  migration). Document this explicitly as a known, accepted limitation in `OutboxPublisher`'s own
  class Javadoc — the real protection against a duplicate publish must come from each future
  call site's own correctness (a state-machine transition that only ever fires once), not from
  this layer. Not a blocker for T04 itself, since nothing calls `publish` yet; worth a named,
  visible caveat so no later task assumes a dedup guarantee this layer doesn't actually provide.

---

### 3. Positive confirmation: `OutboxPublisher`'s 4-parameter signature (no idempotency key) is correctly justified

- **Issue:** Not a finding — positive confirmation. Re-verified the TIB's own reasoning against
  both the real schema and `agents.md`'s own precise wording (idempotency is scoped to
  *consumed* events via `processed_events`, not published ones) — the narrower signature is
  correct, not an oversight relative to crypto's own 5-parameter version.
- **Recommendation:** None.

---

### 4. Positive confirmation: deferring the Testcontainers-Kafka integration test is consistent with this task's own named scope

- **Issue:** Not a finding — positive confirmation. Task 4's own statement names exactly one
  test. Deferring a fuller "event reaches real Kafka" proof matches the same discipline already
  applied in T01 (ShedLock/jackson-yaml deferral) and T03 (property scoping).
- **Recommendation:** None.
