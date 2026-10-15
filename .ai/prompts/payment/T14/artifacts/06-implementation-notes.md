# payment · T14 · Phase 6 — Implementation Notes

Consumes: `artifacts/05-implementation-plan.md`

Test-only task — per this phase's own instructions, writing the real test here is correct (not
deferred to Phase 10), since T14's own task statement is itself entirely test infrastructure.

## What changed

- **`ChainEventConsumerIdempotencyAndOrderingIntegrationTest.java`** (new) — the one class
  planned, with all three Phase 4-approved AC methods: `shouldDedupeDuplicateChainEventsByEventKey`
  (AC1), `shouldIgnoreOutOfOrderOrStaleChainEvents` (AC2, three scenarios), and
  `shouldHandleTheFullForwardSequenceAndReachFinalized` (AC3).
- **`application.properties`** (one line each, two properties) — fixed a genuine, severe,
  pre-existing production bug this test is the first thing in the entire pipeline to actually
  trigger (see Finding 1 below). Approved directly by the user before applying.

## Mapping to acceptance criteria (frozen brief, Phase 4)

All three ACs are satisfied and **actually run green against the real stack** — confirmed
stable across 3 consecutive runs, not just "compiles."

- **AC1 (R13)** — the identical `chain.tx.seen` message, redelivered, leaves exactly one
  `Payment` row; `processedEventRepository.existsById(...)` confirms the real dedupe row.
- **AC2 (R14)** — all three cross-topic scenarios pass: `finalized` for an unknown payment
  creates no row; `reorged` after `FINALIZED` leaves state unchanged; `confirmed` after
  `FINALIZED` (the real cross-topic race, Finding 6) leaves state unchanged.
- **AC3 (O6)** — the full `seen → confirmed → finalized` sequence reaches `FINALIZED` with the
  correct `amount`/`from_address`/`to_address` persisted.

## Deviations from the plan, disclosed — all forced by reality, found only by actually running this against the live stack

### 1. A severe, pre-existing production bug: `@Scheduled` cron strings had 5 fields, not 6

**The single most significant finding of this task.** Booting the real `PaymentServiceApplication`
context for the first time ever in this pipeline (every prior test either mocked collaborators or
used a narrow custom config) immediately failed: `InvoiceExpirySweepJob`'s own
`@Scheduled(cron = "${themistra.payment.invoice.expiry-sweep.cron}")` resolved to
`"0 */5 * * *"` — standard 5-field Unix cron syntax. Spring's own `CronExpression` parser
requires 6 fields (seconds first) and throws `IllegalStateException` at bean-creation time, not
a runtime scheduling failure. **This means the real application has never been able to start,
in this entire pipeline, as a real Spring Boot process** — every prior task's own testing was
either unit-mocked or a Testcontainers slice narrow enough to never trigger
`ScheduledAnnotationBeanPostProcessor`'s own validation. Put directly to the user; **decided:
fix the production config now** — `themistra.payment.ledger.anchor.cron` (identical 5-field
format, no consumer yet, T21 deferred — latent, not yet crashing anything) and
`themistra.payment.invoice.expiry-sweep.cron` both corrected to 6-field form (`"0 0 3 * * *"`,
`"0 */5 * * * *"`), preserving each one's own original intent exactly. Confirmed no existing
test asserts the old literal string value (`ConfigPropertiesBindingTest` binds its own separate
literal test values; `InvoiceExpirySweepJobTest` asserts the annotation's own unresolved
placeholder text) — nothing else needed updating.

### 2. A full, unscoped `@SpringBootTest` also scans `archtestfixtures`' own deliberately-invalid `@Entity` classes

Self-caught, fixed before this note was written. `ArchitectureTest`'s own `RogueUnmappedEntity`
(deliberately has no `@Id`, exercising a negative ArchUnit path) is picked up by Hibernate's
default entity scan (rooted at `PaymentServiceApplication`'s own package), which fails context
startup entirely. No existing test had ever hit this either, for the same reason as Finding 1.
Fixed with an explicit `@EntityScan` on the test class, scoped to the five real feature-module
packages (matching `ArchitectureTest`'s own `FEATURE_MODULES` list exactly) — excludes
`archtestfixtures` without needing any other change.

### 3. `ResourceServerConfig`'s own `securityFilterChain` needs a `JwtDecoder`

Not anticipated in the plan (this task has no HTTP/security surface at all), but a full,
unscoped context still instantiates it. Added `@MockBean JwtDecoder jwtDecoder` rather than
excluding the whole security auto-configuration, keeping the test's own context shape as close
to the real application's as reasonably possible.

### 4. Phase 4 Finding 3's own fix does not actually disable `@Scheduled` task execution

Confirmed via the real Kafka producer logs: `OutboxRelay` is still actively forwarding
`invoice.created`/`payment.seen`/`payment.finalized` rows to the real broker during this test's
own run, despite `spring.task.scheduling.enabled=false`. Root cause: `PaymentServiceApplication`
declares `@EnableScheduling` **explicitly** (T04) — this registers
`ScheduledAnnotationBeanPostProcessor` unconditionally, independent of Boot's own conditional
`TaskSchedulingAutoConfiguration` the `spring.task.scheduling.enabled` property actually governs.
**Kimi's own Finding 3 mechanism claim does not hold for this codebase's own explicit
`@EnableScheduling` usage** — corrected here, not silently left as a false claim. **Not fixed
further**: the resulting noise is confirmed harmless for this task's own assertions specifically
(every seeded invoice's own `expiresAt` is comfortably far in the future relative to the test's
own ~25-second runtime, so `InvoiceExpirySweepJob` never matches any of them regardless of
whether it's still running; `OutboxRelay`'s own forwarded events are outbound, never consumed by
this task's own listener-side assertions) — the property is left in place as a harmless,
documented no-op rather than investigating a real fix for a cosmetic concern outside this task's
own scope.

## Verification

`mvn -o compile`, `mvn -o test-compile`: clean. `mvn -o test -Dtest=ChainEventConsumerIdempotencyAndOrderingIntegrationTest`:
**3/3 passing, confirmed stable across 3 consecutive real runs** against the live Postgres
Testcontainers instance and the real local Kafka broker. `mvn -o test` (full suite): **295/295
passing** (292 baseline + 3 new) — the real application config fix did not break anything else.
