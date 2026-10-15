# payment · T14 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**)

No code in this artifact — documentation only, per this phase's own instructions.

**Branching note:** same established practice as every prior task this session — executed
directly on the long-running `spec/service-specs-and-ai-framework` branch, not a fresh per-task
branch off `main`. `main` itself was never touched and stays deployable. The attribution trailer
below uses this session's own current identity (Claude Sonnet 5), not the generic placeholder
text baked into this prompt template's own header.

## Commit title

`payment T14: idempotency and ordering integration tests (R13, R14, O6)`

## Commit message

```
payment T14: idempotency and ordering integration tests (R13, R14, O6)

First task in the pipeline to run against a real Postgres Testcontainer and
a real, already-running Kafka broker instead of mocks, directly proving R13
(duplicate chain.tx.* delivery is a no-op), R14 (stale/out-of-order events
are ignored and the ignore recorded), and the consumer-side-testable half
of O6 (per-payment ordering) - Kafka's own cross-topic ordering guarantee
is the broker's property, out of this service's own control to test.

R14's only realistically end-to-end-testable scenarios are cross-event-type:
every chain.tx.* schema's own idempotency key is fixed per
(chain, txHash, eventtype), so a same-event-type staleness case (e.g. a
lower confirmation count) is dedupe-blocked by R13 before ever reaching the
staleness-comparison logic in a real test. Covered the three reachable
cases: finalized for an unknown payment, reorged after FINALIZED, and
confirmed after FINALIZED (the real cross-topic race).

Follows the established notification-service precedent (Testcontainers for
Postgres only, real Kafka via the shared services/auth/compose.local.yaml
broker on localhost:9094, KafkaTemplate + Awaitility, no Thread.sleep) -
confirmed by research to be the only precedent in this codebase, since no
service combines Testcontainers-Postgres with a Testcontainers-Kafka image.

Surfaced and fixed, outside this task's own direct scope but blocking it
from running at all: two production cron expressions in 5-field Unix
format, which Spring's CronExpression parser rejects (6-field, seconds-
first) - meaning the real application had apparently never been able to
start as a live Spring Boot process anywhere in this pipeline before this
fix (already committed separately, a88bc44a).

Two independent review rounds (Kimi, Phases 8 and 11) strengthened the test:
producer sends now wait on their own future instead of fire-and-forget;
negative (no-state-change) assertions now first confirm the stimulus event
was actually consumed, closing a vacuous-pass risk; AC3 now also asserts
the invoice reaches PAID and that address_poisoning_flag survives; the
three R14 scenarios were split into independent methods for diagnostic
granularity; a fail-fast broker-reachability check replaces a confusing
40s timeout when the shared Kafka broker isn't running. One Kimi finding
(Phase 8, High) was rejected on verified factual grounds: it claimed the
external-broker design contradicted the Phase 4 approval, when Phase 4 had
in fact explicitly chosen it over a Testcontainers Kafka image for the
identical reason Kimi's own Phase 3 review had already raised and had
already been corrected on.

ChainEventConsumerIdempotencyAndOrderingIntegrationTest: 5/5 passing,
confirmed stable across 3 consecutive runs. Full suite: 297/297 passing
(292 pre-task baseline + 5 new).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Tests (this task is explicitly test-only; no production code touched):**
- `services/payment/src/test/java/com/themistra/payment/consumer/ChainEventConsumerIdempotencyAndOrderingIntegrationTest.java`
  (new) — 5 tests: `shouldDedupeDuplicateChainEventsByEventKey` (named, R13),
  `shouldIgnoreFinalizedForAnUnknownPayment`, `shouldIgnoreReorgedForAnAlreadyFinalizedPayment`,
  `shouldIgnoreOutOfOrderOrStaleChainEvents` (named, R14), `shouldHandleTheFullForwardSequenceAndReachFinalized`
  (O6).

**Production code (fixed earlier this session, before T14 began, to make the stack runnable at
all — already committed separately in `a88bc44a` and `277a31b6`, listed here only for context,
not part of this commit):**
- `services/payment/src/main/resources/application.properties` — two cron expressions widened
  to 6-field Spring format.
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceRepositoryIntegrationTest.java`
  — `TransactionTemplate` fix for a pre-existing T08 defect.
- `services/payment/src/test/java/com/themistra/payment/SchemaBaselineMigrationIntegrationTest.java`
  — updated stale expectations for T11's own already-correct `V7`/`V8` migrations.

## Summary

T14 is the pipeline's first task to prove its requirements against a real, live stack rather
than mocks — and in doing so incidentally proved the stack could actually run at all, surfacing
a latent cron-format bug that would have crashed `payment-service` on any real deployment. The
test itself required inventing a new idempotency/ordering test pattern for this service (no
Kafka+Postgres Testcontainers precedent existed anywhere in the monorepo), adapted from
notification-service's own external-broker convention rather than from a payment-service-local
precedent. Two real review rounds meaningfully strengthened the test beyond its first working
draft, and one high-severity review finding was correctly rejected on the historical record
rather than accepted at face value.

## Testing performed

- `mvn -o test-compile`: clean.
- `mvn -o test -Dtest=ChainEventConsumerIdempotencyAndOrderingIntegrationTest`: **5/5 passing**,
  confirmed stable across 3 consecutive runs after the Phase 11 addendum (12+ consecutive passes
  total across this task's Phases 6/7/9/10/11 combined, across three successive shapes of the
  test class).
- `mvn -o test` (full suite, Docker up): **297/297 passing** (292 pre-task baseline + 5 new).
- Required `docker compose -f services/auth/compose.local.yaml up -d` (Postgres + Kafka) running
  throughout — the first task in this pipeline with zero non-Docker-testable fallback, flagged
  to the user up front.

## Specification references

- **Task:** T14 — Idempotency & ordering tests (`spec/payment-service/tasks.md`).
- **Requirement IDs:** R13, R14 (scoped); O6 (scoped, design.md); L5 (continuity, directly
  proven here for the first time against a real broker); L1, L12, L14 (continuity, unaffected).
- **LOCKED decisions:** none scoped inline; all continuity decisions reconfirmed in Phase 12's
  own traceability matrix.
- **Design-challenge decision (Phase 4, reaffirmed at Phase 9):** real external Kafka broker
  over a Testcontainers Kafka image, matching the only existing precedent in this codebase
  (notification-service).
