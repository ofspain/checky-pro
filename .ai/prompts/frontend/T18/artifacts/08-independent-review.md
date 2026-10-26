# frontend · T18 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T18 — Mismatched state discarded |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md`, `CallbackScreen.tsx`, `callbackWithMismatchedStateIsDiscarded.test.tsx` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 18):**
> **Mismatched state discarded.** A callback whose `state` differs from the stored value is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## Findings

### I1 · `sessionStorage.getItem` is uncaught; a storage-denial error becomes a blank screen

- **Issue ·** `CallbackScreen.tsx:74` calls `globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state)` without a `try/catch`. Accessing `sessionStorage` can throw `SecurityError` (Safari private / blocked site data / some embedded webviews). Because the application has **no production `ErrorBoundary` or `errorElement`** (`routesResolveUnderAppPrefixOnly.test.tsx:22` only inspects the schema; no source file defines one), the thrown error unmounts the React tree and leaves the visitor with a blank page at `/app/callback` after they have already authenticated.
- **Evidence ·** `CallbackScreen.tsx:74`. `grep` for `ErrorBoundary|componentDidCatch|errorElement` across `src/` returns only the route-schema test. `SignInScreen.tsx:41-47` shows the codebase already handles the reciprocal hazard by wrapping `buildAuthorizeRequest` in `try/catch` and showing neutral copy. T18 is the first production read of storage without such guarding.
- **Recommendation ·** Wrap the read and treat a throw as "nothing stored" — identical security outcome, identical L4-visible outcome:
  ```ts
  let stored: string | null = null;
  try {
    stored = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state);
  } catch {
    // Storage denied. Indistinguishable from an attempt this tab never made.
  }
  ```
  Add a test case stubbing `Storage.prototype.getItem` to throw and asserting discard navigation + no exchange.
- **Confidence · High.** This is a genuine reliability defect, not a style issue. It is also exactly the finding G1 from the self-review, confirming it is not a hallucination.

---

### I2 · The URL in `MISMATCH_SHAPES` is built with unencoded interpolation

- **Issue ·** Lines 662–684 build arrivals with `` `/callback?code=abc&state=${supplied}` ``. Today every `supplied` value is URL-safe (`xyz`, `abc`, `abd`, `ABC`, `abcd`, `bc`), so the tests are correct. A future row containing `&`, `#`, `=`, or `?` would silently change the query structure rather than the state's value, and the test would appear to pass for the wrong reason. The same pattern is used in the AC5 case (line 720) and the AC7 case (line 768), but those currently use `MATCHING_STATE = 'xyz'` and `'something-else'` respectively, which are safe.
- **Evidence ·** `callbackWithMismatchedStateIsDiscarded.test.tsx:665`, `672`, `681`, `720`, `768`.
- **Recommendation ·** Build URLs with `URLSearchParams` or wrap the state with `encodeURIComponent`. Existing rows that deliberately test percent-encoded whitespace must keep their raw values, but the helper should default to encoding so future rows cannot accidentally break the query.
- **Confidence · Medium.** Not a current bug, but the test helper is fragile and the mismatch table is exactly the place where someone will add a weird state value.

---

### I3 · `outcomeOf` clears storage as a hidden side effect

- **Issue ·** The `outcomeOf` helper (line 747) unmounts the tree, drains the notice, and calls `globalThis.sessionStorage.clear()` before returning. The clear is necessary to keep the three sequential calls independent, but a reader has to read the whole function to discover it. A helper named `outcomeOf` reads as pure observation.
- **Evidence ·** `callbackWithMismatchedStateIsDiscarded.test.tsx:747-764`.
- **Recommendation ·** Rename to `outcomeAfterReset` or split into `collectOutcome()` and an explicit `resetForNextOutcome()` call at each call site. No behaviour change.
- **Confidence · Medium.** This is maintainability, not correctness, but the helper is central to the AC7 L4 assertion.

---

### I4 · The "empty on both sides" defence cases pass vacuously for one path and do not cover `stored === ''` with URL `state` trimmed to empty

- **Issue ·** The three "empty state never exchanges" cases at lines 698–709 are valuable, but they miss a subtle variant: `storeState('')` plus a callback whose URL `state` is whitespace-only (`%20` or `%09`). T17's guard trims the URL `state` to `''` and returns before the comparison. The test covers `storedValue = '   '` + URL `%20%20%20`, but not `storedValue = ''` + URL `%20`. That combination would hit the explicit `stored === ''` branch in the T18 guard, and is the most direct test of AC3 ("an empty stored value counts as nothing stored"). The existing AC3 coverage for `storedValue = ''` uses `supplied = 'xyz'`, which exercises the branch but not the edge where both sides are empty after trim.
- **Evidence ·** `MISMATCH_SHAPES` line 652: `['an empty stored value', '', 'xyz']`. The "empty on both sides" block lines 698–702.
- **Recommendation ·** Add a row `['stored empty, supplied whitespace only', '', '/callback?code=abc&state=%20']` to the existing `it.each` at line 698. This pins AC3 against the trimmed-empty URL case directly.
- **Confidence · Medium.** The security property is already covered indirectly, but the explicit branch is not.

---

### I5 · The AC5 case hand-rolls teardown instead of reusing a helper

- **Issue ·** The "flips from discard to exchange" case (lines 719–742) duplicates the unmount/clear/reset notice/doUnmock sequence inline. This is the same duplication flagged in G2. The risk is real: if `afterEach` is later extended (e.g. to clear `localStorage` or reset additional mocks), the AC5 case will drift and may start leaking state between its two arrivals.
- **Evidence ·** `callbackWithMismatchedStateIsDiscarded.test.tsx:728-735`.
- **Recommendation ·** Extract a `resetBetweenArrivals()` helper and use it both here and in `outcomeOf`, so the teardown has a single definition.
- **Confidence · Medium.** It works today; the risk is future drift.

---

### I6 · The `reads the state key and no other once the comparison is reached` case double-writes the state key

- **Issue ·** Line 615 calls `seedAllThree()` (which writes `'stored-state'` under the state key) and then immediately calls `storeState(storedValue)` to overwrite it. The double write is harmless but reads like a mistake and weakens the reader's confidence that the test author intended only one writer.
- **Evidence ·** `callbackWithMismatchedStateIsDiscarded.test.tsx:612-616`.
- **Recommendation ·** Add a `seedNonceAndVerifier()` helper that writes only `nonce` and `code_verifier`, leaving `storeState` as the sole writer of the state key in this case.
- **Confidence · Low.** Pure cleanliness.

---

### I7 · `MISMATCH_SHAPES` row `'the stored value padded with whitespace', ' abc ', 'abc'` is valuable but its failure mode is unclear

- **Issue ·** This row tests that the stored value is not trimmed. If a regression trimmed `stored`, `' abc '.trim() === 'abc'` and the callback would exchange. Good. But the row name says "padded with whitespace" while the supplied value is `'abc'`. A reader might expect the test to supply `' abc '` and store `'abc'` (i.e. the inverse). Both shapes matter: (a) stored padded, supplied not; (b) supplied padded, stored not. Only (a) is present. (b) is already covered by the success-path trim test at lines 575–582, which seeds `MATCHING_STATE` and supplies `%09xyz%0A`, but that test exercises T17's trim, not T18's comparison.)
- **Evidence ·** `MISMATCH_SHAPES` line 657; trim success test lines 575–582.
- **Recommendation ·** Either add a second row `['the supplied value padded with whitespace', 'abc', ' abc ']` or rename the existing row to `['the stored value padded with whitespace', ' abc ', 'abc']` (already the name) and add a comment noting that the inverse is covered by the success-path trim test. Prefer adding the row for symmetry.
- **Confidence · Low.** The property is covered, but the table would be clearer with both directions explicit.

---

### I8 · The early-return no-read assertion seeds all three items but does not assert they survive the arrival

- **Issue ·** The `reads no stored key at all when it returns early` case (lines 596–607) asserts zero reads and zero writes, which is correct for early-return paths. But it does not assert that the seeded values are still present after the discard. AC8 is covered separately for the mismatch path (lines 779–791), but the early-return discard shapes (missing state, error-only, etc.) have no equivalent "items survive" assertion beyond the generic AC7 case at lines 321–334, which uses a missing-state URL and no seeded storage.
- **Evidence ·** Lines 596–607; compare AC8 coverage at lines 779–791.
- **Recommendation ·** Extend the early-return no-read assertion (or add a sibling case) to verify that seeded `state`, `nonce`, and `code_verifier` are unchanged after the discard. This closes the gap that a buggy discard might delete storage even on the early-return path.
- **Confidence · Medium.** The separate AC7 case at line 321 does assert survival, but only for the missing-code path with no seeded state; the other early-return shapes are not covered.

---

### I9 · The whole-suite regression claim depends on an environmental `oxlint` issue

- **Issue ·** The self-review reports "687 passed · 1 failed (688)" and attributes the failure to "the known environmental `lintRejectsHandWrittenBackendFetch` AC4 (this host's `oxlint@1.87.0` prints no summary line)". The implementation notes say the same. This is a process observation, not a code defect, but it means the task cannot be verified green on this host without also fixing or accommodating the environmental lint failure. If the task is merged with that failure still present, future regressions in unrelated tests could hide behind the "known failure".
- **Evidence ·** `artifacts/06-implementation-notes.md:98-100`; `artifacts/07-self-review.md:150-152`.
- **Recommendation ·** Record the environmental failure in this artifact and recommend that phase 9 either (a) confirm the failure is truly environmental and identical on CI, or (b) fix the lint/summary-line issue before merging T18. Do not let a "known failure" become a blanket excuse.
- **Confidence · Medium.** This is meta, but important for a clean gate.

---

### I10 · The positive-control success path is only as strong as the value it seeds

- **Issue ·** Every success-path test seeds `MATCHING_STATE = 'xyz'` and arrives with URL `state=xyz`. This is a good positive control, but it does not test that a *different* matching value also succeeds. A constant-comparison mutant that hardcodes `'xyz'` would survive all these cases. The AC5 case prevents a hardcoded constant by varying the stored value, but it uses `MATCHING_STATE` as the matching value too. There is no case that seeds, say, `'abc-matching'` and arrives with `state=abc-matching`.
- **Evidence ·** `MATCHING_STATE` defined at line 130; all success-path cases use it.
- **Recommendation ·** Add one success-path case with a non-`MATCHING_STATE` value (e.g. `'different-matching-state'`) to prove the comparison is value-agnostic beyond the single hardcoded literal. This is a small strengthening of AC5.
- **Confidence · Low.** AC5 already kills the constant mutant with 30 failures, so this is a belt-and-suspenders suggestion.

---

### I11 · `CallbackScreen.tsx` comment says "reads exactly one stored item, the `state`, and only once the query has been found usable"

- **Issue ·** This is accurate, but the doc comment no longer notes that the screen can throw and leave the user stranded. That is not a finding against the comment — the comment is correct — but it is worth confirming that the "only once the query has been found usable" claim still holds after the storage read is guarded. It does: an error before the read would still fall through to the same discard. If I1 is accepted, the comment should be updated to say the read is guarded and a denial is treated as "nothing stored".
- **Evidence ·** `CallbackScreen.tsx:17-20`.
- **Recommendation ·** If I1 is accepted, append a sentence: "If storage is denied, the read is treated as 'nothing stored', which discards safely and preserves L4."
- **Confidence · Low.** Documentation-only.

---

## Summary

| # | Issue | Severity | Recommendation |
|---|---|---|---|
| I1 | Uncaught `sessionStorage.getItem` can throw, leaving a blank post-auth screen | **High** | Wrap in `try/catch` and treat as "nothing stored"; add test |
| I2 | `MISMATCH_SHAPES` URLs built with unencoded interpolation | Medium | Encode state values by default in URL builder |
| I3 | `outcomeOf` hides a `sessionStorage.clear()` side effect | Medium | Rename or split helper |
| I4 | Missing `stored === ''` vs whitespace-only URL state case | Medium | Add row to "empty on both sides" block |
| I5 | AC5 case hand-rolls teardown | Medium | Extract shared reset helper |
| I6 | Comparison-path test double-writes state key | Low | Use `seedNonceAndVerifier()` helper |
| I7 | Whitespace-padded mismatch table is one-directional | Low | Add inverse row or clarify coverage |
| I8 | Early-return no-read test does not assert seeded items survive | Medium | Add survival assertion for early-return paths |
| I9 | Regression claim rests on environmental `oxlint` failure | Medium | Verify environmental failure before merge |
| I10 | Success path only exercises one matching value | Low | Add one success case with a different matching state |
| I11 | Comment should note guarded read if I1 is accepted | Low | Update class doc |

**Verdict:** The implementation correctly satisfies R8, L4, and L16. The security property is real and well-tested (AC5 kills the constant-comparison mutant decisively). The only material defect is **I1** (uncaught storage read), which the self-review already flagged and which phase 9 should fix. The other findings are maintainability, clarity, or coverage gaps that would improve the suite but do not block the task's core correctness.