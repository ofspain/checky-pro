# frontend · T18 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T18 — Mismatched state discarded |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

---

## Findings

### Finding 1 — Timing-identity claim is false

- **Issue:** The TIB (§Constraints) states "Timing is identical by construction: one string comparison, no I/O, no backend call." This is incorrect. The synchrony of the comparison itself does not make timing *identical* across the two discard branches. The mismatch path reaches line 67, reads `sessionStorage.getItem`, does a string comparison, then calls `discard()`. The missing-state path has already called `discard()` by line 63 before any `getItem` call. Their execution profiles differ by one `sessionStorage.getItem` call — trivially small but not "identical by construction" in the strict sense L4 requires.
- **Severity:** Low. The difference is at most a synchronous `getItem` call and cannot be observed by an attacker on the other side of the network. But "identical by construction" is a false claim and the brief should not assert it.
- **Evidence:** `CallbackScreen.tsx:54-68` — the missing-state discard fires at line 63, before the T18 gap at line 67. The mismatch discard must first reach line 67, call `sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state)`, compare, then call `discard()`. One extra synchronous call separates the two paths.
- **Recommended brief amendment:** Replace "Timing is identical by construction" with "Timing differs only by one synchronous `getItem` call, which is unobservable to an attacker and satisfies L4's requirement that no network I/O or backend call distinguishes the paths."

---

### Finding 2 — AC5 "varying only the stored value" is ambiguous about what must stay constant

- **Issue:** AC5 states "One case varies *only* the stored value between two otherwise identical arrivals and shows the outcome flips." The word "arrivals" is ambiguous. The natural reading is the callback URL — same `code` and `state` in the URL query, different stored values — but the phrase "otherwise identical arrivals" could be read as two navigations to the callback with different URL query strings, which would not prove the comparison is against the stored value. The test must pin exactly what is invariant.
- **Severity:** Medium. If the phase 6 implementor reads "arrivals" as varying the URL `state` between two navigations, the resulting test would not satisfy the anti-tautology requirement — it would compare the URL against itself in two passes and still pass for a constant comparison. The AC as written does not prevent the tautology defect it exists to prevent.
- **Evidence:** AC5's rationale in Phase 1: "this package has shipped the 'assertion compared against its own constant' defect eight times." But the AC itself uses "arrivals" where it should use "callback URL state" to make the invariant explicit.
- **Recommended brief amendment:** Clarify AC5: "One case varies *only* the stored value (via `sessionStorage.setItem`) between two navigations carrying the **same** callback URL `state` and `code`, and shows the outcome flips — from discard to seam-call when the stored value is changed to match."

---

### Finding 3 — AC6 "whitespace in the stored value" is underspecified and untestable without a writer for that shape

- **Issue:** AC6 lists "whitespace in the stored value" among the mismatch shapes that must discard. The TIB's constraints section (§Constraints) says "no trimming of the *stored* value." But T15 (`authorizeRequest.ts`) writes a `randomUrlSafeToken()` — a 43-character base64url string — which by construction contains no whitespace. How is a test to create a stored value with "surrounding whitespace" when T15 never writes one? The TIB must either (a) specify that the test sets it directly via `sessionStorage.setItem`, (b) narrow the claim to whitespace in the *callback* value only, or (c) acknowledge that testing whitespace in the stored value requires the test to bypass the normal writer, and state that explicitly.
- **Severity:** Medium-High. Without a stated mechanism, the phase 6 implementor may omit the case as "impossible to reach in production," which would violate the exact-match requirement. Or they may add a test that writes whitespace-padded values via `setItem` but never document that choice, making the test's deliberate storage injection invisible to a reviewer.
- **Evidence:** `authorizeRequest.ts:72` — `const state = randomUrlSafeToken();` → always a 43-character base64url token with no whitespace. `AC6`: "differing by … by surrounding whitespace in the stored value, or by being a prefix/suffix of the stored value, all discard."
- **Recommended brief amendment:** Add to the Inputs section: "The test may write any string to `sessionStorage` under `AUTHORIZE_STORAGE_KEYS.state` before the arrival — this is the only way to produce prefix, suffix, case-differing, and whitespace-padded stored values, since T15's writer produces only 43-character base64url tokens. The comparison must reject them all."

---

### Finding 4 — `null` vs `''` on the stored side: equality semantics are unresolved

- **Issue:** The TIB states "`null` and `''` are both 'nothing stored' and both discard." But `sessionStorage.getItem` returns `null` when absent and `''` is a stored empty string. AC2 covers "nothing stored" (key absent → `null`) and AC3 covers "an empty stored value" (stored `''`). Both discard. However the TIB does not specify **how** they compare equal. The implementor must decide whether `null === ''` is the comparison or whether both are separately tested. The constraints section warns against `null == ''` coercion, which is the right warning, but does not state the positive rule: "treat `null` (absent) and `''` (explicitly empty) as both absent, and both discard."
- **Severity:** Low. AC2 and AC3 provide separate coverage so either implementation passes, and the risk of `==` coercion producing a match is covered by the constraint. But the brief should state the implementation rule positively rather than relying on negative constraints alone.
- **Evidence:** §Constraints: "Absent storage must never compare equal to a supplied state, which is the trap `null == ''`-style coercion would set." The ACs: AC2 "nothing stored discards" and AC3 "empty stored value discards." There is no positive statement about whether the code does `stored === null || stored === ''` first, or folds both into one comparison.
- **Recommended brief amendment:** Add to Constraints: "Positive rule: check `stored === null || stored === ''` before the comparison. Both are 'nothing stored' and both discard without reaching the string comparison. The comparison against the callback `state` is then `===` between two non-empty strings."

---

### Finding 5 — No stated rule for the stored value being a non-string

- **Issue:** `sessionStorage.getItem` returns `string | null`. But nothing in the TIB or ACs addresses what happens if the value under the `state` key is somehow not a string — e.g., if a serialized object lands there from a bug in another task, or if `sessionStorage` is manipulated by a browser extension. In JavaScript, `typeof`-checking is not needed for the happy path because the API type is `string | null`, but `===` comparison between a string and a non-string value (were one to arrive) would silently return `false`, which is arguably correct (discard) but also arguably a silent hole: the callback would be discarded for a reason the implementor never considered.
- **Severity:** Low. The API contract is `string | null` and Vitest mocks would not inject a non-string. But this is the kind of edge case the adversarial reviewer is meant to surface — the brief is silent on it.
- **Evidence:** `authorizeRequest.ts:82`: `session.setItem(AUTHORIZE_STORAGE_KEYS.state, state);` where `state = randomUrlSafeToken()` → always a string. `AUTHORIZE_STORAGE_KEYS` is the only sanctioned writer. The risk is from a future bug, not from T15.
- **Recommended brief amendment:** Explicitly decide: "The stored value is trusted to be a `string` because `AUTHORIZE_STORAGE_KEYS` is the only sanctioned key writer and T15 writes only strings. No `typeof` guard is required; if the stored value is somehow not a string, `===` comparison returns `false` and the callback is discarded, which is the safe default."

---

### Finding 6 — AC9 "narrow the blanket no-read assertion" has an ordering hazard with the existing test suite

- **Issue:** The TIB says to narrow lines 566–583 from "reads none of the three keys" to "reads `state` and nothing else." But the existing assertion at line 570 is under `describe('the shapes a real authorization server can send')` and asserts no reads for **all paths**, including the success path (line 577 passes `code=abc&state=xyz`). After T18, the success path WILL read the `state` key. The TIB must specify whether this existing case is narrowed or split — the success path's assertion moves from "no reads" to "reads only `state`," while the discard paths (missing state, error-only, etc.) should still assert no reads at all because they never reach the comparison.
- **Severity:** High. If the implementor mechanically widens the assertion at line 580-581 to allow `state` reads but applies it to all paths indiscriminately, the test loses its guard: a regression that reads `state` on the missing-state path (before line 63's guard returns) would pass. Conversely, if they leave the blanket assertion unchanged, the success-path case will fail once the comparison is added.
- **Evidence:** Lines 570-583 of the test file. The success-path case (line 577: `'/callback?code=abc&state=xyz'`) will read `state` after T18. The discard-path cases (DISCARD_SHAPES at lines 126–133) must still read nothing because they never reach line 67. The test as written does not distinguish these paths.
- **Recommended brief amendment:** Split the assertion: (a) a narrowed assertion on the **success path** allowing only `AUTHORIZE_STORAGE_KEYS.state` reads; (b) the existing blanket no-read assertion retained for **all discard paths** (missing state, error-only, error+code, and the new mismatch path — the mismatch still reads nothing in the "nothing stored" case, but for the stored-present mismatch case it DOES read `state`). Add to AC9: "On the mismatch path WITH a stored value present, only the `state` key is read; on the mismatch path with nothing stored, no key is read (the null/empty check fires first). All other discard paths still read nothing — the blanket assertion stays for those."

---

### Finding 7 — The `state` that survives redirect (AC8) is not guarded against a compare-by-reference bug

- **Issue:** AC8 asserts the three items survive a mismatch discard. But the TIB does not pin whether the items must **survive on the keys they were written under**. If the implementation reads the stored value into a local variable, compares, and then (in a future task) deletes the key, a reference bug that mutates the local variable would not be caught: AC8 checks that `getItem` still returns the value, which it would even if the in-memory reference was corrupted. This is not T18's bug to fix (task 23 owns deletion), but the adversarial reviewer notes it in case the implementation is tempted to `state = storedState;` and then mutate.
- **Severity:** Low. `===` string comparison is immutable and a reference bug is unlikely. But the exact-match security property depends on the stored value being compared byte-for-byte; any intermediate transformation would widen the injection surface.
- **Evidence:** §Constraints: "the comparison must be exact: no normalisation, no case folding, no trimming of the stored value, no prefix matching." This forbids transformations but does not explicitly forbid assigning the stored value to an intermediate variable that could be mutated later.
- **Recommended brief amendment:** Add to Constraints: "The comparison reads `sessionStorage.getItem` and compares its return value directly to the callback `state` with `===`. No intermediate variable that outlives the comparison, no transformation, no toLowerCase/normalize/trim."

---

### Finding 8 — AC7 shape-to-shape comparison against T17's missing-state outcome does not account for the T18 path being unreachable in the existing suite until T18 is implemented

- **Issue:** AC7 requires comparing the mismatch outcome "mismatch-shape to missing-shape." But the mismatch path does not exist yet — the gap at line 67 is empty. The TIB says to extend the suite. The implementor must add the mismatch cases AND add the AC7 comparison in the same change, and there is an ordering risk: if they write the AC7 assertion first (before adding the comparison code), it has nothing to assert against; if they write the code first (before AC7), the assertion is not covered. This is a mechanical risk, not a design flaw, but the TIB should note it.
- **Severity:** Low. Standard TDD sequencing resolves this. But the TIB's §Files to Modify says to extend the suite, and the implementor may try to add assertions before the code paths exist.
- **Evidence:** `CallbackScreen.tsx:67`: `// TASK 18 goes here`. The suite has no mismatch cases today. AC7: "The copy, the landed path, the history action and the absence of a query string are byte-identical…"
- **Recommended brief amendment:** Add to the §Files to Modify instructions: "Add the comparison code first, then extend the suite — AC7 asserts the outcome of a code path that must compile and run before it can be measured."

---

### Finding 9 — The "43-character base64url token" assumption is unstated in the TIB but critical for AC6's prefix/suffix tests

- **Issue:** AC6 requires testing prefix and suffix differences. To write a stored value that is a prefix of the callback state, the test needs to know the length of the callback URL's `state` AND the length of a real stored state. The TIB says the stored state is "a 43-character base64url token," but this fact lives only in `authorizeRequest.ts` (`randomUrlSafeToken()`). If the test hardcodes a 43-char string, it silently couples to T15's implementation. If it doesn't, the prefix/suffix tests are poorly constrained.
- **Severity:** Low. The test can use a short literal like `'abc'` as the stored value and `'abcd'` as the callback state for prefix, and `'bcde'` vs `'abcde'` for suffix, without knowing the production length. But the TIB's §Inputs section mentioning "43-character base64url token" sets an expectation that the implementor might try to match that shape in the test, which is unnecessary and fragile.
- **Evidence:** §Inputs: "absent, empty, or a 43-character base64url token." AC6: "prefix/suffix of the stored value, all discard."
- **Recommended brief amendment:** Clarify in AC6: "Prefix/suffix tests use short literal strings (e.g. stored `'abc'` vs callback `'abcd'`) to prove exact-match semantics; they do not need to replicate T15's 43-character production shape."

---

### Finding 10 — The "correct the false claim at line :43" instruction is incomplete

- **Issue:** The TIB says to correct the false claim at `CallbackScreen.tsx:43` ("Task 19 owns telling the visitor anything about the error itself"). The Phase 1 extraction explains the correction: no task displays authorization-server error details, and L4 forbids the distinguishable alternative. But the TIB does not specify what the **corrected** comment should say, leaving the implementor to craft replacement copy without guidance. If the replacement is wrong, it creates a new false claim.
- **Severity:** Medium. The implementor may write a replacement that references a different wrong task number, or says something like "the error is discarded per L4" which while true does not explain why the discard is intentional. The TIB should provide the replacement text.
- **Evidence:** `CallbackScreen.tsx:42-43`: `// Task 19 owns telling the visitor anything about the error itself; T17's only outcome is the discard.` Phase 1: "That is wrong, and I wrote it in T17. Task 19 is 'Nonce validated'. Checked: no task displays an authorization-server error detail at all."
- **Recommended brief amendment:** Specify the replacement comment: `// An authorization-server error response is discarded without distinction. L4 forbids surfacing the error detail, and no task in the package displays it — the discard is the only outcome.` Or, if the brief prefers, provide the replacement text in a `Comment correction` row in the Files to Modify table.

---

### Summary

| # | Issue | Severity | Section affected |
|---|---|---|---|
| 1 | "Timing identical by construction" is false — one extra `getItem` separates the paths | Low | Constraints |
| 2 | AC5 "otherwise identical arrivals" is ambiguous about what is invariant | Medium | AC5 |
| 3 | AC6 requires whitespace-padded stored values that T15 never writes — test injection mechanism unstated | Medium-High | AC6, Inputs |
| 4 | `null` vs `''` comparison rule: positive statement missing | Low | Constraints |
| 5 | Non-string stored value: silent `false` is safe but unstated | Low | Constraints |
| 6 | AC9 narrowing has a path-hazard with the existing success-path vs discard-path assertions | High | AC9, §Files to Modify |
| 7 | No guard against storing the comparison in a mutable variable | Low | Constraints |
| 8 | AC7 ordering risk: comparison code must exist before the shape-to-shape assertion can run | Low | §Files to Modify |
| 9 | 43-character assumption is unnecessary for AC6 prefix/suffix tests | Low | AC6, Inputs |
| 10 | Comment-correction instruction is incomplete — replacement text not provided | Medium | §Files to Modify |

**None of these findings are blockers.** The brief is sound in its architecture and the comparison is well-scoped. The highest-severity finding (F6) concerns the test assertion at lines 566–583 and how it must be split between success/discard paths — the rest are clarifications that a careful phase 6 implementor would resolve on their own, but which the adversarial review exists to surface before the brief is frozen.