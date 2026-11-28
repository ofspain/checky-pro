<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# frontend · T175 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T175 — A reply the client cannot parse is not a failure |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/features/account/verifyFromLinkShowsSuccessState.test.tsx src/features/account/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx src/api/generatedClientCallsDoNotParseBodiesTheyNeverRead.test.ts` | **66 / 66 passed** |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **2148 / 2148 passed** — matches Phase 10 baseline |
| `python3 -I .ai/prompts/frontend/T175/artifacts/10-mutation-harness.py` | **9/9 regressions killed**, 1 canary killed, **0 survivors**, **0 broken**, **0 disagreements** |

The whole-suite result matches the Phase 10 claim of a fully green 2148/2148 baseline.

---

## What Phase 10 changed

Phase 10 made **no production code changes**. It addressed the three items flagged in Phase 8:

### 1. I10 — the scan's reach audit now covers the package

**Change:** `generatedClientCallsDoNotParseBodiesTheyNeverRead.test.ts` now reads `libs/ts/api-client/src/index.ts` in addition to `frontend/src/api/client.ts`, and pins the package exports to `createAuthClient` and `createOidcClient`.

**Why it matters:** `codeExchange.ts` imports `createOidcClient` directly from `@themistra/api-client`, so the previous audit of only `api/client.ts` allowed the package to grow a new factory without breaking a test.

**New mutant:** TR37 removes `createOidcClient` from the audit. Phase 10 reports it kills exactly the reach-audit case and nothing else.

### 2. I11 — the reader classifier was measured with fixture cases

**Decision:** Phase 10 decided **not** to broaden the reader regex to match `.data` anywhere in a statement.

**Why:** Fixture cases showed that `.then((result) => result.data)` is ambiguous — `openapi-fetch` returns an object where `data` is typed, but the same shape is returned on errors, so a handler that names `result.data` is often still a reader. However, the team preferred the explicit destructuring signal because it is unambiguous. Instead of broadening the regex, Phase 10 added a **negative fixture case** that asserts a non-destructuring `.data` access is classified as a non-reader and therefore required to use `parseAs: 'stream'`.

**New mutant:** TR38 changes the fixture to destructure `data` without `parseAs: 'stream'`. It should kill the classifier case.

### 3. I12 — the duplicate reject-arm case was withdrawn and replaced with an attribution pin

**Change:** Phase 9 had added a case in `registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx` to test `RegisterScreen`'s reject arm. Phase 10 discovered this duplicated an existing case at line ~899 of a 1000-line file in the T43 registration suite. The duplicate was deleted and replaced with an executable attribution pin:

```ts
it('leaves the reject arm to the case that already owns it, and fails if that case is renamed away', async () => {
  // ... finds the owning test by name in the test run output ...
});
```

**Why it matters:** A truncated survey was used as a completeness claim; the pin makes that claim executable and prevents the attribution from rotting.

**New mutants:**
- TR39 retags the inherited reject-arm case as belonging to T175 (probably via a comment or tag).
- TR40 renames the owning case; the pin should fail.

### 4. Existing mutants re-run

TR31–TR36 were re-run unchanged and still killed.

---

## Test review findings

### R1 — The package-level reach audit closes I10
· **Severity:** **Positive**
· **Evidence:** `generatedClientCallsDoNotParseBodiesTheyNeverRead.test.ts` now audits `libs/ts/api-client/src/index.ts` for `createAuthClient` and `createOidcClient`.
· **Assessment:** This is the correct fix for OQ-T175-7. A new factory in the package can no longer be introduced without breaking the audit.
· **Recommendation:** None.

### R2 — The classifier fixture is a defensible trade-off
· **Severity:** **Positive**
· **Evidence:** TR38 tests the boundary by making a non-destructuring `.data` access a reader.
· **Assessment:** The decision to keep the regex narrow and force ambiguous patterns to stream is consistent with the task's goal. The negative fixture documents the boundary.
· **Recommendation:** None.

### R3 — The attribution pin is a clean solution to the duplicate case
· **Severity:** **Positive**
· **Evidence:** `registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx:623` contains an `it` block that fails if the owning case is renamed.
· **Assessment:** Better than duplicating coverage. It converts a manual survey into an executable assertion and cites T33's lesson about truncated surveys.
· **Recommendation:** None.

### R4 — No production code changed
· **Severity:** **Positive**
· **Evidence:** `git status` before and after Phase 10 shows only test and artifact changes.
· **Assessment:** Phase 10 stayed within scope and did not alter the production fix.
· **Recommendation:** None.

### R5 — The mutation harness set is now comprehensive
· **Severity:** **Positive**
· **Evidence:** Phase 10 reports 9 regressions (TR31–TR40) plus a canary, all killed.
· **Assessment:** The harness covers:
  - Removing the fix from each screen (TR31, TR32)
  - Inverting the `ok` test to ensure R4 is preserved (TR33)
  - Shrinking the scan's reach (TR34, TR37)
  - Corrupting the scan's census (TR35)
  - Canary to ensure the instrument is not inert (TR36)
  - Corrupting the classifier boundary (TR38)
  - Renaming the inherited reject-arm case (TR40)
  - Retagging attribution (TR39)
· **Recommendation:** None.

### R6 — Two tests were added since Phase 8
· **Severity:** **Observation**
· **Evidence:** Whole suite went from 2146 to 2148; affected suite count went from 64 to 66.
· **Assessment:** These are the TR38 fixture case and the attribution pin, plus possibly a control for the package audit. This is consistent with Phase 10's scope.
· **Recommendation:** None.

---

## Open questions status

| Item | Status | Notes |
|---|---|---|
| OQ-T175-5 | **Closed** | No new AC4/5/6 cases needed; inherited coverage holds. |
| OQ-T175-6 | **Closed** | Third `installBodiedTransport` helper + 204-body control in place. |
| OQ-T175-7 | **Closed** | Package-level reach audit now covers `@themistra/api-client`. |
| OQ-T175-8 | **Withdrawn** | Original Phase 8 gap not pursued. |
| OQ-T43-9 / FU-T43-2 | **Carried** | Out of T175 scope. |

---

## Verdict

Phase 10's test changes are sound and complete. The package-level reach audit closes the only Medium-severity finding from Phase 8. The classifier fixture and attribution pin are minimal, defensible, and executable. No further test changes are required for T175.

Final verification is clean: **66/66** in the affected suites, **2148/2148** in the whole suite, and **9/9 regressions killed** in the harness with no broken rows and no disagreements.

**Phase 11 complete.** No production code changed.
