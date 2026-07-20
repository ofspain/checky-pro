# 7. Tasks — ordered execution plan

Execute in order. Each task leaves the module buildable and the test suite green. No real email is sent in CI — the outbound transport is a capturing fake.

## Foundation

1. **Service skeleton & POM.** Add `services/notification` to the root `<modules>`. Create `pom.xml` mirroring `services/auth` (web, validation, resource-server, data-jpa, flyway, postgres, spring-kafka, actuator, prometheus, testcontainers, archunit, awaitility) plus the chosen email-transport client (per O2/Q2). No producer/outbox dependency at launch unless O5 is taken.
2. **Schema V1.** Add `V1__notifications_baseline.sql` (design §4c) and seed the launch templates; run `mvn -pl services/notification flyway:migrate` against local Docker Compose Postgres. Grant the service DB role INSERT+SELECT-only on `delivery_log`.
3. **Config & resource server.** Validated `@ConfigurationProperties` for email, link base URL, retry, and in-app transport (design §4c). Wire JWT resource-server validation for the in-app surface (L8); `PublicEndpoints` allows only actuator. Startup fails on missing config in non-local profiles.

## Consumers & idempotency

4. **Idempotency ledger.** Add `ProcessedEvent` + repository and a dedupe helper that records the event key in the same transaction as the delivery-log append (L1, R7/R8). Unit-test the dedupe.
5. **Contact projection (Q1/O1).** Consume `auth.user.lifecycle` and read email from `auth.email.requested` to populate `contact_projection` (blocker for email delivery). Confirm approach against Q1 before finalizing.
6. **Auth event consumer.** Implement `AuthEventConsumer` for `auth.email.requested` (verify/reset) and `auth.user.lifecycle` (registered) → the corresponding templates (R1, R2, R6). Idempotent per Task 4.
7. **Payment event consumer.** Implement `PaymentEventConsumer` for `payments.invoice.created`, `payment.seen`, `payment.finalized`, `receipt.issued` → templates, resolving recipients per the §4c matrix (R3, R4, R5; confirm Q7).

## Preferences, rendering, delivery

8. **Preference resolver.** Implement `ChannelPreference` + `PreferenceResolver` honouring opt-outs and applying the documented default when none is set (L6, R9/R10/R18). Enforce that SECURITY-category email cannot be disabled.
9. **Template renderer.** Implement `Template` + `TemplateRenderer` populating the versioned template per event+channel; record the template version for the delivery log (L9, R14). Wire link base URL per Q4.
10. **Secret-safe rendering & logging.** Add `SecretSafeLogging` redaction and assert no token/secret/full-key leaks into bodies or logs (L4, R15).
11. **Delivery orchestrator + log.** Implement `DeliveryOrchestrator`: resolve prefs → render → dispatch on each enabled channel → append a `delivery_log` row per attempt with outcome (L3, R11). Record `SUPPRESSED` for opted-out channels (R10).
12. **Email channel (O2/Q2).** Implement `EmailChannel` behind `NotificationChannel` against the capturing fake in tests and the real transport in non-test profiles.

## In-app channel & retry

13. **In-app channel + store.** Implement `InappChannel` persisting `inapp_notifications` and pushing to connected streams. Implement `InappStreamController` (SSE/websocket per O3/Q3, recipient-scoped — R16) and `InappReadController` for unread (R17). Both reject cross-account access (L8).
14. **Bounded retry.** Implement `DeliveryRetry` + `RetryScheduler` (ShedLock): transient failures mark `FAILED` and schedule bounded backoff (R12); exhaustion records `DEAD_LETTERED` (R13, L7). Confirm policy via Q6.

## Contracts & hardening

15. **Consumed-contract tests.** Deserialize each consumed payload against the authored `contracts/events/{auth,payments}/*` schemas; a mismatch fails a contract test, not production (R19). Mirror the auth `UserLifecycleEventPayloadContractTest` pattern.
16. **ArchUnit/module boundaries.** Enforce no cross-module entity imports (L11) and the public-endpoint allowlist (L8). Assert the delivery path makes no synchronous cross-service HTTP call (L2).

## Final verification

17. **End-to-end integration test.** Testcontainers Postgres + Kafka + capturing transport: produce `auth.email.requested(verify)` → exactly one verification email; redeliver the same event → no second email (R8); produce `payments.receipt.issued` → both parties notified with a receipt link; opt a recipient out of PAYMENT email → that channel is `SUPPRESSED`, in-app still delivered.
18. **Run full suite.** `mvn -pl services/notification verify` must pass; Docker image builds from repo root.
19. **Dispute-log check.** Verify the delivery log is append-only and every attempt is reconstructable from the source event key — the "was the merchant notified?" evidence (`ARCHITECTURE.md` §3.5).
20. **Bump spec status.** Once §11 questions (esp. Q1, Q2, Q3, Q4, Q7) are closed and tests pass, change this spec from `DRAFT` to `READY FOR IMPL` and version to `0.2`.
