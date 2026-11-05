<!-- MODEL: Kimi 2.7 — Phase 11 (Adversarial Test Review) -->

# frontend · T32 · Phase 11 — Adversarial Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T32 — `email_verified` is not account state |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

## Verification performed

| Command | Result |
|---|---|
| `vitest run src/auth/emailVerifiedClaimIsNotAccountState.test.ts` | **27/27 passed** |
| `python3 -I .ai/prompts/frontend/T32/artifacts/07-mutation-harness.py` | **14/14 killed, 0 broken, 0 survivors** |
| `oxlint src` | **exit 0, no output** |
| `tsc --noEmit` | **exit 0** |

No production or test code was changed during this review.

---

## Findings

### A1 — Scan helpers are still duplicated instead of shared
· **Severity:** **Minor/Major**
· **Evidence:** T32's test file reimplements `strip`, `refuseEmpty`, `sourceRoot`, `productionSources`, `hitsOf`, `synthetic`, `stanza`, `literalMask`, and the AC7 surface-sweep helpers. The file header calls this deliberate and logs the cost as OQ-T32-4.
· **Assessment:** The duplication is bounded and intentional, but it is still the largest maintenance debt in this task. A bug fix or behavior change in T29/T30/T31's helpers (e.g. URL stripping, test-file exclusion, surface collection) will not propagate to T32. The justification — "a helper edited for one task would silently change what another task's scan means" — is true, but the opposite failure mode is also real: divergence makes the scans inconsistent and hides regressions.
· **Recommendation:** Move the scan helpers to a shared test-utility module such as `src/testing/scanHelpers.ts`, versioned or collectively owned, before the next task adds a third or fourth copy. This has been recommended in both Phase 3 and Phase 8 and remains open.

### A2 — AC8 is still a self-referential word-count guard
· **Severity:** **Minor**
· **Evidence:** `:663-669` checks that the file header contains `source-side`, `not data-flow guards`, `forward guard`, and `OQ-T32-1`.
· **Assessment:** The test does not prove the limitation is *understood*; it only proves four substrings are present. A rewrite that keeps the words but garbles the reasoning around them would pass. Phase 10's header comment acknowledges this explicitly, which is honest, but does not make the guard stronger.
· **Recommendation:** Accept it as a disclosure guard and document that fact in the artifact, or replace the four substring checks with a single required sentence that must appear intact. The current implementation is fine for a disclosure, but should not be mistaken for a behavioral assertion.

### A3 — AC3's anti-coercion regexes are broader than `emailVerified`
· **Severity:** **Minor/Major**
· **Evidence:** `:532-540` forbids `/emailVerified\s*:/`, `/emailVerified\s*=(?!=)/`, `/Boolean\s*\(/`, and `/!!/` anywhere in `readAccount.ts`.
· **Assessment:** The `/Boolean\s*\(/` and `/!!/` patterns are not scoped to `emailVerified`. A future legitimate use of `Boolean(value)` or `!!value` for any other field in `readAccount.ts` would fail this test, even if `emailVerified` is untouched. The failure would force the author to revisit the test, which may be the intent, but it couples the test to the entire file's expression vocabulary rather than to the field it cares about.
· **Recommendation:** Narrow the coercion checks to `emailVerified`, e.g. `/!!\s*account\.emailVerified|account\.emailVerified\s*\?\?\s*true|Boolean\s*\(\s*account\.emailVerified\s*\)/`. This preserves the signal while reducing false positives on unrelated fields.

### A4 — `literalMask` does not handle template-literal expressions
· **Severity:** **Minor**
· **Evidence:** `literalMask` at `:198-224` marks every character inside backticks as literal, including the contents of `${...}` expressions.
· **Assessment:** A pathological file could contain a real `/userinfo` call inside a template expression, e.g. `` const s = `${api.GET('/userinfo')}`; ``. The `.GET(` would be inside the `${...}` block, but `literalMask` would mark it as literal because it does not treat `${` and `}` as code regions. `readsUserinfo` would then return `false`, and a file that names `email_verified` in the same module could be flagged as off-channel. The reverse — a forged channel inside a template expression — is also possible.
· **Recommendation:** Extend `literalMask` to skip `${...}` blocks inside template literals, treating their contents as code rather than literal. Add a dirty-proof case that exercises this shape. This is a corner case today, but template-literal expressions are common enough to be worth closing.

### A5 — AC3 is a source pin, not a runtime proof that `emailVerified` survives unchanged
· **Severity:** **Minor**
· **Evidence:** The new case at `:517-541` checks `readAccount.ts` source for `typeof account.emailVerified === 'boolean'`, `return data;`, and the absence of construction/coercion patterns.
· **Assessment:** This is the right trade for T32 — driving it would duplicate T31's transport harness — but it means the test does not exercise the actual function. A module that passes the source scan but still mutates `emailVerified` through some pattern not in the forbidden list would not be caught. The header acknowledges this as a source-side guard.
· **Recommendation:** No code change needed, but the Phase 10 artifact should make explicit that AC3's third clause is held by source inspection, not by invoking `readAccount`. This prevents a future reader from assuming the case runs the function.

### A6 — No mutant covers the path-constant `/userinfo` limit
· **Severity:** **Minor**
· **Evidence:** Case 17 at `:485-496` documents that `api.GET(USERINFO)` is not recognized and would be flagged as off-channel. There is no corresponding mutant in the harness.
· **Assessment:** The limit is documented and tested as a control, but the mutation harness does not prove it. A regression that accidentally made `USERINFO_CALL` match path constants would not be caught by mutation; it would be caught by the control case, but the harness is the stronger signal.
· **Recommendation:** Add a mutant that introduces `const USERINFO = '/userinfo'; api.GET(USERINFO);` and assert it survives (i.e. is flagged as off-channel). This makes the documented limit a measured, rather than asserted, boundary.

### A7 — `USERINFO_CALL` does not cover bracket-notation or destructured calls
· **Severity:** **Minor**
· **Evidence:** `USERINFO_CALL = '\\.GET\\(\\s*[\'"]/userinfo[\'"]'` at `:278`.
· **Assessment:** Unusual call shapes such as `api['GET']('/userinfo')` or `const { GET } = api; GET('/userinfo')` would not be recognized as channel reads. These are not the normal openapi-fetch calling convention, and TypeScript may not even type-check them, but they are syntactically possible.
· **Recommendation:** Document these as accepted limits in the file header, alongside the path-constant limit. No code change is necessary unless the project allows such call styles.

### A8 — `TOKEN_DECODE` can match decode-looking text inside string literals
· **Severity:** **Minor**
· **Evidence:** `strip()` keeps string literals, and `TOKEN_DECODE` includes `split\(\s*[\'"]\.[\'"]\s*\)`. A production file whose only occurrence of `split('.')` is inside a string literal would be counted as a decoder, causing AC1's "exactly one file" test to fail.
· **Assessment:** This is a false-positive failure mode, which is safer than a false negative. The test would need to be updated, but it would not silently miss a decoder. The control case 8 documents the accepted gap for a decoder named only `decode`.
· **Recommendation:** None. The current behavior fails safe. Document that the pattern intentionally errs on the side of false positives.

### A9 — MR05's 154 failures remain a coarse signal
· **Severity:** **Informational**
· **Evidence:** MR05 breaks the nonce decode and causes ~154 suite failures.
· **Assessment:** The self-review S4 and Phase 10 note this. The mutant is legitimate as an allowlist lower-bound pin, but its kill signal is undiscriminating.
· **Recommendation:** None. Record in the artifact that MR05 is a structural pin, not a precision kill.

---

## What the test generation gets right

- **Phase 8's findings I1, I2, and I3 are closed with code and new mutants.** `USERINFO_CALL` is now a regex, `TOKEN_DECODE` allows formatter-spaced splits, and `literalMask` prevents string/comment spoofing of the channel.
- **AC3's third clause now has an instrument** and MR14 proves it fires.
- **Every acceptance criterion is covered** and the manifest is explicit about which case covers which criterion.
- **The mutation harness is independently reproducible**: 14/14 killed, 0 broken, with the baseline correctly pinned by name.
- **MR12 and MR13 are the right way to record a finding that was taken into code**: each mutant would have survived the phase 7 instrument and is killed by the phase 9 repair.
- **MR11 remains inherited-only**, documenting that verification-state caching is T31's concern.
- **The limitation disclosures are explicit and tested**, so a future reader cannot mistake a green scan for a present-tense UI proof.

---

## Summary

T32's test suite is now well-hardened. The mutation harness and all gates pass independently. The remaining items are maintenance and precision issues rather than correctness gaps: **A1** (helper duplication) is the main debt, **A3** (over-broad coercion regexes) is the most likely to cause a future false positive, and **A4** (template-literal expressions) is the one remaining corner case in the channel detector. None of these block the task, but A1 should be addressed before the package grows further.

**Phase 11 complete.**
