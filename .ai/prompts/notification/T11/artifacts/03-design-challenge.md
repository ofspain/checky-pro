<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# notification · T11 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `notification-service` |
| **Task** | T11 — Delivery orchestrator + log |
| **Spec section** | Delivery orchestration |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Adversarial review of the T11 Phase 2 brief.

---

## Finding 1 · Transaction boundary for `DeliveryOrchestrator.dispatch` is not specified

**Severity:** High

**Evidence:**
- `DeliveryOrchestrator.dispatch` writes to `delivery_log` and calls `PreferenceResolver`, `TemplateRenderer`, and `NotificationChannel.send`.
- `AuthEventConsumer`'s listener methods are `@Transactional`, so a call to `dispatch` from there joins an existing transaction.
- However, `DeliveryOrchestrator` may also be called from tests or future non-transactional callers. Without its own `@Transactional` annotation, each `deliveryLogRepository.save()` would run in its own short transaction, and a failure midway could leave a partially-written log.
- AC9 says `dispatch` must never throw, but it does not say the delivery-log writes must be atomic with the preference/render/send attempt.

**Recommended brief amendment:**
Specify whether `DeliveryOrchestrator.dispatch` is itself `@Transactional` (default `REQUIRED`). Since it writes dispute-grade evidence, recommending `@Transactional` is sensible: it joins the consumer's transaction when called from `AuthEventConsumer`, and creates one when called standalone. Add a test proving that a failure during the second channel's dispatch rolls back the first channel's delivery-log row.

---

## Finding 2 · What recipient is recorded in `DeliveryLog` for the `IN_APP` channel is not specified

**Severity:** Medium

**Evidence:**
- The `delivery_log.recipient` column is documented as "email address or in-app subject" and is nullable.
- The `NotificationChannel.send` signature takes `String email`, which is natural for EMAIL but not for IN_APP.
- The brief does not say what value to persist as `recipient` when the channel is `IN_APP`.

**Recommended brief amendment:**
State the recipient value per channel:
- `EMAIL`: the email address from `ContactProjectionUpdater.findEmail` (or the event payload, if the brief chooses that path).
- `IN_APP`: the account's UUID (as the in-app subject/recipient identifier), since there is no email recipient.

This ensures the delivery log remains reconstructable per channel.

---

## Finding 3 · Behavior when `findEmail` returns `Optional.empty()` is not specified

**Severity:** Medium

**Evidence:**
- `ContactProjectionUpdater.findEmail` is new in this task and returns `Optional<String>`.
- The brief says `DeliveryOrchestrator` resolves the recipient via the projection, but it does not say what happens if the projection has no email.
- A missing projection could occur for an account that has not yet had a lifecycle/email event processed.

**Recommended brief amendment:**
Specify the behavior for missing contact projection:
- For `EMAIL`: log a `FAILED` delivery attempt with `errorDetail` indicating no recipient address (and do not call the channel).
- For `IN_APP`: the channel does not require an email, so proceed normally.

This keeps `dispatch` non-throwing and records the failure in the dispute log.

---

## Finding 4 · Failure-to-outcome mapping is not specified

**Severity:** Medium

**Evidence:**
- AC9 requires `dispatch` to never throw and to convert every internal failure into a logged `delivery_log` row.
- The brief does not define which `outcome` value to use for which failure mode:
  - Render failure (`TemplateRenderer.render` throws `IllegalArgumentException`).
  - Channel send failure (`NotificationChannel.send` throws).
  - Missing recipient projection.
  - Missing channel bean.
  - Unknown `notificationKind`.
- The DB constraint allows `SENT`, `FAILED`, `SUPPRESSED`, `DEAD_LETTERED`.

**Recommended brief amendment:**
Add a small decision table:
- Channel enabled and send succeeds → `SENT`.
- Channel disabled by preference → `SUPPRESSED`.
- Render/lookup/channel/unknown-channel failure → `FAILED` with `errorDetail`.
- Unknown `notificationKind` → no row (no-op), or optionally one `FAILED` row with `errorDetail`.

This makes AC9 testable and consistent.

---

## Finding 5 · How `SecretSafeLogging` is used in `DeliveryOrchestrator` is not specified

**Severity:** Medium

**Evidence:**
- The brief lists `SecretSafeLogging` as a dependency of `DeliveryOrchestrator`.
- It does not say where `redact()` is applied. Potential leak points:
  - SLF4J log statements inside `dispatch` if they include `eventData` or exception messages.
  - `errorDetail` column in `delivery_log` if it contains a secret-shaped substring.
  - `NoOp*Channel` logs already use `RenderedMessage.toString()` which excludes body/subject, but `DeliveryOrchestrator` may log separately.

**Recommended brief amendment:**
Specify that `SecretSafeLogging.redact()` is applied to any free-form text logged or persisted in `errorDetail`, and that `eventData` (which contains tokens) is never logged whole. Add a test asserting `errorDetail` containing `token=abc` is redacted to `token=***`.

---

## Finding 6 · The `NotificationChannel` interface passes `String email` even to `IN_APP`

**Severity:** Low

**Evidence:**
- `NotificationChannel.send(UUID accountUuid, String email, RenderedMessage message)` takes `email` for both channels.
- For `IN_APP`, the email parameter is irrelevant and confusing. A future real `InAppChannel` might need the account UUID and message, not an email address.

**Recommended brief amendment:**
Rename the parameter to `recipient` and document that it holds the email address for `EMAIL` and the account UUID (or in-app subject) for `IN_APP`. This keeps the interface channel-agnostic.

---

## Finding 7 · Behavior when the mapping table has no template for a channel is not specified

**Severity:** Low

**Evidence:**
- The mapping is a private record with `emailTemplateName`/`inAppTemplateName`.
- Some notification kinds may only have an EMAIL template at launch (though the seeded templates provide both for auth events).
- If one of the template names is `null`, the orchestrator must skip that channel to avoid calling `TemplateRenderer.render` with an invalid name.

**Recommended brief amendment:**
State that if a mapping's template name for a channel is `null`, the orchestrator skips that channel entirely (no `SENT` and no `SUPPRESSED` row, or optionally a `SUPPRESSED` row with `errorDetail` explaining no template). This prevents `IllegalArgumentException` from `TemplateRenderer`.

---

## Finding 8 · `DeliveryLog` entity construction / `createdAt` setting is not specified

**Severity:** Low

**Evidence:**
- The brief says the constructor takes every field except `id` and `createdAt`, and that `createdAt` comes from an injected `Clock`.
- It does not say whether `DeliveryLog` exposes a setter, a `withCreatedAt(Instant)` method, or a second constructor/factory that accepts `Clock`.

**Recommended brief amendment:**
Specify the construction pattern. A clean option is a public constructor taking all non-generated/non-timestamp fields plus an `Instant createdAt` parameter, with the orchestrator passing `clock.instant()`. Alternatively, a package-private `DeliveryLog.create(..., Clock clock)` factory. Either way, document it so the implementation is consistent with T04's `Clock` precedent.

---

## Finding 9 · Whether `dispatch` catches `Exception` or `Throwable` is not specified

**Severity:** Low

**Evidence:**
- AC9 says `dispatch` never throws. To guarantee this, the method must catch everything that could escape.
- Catching `Exception` covers checked and runtime exceptions but not `Error`. Catching `Throwable` covers `Error` too, which is usually discouraged but may be justified for a top-level "never throw" boundary.

**Recommended brief amendment:**
Specify the catch boundary. For a dispute-grade orchestrator, catching `Exception` and logging `FAILED` is usually sufficient; `Error` subclasses (e.g., `OutOfMemoryError`) can be allowed to propagate. Document the chosen boundary and add a test with a throwing channel that throws `RuntimeException`.

---

## Finding 10 · No mention of a migration/grant update is correct, but worth confirming

**Severity:** Low

**Evidence:**
- The brief correctly notes no new grant migration is needed because `delivery_log` was granted `INSERT, SELECT` at T02.
- This is a positive observation, not a defect.

**Recommended brief amendment:**
None needed; keep the explicit note.

---

## Summary

The T11 brief correctly scopes the wiring of T06/T08/T09/T10 into the first real delivery path and identifies the temporary channel implementations and the `DeliveryLog` append-only write path. The most consequential gap is **Finding 1**: the transaction boundary for `dispatch` is unspecified, which affects atomicity of dispute-grade log writes. **Findings 2–5** are medium-severity behavioral ambiguities (IN_APP recipient, missing projection, failure-to-outcome mapping, and `SecretSafeLogging` usage). Findings 6–9 are smaller precision items. Finding 10 confirms a correct constraint.
