# notification · T20 · Phase 5 — Implementation Plan

Consumes: `artifacts/04-frozen-task-brief.md`. This artifact contains no code; it is documentation only.

## Clarification of D2's "byte-identical §4c"

§4c (`design.md:26`–`:168`) runs up to `## 5.`. The recipient note belongs directly under the mapping it annotates, which is inside §4c. The section's "copy exactly" rule applies to its **verbatim artifacts**, which are its four fenced blocks:
- the topic→template map
- the default preferences
- the config keys
- the V1 DDL

**What the guard pins.** It pins the SHA-256 of those four blocks joined by `\n`, taken before any edit:

```
9a9ba0fff93ffd2c7528fd75c623099f90d3186960b87358878c3b31f9a70d07
```

Prose outside the fences may gain dated notes, but the fenced artifacts can't change. Kimi Phase 8 may challenge this.

## Steps

### 1. Tests first (D3)

**`common/SecretLeakEndToEndIntegrationTest.shouldNotLeakSecretsOrTokensIntoRenderedMessagesOrLogs`** (R15, L4)

*Setup:*
- Postgres plus its **own** `apache/kafka:3.8.0` container, with topics pre-created (the T07 pattern).
- The real orchestrator and `FakeEmailTransport`.
- `link.base-url=https://app.example.test`.
- A Logback `ListAppender` on the root logger, with `com.themistra` set to DEBUG for the test.

*Steps:* produce `auth.email.requested(password_reset)` with a distinctive token.

*Asserts:*
- The captured email body contains the token exactly once, as `https://app.example.test/reset-password?token=<token>`.
- The `inapp_notifications` body and title don't contain it.
- No captured log event contains it: neither the formatted message nor the throwable text.
- `delivery_log.error_detail` doesn't contain it.

**`delivery/PaymentEmailOptOutIntegrationTest.shouldSuppressOptedOutPaymentEmailButStillDeliverInApp`** (T17's buildable half, R10)

*Setup:*
- The same container setup.
- An admin insert of a `contact_projection` row for the merchant (with an email).
- A `channel_preferences(account, 'PAYMENT', 'EMAIL', enabled=false)` row.

*Steps:* produce `payments.invoice.created` for that merchant.

*Asserts:*
- `delivery_log` holds exactly one EMAIL row (`SUPPRESSED`) and one IN_APP row (`SENT`), both with source key `invoice.created:<uuid>`.
- `FakeEmailTransport` captured no email for the account.
- One `inapp_notifications` row exists, with category `PAYMENT` and the human amount `1.5`.

### 2. `spec/notification-service/requirements.md`

Append a dated *(2026-10-19, T07/T20)* note line after R3, R4 and R5. **R3/R4:** merchant only (no payer identity exists, FU-T07-1). **R5:** merchant only, no receipt link (FU-T07-2 / payment FU-T29-1).

### 3. `spec/notification-service/design.md`

- **L2:** a dated note after it, with the T07 D2 interpretation (the event-fed `invoice_routes`/`payment_routes` projection; no live call).
- **O1, O2, O4, O5:** a dated **Resolved** suffix on each, the same style as O3's existing note. Each cites `ContactProjectionUpdater`, `SesEmailTransport`/`EmailProperties`, `RetryProperties`/`RetryScheduler`, or "log-only, no publisher".
- **§4c:** a dated blockquote right after the mapping fence and its `chain.*` line. It records the shipped recipients (merchant only), the missing receipt link, and that `account.suspended` is unmapped.

### 4. `spec/notification-service/package.md`

- **Header:**
  - `| Version | \`0.2\` |`
  - `| Status | \`READY FOR IMPL\` — <disclosure> |`
- **§11:** a dated `**Resolved (2026-10-19):** …` appended to each of Q1–Q7, with real class citations (F6).
- **§9:** ticks with citations, or `[ ] **Partial —**` (F4 wording). The final set is settled by evidence while editing.

### 5. Guards in `T01SkeletonRegressionTest`

Following payment T29:

| Guard | What it checks |
|---|---|
| `notificationSpecIsBumpedToReadyForImplVersionZeroPointTwo` | Within the header table only: exactly one Version row and one Status row |
| `everyTestNamedInTheVerificationChecklistExists` | Parses `` `Class.method` `` citations in §9 and checks the method is declared in that class (`@Test`, `@ParameterizedTest`, or an `ArchRule` field for L11) |
| `verificationChecklistKeepsItsPartialItemsUnticked` | The partial items stay unticked |
| `verbatimSection4cIsUnchanged` | The §4c fenced-block hash |

### 6. Verification

- **Full suite:** `mvn -o -pl services/notification verify`.
- **Mutations:**
  - put the token into a log line
  - make the preference resolver ignore the opt-out
  - tick a partial item
  - edit one character in a §4c fence
  - cite a missing test
  - revert the version
