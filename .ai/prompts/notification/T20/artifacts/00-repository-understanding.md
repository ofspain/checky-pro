# notification · T20 · Phase 0 — Repository Understanding (resumed)

**Task:** `tasks.md` task 20 (`:41`): "Bump spec status. Once §11 questions (esp. Q1, Q2, Q3, Q4, Q7) are closed and tests pass, change this spec from `DRAFT` to `READY FOR IMPL` and version to `0.2`."

This artifact contains no code; it is documentation only.

**Revision note.** This file replaces the "hold until payment-service unblocks" Phase 0 (`35e87281` / `12edbe6a`), which is still in git history. That blocker has cleared:
- `spec/payment-service` is `READY FOR IMPL` v0.2 (payment T29).
- Notification T07 (`PaymentEventConsumer`) is done, with Phase 12 PASS (`760ab041`, 2026-10-19).

## 1. Architecture summary

`services/notification` is a Spring Boot / Java 21, consume-only service.

**Persistence.** Postgres schema `notifications`, Flyway `V1`–`V14`:
- contact projection
- preferences
- versioned templates
- `processed_events`
- `delivery_log` (append-only)
- `inapp_notifications`
- `delivery_retry`
- ShedLock
- T07's `invoice_routes` / `payment_routes`

**Consumers.**
- `AuthEventConsumer`: `auth.email.requested`, `auth.user.lifecycle`.
- `PaymentEventConsumer`: the four `payments.*` topics.

Both dedupe through `IdempotencyGuard` and then hand off to `NotificationDispatcher` / `DeliveryOrchestrator`.

**Delivery.** `PreferenceResolver` → `TemplateRenderer` → `EmailChannel` (SES / fake) and `InAppChannel` (SSE + persisted store) → `delivery_log`, then bounded retry (`RetryScheduler`, ShedLock).

**Security.** OAuth2 resource server on the in-app stream and read endpoints. `SecretSafeLogging` handles redaction.

The service publishes no events: there is no outbox and no producer call in `src/main`.

## 2. What this task touches

**Spec files.** This task is the one place in the pipeline where `spec/` is edited. Its whole job is the spec's status.

`spec/notification-service/package.md`:

| Location | Current state |
|---|---|
| Header, Version (`:6`) | `0.1` |
| Header, Status (`:9`) | `DRAFT` |
| §9 checklist (`:91`–`:103`) | All unticked |
| §11 (`:122`–`:131`) | Q1–Q7 still phrased as open; only Q8 is marked Resolved |

**Possibly `design.md`.** Its O-items are partly marked: O3 is resolved at `:21`; O1, O2, O4 and O5 aren't. The §4c event→recipient matrix (`:35`–`:38`) says "merchant + payer", and L2 (`:6`) says "the only permitted projection read is the recipient-contact projection". T07 deliberately deviated from both (frozen brief D1, D2), and FU-T07-3 assigned their alignment to this task.

**Code.** None expected. Possibly a guard test, as in payment T29's `T01SkeletonRegressionTest` additions.

## 3. Established patterns

The payment T29 precedent is the same kind of task:
- The header bump is pinned by a test that reads only the header table.
- §9 items are ticked with test citations, or left `[ ] **Partial —**` with a reason.
- A guard test checks that every test named in the checklist exists.
- Spec edits are limited to what the task authorizes.

## 4. Testing conventions

- **Unit tests:** JUnit + Mockito, with a fixed `Clock`.
- **Integration tests:** Testcontainers Postgres + Kafka.
- **ArchUnit:** plain-canary-only (the Surefire `@ArchTest` bug).
- **Baseline today:** `mvn -o -pl services/notification verify` → **413/413** (T07 Phase 11, 2026-10-19).

## 5. Gate check — the state of each §11 question, against the code

| Q | Spec text | What the code shows | Closed? |
|---|---|---|---|
| Q1 recipient resolution | Open | Option (c), a contact projection fed by auth events (T05, `V1` + the auth `email` field added in commit `64557d3`) | Yes in code. Not recorded. |
| Q2 email transport | Open | Amazon SES (`SesEmailTransport`), chosen at T03, built at T12. The `fake` transport is the default outside production. | Yes in code. Not recorded. |
| Q3 in-app transport | Open in `package.md` | SSE, resolved in `design.md` O3 (`:21`) and in `contracts/api/notifications.yaml` | Yes. `package.md` is stale. |
| Q4 link base URL | Open | Shared config `themistra.notification.link.base-url=${AUTH_EMAIL_LINK_BASE_URL:}` (`application.properties:71`). `TemplateRenderer` builds the links. The path convention is still marked provisional in code. The receipt link was removed at T07 (D3, no receipt read endpoint, FU-T07-2). The invoice route is not confirmed by the frontend. | **Partly.** The base-URL source is decided; the link paths are provisional; there is no receipt link. |
| Q5 delivery-status events | Open (recommended: log-only) | Log-only. No publisher exists. | Yes, de facto (the recommended default). |
| Q6 retry policy | Open | 5 attempts, backoff from 30 s capped at 3600 s (`application.properties:77`–`:79`), dead-letter as a `DEAD_LETTERED` row in `delivery_log` (T14) | Yes in code. Not recorded. |
| Q7 event→recipient matrix | Open | **Auth half:** as §4c, except `account.suspended`, which is deliberately unmapped (`DeliveryOrchestratorTest` pins its exclusion). **Payment half:** merchant only, with no payer (T07 D1), through an event-fed routing projection (D2). `chain.provider.degraded` isn't consumed. | **Decided, but it deviates from §4c.** |
| Q8 agents file | Resolved | — | Yes |

**Tests pass:** yes, 413/413.

## 6. Known gaps

1. **R15's named test doesn't exist under its §8 name.** `shouldNotLeakSecretsOrTokensIntoRenderedMessagesOrLogs` is missing. R15 is covered in substance by T10 (`SecretSafeLoggingTest`, `SecretSafeLoggingIntegrationTest`, a static log-scan test), by T12's recipient-PII guards and by T07's log test. But §9 item 1 says every §3 criterion has *a passing named test from §8*. **I do not know** whether to add the literal name or record it as covered under other names.
2. **L11's named test is an ArchUnit rule field, not a method.** `shouldPreventCrossModuleEntityImports` is a `private static final ArchRule` (`ArchitectureTest.java:85`), run by the canary `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` (`:150`). That was the T16 workaround for the Surefire bug. It is enforced, just not as a method with that exact name.
3. **T17's payment half was never built.** T17 deferred "produce `payments.receipt.issued` → both parties notified with a receipt link; opt a recipient out of PAYMENT email → `SUPPRESSED`, in-app still delivered".
   - The receipt-link and both-parties parts can't be built (D1, D3).
   - The PAYMENT opt-out → `SUPPRESSED` part **is** buildable now. Preference suppression is tested generically (T08/T11), but no end-to-end payment-event suppression test exists.
   - **I do not know** whether T20 should require it.
4. **The spec text contradicts the shipped behaviour.**
   - R3–R5 say "both parties" / "with the receipt link".
   - §4c says "merchant + payer".
   - L2's literal wording forbids the routing projection.
   - Bumping to `READY FOR IMPL` without aligning these would certify text the code knowingly doesn't meet.
   - **I do not know** how far T20 may edit `requirements.md` / `design.md` (the task names only "this spec's status").
5. **Q4 isn't fully closed** (see the table). The task names it as a required closure.
