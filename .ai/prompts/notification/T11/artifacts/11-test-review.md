<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# notification · T11 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T11 — Delivery orchestrator + log |
| **Spec section** | Delivery orchestration |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` + T11 test files |
| **Produces** | `artifacts/11-test-review.md` |

Review of the T11 regression-guard tests against the acceptance criteria, `package.md` §8 named tests, and the frozen brief's pinned outcome-decision table.

---

## Gap 1 · `PreferenceResolver.resolve` failure is neither recorded nor tested

**Why it matters:** `dispatchOneChannel` calls `preferenceResolver.resolve(...)` inside its inner `try` block, but the corresponding `catch (Exception e)` at the bottom of that method only logs — it does not save a `FAILED` row. R11 requires every delivery attempt to be recorded; a preference-resolution failure is an attempted delivery that currently disappears from the dispute log. The test suite has no test that stubs `PreferenceResolver.resolve(...)` to throw.

**Suggested test:** Add a unit test in `DeliveryOrchestratorTest` that stubs `preferenceResolver.resolve(...)` to throw `RuntimeException("resolver down")` and asserts:
- `dispatch` does not propagate the exception;
- a `FAILED` row is saved for the affected channel with a redacted `errorDetail`;
- the other channel still proceeds.

Then update `dispatchOneChannel` to save a `FAILED` row in its inner catch (or wrap the resolver call in its own try/save), matching the decision table.

---

## Gap 2 · No dedicated tests for the `NoOpEmailChannel` / `NoOpInAppChannel` beans

**Why it matters:** These temporary channels are real Spring components that implement `NotificationChannel`. Their `channel()` values, their safe-logging contract, and the fact that `DeliveryOrchestrator`'s `List<NotificationChannel>` injection actually collects both beans are load-bearing for the whole task. Today there is no `channel/NoOpEmailChannelTest` or `channel/NoOpInAppChannelTest`, and `IdempotencyGuardIntegrationTest` only asserts the dispatcher bean type, not the channels.

**Suggested tests:**
1. A plain unit test per channel asserting `channel()` returns `"EMAIL"` / `"IN_APP"` and `send` completes without throwing.
2. A unit test asserting the channel's log line (captured via a logging appender/framework such as LogCaptor or a custom appender) contains neither the rendered `subject` nor the rendered `body` when a message with realistic token-bearing content is passed — i.e., the channel relies on `RenderedMessage.toString()` and cannot accidentally be edited to log `message.subject()` / `message.body()`.
3. Optionally, an integration test asserting the application context contains exactly two `NotificationChannel` beans with the expected names (or an ArchUnit test).

---

## Gap 3 · Both channels suppressed simultaneously is not exercised

**Why it matters:** The brief's R10 test name is `shouldSuppressChannelWhenRecipientOptedOut`, and the existing tests prove one channel suppressed while the other sends. The scenario where a recipient has opted out of both `EMAIL` and `IN_APP` for a category is also valid and should produce two `SUPPRESSED` rows.

**Suggested test:** Add a unit test (and optionally an integration test) where both channels resolve to disabled and assert two `SUPPRESSED` rows are written, neither channel renders, and neither `send` is called.

---

## Gap 4 · `displayName` merge behavior when `eventData` already contains the key is not tested

**Why it matters:** `DeliveryOrchestrator.dispatch` creates `renderData = new HashMap<>(eventData)` and then unconditionally overwrites `"displayName"` if the projection has a non-null value. A future caller (e.g., a payment consumer) might pass its own `displayName` in `eventData` and expect it to win, but the projection value will silently override it.

**Suggested test:** Add a unit test where `eventData` already contains `"displayName"` and the projection also returns a display name, then assert which value reaches `TemplateRenderer.render`. This documents the precedence rule. If the projection should always win, lock that; if not, fix the merge order.

---

## Gap 5 · `sourceEventKey` missing from `eventData` on a normal happy path is not tested

**Why it matters:** `DeliveryOrchestrator` defensively reads `sourceEventKey` from `eventData` and can fall back to a synthetic key when `findEmail` throws, but the normal per-channel path passes `null` straight to `save` if `sourceEventKey` is absent. That causes a DB constraint violation, which the inner catch converts to a second failed save, which also fails, and the outer catch finally logs. The behavior is complex and the only test is the synthetic-key fallback for a pre-loop failure.

**Suggested test:** Add a unit test for a normal (non-throwing) dispatch with `eventData = Map.of("token", "...")` (no `sourceEventKey`) and assert that `dispatch` still does not throw and that some observable outcome occurs — even if that outcome is only log-only. This makes the current behavior explicit and protects against future regressions.

---

## Gap 6 · No test asserts the rendered body actually contains `displayName` when populated

**Why it matters:** `dispatchSucceedsEndToEndWhenDisplayNameIsPopulated` only asserts that both rows are `SENT`; it does not prove the display name made it through `TemplateRenderer` into the rendered message. The wiring could be silently broken (e.g., wrong key name) and the test would still pass.

**Suggested test:** In the integration test, render with a known display name and assert the persisted delivery row (or a spied rendered message) reflects that the template placeholder was substituted. Since the rendered body is not persisted in `delivery_log`, the easiest path is to autowire `TemplateRenderer` and call it directly with the merged values, or capture the `RenderedMessage` passed to a spy channel.

---

## Gap 7 · Payment-derived mappings are not exercised with their required template variables

**Why it matters:** The frozen brief includes four payment-derived mappings (`invoice.created`, `payment.seen`, `payment.finalized`, `receipt.issued`). The tests use `invoice.created` for suppression, but with empty `eventData`. The seeded templates require `displayName`, `amount`, `currency`, `invoiceId`, and link source keys (`invoiceUuid`, `receiptUuid`). A future `PaymentEventConsumer` will need to supply these; the orchestrator needs to forward them unchanged.

**Suggested test:** Add a unit test dispatching `invoice.created` with `amount`, `currency`, `invoiceId`, and `invoiceUuid` in `eventData`, and assert that the same values are passed to `TemplateRenderer.render` for both `EMAIL` and `IN_APP`. This guards against accidental key stripping.

---

## Gap 8 · No test verifies the exact 7-entry mapping table

**Why it matters:** The brief emphasizes the mapping table is "VERBATIM" from `design.md` §4c. A future edit could add, remove, or rename an entry, and no test would fail until a production event arrives.

**Suggested test:** Add a reflection/source-based test that reads `DeliveryOrchestrator.NOTIFICATION_MAPPINGS` (or parses the source) and asserts it contains exactly the seven expected keys with the expected email/in-app template names and categories. This is cheap and locks the "verbatim" constraint.

---

## Summary

The T11 test suite is now substantial: 30 new tests across `DeliveryOrchestratorTest` (18), `DeliveryOrchestratorIntegrationTest` (7), and `ContactProjectionUpdaterIntegrationTest` (5). The two `package.md` §8 named tests (`shouldRecordEveryDeliveryAttemptAndOutcomeInLog`, `shouldSuppressChannelWhenRecipientOptedOut`) are present in both unit and integration form, AC10's transaction-join behavior is proven against a real transaction manager, and the Phase 8 findings around missing projection, `IN_APP` recipient, `errorDetail` redaction, `displayName` wiring, and pre-loop fallback rows are all covered.

The most important remaining gap is **Gap 1**: a throwing `PreferenceResolver` leaves no delivery-log row and has no test. **Gap 2** (no channel-level tests) is the next most consequential because the temporary channels are the only dispatch targets today. Gaps 3–8 are smaller coverage and documentation-of-behavior items.
