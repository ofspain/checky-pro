# crypto · T10 · Phase 12 — Specification Verification

**Task (verbatim, `tasks.md` #10):** Provider health + degraded. Track `ProviderHealth`; emit
`chain.provider.degraded` when a provider is unhealthy or repeatedly disagrees, continuing if quorum is
still achievable (R5).

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R5 — unhealthy/lagging/repeatedly-disagreeing → `chain.provider.degraded`, continue if quorum still achievable | Yes | `ProviderHealthTracker.recordUnhealthy`/`recordDisagreement` (`ProviderHealthTracker.java:86-123`); `ProviderDegradedPublisher.publish` (`ProviderDegradedPublisher.java`) | `ProviderHealthTrackerTest.shouldEmitProviderDegradedWhenAProviderIsUnhealthy` (named test) + 13 others | No | No — "continue if quorum is still achievable" is satisfied by construction: this task never gates or blocks quorum evaluation (T09, unmodified); it only tracks/reports alongside it |
| L5 — deterministic idempotency key | Yes | `ProviderDegradedPublisher.publish`'s key construction (`":degraded:" + occurredAt + ":" + UUID`) | `ProviderDegradedPublisherTest.twoPublishesWithIdenticalArgumentsProduceDifferentIdempotencyKeys` (exact regex, Phase 11) | No | No |
| L15 — module boundaries | Yes | No import of `adapter`/`observation`/`quorum` anywhere under `provider/` | `ProviderModuleBoundaryTest` (Phase 11 addition — source-scan, closes a Phase 10 gap) | No | No |
| AC1 (upsert per (chain, provider)) | Yes | `ProviderHealthTracker.fetchOrCreate` (`:133-136`) | `ProviderHealthTrackerTest.distinctProvidersOnTheSameChainAreTrackedIndependently`, `.distinctChainsTrackTheSameProviderNameIndependently` | No | No |
| AC2 (degraded emitted exactly on healthy→unhealthy) | Yes | `recordUnhealthy`'s `if (health.healthy())` gate (`:89-92`); `recordDisagreement`'s identical gate (`:104-108`) | `.recordUnhealthyOnAnAlreadyUnhealthyProviderDoesNotReemitOrResave`, `.recordHealthyNeverEmitsAnEvent` | No | No |
| AC3 (counter semantics) | Yes | `recordDisagreement`'s early-return-before-touching-the-counter when already unhealthy (`:104-108`) | `.recordDisagreementWhileAlreadyUnhealthyUpdatesTimestampButNeverTouchesTheCounterOrPublishes`, `.recordHealthyResetsTheDisagreementCounter` | No | No |
| AC4 (migration grant, no V1-V3 change) | Yes | `V4__crypto_app_provider_health_grant.sql` | `ProviderHealthRepositoryIntegrationTest.anUpdateToAnAlreadyPersistedRowSucceeds`, `.deleteStillFailsAtTheDatabaseLevel`, `.aSecondRowForTheSameChainAndProviderViolatesTheUniqueConstraint` (Phase 11) | No | No |
| AC5 (module boundaries) | Yes | Same as L15 row | `ProviderModuleBoundaryTest` | No | No |
| AC6 (idempotency) | Yes | Same as L5 row | Same as L5 row | No | No |
| AC7 (payload shape) | Yes | `ProviderDegradedPublisher.Payload(chain, provider, reason, occurredAt)` | `ProviderDegradedPublisherTest.publishBuildsTheDocumentedPayloadShape`, `.payloadSerializesToTheDocumentedJsonShapeWithIsoInstantAndEnumName` (Phase 11 — also caught a real ObjectMapper-configuration gotcha, see Amendments below) | No | No |

## Amendments (Phase 3, 10 findings; Phase 8, 8 findings; Phase 11, 12 gaps) — verification

**Phase 3 (design challenge), 9 in full + 1 partial, all verified implemented as decided:** config
prefix `themistra.crypto.provider-health.*` (`ProviderHealthProperties.java`, confirmed a genuine
factual-error fix, not a style choice); restart/cross-replica counter limitations documented
(`ProviderHealthTracker.java` class Javadoc); `DegradationReason` queryable via `chain.outbox.payload`
documented (`DegradationReason.java`); event schema file creation correctly NOT done in this task
(confirmed absent from `Files to Create` and from disk — `contracts/events/chain/` still does not
exist); `eventType` pinned; counter-while-unhealthy semantics clarified; idempotency key UUID-
strengthened; payload shape pinned (`chain`, `provider`, `reason`, `occurredAt`); `lastDisagreementAt`
historical-marker semantics documented (`ProviderHealth.java` class Javadoc).

**Phase 8 (independent review), 6 in full + 2 accepted-as-disclosed-risk, all verified:** null guards in
`ProviderHealth.create` (`ProviderHealth.java:59-67`); colon guard in `ProviderDegradedPublisher.publish`
(`ProviderDegradedPublisher.java`, `requireNoColon`); the two concurrency races (first-write INSERT,
lost-update/no-`@Version`) confirmed still disclosed-not-fixed in `ProviderHealthTracker`'s class
Javadoc, exactly as decided at the Phase 9 gate; the void-return-type suggestion confirmed NOT applied
(no public signature changed, per Phase 9's own explicit rejection).

**Phase 11 (test review), 9 full + 1 partial-via-cheaper-alternative + 1 documentation-only + 3
rejected, all verified:** the previously-missing AC5 module-boundary test now exists
(`ProviderModuleBoundaryTest.java`) — this closes a genuine Phase 10 gap, not a new amendment; the
`@Version`-absence tripwire (`ProviderHealthTest.hasNoVersionFieldYetLostUpdatesUnderConcurrentAccessAreAnAcceptedRisk`)
stands in place of a rejected concurrency test; all three rejected concurrency-test suggestions (Gaps 2,
3's literal form, 7) remain undefended risks, consistently disclosed at Phase 7, 8, and now 12.

**One notable verification finding from Phase 11 itself, not a defect:** investigating Gap 5 (payload
JSON serialization) confirmed a real, previously-latent Jackson configuration gotcha — a bare
`new ObjectMapper()` (the exact pattern `OutboxPublisherTest`, T04, already uses) throws on
`java.time.Instant`, and even `findAndRegisterModules()` alone serializes it as a numeric epoch-seconds
value, not ISO-8601, unless `WRITE_DATES_AS_TIMESTAMPS` is explicitly disabled. Production is unaffected
(Spring Boot's auto-configured `ObjectMapper` bean disables that feature by default), but this is the
first task to put a `java.time` type through `OutboxPublisher`'s payload path at all, so the gap was
previously untested. `ProviderDegradedPublisherTest.payloadSerializesToTheDocumentedJsonShapeWithIsoInstantAndEnumName`
now locks down the exact, correct shape.

## Files-to-create / Files-to-modify conformance

All seven files listed under "Files to Create" in the frozen brief exist at their exact specified paths
(`V4__crypto_app_provider_health_grant.sql`, `ProviderHealthProperties.java`, `ProviderHealth.java`,
`ProviderHealthRepository.java`, `DegradationReason.java`, `ProviderDegradedPublisher.java`,
`ProviderHealthTracker.java`). "Files to Modify" held — only `application.properties` was touched, adding
the one documented property. No file under "Files NOT to Modify" was touched: `V1`-`V3` (T02),
`common/ClockConfig.java`, `events/OutboxPublisher.java`, `events/EventTopics.java` (T04), anything
under `adapter/`, `observation/`, `quorum/` (T05-T09), `contracts/events/chain/` (still doesn't exist —
correctly left to task 23), and nothing under `spec/`.

## Required Tests conformance

All required tests from the frozen brief exist, plus the Phase 11 (Kimi)-driven additions layered on
top (all human-approved 2026-09-03): `ProviderModuleBoundaryTest` (1, closing a genuine Phase 10 gap),
plus 9 test additions/enhancements across `ProviderHealthTest`/`ProviderDegradedPublisherTest`/
`ProviderHealthTrackerTest`/`ProviderHealthRepositoryIntegrationTest`. Current suite state (last full
run, this session): 311 module tests total, 304 passing, 7 errors — all Docker-environment-unavailable
(`IllegalState: … Docker environment …`), a pre-existing, disclosed environment limitation (6
pre-existing from T02/T04/T08/T09, 1 new from this task's own
`ProviderHealthRepositoryIntegrationTest`), not a code defect. Zero genuine failures.

## Principal-engineer review

**(1) Is the task fully complete?** Yes. Every class named in the frozen brief exists, is wired together
as specified, and every acceptance criterion has direct evidence and a passing test (subject only to
the environment's lack of Docker, which blocks *execution* of this task's own one integration test, not
its existence or correctness — it compiles cleanly and mirrors the T08/T09-established, already-proven
pattern, extended with the first successful-`UPDATE` proof in this service's own test history).

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC7, see matrix above, each with
file:line evidence and test coverage.

**(3) Does it violate any LOCKED decision?** No. L5 (deterministic idempotency key) and L15 (module
boundaries) are both implemented exactly as decided and independently verified by dedicated tests
(`ProviderModuleBoundaryTest`, the idempotency-key regex test). No cross-module import violation:
`provider/` imports only `common.config.ProviderHealthProperties` and `events.OutboxPublisher`, nothing
from `adapter/`, `observation/`, or `quorum/`.

**(4) Remaining risks?**
- Two concurrency races are explicitly accepted, disclosed, and NOT fixed by this task: a first-write
  INSERT race on `(chain, provider)` (can surface as a raw `DataIntegrityViolationException`), and a
  lost-update race under concurrent read-modify-write (no `@Version` column — the correct fix would
  require a schema change beyond the frozen brief's grant-only `V4` migration). Both are extensively
  documented in `ProviderHealthTracker`'s own class Javadoc and were deliberately not test-covered with
  real concurrency tests (rejected at the Phase 11 gate as low-value confirmations of already-known,
  deliberately-unfixed limitations) — a `ProviderHealth.hasNoVersionFieldYetLostUpdatesUnderConcurrentAccessAreAnAcceptedRisk`
  tripwire test exists as the cheaper alternative for the second race.
- The in-memory disagreement counter is process-local: it resets on every restart and is not
  coordinated across replicas (design.md O5, explicitly deferred, requiring its own author approval).
  An operational, not correctness-critical, limitation — T09's quorum evaluation remains the actual
  source of truth for `AGREED`/`HELD`.
- `"unhealthy"`/`"lagging"` remain unimplemented as *detection* logic — this task only provides the
  tracking primitive (`recordUnhealthy`); no caller exists yet anywhere in this codebase to invoke it,
  consistent with every prior task's own "no real caller in this task's own scope" pattern.
- `ProviderHealthRepositoryIntegrationTest` has never actually executed in this environment (Docker
  unavailable throughout this session) — it compiles and is structurally sound, but its assertions
  (including the first-ever "`UPDATE` succeeds" proof in this service) are unverified against a real
  Postgres until Docker is available.

## Verdict

**PASS** — every requirement, LOCKED decision, and acceptance criterion for T10 is implemented with
file:line evidence and test coverage; all three review phases (3, 8, 11) were fully triaged with
reasoned accept/reject/partial dispositions, including deliberate refusals to fix two disclosed
concurrency races within this task's own narrow, non-schema-changing scope; remaining risks are
pre-existing environment limitations or explicitly accepted-by-design/deferred-to-a-later-task risks,
not defects.
