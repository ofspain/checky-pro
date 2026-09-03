<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge) for crypto · T10. -->

# crypto · T10 · Phase 3 — Design Challenge Findings

**Scope:** Adversarial review of the Phase 2 Task Implementation Brief for provider health + degraded events (T10) before it is frozen.

**Directive:** Do not redesign and do not implement. Surface hidden assumptions, ambiguous rules, missing edge cases, and conflicts with locked decisions / `spec/crypto-service/agents.md`. Each finding: **Issue · Severity · Evidence · Recommended brief amendment.**

---

## Issue 1 — New configuration key uses `crypto.provider-health.*`, breaking the established `themistra.crypto.*` namespace convention

**Severity:** High

**Evidence:**
- Existing `@ConfigurationProperties` records in this service use the `themistra.crypto.*` prefix:
  - `ProviderProperties` → `themistra.crypto.providers`
  - `SnapshotProperties` → `themistra.crypto.snapshot`
  - `ScreeningProperties` → `themistra.crypto.screening`
  - `KmsProperties` → `themistra.crypto.kms`
- The brief proposes `crypto.provider-health.disagreement-threshold` for the new `ProviderHealthProperties` record.
- This inconsistency will confuse operators and break configuration discovery; it also risks collision with other Spring/Spring Security properties that use the top-level `crypto.*` namespace.

**Recommended brief amendment:**
Change the property prefix to `themistra.crypto.provider-health.disagreement-threshold`, matching the existing convention. Update the `application.properties` entry accordingly.

---

## Issue 2 — The disagreement counter is process-local and resets on every restart

**Severity:** Medium-High

**Evidence:**
- The brief places the consecutive-disagreement counter in an in-memory `ConcurrentHashMap`/`AtomicInteger` structure inside `ProviderHealthTracker`.
- A pod restart (rolling update, crash, eviction) wipes the counter.
- A provider that disagrees `threshold-1` times, triggers a restart, then disagrees once more would not trip the degraded threshold — even though the persisted `lastDisagreementAt` shows recent disagreements.
- The brief acknowledges multi-replica coordination is deferred, but it does not address the single-replica restart case.

**Recommended brief amendment:**
Either (a) persist the consecutive-disagreement count in `provider_health` (a schema change), (b) recompute it from recent `observations`/`quorum_decisions` on startup, or (c) explicitly document that the counter is ephemeral and that a provider must disagree `threshold` times within a single process lifetime to trigger degradation.

---

## Issue 3 — `DegradationReason` is not persisted, only emitted in the event

**Severity:** Medium

**Evidence:**
- `provider_health` has no column for the reason a provider became unhealthy.
- `DegradationReason` is carried only in the `chain.provider.degraded` event payload.
- If the outbox event has not yet been relayed to Kafka, or if ops is querying the database directly, they cannot tell whether the provider is unhealthy due to RPC failures, lag, or repeated disagreements.

**Recommended brief amendment:**
Add a `degradation_reason VARCHAR(...)` column to `provider_health` (a new migration, since `V1` is frozen) and populate it on the healthy→unhealthy transition. Alternatively, document that the reason is observable only via the emitted event and that ops must query the outbox/event stream for it.

---

## Issue 4 — No event contract/schema is defined for `chain.provider.degraded`

**Severity:** Medium-High

**Evidence:**
- `package.md` §2 lists `contracts/events/chain/*.schema.json` as in-scope deliverables.
- R28 states: "WHERE `contracts/api/crypto-internal.yaml` and `contracts/events/chain/*` are authored, THEN internal responses and emitted events SHALL conform to them."
- The brief describes the event's aggregate type, aggregate id, idempotency key, and a payload containing `DegradationReason`, but it never specifies a JSON Schema contract file or the exact payload shape.
- Without a schema, consumers cannot validate the event, and contract tests (task 23) cannot be written.

**Recommended brief amendment:**
Add `contracts/events/chain/provider-degraded.v1.schema.json` to the Files to Create list, defining required fields (`chain`, `provider`, `reason`, `occurredAt`) and their types. Reference `contracts/events/chain/tx-finalized.v1.schema.json` as the envelope pattern.

---

## Issue 5 — The `eventType` passed to `OutboxPublisher` is not specified

**Severity:** Medium

**Evidence:**
- `OutboxPublisher.publish` requires both `aggregateType` and `eventType` parameters.
- The brief specifies `aggregateType = "provider"` and the topic mapping, but it never states what `eventType` `ProviderDegradedPublisher` will pass.
- `EventTopics.forAggregateType` maps aggregate type to topic; the event type is stored in the outbox row and may be used by consumers for routing/filtering.

**Recommended brief amendment:**
Specify the `eventType` value (e.g., `"provider-degraded"`) and ensure it is consistent with the schema file name and consumer expectations.

---

## Issue 6 — `recordDisagreement` counter behavior while the provider is already unhealthy is ambiguous

**Severity:** Medium

**Evidence:**
- The brief says `recordDisagreement` increments the in-memory counter and, once it reaches the threshold, delegates to the same not-already-unhealthy-gated transition as `recordUnhealthy(..., REPEATED_DISAGREEMENT)`.
- If the provider is already unhealthy, the not-already-unhealthy gate blocks the transition.
- The brief does not say whether the counter is reset in that blocked branch or whether it keeps incrementing indefinitely until `recordHealthy` is called.
- An unbounded counter could theoretically overflow an `AtomicInteger` (practically impossible in normal operation, but the semantics matter for tests and reasoning).

**Recommended brief amendment:**
Clarify that `recordDisagreement` increments the counter only while the provider is currently healthy; if the provider is already unhealthy, the call updates `lastDisagreementAt` but does not touch the counter. This makes the counter meaningful for the healthy→unhealthy transition only.

---

## Issue 7 — Idempotency key collision is possible if two transitions share the same instant

**Severity:** Low-Medium

**Evidence:**
- The idempotency key is `{chain}:{provider}:degraded:{transitionInstant}`.
- `transitionInstant` is derived from `clock.instant()`, which typically has nanosecond precision.
- If two degradation transitions for the same provider occur within the same nanosecond (or if the clock implementation truncates to milliseconds), the keys collide and the second publish fails with a `DataIntegrityViolationException`.
- The brief states the timestamp is computed once and reused, which prevents self-collision for a single transition, but not collision between distinct transitions.

**Recommended brief amendment:**
Include a nanosecond component or a per-process monotonic sequence number in the key, or document the accepted collision risk and the expected caller behavior (treat the duplicate-key exception as a no-op for the same logical transition).

---

## Issue 8 — No cross-replica agreement on disagreement count despite multiple replicas running the service

**Severity:** Medium

**Evidence:**
- The brief explicitly defers multi-replica watcher assignment to design.md O5.
- However, `ProviderHealthTracker`'s in-memory counter means each replica maintains its own disagreement count for the same provider.
- A provider that disagrees once on replica A and twice on replica B would not trip the threshold on either replica, even though the aggregate count is 3.
- The brief treats this as an operational/observability signal, but R5 frames degraded events as a real signal for ops.

**Recommended brief amendment:**
Document that the disagreement threshold is per-replica for launch and that a future task (O5) will coordinate cross-replica health state if a global threshold is required. If this is unacceptable for launch, consider persisting the count or using a shared cache.

---

## Issue 9 — No explicit `occurredAt` field in the event payload

**Severity:** Low-Medium

**Evidence:**
- The brief describes the idempotency key including `transitionInstant` but does not describe the event payload fields.
- Other `chain.*` event schemas (e.g., `tx-finalized.v1.schema.json`) include `occurredAt`.
- Consumers likely need a stable, semantically-meaningful timestamp for the degradation event.

**Recommended brief amendment:**
Include `occurredAt` (the transition instant) in the `provider-degraded` event payload and schema.

---

## Issue 10 — `recordHealthy` does not clear `lastDisagreementAt`

**Severity:** Low

**Evidence:**
- The brief says `markHealthy` sets `healthy=true`, `lastOkAt`, and `updatedAt`.
- It does not clear `lastDisagreementAt`.
- A recovered provider will still show the timestamp of its last disagreement, which may be confusing for dashboards that interpret `lastDisagreementAt != null` as "currently disagreeing."

**Recommended brief amendment:**
Either (a) clear `lastDisagreementAt` on `markHealthy`, or (b) document that `lastDisagreementAt` is a historical field representing the most recent disagreement ever observed, not the current state, and that `healthy` is the authoritative current-state flag.

---

## Summary table

| # | Issue | Severity | Recommended brief amendment |
|---|-------|----------|------------------------------|
| 1 | Property prefix inconsistent with convention | High | Use `themistra.crypto.provider-health.*` |
| 2 | Disagreement counter is process-local | Medium-High | Persist or document ephemeral nature |
| 3 | `DegradationReason` not persisted | Medium | Add reason column or document event-only |
| 4 | No event schema contract | Medium-High | Create `provider-degraded.v1.schema.json` |
| 5 | `eventType` not specified | Medium | Pin the event type value |
| 6 | Counter behavior while already unhealthy ambiguous | Medium | Clarify counter semantics |
| 7 | Idempotency key can collide on same instant | Low-Medium | Add sequence/nanoseconds or document risk |
| 8 | Cross-replica disagreement count not coordinated | Medium | Document per-replica scope |
| 9 | No `occurredAt` in payload | Low-Medium | Add `occurredAt` to schema |
| 10 | `lastDisagreementAt` not cleared on recovery | Low | Clear it or document semantics |

(End of design challenge.)
