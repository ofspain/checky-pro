# notification · T07 · Phase 0 — Repository Understanding (resumed)

**Task:** `spec/notification-service/tasks.md` task 7 (`:16`). This artifact contains no code; it is documentation only.

**Revision note.** This file replaces the 2026-09-28 "blocked, deferred" Phase 0 (`f2272c51`), which is still in git history. The blocking condition has cleared:
- `spec/payment-service/package.md` is now `READY FOR IMPL` v0.2 (payment T29, 2026-10-19).
- All four consumed events now have committed contracts under `contracts/events/payments/` (payment T24).

## 1. Architecture summary

`services/notification` is a Spring Boot / Java 21 module.

**Persistence.**
- Postgres schema `notifications`, Flyway `V1`–`V11`. `V1` is the §4c baseline; `V3` seeds the launch templates.
- The app role is `notification_app`, with per-table grants added in `V2` and `V4`–`V11`.

**Consumers** (`consumer/`). There is one consumer today, `AuthEventConsumer`, with two listeners: `auth.email.requested` and `auth.user.lifecycle`. Each listener:
1. parses JSON into a DTO record in `consumer/dto`
2. builds a deterministic event key
3. calls `IdempotencyGuard.recordIfNew` against `processed_events`; if the event was already seen, it returns
4. refreshes `ContactProjectionUpdater`
5. calls `NotificationDispatcher.dispatch(accountUuid, kind, eventData)`

Each listener is `@Transactional`.

**Dispatch.** `delivery/DeliveryOrchestrator` implements `NotificationDispatcher`.
- It maps a `kind` to a `NotificationMapping` (EMAIL template name, IN_APP template name, category) through a static `NOTIFICATION_MAPPINGS`. Today that map has only the three auth kinds.
- An unknown kind is logged at DEBUG and skipped (`:148`), or rejected on replay (`:317`).
- It resolves preferences (`preference/PreferenceResolver`) and contacts (`contact_projection`, keyed by `account_uuid`).
- It renders through `template/TemplateRenderer` and delivers through `channel/EmailChannel` and `InAppChannel`.
- It logs to `delivery_log`, and retries through `delivery_retry` and `RetryScheduler` (ShedLock).

**Links.** `TemplateRenderer` computes `invoiceLink` (`baseUrl + "/invoices/" + invoiceUuid`) and `receiptLink` (`baseUrl + "/receipts/" + receiptUuid`) itself (`:107`, `:112`). The path convention is disclosed in the code as provisional (Q4). Link values supplied by the caller are ignored.

**Security.** OAuth2 resource server for the in-app read and stream endpoints. Consumers have no HTTP surface.

## 2. What this task touches

**Exists:**
- `AuthEventConsumer` (the pattern to copy)
- `IdempotencyGuard`
- `NotificationDispatcher` and `DeliveryOrchestrator`
- `NOTIFICATION_MAPPINGS`, which must gain four payment kinds, category `PAYMENT`
- `TemplateRenderer`
- the `V3` seeded templates, already present: `invoice.created`, `payment.seen`, `payment.finalized` and `receipt.issued`, each for EMAIL and IN_APP
- the `PAYMENT` default preferences (§4c, email ON, in-app ON)

**New, per the task text:**
- `consumer/PaymentEventConsumer`
- payment event DTOs in `consumer/dto`
- possibly a local projection: see §5, gap 2

**Contracts consumed:** `contracts/events/payments/{invoice-created,payment-seen,payment-finalized,receipt-issued}.v1.schema.json`.

## 3. Established patterns

- **Consumer shape:** `@KafkaListener(topics=…, groupId="${spring.kafka.consumer.group-id}") @Transactional`.
- **DTOs:** records.
- **Event key:** built per event.
- **Idempotency:** dedupe before any side effect.
- **Unknown values:** dedup-recorded and not dispatched, never thrown.
- **Flyway:** each new table gets its own `notification_app` grant migration. The established style is one grant per migration, `V4`–`V11`.
- **Config:** flat `application.properties` only.

## 4. Testing conventions

- **Unit tests:** plain JUnit + Mockito with a fixed `Clock`, for example the existing `AuthEventConsumerTest`.
- **Integration tests:** Testcontainers Postgres + Kafka.
- **ArchUnit:** plain-canary-only. Any `@ArchTest` field yields 0 tests under Surefire 3.5.3; this module's T16 established the workaround.
- **Named tests (`package.md` §8):**
  - `shouldNotifyBothPartiesOnInvoiceCreated`
  - `shouldNotifyOnPaymentSeen`
  - `shouldNotifyWithReceiptLinkOnReceiptIssued`

## 5. Known gaps — found against the real payment contracts

These matter more than anything else in this artifact. Q7 (`package.md:130`, the event→recipient matrix) is **still open**, and the task says to "confirm Q7". The §4c matrix says **"merchant + payer"** for all four payment events. The real producer can't support that as written.

### Gap 1 — There is no payer identity anywhere

- A payer is an anonymous wallet, identified only by `fromAddress`. It appears in `payment.finalized` only, and is optional there.
- No payment event carries a payer account UUID or email.
- `contact_projection` is keyed by `account_uuid` and fed only by auth events.

So "notify the payer" can't be implemented with the data that exists. **I do not know** whether the product intends a payer contact to be added (for example, an email collected at invoice creation), or whether payer notification is dropped for Phase 1.

### Gap 2 — Two events can't name the merchant

| Event | Merchant identifiable? |
|---|---|
| `invoice.created` | Yes. Carries `merchantAccountUuid` (required). |
| `receipt.issued` | Yes. Carries `merchantAccountUuid`, but it is *optional* in the schema; the real producer always sets it (payment E2E asserts it). |
| `payment.seen` | Only through `invoiceUuid`. No merchant. |
| `payment.finalized` | No. Carries neither `invoiceUuid` nor merchant, only `paymentUuid`. |

Resolving the merchant for those two events needs one of:
- a local projection (`invoiceUuid → merchantAccountUuid` from `invoice.created`, plus `paymentUuid → invoiceUuid` from `payment.seen`), with a new table, migration and grant
- a synchronous lookup against payment-service, which would break this service's event-driven, projection-not-live-read stance (cf. `contact_projection`'s "Not a live Auth read")
- a change to the payment contracts, which is out of this task's scope

**I do not know** which one is intended.

### Gap 3 — The templates need values the events don't have

| Placeholder | Problem |
|---|---|
| `{{currency}}` | Used by all four EMAIL templates. No event carries a currency or symbol, only `tokenContractAddress`. |
| `{{amount}}` | Missing from `payment.seen`. |
| `{{invoiceId}}` | Missing from `payment.finalized`. |
| `{{displayName}}` | Comes from `contact_projection`, which is fine for the merchant. |

The renderer treats a missing key as empty. **I do not know** whether empty substitution is acceptable or whether the templates need changing. `V3` is immutable, so a change would need a new seed migration with a version bump, which is L9's own convention.

### Gap 4 — There is no working receipt link (R5)

- R5 requires "include the receipt link".
- The renderer builds `<base-url>/receipts/<receiptUuid>`. That path is provisional (Q4), and **no endpoint serves it**: payment-service has no receipt read (payment FU-T29-1), and no frontend route is known.
- The event's `receiptUri` is an internal `s3://bucket/key`. It is not user-reachable, and exposing it would leak bucket names.

### Gap 5 — Idempotency keys

- Payment events carry no `idempotencyKey` field.
- The Kafka key is the aggregate UUID (`invoiceUuid`, `paymentUuid` or `receiptUuid`).
- Payment-service publishes at least once, and its E2E test only checks that redeliveries are identical.

A per-event key such as `<topic>:<aggregateUuid>` would dedupe correctly, since each payment event fires once per aggregate. I have to confirm this in Phase 1, using `occurredAt` the way the auth key pattern does. Not a blocker.

### Gap 6 — `payment.finalized` exists in the matrix but not in R3–R5

R3–R5 cover `invoice.created`, `payment.seen` and `receipt.issued`. The task text and the §4c matrix include `payment.finalized` too. **I do not know** whether it gets a requirement. A template is already seeded for it.

## Other unknowns

- **Anchor deferral.** This task was frozen behind the payment spec; that has cleared.
- **Infra pivot memory.** It notes services work was once paused for AWS infra. No new pause is known, so I'm proceeding as instructed ("move on").
