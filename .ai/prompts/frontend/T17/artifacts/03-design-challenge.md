# frontend · T17 — Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T17 — Missing state discarded |
| **Spec section** | Phase 1a — Security properties |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

**Task statement (verbatim):**
> **Missing state discarded.** A callback without `state` is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## Scope and method

This is an adversarial review of `artifacts/02-task-implementation-brief.md` for **T17 — Missing state discarded**. Findings are against the brief itself; no code was changed.

The brief is unusually concrete, which makes the design challenge easier in some ways and harder in others: many decisions are already made, so the review must test whether those decisions hold up under the project's standing rules and the task's security purpose.

---

## Findings

### 1. Module-scoped `signInNotice` state is shared across tests and visits
**Issue ·** `signInNotice.ts` keeps `pending` in a module-level variable. Vitest runs tests in the same process and reuses the module graph; a test that sets a notice and does not consume it, or a test that consumes partially, will leak state into the next test. The same risk exists in production if two callback navigations occur before the sign-in screen mounts, or if a test mocks navigation without rendering the target screen.  
**Severity ·** Medium — test isolation is a real concern; in production the window is narrow but not impossible.  
**Evidence ·** Brief §`src/auth/signInNotice.ts`: `let pending: SignInNotice | null = null;`.  
**Recommendation ·** Lock a cleanup rule in the frozen brief: every test that sets a notice must either render the sign-in screen to consume it or call `takeSignInNotice()` in `afterEach`. For production safety, document that the notice is single-read and intentionally lost on reload; if the owner requires durability across reload, use `sessionStorage` (which would need an L16 carve-out change) or `history.state` (with explicit expiry).

### 2. The `AuthorizationCodeGrant` seam type is too narrow for T20
**Issue ·** `codeExchange.ts` declares `type AuthorizationCodeGrant = { code: string; state: string }` and an empty `exchangeAuthorizationCode`. T20's token exchange will need at least the `code_verifier` (from sessionStorage), the `redirect_uri`, and possibly `client_id`. If the seam type stays this shape, T20 will have to either read those itself or change the type, which breaks T17's tests.  
**Severity ·** Medium — the seam is meant to make T20's job easier, but a too-narrow seam may require a breaking refactor.  
**Recommendation ·** Decide now whether the seam owns reading the remaining parameters from sessionStorage/constants, or whether callers pass them. Two stable options:
- **Option A (preferred):** `exchangeAuthorizationCode` takes `{ code, state, codeVerifier, redirectUri }`, and the callback reads `code_verifier` from storage before calling. This keeps T17's spy meaningful and T20 pure.
- **Option B:** Keep `{ code, state }` and let `exchangeAuthorizationCode` read `code_verifier`, `redirect_uri`, and `client_id` internally. This makes T17's spy simpler but hides dependencies that matter for testing.
Either is acceptable; the frozen brief should lock one.

### 3. Treating a missing `code` as a discard is sensible but should be locked
**Issue ·** R8 speaks only of missing `state`. The brief discards when `code` is missing or empty too. The alternative — calling the seam with a null code — is worse, and the type forbids it. But this is an extension of the requirement that should be explicit.  
**Severity ·** Low — the right call, but should not be accidental.  
**Recommendation ·** Add a locked decision: "A callback without `code` is treated as a discard; the seam is never called with an invalid grant." Include it in the AC table and the test cases.

### 4. The navigation path `/${SIGN_IN_PATH}` assumes basename handling
**Issue ·** The brief shows `navigate(`/${SIGN_IN_PATH}`, { replace: true })` where `SIGN_IN_PATH = 'sign-in'`. With `basename: '/app'`, React Router should resolve this to `/app/sign-in`, but the exact behavior depends on version and whether the leading slash is treated as absolute or relative to basename. If it resolves to `/sign-in`, the route will not match and the visitor lands on a 404 or the catch-all.  
**Severity ·** Low — likely correct, but the test should prove it rather than assume it.  
**Evidence ·** Brief §`CallbackScreen.tsx` line 101; `frontend/src/app/routes.tsx` uses `basename: APP_BASENAME`.  
**Recommendation ·** Add an AC/test that asserts the post-discard URL is exactly `/app/sign-in` (not `/sign-in`) when running under the memory router with `basename: '/app'`. If the navigation target is wrong, the test fails before the screen copy is checked.

### 5. The empty seam is correct for T17 but creates a temporary behavior
**Issue ·** `exchangeAuthorizationCode` is empty. T17's "success" case calls it to prove the seam is reachable. In a deployed build between T17 and T20, a callback with a `state` present would navigate nowhere and leave the user on a holding screen while an empty async function runs. T18 is supposed to land before T17? The brief says "task 18 adds the comparison" and "task 18 lands first."  
**Severity ·** Low — deployment ordering is an owner/CI concern, not a code defect, but the brief should be explicit.  
**Recommendation ·** Record the deployment assumption in the frozen brief: "T17 must not be deployed without T18 (state comparison) and T20 (token exchange). Until then, the seam is intentionally empty and the success path is a no-op."

### 6. Not deleting stored items after discard (OQ-2) should be settled now
**Issue ·** The brief leans toward leaving `state`, `nonce`, and `code_verifier` in sessionStorage after a discard, with AC8 pinning that they are still present. This is a security/usability trade-off: stale PKCE material persists in the tab until it closes or is overwritten.  
**Severity ·** Low — not a blocker, but a latent decision.  
**Recommendation ·** Lock OQ-2 in the frozen brief with the chosen behavior and rationale. For example: "Discard does not delete the three L16 items; T15's next request overwrites them, and premature deletion would complicate task 23's diagnostics. If the owner decides otherwise, the change is localized to the discard branch and AC8 reverses."

### 7. The `useRef` latch pattern is right but should be asserted, not assumed
**Issue ·** The brief uses a `handled` ref to survive StrictMode double invocation. This is the correct pattern, but if a future edit removes the latch, the suite might still pass under a non-StrictMode test renderer.  
**Severity ·** Low.  
**Recommendation ·** Add a test that renders `<CallbackScreen />` under `StrictMode` (or manually invokes the effect twice) and asserts the seam is called exactly once for the success case and the navigation happens exactly once for the discard case.

### 8. No source scan forbids `history.state` or `localStorage` in the new files
**Issue ·** The brief's Constraints list says no absolute address / no `import.meta.env`, but it does not explicitly forbid `history.state` or `localStorage` in the new files. The implementation correctly avoids them, but a source scan would catch a drift.  
**Severity ·** Low.  
**Recommendation ·** Add required source-level assertions: `CallbackScreen.tsx`, `signInNotice.ts`, and `codeExchange.ts` contain no `localStorage`, no `history.state`, no `sessionStorage.setItem` except in `CallbackScreen.tsx` (which is allowed), and no absolute URL literals.

### 9. The `signInNotice` union type is premature
**Issue ·** The brief defines `export type SignInNotice = 'discarded'` with a comment that task 18 may add a mismatch notice. Today the only value is `'discarded'` and L4 forbids distinct copy. The union adds no type safety yet and may never grow.  
**Severity ·** Info.  
**Recommendation ·** Either keep it and document that it is a forward-compatibility hook, or simplify to `type SignInNotice = 'discarded'` only when task 18's scope is known. The frozen brief should state the choice so task 18 does not treat the type as a commitment.

### 10. T17 modifies four existing files; the ripple should be tracked
**Issue ·** The brief modifies `SignInScreen.tsx`, `routes.tsx`, `catalogue.ts`, and `routesResolveUnderAppPrefixOnly.test.tsx`. This is necessary but increases coupling. AC11 says T01, T02, T13, T16 green; the brief does not explicitly require re-running the full suite, only the named tasks.  
**Severity ·** Low.  
**Recommendation ·** Add a regression obligation: after Phase 6, run `vitest run` and confirm no earlier task regressed. The brief already implies this for T01/T02/T13/T16; make it explicit.

---

## Open Questions

- **OQ-1** is resolved by the in-memory notice; the only remaining concern is test isolation.
- **OQ-2** should be locked: delete stored items on discard or leave them; either is fine, but the choice must be explicit.
- **OQ-4** should be locked: missing `code` is a discard.
- **OQ-3** (copy) is correctly left to the owner.
- The seam type and navigation path resolution are design decisions that should be locked before Phase 6.
