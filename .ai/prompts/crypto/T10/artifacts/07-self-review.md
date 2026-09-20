# crypto · T10 · Phase 7 — Self Review

Self-review of the Phase 6 diff (`V4__crypto_app_provider_health_grant.sql`,
`ProviderHealthProperties.java`, `ProviderHealth.java`, `ProviderHealthRepository.java`,
`DegradationReason.java`, `ProviderDegradedPublisher.java`, `ProviderHealthTracker.java`,
`application.properties`) against the frozen brief and `agents.md`. No code changed in this phase —
findings only, per the phase directive.

---

## Finding 1 — Two concurrent first-time calls for the same `(chain, provider)` can both attempt an INSERT, and the loser gets an uncaught `DataIntegrityViolationException`

**Severity:** Medium

**Evidence:** `ProviderHealthTracker.fetchOrCreate` (`ProviderHealthTracker.java:116-119`) calls
`repository.findByChainAndProvider(...)`; if empty, builds a new, unsaved `ProviderHealth` (`id=null`)
via `ProviderHealth.create(...)`. If two threads call, e.g., `recordUnhealthy` for the same `(chain,
provider)` at the same moment, before either has ever created that row, both `findByChainAndProvider`
calls can return empty, both construct a fresh entity, and both proceed to `repository.save(health)` —
the second `save` is a JPA `INSERT` (not `merge`, since `id` is still `null` at save time) that violates
`provider_health`'s own `UNIQUE (chain, provider)` constraint. The frozen brief's own Constraints
section accepts a narrow race for *duplicate degraded events* between two already-existing-row
transitions, but this is a distinct, more basic race — a genuine first-write-wins-or-crashes conflict
on entity creation itself, not merely a duplicate-event risk.

**Recommendation:** Either catch `DataIntegrityViolationException` around the insert path and retry as
an update (re-fetch, then proceed), or accept and document this as a launch-scope limitation (in
practice, the first health signal for a given `(chain, provider)` pair is unlikely to arrive from two
threads simultaneously, since it requires the very first observation of that provider to race with
itself) — but the brief should say which, rather than leaving it unaddressed.

---

## Finding 2 — The in-memory disagreement counter is not transactional and does not roll back with a failed `@Transactional` method

**Severity:** Low-Medium

**Evidence:** `ProviderHealthTracker.recordDisagreement` (`:82-106`) increments
`disagreementCounts` (a plain in-memory `ConcurrentHashMap`) *before* the surrounding `@Transactional`
method necessarily succeeds. If `repository.save` or (inside `transitionToUnhealthy`,
`:108-114`) `publisher.publish` subsequently throws and the transaction rolls back, the DB state
correctly reverts (the row stays `healthy=true`), but the counter increment already happened and is
not undone — a failed attempt still "spends" one count. In practice this self-corrects (the next
`recordDisagreement` call will very likely still be at or above threshold and will simply retry the
transition), but the counter and the persisted state can transiently disagree in a way the code doesn't
acknowledge.

**Recommendation:** Low priority given the self-correcting behavior; worth a one-line comment
acknowledging the non-transactional nature of the counter, consistent with how the class Javadoc already
discloses the counter's restart/cross-replica limitations.

---

## Finding 3 — `ProviderHealth.create`'s `now` parameter is always immediately overwritten by the tracker's very next call

**Severity:** Low

**Evidence:** In all three `ProviderHealthTracker` methods, `fetchOrCreate` (which may call
`clock.instant()` once, internally, to construct a brand-new entity via `create(chain, provider, now)`)
is always immediately followed by a call to `markHealthy`/`markUnhealthy`/`recordDisagreement` with a
*second*, separately-obtained `clock.instant()` value — which unconditionally overwrites `updatedAt`
(and, for `markHealthy`/`markUnhealthy`, also sets the field that mutator owns). The first `clock.instant()`
call's result is therefore never observable in any of this task's own call paths. Harmless today, but
`create`'s own `now` parameter effectively does nothing except transiently satisfy the `NOT NULL`
constraint on `updated_at` between construction and the guaranteed-to-follow mutator call.

**Recommendation:** Low priority; either accept as a natural consequence of `fetchOrCreate`'s
always-followed-by-a-mutator shape, or simplify `create` to not take `now` at all (leaving `updatedAt`
transiently unset in memory until the first mutator call sets it) — a minor design polish either way,
not a correctness issue.

---

## Finding 4 — `ProviderHealth.create` has no null-guards of its own

**Severity:** Low

**Evidence:** `ProviderHealth.create` (`ProviderHealth.java:59-66`) assigns `chain`/`provider`/`now`
directly with no `Objects.requireNonNull` guard. `ProviderHealthTracker`'s own public methods do guard
all three at their own entry points, so no null ever reaches `create` today — but `create` is a `public
static` factory with no documented non-null contract of its own, the same category of finding T08's own
self-review raised for `ObservationSnapshotStore`.

**Recommendation:** Low priority given the only current caller is trusted; worth a guard or Javadoc
contract if `create` is ever called from elsewhere.

---

## Finding 5 — `chain`/`provider` are concatenated into `aggregateId`/the idempotency key with a bare `:` separator, with no escaping

**Severity:** Low

**Evidence:** `ProviderDegradedPublisher.publish` (`:36-41`) builds `aggregateId = chain + ":" +
provider` and the idempotency key similarly. If either `chain` or `provider` ever contained a literal
`:` character, two distinct `(chain, provider)` pairs could theoretically produce the same `aggregateId`
string (e.g., `chain="A", provider="b:c"` vs. `chain="A:b", provider="c"`). In practice `chain` is
constrained elsewhere to `ETHEREUM|TRON` (`ProviderProperties`'s own `@Pattern`) and `provider` names
are operator-controlled config values, not user input — the same low-likelihood, low-impact category as
T08's own "colons in the S3 key" finding.

**Recommendation:** Low priority given the practical constraints on both inputs; no action needed unless
`provider` names ever become less controlled.

---

## Summary table

| # | Issue | Severity |
|---|-------|----------|
| 1 | Concurrent first-time calls can race on INSERT, uncaught constraint violation | Medium |
| 2 | In-memory counter not rolled back with a failed transaction | Low-Medium |
| 3 | `create`'s `now` parameter always immediately overwritten | Low |
| 4 | No defensive null-checks in `ProviderHealth.create` | Low |
| 5 | Unescaped `:` separator in `aggregateId`/idempotency key | Low |

(End of self-review.)
