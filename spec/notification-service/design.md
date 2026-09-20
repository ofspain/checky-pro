# 4. Design — how to build it

## 4a. LOCKED decisions — implement exactly, do NOT deviate

- L1. **Idempotent by event key.** Every consumer dedupes on the source event's stable key (`ARCHITECTURE.md` §3.5, §4 rule 3). A `processed_events` table with the event key as PK is written in the same transaction as the delivery-log append; duplicate Kafka delivery must never double-send (R7, R8). Kafka is at-least-once — this is not optional.
- L2. **Consume-only at launch.** This service initiates no domain state and makes no synchronous cross-service call on the delivery path (`ARCHITECTURE.md` §4 rule 4: no long synchronous chains). It reacts to events and renders what they carry. The only permitted projection read is the recipient-contact projection (O1), which is itself fed by consumed auth events, not a live call.
- L3. **Dispute-grade delivery log.** Every attempt on every channel is recorded with recipient, channel, source event key, template version, outcome, and timestamp (`ARCHITECTURE.md` §3.5). The log is append-only — a retry adds a new attempt row; it never overwrites the prior attempt. This is the evidence for "was the merchant notified?" in later dispute resolution.
- L4. **No secrets or tokens in messages or logs.** Rendered bodies and log lines never contain access/refresh tokens, raw secrets, full API keys, or reset-token values beyond the single intended one-time link (mirrors auth `target-design.md` §13, assertion-tested). R15.
- L5. **Channels behind one interface.** Email and in-app implement a common `NotificationChannel`; the delivery orchestrator is channel-agnostic. Webhooks (HMAC-signed) and mobile push are "Next" and must slot in without touching the orchestrator (`ARCHITECTURE.md` §3.5). When webhooks arrive, their payloads are HMAC-signed (threat #7, `SECURITY-THREAT-MODEL.md`).
- L6. **Preference resolution with a safe default.** Delivery honours the recipient's per-category channel preferences and opt-outs; absent a preference, a documented default applies — never "send on every channel" and never "fail" (R9, R10, R18).
- L7. **Bounded retry, then dead-letter.** Transient failures retry on a bounded backoff and terminate in a `DEAD_LETTERED` outcome (R12, R13). No infinite retry loop; no silent drop.
- L8. **Zero trust on the in-app surface.** The in-app stream and read API validate the recipient's JWT as an OAuth2 resource server against the Auth JWKS and scope results to the caller's `sub` (`ARCHITECTURE.md` §3.1). No endpoint is public except actuator health/info/prometheus.
- L9. **Templates are versioned.** Each rendered message records the template version used (in the delivery log), so a later dispute can reconstruct exactly what the recipient was shown (L3). Templates are seeded/versioned, not runtime-edited.
- L10. **Secrets discipline.** No email-transport credential, DB password, or key is committed. External Secrets Operator injects them; validated `@ConfigurationProperties` fail startup on missing/invalid config in non-local profiles (`ARCHITECTURE.md` §8; auth `target-design.md` §16).
- L11. **Module boundaries.** Package-by-feature under `com.themistra.notification`; no feature module imports another feature module's entity. Shared plumbing lives in `common`. Enforced by ArchUnit, mirroring the auth service.

## 4b. OPEN decisions — implementer/Claude MAY propose

- O1. **Recipient resolution (Q1).** Recommended: a `contact_projection` table populated by consuming `auth.user.lifecycle` and reading the email from `auth.email.requested` payloads, so the delivery path never makes a live Auth call (L2). Propose the projection's columns and how it stays consistent; confirm against Q1 before finalizing. **Blocker for email delivery.**
- O2. **Email transport (Q2).** Propose SES vs SendGrid vs SMTP with the IAM/secret and cost trade-offs; recommend one; implement behind `EmailChannel`. Proceed after author selection.
- O3. **In-app transport (Q3).** Propose SSE vs WebSocket and the ingress routing; recommend SSE. Include how the persisted unread set backs reconnects.
- O4. **Retry/backoff policy (Q6).** Propose max attempts, backoff schedule, and dead-letter destination (a `dead_letter` table vs a Kafka DLQ topic); recommend values; proceed if low-risk.
- O5. **Delivery-status events (Q5).** Propose whether to publish `notifications.delivery.*` events via an outbox for downstream dispute/intelligence consumers, or stay log-only at launch. Recommended default: **log-only at launch**; add the outbox only when a consumer needs it (mirrors the auth spec's "don't extract outbox until a second service needs it" discipline).
- O6. **Template engine.** Propose the templating approach (e.g. a simple built-in vs a library) for email HTML + in-app payloads; recommend one; proceed if low-risk.

## 4c. VERBATIM artifacts — copy exactly, do not paraphrase

### Consumed topics → template mapping (confirm recipients via Q7)

```
auth.email.requested (verify_email)   -> template: email.verify           -> recipient: the account (email in payload)
auth.email.requested (password_reset) -> template: email.password_reset   -> recipient: the account
auth.user.lifecycle (user.registered) -> template: user.welcome           -> recipient: the account
auth.user.lifecycle (user.suspended)  -> template: account.suspended       -> recipient: the account   (optional; Q7)
payments.invoice.created               -> template: invoice.created         -> recipients: merchant + payer (Q7)
payments.payment.seen                  -> template: payment.seen            -> recipients: merchant + payer (Q7)
payments.payment.finalized             -> template: payment.finalized       -> recipients: merchant + payer (Q7)
payments.receipt.issued                -> template: receipt.issued          -> recipients: merchant + payer, with receipt link (Q4)
```
`chain.*` events are NOT consumed by this service — `chain.provider.degraded` is an ops alert (observability), not a user notification.

### Default channel preferences (used when a recipient has none — L6/R18)

```
category = SECURITY   (verify, reset, suspended, security_alert) : email = ON,  in_app = ON   (email cannot be disabled)
category = PAYMENT     (invoice, payment.*, receipt)             : email = ON,  in_app = ON
category = MARKETING   (future)                                  : email = OFF, in_app = OFF
```

### New configuration keys (add to `application.properties`)

```properties
# --- Email transport (impl per O2/Q2) ---
themistra.notification.email.from=no-reply@checky.pro
themistra.notification.email.transport=${EMAIL_TRANSPORT:ses}

# --- Link base URL (same unknown as auth Q4; may instead arrive in the event payload) ---
themistra.notification.link.base-url=${AUTH_EMAIL_LINK_BASE_URL:}

# --- Retry / backoff (defaults; confirm via Q6) ---
themistra.notification.retry.max-attempts=5
themistra.notification.retry.initial-backoff-seconds=30
themistra.notification.retry.max-backoff-seconds=3600

# --- In-app stream (impl per O3/Q3) ---
themistra.notification.inapp.transport=sse
```

### First Flyway migration `V1__notifications_baseline.sql`

```sql
-- Notification Service baseline (notifications schema). Delivery log is append-only and
-- dispute-grade (L3): the service DB role has INSERT + SELECT only on delivery_log.

CREATE SCHEMA IF NOT EXISTS notifications;
SET search_path TO notifications;

-- Projection of recipient contact info, fed by consumed auth events (O1/Q1). Not a live Auth read.
CREATE TABLE contact_projection (
    account_uuid UUID PRIMARY KEY,
    email CITEXT,
    display_name VARCHAR(200),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE channel_preferences (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_uuid UUID NOT NULL,
    category VARCHAR(16) NOT NULL,               -- SECURITY | PAYMENT | MARKETING
    channel VARCHAR(16) NOT NULL,                -- EMAIL | IN_APP | WEBHOOK | PUSH
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_pref UNIQUE (account_uuid, category, channel),
    CONSTRAINT chk_pref_category CHECK (category IN ('SECURITY','PAYMENT','MARKETING')),
    CONSTRAINT chk_pref_channel CHECK (channel IN ('EMAIL','IN_APP','WEBHOOK','PUSH'))
);

CREATE TABLE templates (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(64) NOT NULL,                   -- e.g. email.verify, receipt.issued
    channel VARCHAR(16) NOT NULL,
    version INT NOT NULL,
    subject VARCHAR(256),
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_template UNIQUE (name, channel, version)
);

-- Idempotency ledger (L1): dedupe consumed events on their stable key.
CREATE TABLE processed_events (
    event_key VARCHAR(200) PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Dispute-grade, append-only delivery log (L3). Never UPDATE to erase a prior attempt.
CREATE TABLE delivery_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_uuid UUID,
    recipient VARCHAR(256),                       -- email address or in-app subject
    channel VARCHAR(16) NOT NULL,
    source_event_key VARCHAR(200) NOT NULL,
    template_name VARCHAR(64),
    template_version INT,
    attempt SMALLINT NOT NULL DEFAULT 1,
    outcome VARCHAR(16) NOT NULL,                 -- SENT | FAILED | SUPPRESSED | DEAD_LETTERED
    error_detail VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_delivery_outcome CHECK (outcome IN
        ('SENT','FAILED','SUPPRESSED','DEAD_LETTERED'))
);
CREATE INDEX idx_delivery_log_event ON delivery_log(source_event_key);
CREATE INDEX idx_delivery_log_account ON delivery_log(account_uuid, created_at);

-- In-app notification store (backs the SSE/websocket stream + unread read API).
CREATE TABLE inapp_notifications (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    notification_uuid UUID NOT NULL UNIQUE,
    account_uuid UUID NOT NULL,
    category VARCHAR(16) NOT NULL,
    title VARCHAR(256) NOT NULL,
    body TEXT NOT NULL,
    link VARCHAR(512),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_inapp_unread ON inapp_notifications(account_uuid, created_at) WHERE read_at IS NULL;

-- Bounded-retry scheduling for transient failures (L7). Dead-lettering per O4.
CREATE TABLE delivery_retry (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    source_event_key VARCHAR(200) NOT NULL,
    account_uuid UUID,
    channel VARCHAR(16) NOT NULL,
    attempt SMALLINT NOT NULL,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_delivery_retry_due ON delivery_retry(next_attempt_at);

CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
```

## 5. Data model & schema changes

Greenfield: `V1__notifications_baseline.sql` (§4c) is the whole baseline, in the `notifications` schema. Aggregates:

- `ContactProjection` — recipient email/name projected from auth events (O1/Q1); the only "read" the delivery path needs, and it is local.
- `ChannelPreference` — per-account, per-category, per-channel enable/opt-out; default applied when absent (L6).
- `Template` — versioned per name+channel; the version used is recorded per delivery (L9).
- `ProcessedEvent` — idempotency ledger (L1).
- `DeliveryLog` — append-only dispute-grade record of every attempt/outcome (L3).
- `InappNotification` — persisted in-app items backing the stream + unread API.
- `DeliveryRetry` — bounded retry scheduling (L7).

No money is handled by this service; no floating-point type is introduced. Any amount shown in a message is rendered from the event's decimal-string value as-is.

## 6. Package & file map

New files under `services/notification/src/main/java/com/themistra/notification/`:

```
consumer/
├── AuthEventConsumer.java                 (auth.email.requested, auth.user.lifecycle)
├── PaymentEventConsumer.java              (payments.invoice.created / payment.* / receipt.issued)
├── ProcessedEvent.java / ProcessedEventRepository.java   (idempotency — L1)
└── dto/ (deserialization records matching contracts/events/*)

preference/
├── ChannelPreference.java / ChannelPreferenceRepository.java
├── PreferenceResolver.java               (opt-out + default — L6)
└── ContactProjection.java / ContactProjectionRepository.java   (O1)

template/
├── Template.java / TemplateRepository.java
└── TemplateRenderer.java                  (versioned render — L9, R14)

delivery/
├── DeliveryLog.java / DeliveryLogRepository.java   (append-only — L3)
├── DeliveryOrchestrator.java              (resolve prefs → render → dispatch → log)
├── DeliveryRetry.java / DeliveryRetryRepository.java
└── RetryScheduler.java                    (ShedLock; bounded backoff → dead-letter — L7)

channel/
├── NotificationChannel.java               (interface — L5)
├── EmailChannel.java                      (O2/Q2)
├── InAppChannel.java                      (persists + pushes to stream)
└── (WebhookChannel, PushChannel — NOT built at launch)

inapp/
├── InappNotification.java / InappNotificationRepository.java
├── InappStreamController.java             (SSE/websocket — L8, R16)
└── InappReadController.java               (GET unread — L8, R17)

common/
├── PublicEndpoints.java                   (actuator health/info/prometheus only)
├── ApiExceptionHandler.java              (RFC 9457)
├── ResourceServerConfig.java             (JWT vs Auth JWKS — L8)
├── SecretSafeLogging.java                (redaction guard — L4/R15)
└── config/*Properties.java               (validated @ConfigurationProperties — L10)
```

Tests mirror the layout under `src/test/java/com/themistra/notification/` with a capturing fake transport. This service authors no new published-event contract at launch (consume-only, L2); it deserializes against the existing `contracts/events/{auth,payments}/*` schemas. If O5 is taken, add `contracts/events/notifications/*` and an outbox module.
