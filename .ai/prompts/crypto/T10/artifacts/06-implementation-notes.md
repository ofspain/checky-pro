# crypto · T10 · Phase 6 — Implementation Notes

## What changed

Seven new files, one modified file — exactly as planned (Phase 5), no file the frozen brief did not
authorize was touched, and no existing Java file was modified.

- **`V4__crypto_app_provider_health_grant.sql`** — grants `crypto_app` `INSERT, SELECT, UPDATE` (no
  `DELETE`) on `chain.provider_health`, closing the Phase 0-confirmed gap in `V2`. `V1`-`V3` untouched.
- **`ProviderHealthProperties.java`** — `@ConfigurationProperties(prefix =
  "themistra.crypto.provider-health")`, one `@Positive int disagreementThreshold` field, mirroring
  `FinalityProperties`'s exact style.
- **`ProviderHealth.java`** — JPA entity, update-in-place, three named mutators (`markHealthy`,
  `markUnhealthy`, `recordDisagreement`), no raw setters, package-private no-arg constructor, public
  static `create(...)`.
- **`ProviderHealthRepository.java`** — package-private `JpaRepository<ProviderHealth, Long>` plus
  `findByChainAndProvider`.
- **`DegradationReason.java`** — enum `UNHEALTHY, LAGGING, REPEATED_DISAGREEMENT`.
- **`ProviderDegradedPublisher.java`** — wraps `OutboxPublisher.publish(...)`; `aggregateType =
  "provider"`, `aggregateId = "{chain}:{provider}"`, `eventType = "chain.provider.degraded"`,
  `idempotencyKey = "{chain}:{provider}:degraded:{occurredAt}:{UUID}"`; nested `record Payload(chain,
  provider, reason, occurredAt)`.
- **`ProviderHealthTracker.java`** — the coordinator (`@Transactional` on all three public methods,
  the first coordinator in this service needing it): `recordHealthy`, `recordUnhealthy`,
  `recordDisagreement`, backed by a `ConcurrentHashMap<ProviderKey, AtomicInteger>` consecutive-
  disagreement counter. Exactly one `repository.save` call per invocation on every code path (no
  redundant double-save), matching the Phase 5 plan's own refinement over the Phase 2 draft's
  two-saves-on-transition sketch.
- **`application.properties`** — added `themistra.crypto.provider-health.disagreement-threshold=3`.

## Mapping to plan and acceptance criteria

| AC | Satisfied by |
|---|---|
| AC1 (upsert per (chain, provider)) | `ProviderHealthTracker.fetchOrCreate` + `repository.save` on every public method |
| AC2 (degraded emitted exactly on healthy→unhealthy) | `recordUnhealthy`'s `if (health.healthy())` gate; `recordDisagreement`'s identical gate before incrementing/transitioning |
| AC3 (counter semantics) | `recordDisagreement`'s early return (`repository.save` + `return`) when `!health.healthy()`, before the counter is ever touched; `recordHealthy`'s `disagreementCounts.remove(...)` |
| AC4 (migration grant, no V1-V3 change) | `V4__crypto_app_provider_health_grant.sql` |
| AC5 (module boundaries) | `provider/` imports only `com.themistra.crypto.common.config.ProviderHealthProperties` and `com.themistra.crypto.events.OutboxPublisher` — no import from `adapter/`, `observation/`, `quorum/` anywhere in the new files |
| AC6 (idempotency key) | `ProviderDegradedPublisher.publish`'s key construction, UUID-suffixed |
| AC7 (payload shape) | `ProviderDegradedPublisher.Payload(chain, provider, reason, occurredAt)` |

## Deviations forced by reality

None. Unlike T09 (which hit an unanticipated package-private-converter accessibility conflict during
implementation), this task's frozen brief and implementation plan translated into code without
surprises — `mvn -pl services/crypto compile` and `mvn -pl services/crypto test-compile` both succeeded
on the first attempt, with zero warnings beyond pre-existing ones. The one deliberate refinement over
the Phase 2 draft (single `repository.save` per code path in `recordDisagreement`, rather than a
save-then-possibly-save-again sequence) was already anticipated and specified in the Phase 5 plan
itself, not discovered during coding — so it is a plan refinement, not a Phase 6 deviation.
