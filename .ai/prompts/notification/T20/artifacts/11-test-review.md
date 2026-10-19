# notification · T20 · Phase 11 — Test Review

Gaps found by reviewing the Phase 10 test manifest and the final `T01SkeletonRegressionTest` against the frozen acceptance criteria.

---

## Gap 1 — No guard verifies the §11 Q1–Q7 resolved notes

- **Why it matters:** The spec bump's AC2 depends on dated, attributed resolution notes for Q1–Q7. A future editor could remove a date, task citation, or resolved conclusion while editing §11 for another reason, and no test would fail.
- **Suggested test:** Add a lightweight regex guard that each `Q1.` through `Q7.` block in §11 contains a "Resolved" marker, a date, and a task/class citation. Skip Q8 because it is intentionally open.

---

## Gap 2 — No guard verifies that `FU-T20-1` is tracked outside the spec

- **Why it matters:** `FU-T20-1` (the in-app verify template should drop its link) is mentioned in the spec header, item 6, and the new deviation-note guard. If the follow-up is resolved in code but the spec is not updated, the spec would stay partial forever. Conversely, if it is never added to `tasks.md`, it may be forgotten.
- **Suggested test:** Not a code test, but add a manual/PR checklist item requiring the follow-up to appear in `tasks.md` or a project tracker. A stronger guard could parse `tasks.md` for `FU-T20-1`.

---

## Gap 3 — No test pins the template version that carries the verification link

- **Why it matters:** `shouldNotLeakTheVerificationTokenIntoRenderedMessagesOrLogs` pins the current behaviour (token appears once in each channel's body, inside its link). It does not assert that the in-app body comes from the V3 `user.verify` template. If a future migration adds a V4 template without the link, the test would fail in a confusing way because it expects the link.
- **Suggested test:** Add an assertion that the rendered in-app body's template version is `1` or `3` (whatever carries the link today), or query `templates` to confirm `user.verify` IN_APP has no V2. This makes the follow-up boundary explicit.

---

## Gap 4 — No guard detects a duplicated or missing §8 named test

- **Why it matters:** `everyTestNamedInTheTestPlanExists` proves each of the 20 §8 names maps to an enabled test. It does not prove the list has exactly 20 entries, that they are unique, or that no requirement is listed twice. A copy-paste error could duplicate an entry and still pass.
- **Suggested test:** Assert that the parsed §8 names have size 20, are distinct, and cover R1–R19 plus L11 (e.g., by parsing the `→ R#` suffixes).

---

## Gap 5 — No guard verifies the partial notes also exist in `design.md` / `requirements.md`

- **Why it matters:** The spec bump keeps `package.md` honest, but the underlying `design.md` and `requirements.md` also contain the R3/R5/L2/§4c text. If the deviation notes in those files are accidentally removed, `package.md` would be accurate but the authoritative design docs would be stale.
- **Suggested test:** Add source-text assertions that `design.md` contains the L2 interpretation note and the §4c "Recipients as shipped" note, and that `requirements.md` R3/R5 contain a `Partial` or `merchant-only` qualifier. This is optional but would close the loop across the three spec files.

---

## Gap 6 — No test verifies the payment opt-out E2E covers the in-app delivery path

- **Why it matters:** `PaymentEmailOptOutIntegrationTest.shouldSuppressOptedOutPaymentEmailButStillDeliverInApp` is the cited test for AC5. Its name implies both behaviours, but the manifest does not describe its assertions. If the test only asserted email suppression without checking in-app delivery, the name would over-promise.
- **Suggested test:** Not a new test, but document the assertion list in the Phase 10 manifest, or add a unit test that asserts the in-app outcome is `SENT` when email is opted out.

---

## Summary

T20's test suite is very strong after Phase 10: it guards the header, the §9 citations, the partial items, the §8 named-test list, the §4c verbatim hash, and the dated deviation notes. The highest-value addition is Gap 1 (§11 resolution guard). Gaps 2–6 are smaller tracking, versioning, and cross-file consistency notes.
