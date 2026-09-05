<!-- MODEL: Kimi 2.7 -- Phase 8 (Independent Code Review) for crypto / T10. -->

# crypto / T10 / Phase 8 -- Independent Review Findings

**Scope:** Independent adversarial review of the implemented provider-health code and the Phase 7 self-review.

**Directive:** Do not rewrite code. Return findings as **Issue / Evidence / Recommendation / Confidence.**

---

## Issue 1 -- Two concurrent first-time calls for the same (chain, provider) can race on INSERT and surface an uncaught DataIntegrityViolationException

**Evidence:**
- `ProviderHealthTracker.fetchOrCreate` calls `repository.findByChainAndProvider`; if empty, it builds a new `ProviderHealth` via `ProviderHealth.create` with `id=null`.
- If two threads invoke any tracker method for the same `(chain, provider)` before any row exists, both `findByChainAndProvider` calls can return empty.
- Both threads then call `repository.save(health)` with a new entity; the second `save` issues an INSERT that violates `provider_health`'s `UNIQUE (chain, provider)` constraint.
- The method is `@Transactional`, but the pre-flight read and the insert are not atomic, so the transaction rolls back with a raw `DataIntegrityViolationException` rather than a named, domain-level error.

**Recommendation:**
Catch `DataIntegrityViolationException` around the save path and retry by re-fetching the now-existing row, then re-applying the intended mutation. Alternatively, document this as an accepted launch-scope limitation.

**Confidence:** High

---

## Issue 2 -- No optimistic locking; concurrent updates to the same row can silently overwrite each other

**Evidence:**
- `ProviderHealth` has no `@Version` column or other optimistic-locking mechanism.
- `ProviderHealthTracker` reads the row, mutates the in-memory entity, and saves it within a `@Transactional` method.
- Under default `READ COMMITTED` isolation, two concurrent transactions can both read the same row, both decide to transition, and the later commit can overwrite the earlier one without any conflict detection.
- Example: Thread A reads `healthy=true`, calls `markUnhealthy`, saves, and publishes a degraded event. Thread B reads the same `healthy=true` before A commits, calls `recordHealthy`, and saves. B's commit can overwrite A's, leaving the row `healthy=true` even though a degraded event was emitted.
- This is a different, more severe failure mode than the duplicate-event race the brief already accepts.

**Recommendation:**
Add an `@Version` column to `provider_health` and handle `OptimisticLockingFailureException` by re-fetching and retrying. If a schema change is out of scope, document the lost-update risk explicitly.

**Confidence:** High

---

## Issue 3 -- The in-memory disagreement counter is incremented before the transaction commits and is not rolled back on failure

**Evidence:**
- `ProviderHealthTracker.recordDisagreement` increments `disagreementCounts` before `repository.save` and `publisher.publish` run inside the `@Transactional` boundary.
- If `save` or `publish` throws and the transaction rolls back, the DB/outbox state reverts, but the counter increment persists.
- The counter self-corrects on the next call, but the code does not acknowledge this transient inconsistency.

**Recommendation:**
Add a class-level comment documenting that the counter is intentionally non-transactional. For stronger consistency, move the increment into `transitionToUnhealthy` after the transition is guaranteed, or persist the counter.

**Confidence:** High

---

## Issue 4 -- ProviderHealth.create accepts null arguments with no validation

**Evidence:**
- `ProviderHealth.create` assigns `chain`, `provider`, and `now` directly without `Objects.requireNonNull`.
- The tracker's public methods guard these values, but `create` is itself a public static factory that could be called from elsewhere.
- A null `now` would satisfy the in-memory entity but fail at the DB layer with a less clear error.

**Recommendation:**
Add `Objects.requireNonNull` guards for `chain`, `provider`, and `now` in `ProviderHealth.create`, mirroring the discipline used in tracker methods.

**Confidence:** High

---

## Issue 5 -- chain/provider are concatenated into aggregateId and idempotency key with an unescaped colon separator

**Evidence:**
- `ProviderDegradedPublisher.publish` builds `aggregateId = chain + ':' + provider` and `idempotencyKey = chain + ':' + provider + ':degraded:' + occurredAt + ':' + UUID`.
- If `chain` or `provider` ever contained a literal `:`, two distinct `(chain, provider)` pairs could produce the same string.
- In practice `chain` is constrained to `ETHEREUM|TRON` and `provider` is operator-controlled, so this is low-likelihood. The impact is partition-key collision and idempotency-key ambiguity.

**Recommendation:**
Either escape or validate `provider` names to exclude `:`, or document that provider names must not contain `:`. A cheap guard is an `IllegalArgumentException` in `ProviderDegradedPublisher.publish` if either input contains `:`.

**Confidence:** Low

---

## Issue 6 -- ProviderHealth.create's now parameter is immediately overwritten by the tracker's mutator call

**Evidence:**
- In all three tracker methods, `fetchOrCreate` obtains a `clock.instant()` to pass to `ProviderHealth.create`, then immediately calls a mutator with a second `clock.instant()` that overwrites `updatedAt`.
- The `create` parameter's only effect is to transiently satisfy `updated_at NOT NULL` between construction and the mutator call.
- This is harmless but slightly confusing and suggests the factory API could be simpler.

**Recommendation:**
Low priority. Consider removing `now` from `ProviderHealth.create` and having the constructor leave `updatedAt` null until the first mutator call, or document the transient nature of the parameter.

**Confidence:** Low

---

## Issue 7 -- ProviderHealthTracker methods return void, giving callers no visibility into whether a transition or event occurred

**Evidence:**
- `recordHealthy`, `recordUnhealthy`, and `recordDisagreement` all return `void`.
- A caller that invokes `recordUnhealthy` cannot tell whether the provider was already unhealthy (no event) or just transitioned (event emitted).
- Future tasks that need to react to health transitions will have to query the repository separately.

**Recommendation:**
Consider returning a small result object or the current `ProviderHealth`. If out of scope, document that callers must query `ProviderHealthRepository` to observe the resulting state.

**Confidence:** Low-Medium

---

## Issue 8 -- The event eventType value is not contractually pinned

**Evidence:**
- `ProviderDegradedPublisher.publish` passes the string `chain.provider.degraded` as the `eventType` to `OutboxPublisher`.
- `EventTopics` maps aggregate type `provider` to topic `chain.provider.degraded`, so the event type and topic name coincide.
- The brief and code do not explain whether this coincidence is intentional or whether consumers should expect `eventType` to equal the topic name. A future refactor could change one without the other.

**Recommendation:**
Document the chosen `eventType` value in `ProviderDegradedPublisher`'s Javadoc and ensure it is reflected in the future `provider-degraded.v1.schema.json` contract.

**Confidence:** Low-Medium

---

## Summary table

| # | Issue | Severity | Confidence |
|---|-------|----------|------------|
| 1 | First-write INSERT race | Medium-High | High |
| 2 | No optimistic locking / lost updates | High | High |
| 3 | In-memory counter not transactional | Low-Medium | High |
| 4 | ProviderHealth.create null guards missing | Low | High |
| 5 | Unescaped colon in aggregate/idempotency key | Low | Low |
| 6 | create's now parameter always overwritten | Low | High |
| 7 | Tracker methods return void | Low-Medium | Medium |
| 8 | eventType value not contractually documented | Low-Medium | Medium |

(End of independent review.)
