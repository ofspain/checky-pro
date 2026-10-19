# payment · T29 · Phase 3 — Design Challenge

Findings from an adversarial review of `artifacts/02-task-implementation-brief.md` (TIB) against `spec/payment-service/agents.md`, `spec/payment-service/package.md`, and the crypto T29 precedent.

---

## Finding 1 — Editing `spec/payment-service/package.md` is an exception to the normal "do not modify spec" guardrail

- **Issue:** The standing pipeline rules forbid modifying files under `spec/`. T29 explicitly asks to edit `package.md` and add a test guard. While the crypto precedent and `tasks.md` task 29 justify this, the exception needs human sign-off at Phase 4 so it is not mistaken for an accidental spec mutation.
- **Severity:** Medium — process risk, not technical risk.
- **Evidence:** TIB OQ1; `spec/payment-service/agents.md` (spec is authoritative); system pipeline rules.
- **Recommended brief amendment:** Add a one-line rationale to the Phase 4 gate: "This edit is the T29 spec bump and is approved because `tasks.md` task 29 explicitly assigns `package.md` header/status update to this task, mirroring `spec/crypto-service/package.md`."

---

## Finding 2 — The header guard test must tolerate minor markdown table formatting changes

- **Issue:** A guard test that asserts the exact text of lines 6 and 9 in `package.md` will break if a future editor adds a blank line, changes column padding, or reorders the header table rows. The guard should parse the key-value table, not hard-code line numbers.
- **Severity:** Low — the file is stable, but the test should be robust.
- **Evidence:** TIB Required Tests; `spec/payment-service/package.md` lines 3–11.
- **Recommended brief amendment:** Have the test read the header table with a regex such as `\\| Version \\| \\`(0\\.2)\\` \\|` and `\\| Status \\| \\`READY FOR IMPL\\`.*?\\|`, or parse the table into a map. Avoid `Files.readAllLines(...).get(5)`.

---

## Finding 3 — `READY FOR IMPL` status with a known unbuilt requirement is semantically awkward

- **Issue:** The service implementation is complete except for the R28 receipt-read half. Bumping the spec status to `READY FOR IMPL` suggests the spec is ready to be implemented, yet implementation has already happened. The crypto precedent uses the same wording, but the Status line currently carries a long historical note that explains this.
- **Severity:** Low — convention over clarity.
- **Evidence:** TIB OQ3; `spec/payment-service/package.md` line 9; `spec/crypto-service/package.md` line 9.
- **Recommended brief amendment:** Keep the status as `READY FOR IMPL` (crypto precedent) but replace the historical note with a concise disclosure: `READY FOR IMPL` for tasks 1–29; R26 deferred; R28 receipt read endpoint unbuilt and tracked as a follow-up. Move the detailed history to a footnote if needed.

---

## Finding 4 — The §9 checklist citations are not mechanically enforced

- **Issue:** The brief says every cited test name will be checked with `grep "void <name>("` before commit. That is a manual step. A typo or later rename would leave the spec citing a non-existent test without failing the build.
- **Severity:** Low — the guard test only watches the header, not the checklist.
- **Evidence:** TIB Constraints; `spec/payment-service/package.md` §9.
- **Recommended brief amendment:** Consider a lightweight CI script or a one-off test that parses the cited test names from `package.md` §9 and asserts each `void <name>(` exists in `services/payment/src/test/java/`. If that is too heavy for T29, record the manual `grep` command in the Phase 6 notes so it is reproducible.

---

## Finding 5 — `tasks.md` task 29 status is not updated by this task

- **Issue:** `spec/payment-service/tasks.md` is the source of the task list. After T29 lands, task 29 should arguably be marked done. The brief scopes `tasks.md` out, but leaving it untouched means the task list does not reflect completion.
- **Severity:** Low — documentation consistency.
- **Evidence:** TIB Scope / Files NOT to Modify; `spec/payment-service/tasks.md`.
- **Recommended brief amendment:** Either add `tasks.md` task 29 to the "Files to Modify" list with a small status update, or explicitly record in the Phase 6 notes that `tasks.md` will be updated in a separate admin commit.

---

## Finding 6 — The cited test count `532/532` is not guarded against future drift

- **Issue:** The TIB cites a `verify` result of 532/532. Once T29's guard test is added, the count becomes 533. If a future task adds or removes tests, the spec's citation of the exact count will become stale unless someone remembers to update it.
- **Severity:** Info — exact counts are common in these artifacts.
- **Evidence:** TIB AC1; `spec/payment-service/package.md` §9 (after edit).
- **Recommended brief amendment:** In the spec text, cite the test count as "532/532 at commit `b8a654e8`" rather than an unqualified current count, so future readers know it is a snapshot. Update the T29 guard-test count to 533 in AC6.

---

## Finding 7 — Partial item 13 should not be ticked, but its wording may be misunderstood

- **Issue:** Item 13 covers the cross-service "second matching transaction produces no further event" proof. The payment half is proven, but the full end-to-end against real crypto-service unregister behaviour is unproven due to a known crypto defect. The brief says keep it "visibly partial." A partial item that is not ticked but has a long explanation is fine; however, a reader might still assume the requirement is satisfied because the payment half is green.
- **Severity:** Low — communication risk.
- **Evidence:** TIB AC2; `spec/payment-service/package.md` §9 item 13.
- **Recommended brief amendment:** Use a clear prefix such as `[~]` or `[-]` for partial items, with the label "Partial — payment half proven, cross-service half blocked by crypto-service unregister defect (FU-...)." Avoid `[x]` on any sub-claim that is not fully proven.

---

## Summary

T29 is a documentation and guard-test close-out. The most important disposition at Phase 4 is Finding 1: confirm the spec-edit exception. Findings 2–7 are robustness, clarity, and consistency notes that should be addressed before the spec bump lands. No production code changes are needed.
