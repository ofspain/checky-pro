# frontend · T18 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T18 — Mismatched state discarded |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` |
| **Reviewed** | `CallbackScreen.tsx` (102 lines) and `callbackWithMismatchedStateIsDiscarded.test.tsx` (882 lines, 126 cases) |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 18):**
> **Mismatched state discarded.** A callback whose `state` differs from the stored value is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

Findings only; fixes are phase 9's.

---

## Findings

### G1 · The storage read can throw, and nothing catches it — unlike T15's equivalent

**Severity · Medium.** The one finding here I would act on.

**Evidence ·** `CallbackScreen.tsx:68` inside the effect; compare `SignInScreen.tsx:41-47`.

**Issue ·** `globalThis.sessionStorage.getItem(...)` is not merely a call that can return `null` — **touching
`sessionStorage` at all can throw.** A browser with site data blocked, or Safari in a configuration that denies
storage to the context, raises a `SecurityError` on property access, and some embedded webviews do the same.
T18 put that call inside a `useEffect` with no `try`.

**There is no error boundary in this application.** A grep for `ErrorBoundary`, `componentDidCatch` or
`errorElement` across `src/` returns **one test file and no production source**. So a throw here unmounts the
tree and the visitor gets a blank page at the end of a sign-in — the worst possible place for one, because they
have already authenticated and have no way back.

**T17 did not have this exposure** because its callback screen touched no storage, and **T15 already handles the
same hazard the other way:** `buildAuthorizeRequest` writes three items unguarded, but its caller wraps the call
in `try/catch` and shows neutral copy (`SignInScreen.tsx:41-47`). So the codebase has a precedent, the precedent
is the opposite of what T18 did, and T18 is the first uncaught storage access in the application.

**Recommendation ·** Wrap the read and treat a throw as "nothing stored", which discards:

```ts
let stored: string | null = null;
try {
  stored = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.state);
} catch {
  // Storage denied. Indistinguishable from an attempt this tab never made, which is the honest reading.
}
```

That is also the correct *security* outcome — if the stored state cannot be read, there is no evidence the
callback belongs to this tab, so it must not be exchanged — and it is L4-neutral, since the visitor gets the
same sentence as every other discard. A case can drive it by stubbing `Storage.prototype.getItem` to throw.

### G2 · The AC5 case hand-rolls teardown and module reset inside its body

**Severity · Low.**

**Evidence ·** the `flips from discard to exchange when only the stored value is changed to match` case.

**Issue ·** To run two arrivals with two separate seam spies in one case, the body unmounts the first router,
drains the notice, calls `vi.resetModules()` and `vi.doUnmock('./codeExchange')` — six lines duplicating what
`afterEach` does, in the middle of the most security-relevant assertion in the file. It works, and the mutation
sweep proves it bites (N7 killed it with 30 failures), but the duplication is the kind that drifts out of step
with `afterEach` when `afterEach` changes.

**Recommendation ·** Extract a `resetBetweenArrivals()` helper used by both this case and `afterEach`, so the
teardown has one definition. No behaviour change.

### G3 · `outcomeOf` clears storage as a side effect of collecting a value

**Severity · Low.**

**Evidence ·** the AC7 block's `outcomeOf` helper.

**Issue ·** A function named for *reading* an outcome also unmounts the tree, drains the notice and calls
`sessionStorage.clear()`. The clear is needed so the next call starts from a known state, but a reader has to
notice it to understand why three sequential calls do not interfere. A name that admits the teardown, or an
explicit reset between calls, would say so.

### G4 · The three empty-state cases pass today through T17's guard, not through T18's code

**Severity · Info**, but worth stating so nobody misreads them.

**Evidence ·** the `an empty state never exchanges, whichever guard catches it` block.

**Issue ·** Those cases were added precisely because they assert the property rather than the path, and that is
their value — but it means **they do not exercise T18's `stored === ''` test at all today**: T17's guard returns
first. Mutant N3, which removes `stored === ''`, still leaves all 126 cases passing. The cases are load-bearing
only against the *two-part* mutant N9, which relaxes both guards. A reviewer who expected them to pin T18's own
branch would be wrong, and the block's doc comment should say which mutant makes them earn their place.

### G5 · The split's comparison-path case writes the state key twice

**Severity · Info.**

**Evidence ·** `reads the state key and no other once the comparison is reached` calls `seedAllThree()` and then
`storeState(storedValue)`.

**Issue ·** The first write puts `'stored-state'` under the state key and the second immediately overwrites it.
Harmless, and `seedAllThree` is there to make the *nonce* and *verifier* present so the no-read assertions are
about restraint rather than an empty store — but the double write reads as a mistake.

**Recommendation ·** A `seedNonceAndVerifier()` helper for this case, leaving `storeState` the only writer of
the state key.

### G6 · `MISMATCH_SHAPES` interpolates supplied values into URLs unencoded

**Severity · Info.**

**Issue ·** Rows build `` `/callback?code=abc&state=${supplied}` ``. Every current value is URL-safe, so this is
correct today. A future row containing `&`, `#` or `=` would silently change the query's shape rather than the
state's value, and the row would appear to pass for the wrong reason.

**Recommendation ·** `encodeURIComponent(supplied)`, except in the rows that deliberately test percent-encoded
whitespace.

---

## Known and accepted, not findings

**N2 and N3 are equivalent mutants.** Removing `stored === null` or `stored === ''` changes no observable
behaviour, because neither value can equal a `state` the guard above has already proved non-empty. They are kept
deliberately, the code says why, and what they defend is a coupling between two guards rather than a behaviour
of their own. Phase 6 records the measurement that justified keeping them — including the hole that the
measurement exposed and the three cases that closed it.

## Checked and clean

- **Exactness (the security property)** — `===` only; no `trim` on the stored value, no case folding, no
  `normalize`, no prefix, suffix or substring matching. Enforced by a six-pattern source scan, each pattern
  carrying its own synthetic violation, and by mutants N4, N5 and N6.
- **Non-vacuity** — AC5's mutant N7 (compare against a constant) fails 30 cases, so the comparison is provably
  against storage. The nine-row mismatch table has a positive control. The AC7 outcome comparison asserts the
  collected outcome is non-empty before comparing three of them for equality.
- **L4** — the mismatch outcome is compared **byte-for-byte to the missing-state outcome**, on text, pathname,
  search and history action, rather than to the catalogue. T18 added no string.
- **L16** — one key read; `nonce` and `code_verifier` asserted unread on every path; nothing written, removed or
  cleared.
- **Idempotency** — T17's `handled` ref still makes the effect single-shot; T18 added no module state.
- **Module boundaries** — the key comes from `AUTHORIZE_STORAGE_KEYS`, with a case asserting the literal
  `'themistra.auth.'` appears nowhere in the screen. The new import does not deepen the circular import, and the
  module-scope scan now covers `AUTHORIZE_STORAGE_KEYS` as well as `SIGN_IN_PATH`.
- **Secret handling** — the state is never logged; the code is never logged; no copy names a cause.

## Suite state at review

**687 passed · 1 failed (688)**, delta **+54**. T18's file is **126/126**. The failure is the known
environmental `lintRejectsHandWrittenBackendFetch` AC4. `tsc` and `oxlint` clean. Nine mutants run: seven
killed, two equivalent by construction and documented.

## Open questions

**OQ-2** — T18 reads without consuming, so a correct callback URL compares equal more than once until task 23
deletes the items. Now observable rather than hypothetical. Recommend it stays task 23's.
**OQ-1** is implemented as AC2. **OQ-3** (unreviewed copy) unchanged.

---

## Next

**Phase 8 — independent review. This is Kimi 2.7's phase, and I am stopping here for it.**
