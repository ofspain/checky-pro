# notification · T12 · Phase 7 — Self Review

Self-review of the Phase 6 diff against the frozen brief and `agents.md`. Findings only — no code
changed here.

---

## Finding 1 · `SesEmailTransport.send` builds the request outside its own try/catch, so a
non-`SdkException` failure during request construction would escape unsanitized

**Severity:** Medium

**Evidence:** `channel/SesEmailTransport.java:46-57` (the `SendEmailRequest.builder()...build()`
chain) sits before the `try` block that starts at line 59. Only `sesV2Client.sendEmail(request)`
(line 60) is protected.

**Issue:** AC8 requires that `SesEmailTransport` "never lets a raw SDK exception escape unmodified."
As written, the class only honors that for a failure *during the SES API call itself*. A failure
during *request construction* (e.g. a null field reaching a builder in a way that throws before the
`try` block is entered) would propagate completely unmodified and unsanitized. This is not currently
reachable via the real call path — `EmailChannel.validate` (Phase 6) already guarantees a non-null
`recipient`/`subject` before an `EmailMessage` is ever constructed — but `SesEmailTransport`'s own
contract, evaluated in isolation (e.g. if a future caller bypassed `EmailChannel`, or a unit test
constructs a malformed `EmailMessage` directly), is only partially honored.

**Recommendation:** Move the request-construction block inside the `try`, or add a second, broader
`catch (RuntimeException e)` after the `SdkException` catch that also converts to a sanitized
`EmailDeliveryException` — closing the gap for any exception shape, not only the SDK's own.

---

## Finding 2 · A real email can be sent more than once for the same logical event if the
surrounding transaction aborts after `send` succeeds but before commit

**Severity:** Medium-High

**Evidence:** `DeliveryOrchestrator.dispatch` (T11, unchanged) is `@Transactional`; `EmailChannel.send`
(`channel/EmailChannel.java`) is called synchronously from inside that open transaction, and no
per-channel exception ever propagates to roll it back (every failure is caught and converted to a
`FAILED` row — T11's own already-verified AC9 guarantee). `AuthEventConsumer`'s own listener methods
(T06) call `dispatch(...)` as their last statement, in the same transaction as
`idempotencyGuard.recordIfNew`/`contactProjectionUpdater.upsertEmail`.

**Issue:** Under *normal* operation, no channel-level failure can cause a rollback after a
successful send (T11's AC9 already forecloses that path — reconfirmed by inspection here, not
merely assumed). The remaining, narrower risk is a transaction abort caused by something *outside*
`dispatch()`'s own control within the same transaction boundary — e.g. a forced Kafka
consumer-group rebalance mid-processing, which Spring Kafka's own default behavior can abort as a
rolled-back transaction. If that abort happens *after* `EmailChannel.send` has already made a real,
non-transactional SES call but *before* the surrounding transaction (including the idempotency
record) commits, Kafka redelivers the message; since the idempotency record was rolled back too,
`dispatch()` runs again and a second real email goes out for the same logical event. This is not a
new bug T12 introduces — it is an inherent consequence of T11's own already-frozen "channel call
inside the DB transaction" design — but T12 is the first task where this has a real, non-hypothetical
consequence (a `NoOp` channel had nothing to duplicate).

**Recommendation:** Document this as a disclosed, accepted risk of the current architecture (SES is
not transactional; true exactly-once delivery across Kafka + Postgres + an external HTTP API is not
achievable without a two-phase or outbox-style redesign). For `verify_email`/`password_reset`
specifically, a duplicate send re-delivers the same still-valid link, which is low-harm, not a
security or correctness issue for the recipient. A real fix (e.g. separating the transactional
idempotency write from the external send, or an outbox) would mean revisiting T11's own frozen
transaction boundary — out of this task's own scope to redesign.

---

## Finding 3 · A real, network-bound external call now runs synchronously inside an open DB
transaction

**Severity:** Medium

**Evidence:** Same call chain as Finding 2 — `DeliveryOrchestrator.dispatch`'s own `@Transactional`
boundary (T11) now wraps a real AWS SES network call (`SesEmailTransport.send`,
`channel/SesEmailTransport.java:60`) for the first time, rather than a `NoOp` no-latency placeholder.

**Issue:** This holds a database connection (and the surrounding transaction) open for the duration
of an external HTTP call to AWS, increasing connection-pool pressure and transaction/lock duration
under load or during an SES slowdown — a resource-contention risk that did not previously exist in
practice.

**Recommendation:** Same root cause as Finding 2 — a disclosed, accepted property of T11's own
already-frozen design, not something this task's own scope authorizes changing. Worth flagging for a
future task or ADR if SES latency/availability ever becomes an operational problem.

---

## Finding 4 · `EmailChannel` does not itself validate `message.body()`, relying entirely on an
upstream contract

**Severity:** Low

**Evidence:** `channel/EmailChannel.java`'s own `validate` method checks `recipient` and
`message.subject()` only, not `message.body()`. `TemplateRenderer`'s own contract (T09) makes `body`
never-null in practice (`Template.body` is `NOT NULL`; `substitute()` always returns a `String`).

**Issue:** If that upstream contract were ever violated by a future bug, the failure would surface as
a raw `NullPointerException` deep inside `SesEmailTransport`'s own request-building code (compounding
Finding 1 — outside its own `try/catch`), rather than a clear `IllegalArgumentException` at
`EmailChannel`'s own validation boundary, the same way a null `subject` already does.

**Recommendation:** Either add the same defensive check for `body`, or explicitly document in
`EmailChannel`'s own Javadoc why it is trusted and not re-validated (mirroring how `TemplateRenderer`
itself documents which fields it trusts vs. validates).

---

## Finding 5 · An invalid `themistra.notification.email.transport` value fails startup with a
generic, unhelpful Spring DI error

**Severity:** Low

**Evidence:** `channel/FakeEmailTransport.java`/`channel/SesEmailTransport.java` are each
`@ConditionalOnProperty(..., havingValue = "fake"/"ses")` — an exact, case-sensitive match. No third
value, and no explicit validation of the property's own allowed value set, exists anywhere.

**Issue:** Any value other than exactly `"ses"` or `"fake"` (a typo, a stray case difference, an
unsupported vendor name) leaves `EmailChannel`'s own `EmailTransport` constructor dependency
unsatisfiable, failing application startup — correct fail-fast behavior in spirit (`agents.md`:
"startup FAILS on missing/invalid values"), but the resulting error
(`NoSuchBeanDefinitionException`/`UnsatisfiedDependencyException`) does not name the actual
misconfigured property or its allowed values, unlike `LinkPropertiesStartupValidation`'s own
purpose-built, readable failure message for an analogous problem.

**Recommendation:** Consider a small, dedicated startup validator (mirroring
`LinkPropertiesStartupValidation`'s own established pattern) asserting `transport` is exactly `"ses"`
or `"fake"`, with a clear message naming both the actual value and the allowed set. Not required by
any acceptance criterion — a readability/operability improvement, not a correctness gap.
