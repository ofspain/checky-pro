# Feature Spec: Notification Service — Phase 1

| Field | Value |
|---|---|
| Spec ID | `NOTIF-PHASE1` |
| Version | `0.1` |
| Author (senior/owner) | `<name>` |
| Implementer | `TBD` |
| Status | `DRAFT` |
| Target repo / service | `services/notification` |
| Skills to load | `spec-authoring`, `code-review` |
| Standing rules | [`agents.md`](agents.md) in this directory is authoritative for `services/notification` (distilled from `ARCHITECTURE.md`, `docs/service-languages.pdf`, the ADRs, and the sibling `spec/auth-service`). This spec references it and does not restate or override it except where §4a says so explicitly. |

## 0. TL;DR

The Notification Service is the platform's fan-out layer: it is a purely **idempotent Kafka consumer** that turns domain events (`invoice.created`, `payment.seen`, `payment.finalized`, `receipt.issued`, `auth.user.lifecycle`, `auth.email.requested`) into user-facing messages over **email and in-app** at launch, resolving each recipient's channel preferences and recording every delivery attempt in a **dispute-grade delivery log** ("was the merchant actually notified?"). It owns the `notifications` schema. It never initiates domain state and duplicate Kafka delivery must never double-send.

## 1. Context & why now

Notifications are how the product's value becomes visible: a merchant learns their invoice was paid, a payer gets a receipt link, a user gets their email-verification link. `ARCHITECTURE.md` §3.5 defines the service; the auth service's design (`spec/auth-service` and `services/auth/docs/architecture/target-design.md` §9, §12) explicitly delegates *sending email* to this service — Auth only emits `auth.email.requested` (verify/reset) and expects Notification to deliver. So this service is a hard dependency of the end-to-end registration flow, not just payment updates.

It is deliberately lean (`ARCHITECTURE.md` §1 principle 4: "lean where it can be retrofitted"): a Kafka consumer with templates and a delivery log needs no second runtime (`docs/service-languages.pdf` §2). But two things are load-bearing from day one and hard to retrofit: **idempotency** (Kafka is at-least-once; a double-charge-of-attention erodes trust) and the **delivery log** (needed later for "did the merchant get notified?" disputes, `ARCHITECTURE.md` §3.5 and Phase 3 dispute resolution in `new_features.md`). Build order places it in weeks 7–10 (`ARCHITECTURE.md` §10), after the events it consumes are defined. Nothing exists yet beyond the README; this spec builds the launch scope.

## 2. Scope

**In scope**

- `consumer` module: idempotent Kafka listeners for the launch event set, deduping on event key.
- `preference` module: per-account channel preferences (which channels, opt-outs).
- `template` module: versioned message templates per event type and channel.
- `delivery` module: the append-only, dispute-grade delivery log and delivery orchestration (attempt, outcome, retry state).
- `channel` module: the email channel and the in-app channel (SSE/websocket) at launch, behind a common `NotificationChannel` interface so webhooks (HMAC) and push slot in later.
- `events` module: consumer offset/idempotency plumbing (this service mostly consumes; any outbound delivery-status events are optional, see O5).
- Contract artifacts: this service **consumes** existing contracts under `contracts/events/{auth,payments,chain?}/`; it authors no new event it publishes at launch unless O5 is taken.
- Supporting code: validated `@ConfigurationProperties`, RFC 9457 errors for the small in-app read API, resource-server JWT validation for the in-app stream, ArchUnit module-boundary tests.

**Explicitly out of scope**

- **Initiating any domain state.** This service only reacts to events; it never creates invoices, accounts, or receipts.
- **Merchant webhooks and mobile push** — explicitly "Next", not launch (`ARCHITECTURE.md` §3.5). The channel interface must not preclude HMAC-signed webhooks, but they are not built here.
- **Composing the *content* of chain facts** — it renders what events carry; it does not call the Crypto or Payment services to enrich a message beyond what the event provides (no synchronous cross-service read on the delivery path).
- **Deciding *whether* an event should exist** — e.g. it does not re-verify a payment; if `payment.finalized` arrived, it notifies.
- **The email-link *values*** — the base URL/path for verification and receipt links comes from configuration/event payload (ties to auth Q4), not invented here.
- Phase 2+ intelligence, dispute narratives, reputation (`new_features.md`) — later consumers of the same events.

## 3. Requirements — acceptance criteria (EARS)

See [`requirements.md`](requirements.md).

## 4. Design — how to build it

See [`design.md`](design.md).

## 5. Data model & schema changes

See [`design.md`](design.md#5-data-model--schema-changes).

## 6. Package & file map

See [`design.md`](design.md#6-package--file-map).

## 7. Tasks — ordered execution plan

See [`tasks.md`](tasks.md).

## 8. Test plan — named tests

Integration tests use Testcontainers (Postgres + Kafka) and a fake outbound transport (captured emails / captured in-app pushes) — no real email is sent in CI. Idempotency and preference logic are unit-tested with a fixed `Clock`.

- `shouldSendVerificationEmailOnAuthEmailRequestedVerify` → R1
- `shouldSendPasswordResetEmailOnAuthEmailRequestedReset` → R2
- `shouldNotifyBothPartiesOnInvoiceCreated` → R3
- `shouldNotifyOnPaymentSeen` → R4
- `shouldNotifyWithReceiptLinkOnReceiptIssued` → R5
- `shouldWelcomeUserOnUserRegistered` → R6
- `shouldDedupeDuplicateEventDeliveryByEventKey` → R7
- `shouldNotDoubleSendWhenSameEventRedelivered` → R8
- `shouldResolveChannelPreferencesPerRecipient` → R9
- `shouldSuppressChannelWhenRecipientOptedOut` → R10
- `shouldRecordEveryDeliveryAttemptAndOutcomeInLog` → R11
- `shouldMarkDeliveryFailedAndScheduleRetryOnTransientError` → R12
- `shouldStopRetryingAndDeadLetterAfterMaxAttempts` → R13
- `shouldRenderTemplateWithEventDataAndSelectedChannel` → R14
- `shouldNotLeakSecretsOrTokensIntoRenderedMessagesOrLogs` → R15
- `shouldStreamInAppNotificationsToAuthenticatedRecipientOnly` → R16
- `shouldReturnUnreadInAppNotificationsForCaller` → R17
- `shouldFallBackToDefaultPreferenceWhenNoneSet` → R18
- `shouldConformToConsumedEventSchemas` → R19
- `shouldPreventCrossModuleEntityImports` → L11

## 9. Verification checklist — implementer self-checks before raising PR

- [ ] All §3 acceptance criteria have a passing named test from §8.
- [ ] Every §4a LOCKED decision implemented as written (no silent deviation).
- [ ] Every §4c VERBATIM artifact copied exactly (event→template map, DDL, config keys).
- [ ] **Every consumer is idempotent**: replaying any consumed event twice results in exactly one delivery (a test asserts this per event type).
- [ ] The delivery log records every attempt and terminal outcome; it is append-only (no UPDATE that erases prior attempts) and correlatable to the source event key.
- [ ] No secret, token, password-reset value, or full API key ever appears in a rendered message body or a log line (assertion-tested — mirrors auth `target-design.md` §13).
- [ ] Channel-preference resolution honours opt-outs and falls back to a documented default.
- [ ] The in-app stream authenticates the recipient (resource-server JWT) and never streams another account's notifications.
- [ ] Retry/backoff is bounded and terminates in a dead-letter outcome, not an infinite loop.
- [ ] `mvn -pl services/notification verify` passes (unit + integration + Testcontainers).
- [ ] Consumed payloads validate against the authored `contracts/events/*` schemas; a schema mismatch fails a contract test, not production.

## 10. Migration, rollout & rollback

**Schema**

- Greenfield: the first migration is `V1__notifications_baseline.sql` (see [`design.md`](design.md#4c-verbatim-artifacts)), `notifications` schema, Flyway DDL-only. Templates are seeded by migration/config, versioned, never hand-edited at runtime. No pre-existing schema to preserve.

**Code rollout**

- Deploy order (`ARCHITECTURE.md` §10): weeks 7–10, after Auth (`auth.email.requested`), Payment (`payments.*`), and their event contracts exist. It can run standalone against Testcontainers-produced events.
- Readiness gates on DB + Kafka + the outbound email transport reachability (per O2). A pod that cannot reach its email transport should still consume and log deliveries as `FAILED`+retryable rather than dropping them.
- Rolling update on EKS; ≥ 2 replicas in one consumer group so partitions are shared; idempotency (dedupe on event key) makes rebalance-induced redelivery safe.
- Because this service only consumes, a bad deploy cannot corrupt upstream domain state; the worst case is delayed or duplicated notifications, and the dedupe key bounds duplication.

**Emergency rollback**

- Revert to the previous image. Unprocessed events remain on Kafka and are re-consumed; already-logged deliveries are skipped by the dedupe key, so rollback does not re-send. In-flight in-app streams reconnect from the persisted unread set.

## 11. Open questions for the author

- Q1. **Recipient resolution.** Events carry account UUIDs and (for payments) a payer, but this service needs an email/contact per recipient. Does it (a) consume `auth.user.lifecycle`/`auth.email.requested` payloads that already include the email, (b) call an Auth internal endpoint, or (c) maintain a projected contact table fed by auth events? Recommended default in `design.md` §4b-O1 is (c) a projection — confirm. Blocker for email delivery.
- Q2. **Email transport.** Amazon SES vs SendGrid vs SMTP relay? Drives the `EmailChannel` implementation and IAM/secrets. Placeholder in `design.md` §4b-O2.
- Q3. **In-app transport.** SSE vs WebSocket for the in-app channel, and how it is routed through ingress-nginx (`ARCHITECTURE.md` §3.5 says "SSE/websocket routed through the edge"). Recommend SSE for simplicity; confirm. Drives O3.
- Q4. **Email link base URL.** Verification/reset/receipt links need a base URL. This is the same unknown as auth's Q4: does it come from `auth.email.requested`'s payload, a shared `AUTH_EMAIL_LINK_BASE_URL`/`SPA_REDIRECT_URI` config, or per-event links already built upstream? Blocker for link-bearing templates (R1/R2/R5).
- Q5. **Delivery-status events.** Should this service publish delivery-status events (e.g. `notifications.delivery.failed`) back onto Kafka for later dispute/intelligence consumers, or is the delivery log sufficient at launch? Recommended default: log-only at launch (§4b-O5). Confirm.
- Q6. **Retry/backoff policy.** Max attempts, backoff schedule, and dead-letter destination for transient email failures. Placeholder in `design.md` §4b-O4.
- Q7. **Which events actually notify whom.** Confirm the event→recipient→channel matrix in `design.md` §4c (e.g. does `payment.seen` notify the payer, the merchant, or both; is `chain.provider.degraded` an ops-only alert this service ignores?).
- Q8. **Agents / standing-rules file.** **Resolved (2026-07-20):** `spec/notification-service/agents.md` now holds the durable rules and this spec references it. Open follow-up: whether to also seed a single repo-root `agents.md` for the platform-common section shared across all four service files (dedupe), or keep them self-contained per service.
