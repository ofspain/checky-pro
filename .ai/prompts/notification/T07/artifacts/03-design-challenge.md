# notification · T07 · Phase 3 — Design Challenge

Findings from an adversarial review of `artifacts/02-task-implementation-brief.md` (TIB) against `spec/notification-service/agents.md`, `spec/notification-service/design.md` §4c, and `spec/notification-service/requirements.md` R3–R5.

---

## Finding 1 — The baseline scope silently deviates from the §4c VERBATIM event→recipient matrix

- **Issue:** `design.md` §4c says all four payment events notify **merchant + payer**. The TIB baseline limits Phase 1 to merchant-only for `invoice.created` and `receipt.issued`, and leaves `payment.seen`/`payment.finalized` conditional on OQ2. That is a material change to a VERBATIM artifact, not just an implementation detail.
- **Severity:** High — it changes the user-facing contract and the seeded template/recipient logic.
- **Evidence:** `spec/notification-service/design.md` lines 35-38; TIB Scope, OQ1–OQ2.
- **Recommended brief amendment:** At Phase 4, either (a) confirm the deviation as a product decision and update `design.md` §4c to match, or (b) keep the matrix and implement payer notification now. Until then, add a bold note in the brief: "This baseline deviates from `design.md` §4c pending Q7/OQ1–OQ2 disposition."

---

## Finding 2 — Hand-written DTOs conflict with the `agents.md` generated-models rule

- **Issue:** `agents.md` states that deserialization models are generated from `contracts/` and are never hand-written, so schema mismatches fail a contract test rather than production delivery. The TIB explicitly creates four hand-written `consumer/dto/*Event` records. While T06 (`UserLifecycleEvent`) set a precedent, that precedent is not a locked exception.
- **Severity:** Medium — increases schema-drift risk and maintenance cost.
- **Evidence:** `spec/notification-service/agents.md` Events & messaging; TIB "Consumer and DTOs".
- **Recommended brief amendment:** Either (1) generate the payment event models from `contracts/events/payments/*.v1.schema.json` and delete the hand-written DTO plan, or (2) add a strong contract test that asserts every DTO field matches the schema and that unknown fields are ignored, and record the deviation as a locked exception in §4a.

---

## Finding 3 — `invoiceId` in `eventData` does not exist in the consumed event schema

- **Issue:** AC1 requires `eventData` to contain `invoiceUuid`, `invoiceId`, and `amount`. The `payments.invoice.created` schema has `invoiceUuid` but not `invoiceId` (there is no separate display identifier). Either the DTO must synthesize `invoiceId` from `invoiceUuid`, or AC1 is asking for a field the producer does not provide.
- **Severity:** Medium — the acceptance criterion is not directly satisfiable from the event.
- **Evidence:** TIB AC1; `contracts/events/payments/invoice-created.v1.schema.json` (implied); `spec/payment-service/invoice/event/InvoiceCreatedPayload.java`.
- **Recommended brief amendment:** Clarify whether `invoiceId` means the same value as `invoiceUuid`, a different field to be added to the event, or a typo. Do the same for `invoiceId` in AC3 (`receipt.issued`).

---

## Finding 4 — `payment.seen` / `payment.finalized` merchant notification needs a projection that L2 forbids

- **Issue:** The `payments.payment.seen` and `payments.payment.finalized` payloads carry `paymentUuid` (and `invoiceUuid` for `payment.seen`) but not `merchantAccountUuid`. Notifying the merchant therefore requires a local projection from prior events. TIB L2 says "no live call; the contact projection is the only projection." Adding an invoice→merchant projection is a new projection and needs an explicit L2 interpretation.
- **Severity:** Medium — without OQ2(a) approval, the baseline cannot satisfy R4/R5-style merchant notification for these events.
- **Evidence:** TIB L2, OQ2; `spec/payment-service/payment/event/PaymentSeenPayload.java`; `spec/payment-service/payment/event/PaymentFinalizedPayload.java`.
- **Recommended brief amendment:** State the default Phase 4 decision explicitly: if OQ2(a) is not granted, `payment.seen` and `payment.finalized` dispatch **nothing** (only dedupe/log) for this revision, and merchant notification for them becomes a follow-up. Do not leave AC2/AC4 ambiguous.

---

## Finding 5 — The receipt link has no working endpoint to resolve to

- **Issue:** OQ4 acknowledges that the receipt read endpoint is unbuilt (`FU-T29-1`). AC3 still asks to include a receipt link. Rendering a link that 404s when clicked degrades merchant trust. The TIB says "render the provisional link and disclose it," which papers over a broken UX.
- **Severity:** Medium — user-facing broken link.
- **Evidence:** TIB AC3, OQ4.
- **Recommended brief amendment:** Either (a) defer `receipt.issued` notification entirely until the read endpoint exists, (b) render a placeholder URL with clear copy like "receipt available soon" and no clickable link, or (c) confirm the product accepts a 404 link for now and document it in the template copy.

---

## Finding 6 — `payment.finalized` has no requirement ID and no decided recipient

- **Issue:** AC4 says `payment.finalized` handling is "per OQ5," but OQ5 just says "follows OQ2." There is no R-number for `payment.finalized`, so it is unclear whether it should notify anyone in Phase 1. The §4c matrix lists it as merchant + payer, but the baseline defers payer notification and makes merchant notification conditional on OQ2.
- **Severity:** Medium — scope ambiguity.
- **Evidence:** TIB Business Rules, AC4, OQ5; `spec/notification-service/design.md` §4c.
- **Recommended brief amendment:** Give `payment.finalized` an explicit Phase 4 decision: dispatch exactly like `payment.seen` if OQ2(a) is granted, or skip entirely (dedupe only) if not. Add the decision to the Open Questions table.

---

## Finding 7 — Existing templates reference fields the payment events do not provide

- **Issue:** OQ3 says `{{currency}}` / `{{amount}}` have no source in the payment event schemas (the events carry `amount` as a decimal string but not `currency`). The recommendation is to seed `version=2` templates that drop those placeholders. If OQ3 is not granted, the renderer will fail or render blank fields.
- **Severity:** Medium — blocks any dispatch if templates are inconsistent with event data.
- **Evidence:** TIB OQ3; `spec/notification-service/design.md` §4c.
- **Recommended brief amendment:** Make the `version=2` template seed part of the baseline (not conditional), because the baseline cannot render otherwise. If the existing `invoice.created` template already works for auth, still add payment-specific `version=2` templates in a `V12__payment_notification_templates.sql` migration.

---

## Finding 8 — Test name `shouldNotifyBothPartiesOnInvoiceCreated` assumes payer notification is in scope

- **Issue:** The required test name says "both parties," but the baseline defers payer notification (OQ1). If the Phase 4 decision is merchant-only, the test will be misleadingly named.
- **Severity:** Low — naming clarity.
- **Evidence:** TIB Required Tests, OQ1.
- **Recommended brief amendment:** Either rename the test to `shouldNotifyMerchantOnInvoiceCreated` now, or keep the name and add a comment/disclosure that "both parties" is the design intent and payer notification is deferred. Do not let the name drift from the actual assertion.

---

## Finding 9 — Event payloads may carry PII that must not appear in INFO logs

- **Issue:** The `payments.*` schemas include blockchain addresses (`payToAddress`, `fromAddress`, `toAddress`) and merchant/payer identifiers. AC6 requires INFO logging with no PII when no recipient is resolvable. The TIB does not describe how the consumer will avoid logging those fields.
- **Severity:** Medium — security/standing-rule violation if mishandled.
- **Evidence:** `spec/notification-service/agents.md` Security; TIB AC6; payment event payloads.
- **Recommended brief amendment:** Specify that the consumer logs only the source event key/topic and a sanitized event identifier (e.g., `invoiceUuid`/`paymentUuid`), using the existing `SecretSafeLogging` utility. Never log the full JSON payload at INFO.

---

## Summary

T07's biggest design risks are the deviation from the §4c recipient matrix (Finding 1), the hand-written DTO decision (Finding 2), the missing `invoiceId` source (Finding 3), and the L2 projection conflict for `payment.seen`/`payment.finalized` (Finding 4). These need explicit Phase 4 decisions before implementation. Findings 5–9 are smaller scope, UX, and security clarity notes.
