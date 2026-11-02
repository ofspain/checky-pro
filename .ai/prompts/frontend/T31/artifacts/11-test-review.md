<!-- MODEL: Kimi 2.7 — Phase 11 (Adversarial Test Review) -->

# frontend · T31 · Phase 11 — Adversarial Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T31 — Account status from accounts/me |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx -t "T31:"` | **52/52 passed** |
| `vitest run src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx` | **105/105 passed** |
| `vitest run src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx` | **262/262 passed** |
| `oxlint src` | **exit 0** |
| `python3 -I .ai/prompts/frontend/T31/artifacts/07-mutation-harness.py` | **Refused to run** — baseline 2 failures, expected 1. See A1. |
| `vitest run` (whole suite) | **1505 passed / 2 failed** in `src/api/lintRejectsHandWrittenBackendFetch.test.ts`. See A1. |

No production or test code was changed during this review.

---

## Findings

### A1 — The whole-suite gate and mutation harness are not reproducible in this session
· **Severity:** **Major** (reproducibility / process)
· **Evidence:**
  - Phase 10 reports the whole suite as **1506 passed / 1 failed**, and the mutation harness as **28/28 killed, 0 broken**.
  - In this session, `vitest run` reports **1505 passed / 2 failed**, both failures inside `src/api/lintRejectsHandWrittenBackendFetch.test.ts`:
    1. `passes today's frontend/src through the lint script, running only the two L6 rules (AC4)` — `expected '' to contain 'Found 0 warnings and 0 errors'`.
    2. `stops build at lint, so tsc and Vite never run on a violation (AC2, end to end)` — fails because `tsc --noEmit` rejects `findLastIndex` in `src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx`.
  - The mutation harness refuses to run: `REFUSING TO RUN: baseline is 2 failures, expected 1`.
· **Assessment:** The extra failure is the same `tsc` issue that Phase 9 logged as **OQ-T31-4** and rejected as a T31 finding. Here it surfaces inside an *existing* test that runs `tsc --noEmit` as part of an end-to-end build check. That makes it a gate failure, not merely a red `tsc` command. Phase 10's 28/28 mutation score and whole-suite count could not be independently reproduced here.
  - The first failure (empty oxlint output) appears to be an oxlint summary-line format difference on this host and is likely the same environmental class Phase 9 already described.
  - The second failure validates that the `tsconfig.json` lib ceiling issue has teeth: it breaks the build-gate test, not just a standalone typecheck.
· **Recommendation:** Before declaring T31's test generation complete, re-run the mutation harness in an environment where the whole-suite baseline matches Phase 10's exactly, or update the harness's `EXPECTED_BASELINE_FAILURES` / `KNOWN_ENVIRONMENTAL_FAILURES` to match the real floor. More importantly, resolve OQ-T31-4: either add `"ES2023"` to `lib` (since the code already uses ES2023 APIs) or rewrite `stateSurvivesRedirectAndIsConsumedOnce.test.tsx:718` to avoid `findLastIndex`. Until then, the whole-suite gate is environment-dependent and the mutation score is not independently verifiable.

### A2 — The `confinedTo` place bound is a brittle source-shape pin
· **Severity:** **Minor/Major**
· **Evidence:** `frontend/src/auth/identityErrorsIndistinguishableAcrossAccountStates.test.tsx:263` licenses `profile/readAccount.ts` to name `locked`, `suspended`, and `deleted` only inside `/export const ACCOUNT_STATUSES = \[[^\]]*\]/`. The test at `:1670-1675` removes that construct and asserts no state words remain.
· **Assessment:** The bound is correct and has a dirty-proof, but it is tied to a very specific textual shape. If `ACCOUNT_STATUSES` is ever refactored — e.g. generated from a helper, split across multiple declarations, or moved to a separate module — the regex will no longer match the "licensed construct". The test will then fail with a message about the construct disappearing, and the natural "fix" is to widen the regex or remove the place bound. That would re-open the gap I2 closed without anyone noticing.
· **Recommendation:** Add a comment to the `confinedTo` field and the test that the construct must remain a single literal `export const ACCOUNT_STATUSES = [...]`, and that refactoring it requires moving the license with it. Better: keep `ACCOUNT_STATUSES` in its own tiny module (e.g. `profile/accountStatus.ts`) and license only that module, so the place bound is the whole file and cannot drift with a refactor of `readAccount.ts`.

### A3 — AC1's source-shape case only inspects `readAccount.ts`
· **Severity:** **Minor**
· **Evidence:** `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx:1546-1554` reads `profile/readAccount.ts` and asserts it contains exactly one `/accounts/me` literal, no `fetch`, no assembled path. It does not scan the rest of the production tree.
· **Assessment:** The property being asserted is "the path is a quoted literal and never assembled". MQ06 proved a mutant can assemble the path inside `readAccount.ts` while keeping the same runtime string. But the same mutant could in principle appear in any file that calls `/accounts/me`. Today only `readAccount.ts` is expected to call it, but the test does not enforce that expectation.
· **Recommendation:** Add a tree-wide source scan (bounded to production files) that asserts `/accounts/me` appears only in `profile/readAccount.ts` and only as a quoted literal. This closes the same escape in any future caller, not only the current one.

### A4 — Store-emptiness on success path checks only keys
· **Severity:** **Minor**
· **Evidence:** `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx:1999-2000` asserts `Object.keys(globalThis.sessionStorage)` and `localStorage` are empty after a successful read.
· **Assessment:** This is the needle-free form of the leak claim and is correct for the regression it targets (MQ26: a cache of `String(account.emailVerified)`). However, `Object.keys` being empty is a slightly weaker claim than "no value was written": a write of an empty string under a key would still pass. MQ26 would catch a non-empty value, but not an empty-string value.
· **Recommendation:** Minor. If hardening further, assert `Object.entries(...)` is empty on the success path too, so a key-with-empty-value regression is caught. This is probably overkill given MQ26.

### A5 — AC9 does not drive a 401 with a response body
· **Severity:** **Minor**
· **Evidence:** The AC9 case at `:1882-1903` drives a 401 with an empty body and a `WWW-Authenticate` header. It asserts no header detail leaks. It does not drive a 401 whose body contains detail.
· **Assessment:** `readAccount.ts:196` throws `ReadAccountError('unauthorized')` immediately on 401/403, before `isUsableAccount` touches the body, so a body cannot leak through this path. The test does not exercise that shape, so a future change that accidentally moved the body check before the status check would not be caught by this case.
· **Recommendation:** Add one AC9 sub-case that returns a 401 with a body containing a distinctive detail (e.g. `{ error: 'account suspended' }`) and asserts the detail does not appear in the error message, stack, or swept surfaces. This makes the "status check comes first" ordering part of the test record.

### A6 — The no-session guard cases do not cover all whitespace shapes
· **Severity:** **Minor**
· **Evidence:** `frontend/src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx:2084-2089` tests `null`, `''`, `' '`, and `'\t\n'`.
· **Assessment:** `String.prototype.trim()` covers all Unicode whitespace, so the implementation is correct. The four cases are sufficient to prove the principle. More exotic whitespace (e.g. `\u00A0`, `\u2003`) is unlikely to arrive from the token store and would be over-testing.
· **Recommendation:** None. The case selection is proportionate.

### A7 — The mutation harness count check fires before the name check
· **Severity:** **Minor**
· **Evidence:** `frontend/.ai/prompts/frontend/T31/artifacts/07-mutation-harness.py:319-338`. The harness first checks `failed != EXPECTED_BASELINE_FAILURES`, then checks `files_failed != KNOWN_ENVIRONMENTAL_FAILURES`.
· **Assessment:** In this session, both failures were inside the one known environmental file, so the name check would have passed. But the count check refused first, stopping the run. The design intent is that the count catches a *second* failure inside the known file, which is sensible. In this case, however, the known file has two failing tests on this host, so the count check prevents verification.
· **Recommendation:** Consider whether the count check should allow the known environmental file to contribute any number of failures, while still refusing if an *unknown* file fails. The current design protects against a swap; it also blocks environments where the known test is more broken. At minimum, document that the harness must be run on a host where the environmental test contributes exactly one failure.

---

## What the test generation gets right

- **Phase 8's I2–I5 and I8–I9 are closed with behavioural fixes and new mutants (MQ26–MQ28).** Each fix has a case and a mutant, not only a case.
- **AC5 is held without editing T30's helpers.** T31 defines its own claim pattern and reuses T30's decode-bound helpers.
- **AC7 handles `emailVerified: false` and blank required fields correctly**, with cases and mutants.
- **AC9 was measured against `/accounts/me` itself**, with the live `WWW-Authenticate` value used as the needle.
- **AC10 uses store emptiness as the needle-free claim for the boolean field**, which is stronger than adding `'true'`/`'false'` to the needle list.
- **The T29 place bound and T27 left-bound control are dirty-proofed**, so the cross-task edits do not rot silently.

---

## Summary

T31's test suite is dense and well-instrumented. The single blocker to independent verification is **A1**: the whole-suite gate is environment-dependent because of the pre-existing `lintRejectsHandWrittenBackendFetch` test, which now fails twice in this session (once on oxlint output format, once on the `tsc` `findLastIndex` issue). Until that is resolved or the harness is updated to match the real floor, the 28/28 mutation score cannot be reproduced here. The remaining findings (A2–A7) are smaller hardening items; A2 and A3 are the most worth closing before calling the task complete.

**Phase 11 complete.**
