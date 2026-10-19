# notification · T20 · Phase 3 — Design Challenge

Findings from an adversarial review of `artifacts/02-task-implementation-brief.md` (TIB) against `spec/notification-service/agents.md`, the current `spec/notification-service/package.md`, and the payment T29 precedent.

---

## Finding 1 — Editing `spec/notification-service/package.md` is an exception to the normal "do not modify spec" guardrail

- **Issue:** The standing pipeline rules forbid modifying files under `spec/`. T20 explicitly edits `package.md` and adds guard tests. The exception is justified by `tasks.md` task 20 and the payment T29 precedent, but it still needs human sign-off at Phase 4 so it is not mistaken for an accidental spec mutation.
- **Severity:** Medium — process risk.
- **Evidence:** TIB Scope; `spec/notification-service/agents.md`; system pipeline rules.
- **Recommended brief amendment:** Add a one-line rationale to the Phase 4 gate: "This edit is the T20 spec bump and is approved because `tasks.md` task 20 explicitly assigns the `package.md` header/status update to this task, mirroring payment T29."

---

## Finding 2 — The header guard test must not rely on hard-coded line numbers

- **Issue:** A guard that asserts the exact text of lines 6 and 9 will break if a future editor adds rows to the header table, changes column padding, or reorders fields. The guard should parse the key-value table.
- **Severity:** Low — the file is stable, but robustness matters.
- **Evidence:** TIB Required Tests; `spec/notification-service/package.md` lines 3–11.
- **Recommended brief amendment:** Read the header table (the content before `## 0.`) and match `^\| Version \| \`0\.2\` \|` and `^\| Status \| \`READY FOR IMPL\`` within that substring only.

---

## Finding 3 — `READY FOR IMPL` with three partial items may confuse readers

- **Issue:** The status bump declares the spec ready for implementation, yet three §9 items will remain visibly partial. The payment T29 precedent uses the same wording, but the notification spec has more partials (items 1–3). A reader might expect a `READY FOR IMPL` spec to be fully closed.
- **Severity:** Low — convention over clarity.
- **Evidence:** TIB AC; `spec/notification-service/package.md` §9; payment T29 artifact.
- **Recommended brief amendment:** Keep the status as `READY FOR IMPL` but make the Status cell disclosure explicit: `"READY FOR IMPL" for tasks 1–20, with items 1–3 of §9 honestly partial and owned by tracked follow-ups (FU-T07-1/2/4, payment FU-T29-1).`

---

## Finding 4 — §8 named tests have drift that the spec must honestly record

- **Issue:** `shouldNotifyBothPartiesOnInvoiceCreated` and `shouldNotifyWithReceiptLinkOnReceiptIssued` are kept literally in §8, but the actual behaviour is merchant-only and no-link, respectively. Item 3 (§4c VERBATIM artifacts) and item 1 (R5) are therefore partial. If the spec citations use the literal names without explaining the drift, a reader will think the full matrix is implemented.
- **Severity:** Medium — the spec must not misrepresent what the tests prove.
- **Evidence:** TIB expected partials; `spec/notification-service/package.md` §8.
- **Recommended brief amendment:** In the §9 partial notes, explicitly state:
  - Item 1: R5 is partially satisfied; `shouldNotifyWithReceiptLinkOnReceiptIssued` asserts `receiptId` and the absence of a link because the receipt read endpoint is unbuilt.
  - Item 3: the §4c matrix says merchant + payer; the implemented tests prove merchant-only, with payer notification deferred.

---

## Finding 5 — R15 coverage may need a more specific test

- **Issue:** OQ3 recommends adding a literal named test as an aggregate R15 check over a rendered body and a log line. §8 already lists `shouldNotLeakSecretsOrTokensIntoRenderedMessagesOrLogs`, but the TIB implies it may not fully cover both rendered body and logs. If the existing test already does, OQ3 is moot; if not, the spec bump should not claim R15 is closed.
- **Severity:** Low — depends on actual test contents.
- **Evidence:** TIB OQ3; `spec/notification-service/package.md` §8.
- **Recommended brief amendment:** Before ticking item 6 (no secrets/tokens in rendered body or logs), verify that `shouldNotLeakSecretsOrTokensIntoRenderedMessagesOrLogs` inspects both the rendered output and a captured log line. If not, either add the test or keep item 6 partial.

---

## Finding 6 — The resolved notes for Q1–Q7 must cite concrete code, not just task numbers

- **Issue:** The TIB asks each resolved note to cite its implementing task and code. A note that only says "resolved by T07" is less useful than one that names the class/method or the locked decision. Inaccurate citations will become stale as the code evolves.
- **Severity:** Low — documentation quality.
- **Evidence:** TIB Scope item 2.
- **Recommended brief amendment:** For each Q, cite the specific class or decision:
  - Q1: `ContactProjectionUpdater` / `contact_projection` table.
  - Q2: `EmailChannel` / `EmailTransportProperties` / SES.
  - Q3: `InAppNotificationStream` / SSE endpoint.
  - Q4: `LinkProperties` / `TemplateRenderer`; note `AUTH_EMAIL_LINK_BASE_URL`.
  - Q5: no publisher built; delivery log only.
  - Q6: `RetryProperties` / `DeliveryRetryScheduler`.
  - Q7: `PaymentEventConsumer` merchant-only dispatch; `PaymentRouting` projection.

---

## Finding 7 — `tasks.md` task 20 status is not updated by this task

- **Issue:** `spec/notification-service/tasks.md` lists task 20 as the final spec-bump task. The TIB scopes `tasks.md` out, but leaving it untouched means the task tracker does not reflect completion.
- **Severity:** Info — documentation consistency.
- **Evidence:** TIB Files NOT to Modify; `spec/notification-service/tasks.md`.
- **Recommended brief amendment:** Either add `tasks.md` task 20 to the "Files to Modify" list with a small status update, or explicitly record in the Phase 6 notes that `tasks.md` will be updated separately.

---

## Finding 8 — The §4c VERBATIM rule conflicts with recording deviations

- **Issue:** L2 (§4c is VERBATIM) forbids rewriting the original matrix, while the bump must disclose that the implementation deviates from it. The recommended approach (OQ2(b): dated annotations below the verbatim block) is correct, but it needs to be done carefully so the verbatim text is not edited.
- **Severity:** Low — risk of accidental edit.
- **Evidence:** TIB Locked Decisions; `spec/notification-service/design.md` §4c.
- **Recommended brief amendment:** Add a Phase 4 checklist item: "Verify that `design.md` §4c text is byte-identical before and after the edit; only new annotations below it may change."

---

## Summary

T20 is a documentation and guard-test close-out, analogous to payment T29. The most important dispositions at Phase 4 are Finding 1 (spec-edit exception) and Finding 4 (honest drift disclosure for the partial §9 items). Findings 2, 3, 5–8 are robustness, clarity, and consistency notes. No production code changes are needed.
