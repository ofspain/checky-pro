# frontend · T17 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T17 — Missing state discarded |
| **Spec section** | Phase 1a — Security properties |
| **Model** | Kimi 2.7 |
| **Consumes** | `frontend/src/auth/CallbackScreen.tsx`, `frontend/src/auth/codeExchange.ts`, `frontend/src/auth/signInNotice.ts`, `frontend/src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx`, `frontend/src/app/routes.tsx`, `artifacts/07-self-review.md`, `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/08-independent-review.md` |

**Task statement (verbatim):**
> **Missing state discarded.** A callback without `state` is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## Review scope

Reviewed the Phase 6 T17 implementation against the frozen brief, `agents.md`, and the Phase 7 self-review. No code was rewritten.

The implementation is clean: a single-effect callback screen, a real (empty) exchange seam for the test to spy on, an in-memory notice, and strong test coverage including StrictMode and source scans. The self-review found six issues; this review corroborates them and adds two fresh observations.

---

## New findings

### 1. The seam rejection is discarded with `void` — this will become an unhandled rejection in task 20
**Issue ·** `CallbackScreen.tsx:48` calls `void exchangeAuthorizationCode({ code, state })`. The `void` operator discards the returned promise. Today the seam is empty and can only resolve, so the bug is latent. Once task 20 adds network I/O, a rejection (offline, 400, clock skew, revoked verifier) becomes an unhandled promise rejection. The visitor stays on "Checking your sign-in…" indefinitely with no navigation and no error path.  
**Severity ·** Medium — the failure mode is real and will land silently in task 20 unless T17 defines it now.  
**Evidence ·** `frontend/src/auth/CallbackScreen.tsx:48`; `frontend/src/auth/codeExchange.ts:18-21`.  
**Recommendation ·** Attach a `.catch()` that routes to the existing discard path: set the notice and navigate to sign-in with `replace: true`. The copy is already neutral (L4), so "could not complete" is the honest message. This costs a few lines and makes the failure mode explicit. Add a test that mocks a rejecting seam and asserts the visitor is returned to `/app/sign-in` with the discarded notice and that storage is untouched.

### 2. No case exercises a whitespace-only `state` or `code`
**Issue ·** This is the concrete test gap behind the self-review's F3. The discard guard rejects `null` and `''`, but `?state=%20` or `?code=%20` produces a whitespace string that reaches the seam. Task 18's comparison will reject it, but T17's stated contract is "empty value counts as missing" and a whitespace value is empty for all practical purposes.  
**Severity ·** Low — the end state is still safe, but the contract and behavior mismatch.  
**Evidence ·** `frontend/src/auth/CallbackScreen.tsx:34-38`; `DISCARD_SHAPES` in the test file does not include whitespace.  
**Recommendation ·** Either add a `.trim()` before the emptiness test and add a whitespace row to `DISCARD_SHAPES`, or narrow the comment to say that only literal empty strings are T17's responsibility. The first is simpler and aligns behavior with the stated reasoning.

### 3. The effect dependency array contradicts the `handled` latch
**Issue ·** The effect declares dependencies `[params, navigate]` but the first thing it does is return if `handled.current` is true. That makes the dependencies inert: a change in `params` will not be handled. The self-review's F2 covers this; the fresh point is that the contradiction is a maintainability hazard — a future edit might remove the latch thinking the deps already handle updates, or key the latch on `params` thinking the deps do not matter.  
**Severity ·** Low — no behavior is wrong today.  
**Evidence ·** `frontend/src/auth/CallbackScreen.tsx:27-49`.  
**Recommendation ·** Reduce the dependency array to `[]` with a comment that one mount handles one callback, and add a test that re-renders the screen with different params and asserts the seam is still called only once (or not at all for the discard path). This makes the design honest.

---

## Corroborated findings (from Phase 7 self-review)

| # | Self-review finding | Independent view |
|---|---|---|
| F1 | `void exchangeAuthorizationCode(...)` discards rejection | **Confirmed — see New Finding 1.** This is the most important defect. |
| F2 | Effect deps and `handled` latch contradict | **Confirmed.** See New Finding 3 for the test angle. |
| F3 | Whitespace-only `state`/`code` passes guard | **Confirmed.** See New Finding 2 for the test gap. |
| F4 | No StrictMode case asserting seam unreached on discard | **Confirmed, low.** Add one case: spy the seam, render a discard shape under `{ strict: true }`, assert `calls` is empty. |
| F5 | `'callback'` literal while `sign-in` got a constant | **Confirmed, cosmetic.** A `CALLBACK_PATH` constant used in `routes.tsx` and `PUBLIC_ROUTE_PATHS` would be symmetric. Mutating it today fails T02/T17 loudly, so it is not a risk. |
| F6 | AC15 negative control pairs by index | **Confirmed, low.** Key violations to labels in one record to prevent misalignment. |

---

## Non-gaps (acknowledged, no change needed)

- **The seam is real and testable**, and the positive control proves the negative assertions are about a reachable collaborator.
- **The verifier stays out of the URL and the return type** — structural, not only scanned.
- **The notice is in-memory and single-read**, with `afterEach` cleanup preventing test leaks.
- **The callback navigates with `replace: true` to `/app/sign-in`**, measured under basename `/app`.
- **The three stored L16 items are neither read nor written** by the callback, asserted by Storage spies and key-set equality.
- **StrictMode is asserted** for the success path (seam called once) and the discard path (navigate once).
- **The source scans forbid `localStorage`, `history.state`, `sessionStorage.setItem`, absolute URLs, and `import.meta.env`** in the new files, with a positive control proving each pattern works.
- **The catalogue scan and copy-property guard enforce neutral copy** (no cause, credential, or account state named).
- **The route table drift guard and cycle property are preserved**.
- **T02's `SCREENS_ATTACHED_SO_FAR` widening is deliberate and counted**.
- **Suite state 608/609** with one known environmental T07 failure (`pnpm` not on PATH) is consistent with prior tasks.

---

## Summary

T17's callback screen is well-implemented and well-tested. The highest-priority Phase 9 fix is the discarded seam rejection (F1 / New Finding 1): add a `.catch()` route to the discard path and test it. The next most important is adding a whitespace case to `DISCARD_SHAPES` (F3 / New Finding 2). The remaining items are small: a StrictMode seam-zero case (F4), clarifying the effect dependencies (F2 / New Finding 3), a `CALLBACK_PATH` constant (F5), and keyed violation records (F6). All are test or code-quality improvements; the security properties are sound.
