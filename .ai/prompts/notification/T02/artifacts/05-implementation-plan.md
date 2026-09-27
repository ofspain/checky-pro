# notification · T02 · Phase 5 — Implementation Plan

## Files to create

- `services/notification/src/main/resources/db/migration/V1__notifications_baseline.sql`
- `services/notification/src/main/resources/db/migration/V2__notification_app_role_and_grants.sql`
- `services/notification/src/main/resources/db/migration/V3__seed_launch_templates.sql`
- `services/notification/src/test/java/com/themistra/notification/NotificationBaselineMigrationIntegrationTest.java`

## Correction to Phase 4's own disposition (caught while pinning exact assertions)

Finding #4's accepted resolution suggested `COUNT(DISTINCT name) = 7`. Checked against Finding #3's own
naming table (also accepted, same phase): 5 of the 7 mappings reuse one name across both channels, but
2 (`verify_email`, `password_reset`) use **different** names per channel (`email.verify`/`user.verify`,
`email.password_reset`/`user.password_reset`). Real count: `5×1 + 2×2 = 9` distinct names across 14
rows, not 7. The test below uses the correct number — an example of the same verify-before-trusting
discipline applied to my own prior phase's output, not just Kimi's.

## Exact file content

### 1. `V1__notifications_baseline.sql` — byte-for-byte `design.md` §4c copy

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

### 2. `V2__notification_app_role_and_grants.sql`

```sql
-- T02 AC2: the runtime application role, least-privilege by construction. Deliberately kept out of
-- V1__notifications_baseline.sql, which is a verbatim transcription of design.md §4c and carries no
-- grant/role statements of its own.
--
-- checky (the role V1 was created by, via the Flyway Maven plugin) owns every table in this
-- migration and is never used by the running application. notification_app owns nothing and
-- connects only as a grantee - in PostgreSQL, table owners bypass GRANT/REVOKE entirely, so the
-- restriction below only means anything because of that owner/grantee split (mirrors
-- crypto-service's own V2__crypto_app_role_and_grants.sql exactly).
--
-- No password is set here: this migration must be safe to run unmodified in any environment, and a
-- committed password would only ever be right for local dev. Real environments set
-- notification_app's password out-of-band (External Secrets Operator / IAM, L10). For local dev, run
-- once after this migration: `ALTER ROLE notification_app PASSWORD 'notification-app-local-only';`.
-- The database name is likewise never hardcoded - GRANT CONNECT targets current_database() via
-- dynamic SQL.
--
-- This task's own literal scope grants only delivery_log. The other six baseline tables
-- (contact_projection, channel_preferences, templates, processed_events, inapp_notifications,
-- delivery_retry) and shedlock each get their own grant migration in the task that first needs
-- runtime access to them, mirroring crypto-service's own incremental-grant pattern (one grant
-- migration per newly-consuming task) - not granted upfront here.

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'notification_app') THEN
        CREATE ROLE notification_app LOGIN;
    END IF;
    EXECUTE format('GRANT CONNECT ON DATABASE %I TO notification_app', current_database());
END
$$;

GRANT USAGE ON SCHEMA notifications TO notification_app;

-- Every table in notifications uses GENERATED ALWAYS AS IDENTITY; nextval() on the underlying
-- sequence requires USAGE, or an otherwise-permitted INSERT still fails.
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA notifications TO notification_app;

-- T02's own literal scope: INSERT + SELECT only, no UPDATE, no DELETE, ever.
GRANT INSERT, SELECT ON notifications.delivery_log TO notification_app;
```

### 3. `V3__seed_launch_templates.sql`

```sql
-- T02 AC3: minimal, real seed content for the 7 launch event->template mappings, both EMAIL and
-- IN_APP channel variants (14 rows), matching the default channel-preference matrix (design.md §4c:
-- SECURITY and PAYMENT categories are ON for both channels by default). Placeholder syntax is
-- {{variable}} - a reasonable, engine-agnostic default; O6 (the actual rendering engine) is task 9's
-- own open decision and may require revisiting this syntax, not treated as final here.
--
-- auth.user.lifecycle (user.suspended) -> account.suspended is explicitly marked optional (Q7) and
-- is not seeded at launch.
--
-- IN_APP names for the two auth-originated, EMAIL-prefixed templates are distinct from their EMAIL
-- counterparts (user.verify, user.password_reset), per the Phase 4 frozen brief's own naming table;
-- the other five mappings reuse the identical name across both channels, differentiated only by the
-- channel column.

INSERT INTO notifications.templates (name, channel, version, subject, body) VALUES
    ('email.verify', 'EMAIL', 1,
        'Verify your Themistra account',
        'Hi {{displayName}}, please verify your email by visiting {{verificationLink}}. This link expires in 24 hours. If you did not request this, ignore this email.'),
    ('user.verify', 'IN_APP', 1,
        NULL,
        'Verify your email address to finish setting up your account. {{verificationLink}}'),
    ('email.password_reset', 'EMAIL', 1,
        'Reset your Themistra password',
        'Hi {{displayName}}, we received a request to reset your password. Visit {{resetLink}} to choose a new one. This link expires in 1 hour. If you did not request this, you can safely ignore this email.'),
    ('user.password_reset', 'IN_APP', 1,
        NULL,
        'A password reset was requested for your account. If this was not you, secure your account immediately.'),
    ('user.welcome', 'EMAIL', 1,
        'Welcome to Themistra',
        'Hi {{displayName}}, welcome aboard! Your account is ready. {{getStartedLink}}'),
    ('user.welcome', 'IN_APP', 1,
        NULL,
        'Welcome to Themistra, {{displayName}}! Explore your dashboard to get started.'),
    ('invoice.created', 'EMAIL', 1,
        'New invoice created',
        'Hi {{displayName}}, an invoice for {{amount}} {{currency}} has been created. {{invoiceLink}}'),
    ('invoice.created', 'IN_APP', 1,
        NULL,
        'A new invoice for {{amount}} {{currency}} was created.'),
    ('payment.seen', 'EMAIL', 1,
        'Payment detected on-chain',
        'Hi {{displayName}}, we have seen a payment of {{amount}} {{currency}} on-chain for invoice {{invoiceId}}. It is awaiting confirmations.'),
    ('payment.seen', 'IN_APP', 1,
        NULL,
        'Payment of {{amount}} {{currency}} seen on-chain, awaiting confirmation.'),
    ('payment.finalized', 'EMAIL', 1,
        'Payment finalized',
        'Hi {{displayName}}, your payment of {{amount}} {{currency}} for invoice {{invoiceId}} has been finalized on-chain.'),
    ('payment.finalized', 'IN_APP', 1,
        NULL,
        'Payment of {{amount}} {{currency}} finalized.'),
    ('receipt.issued', 'EMAIL', 1,
        'Your receipt is ready',
        'Hi {{displayName}}, your receipt for invoice {{invoiceId}} is ready. {{receiptLink}}'),
    ('receipt.issued', 'IN_APP', 1,
        NULL,
        'Your receipt is ready. {{receiptLink}}');
```

### 4. `NotificationBaselineMigrationIntegrationTest.java` — design (mirrors crypto's own file exactly,
adapted for one granted table instead of three, plus the `citext` setup step and template-seed check)

- `GRANTED_TABLES = List.of("delivery_log")`.
- `UNGRANTED_TABLES = List.of("contact_projection", "channel_preferences", "templates", "processed_events", "inapp_notifications", "delivery_retry", "shedlock")`.
- `@BeforeAll`: create the `citext` extension via a raw admin connection **before** calling
  `Flyway.configure()...schemas("notifications")...migrate()` (Finding #1) — self-contained, not
  relying on auth's own migration having run in this isolated Testcontainers instance.
- `allEightBaselineTablesExistAndNoOthers()` — expects the 7 baseline tables + `shedlock`.
- `v1MigrationFileIsByteForByteIdenticalToDesignDocVerbatimBlock()` — path
  `../../spec/notification-service/design.md`, `V1__notifications_baseline.sql`.
- `allMigrationsAreRecordedAsSuccessfulInFlywayHistory()` — expects versions `"1"`, `"2"`, `"3"`.
- `v2RoleCreationGuardIsIdempotentUnderARealReRun()`.
- `notificationAppRoleRequiresItsProvisionedPassword()`.
- `notificationAppCanInsertAndSelectButNotUpdateOrDeleteOnDeliveryLog()` — insert fixture:
  `INSERT INTO notifications.delivery_log (channel, source_event_key, outcome) VALUES ('EMAIL', '<key>', 'SENT')`,
  keyed on `source_event_key` for the SELECT/UPDATE/DELETE probes (mirrors crypto's own `tx_hash` key
  role).
- `notificationAppHasNoAccessAtAllToTablesOutsideAc2Scope()`.
- `notificationAppCannotPerformDdlInTheNotificationsSchema()` — `CREATE TABLE notifications.evil_test`
  denied "permission denied"; `DROP TABLE notifications.delivery_log` denied "must be owner of table
  delivery_log".
- `baselineTablesAreOwnedByTheMigrationRoleNeverByNotificationApp()`.
- `launchTemplatesAreSeededWithVersionOne()` (Finding #4, corrected count): `COUNT(*) = 14`,
  `COUNT(DISTINCT name) = 9` (not 7 — see correction above), every row `version = 1`, and the exact
  14 `(name, channel)` pairs from the seed file present.

No `runtimeFlywayIsDisabledInApplicationProperties`-equivalent method (Finding #2) — deferred to task 3.

## Execution order

1. Create `V1__notifications_baseline.sql` exactly as pinned; diff it against `design.md`'s own fence
   directly to confirm byte-for-byte equality before moving on.
2. Create `V2__notification_app_role_and_grants.sql` exactly as pinned.
3. Create `V3__seed_launch_templates.sql` exactly as pinned.
4. Create `NotificationBaselineMigrationIntegrationTest.java` per the design above.
5. Run `mvn -pl services/notification -am test -Dtest=NotificationBaselineMigrationIntegrationTest`
   against the Testcontainers instance — this requires Docker, which is available in this environment.
6. Attempt the real local migration, per Phase 4's exact command sequence:
   ```bash
   docker compose -f services/auth/compose.local.yaml up -d
   mvn -pl services/auth flyway:migrate
   mvn -pl services/notification flyway:migrate
   ```
   Record the honest result either way.
7. Run the full module `mvn -pl services/notification -am verify` for the final record.
8. Write Phase 6's implementation notes with the real results of steps 5-7.
