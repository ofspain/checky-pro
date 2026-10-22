# frontend · T05 · Phase 9 — Review Resolution (Human Approval gate)

STATUS: RESOLVED (2026-10-22)

Consumes `07-self-review.md` (6 findings) and `08-independent-review.md` (Kimi 2.7, 8 findings). Each claim was
checked against `sessionBoundary.ts`, the frozen brief and, where behaviour was in question, a scratch probe. None needs
an owner trade-off, so they are settled here, as at the earlier gates.

## Resolution log

| Source | Finding | Decision | Change |
|---|---|---|---|
| P7-1 / P8-1 | A request that waited on a failed renewal starts a second one | **ACCEPTED** (confirmed bug: the probe printed `renewals 2 hooks 2`) | `sessionBoundary.ts`: `onRequest` now records `waitedOnFailedRenewal` (`await inFlight.then(() => false, () => true)`, lines 98–99), stored with the entry (line 108). In `onResponse`, a 401 whose token is unchanged and whose request waited on a failed renewal is returned as is, with no renewal (line 119). If the token *did* change meanwhile, the normal re-send path still applies. |
| P8-2 | A null-token 401 shouldn't renew | **REJECTED (settled at Phase 4, finding 5; premise false)** | None. Renewal is silent re-auth on the SAS session cookie (L3, `agents.md`) and needs no prior token. After a reload the in-memory token is `null` while the SAS session is valid, which is exactly when renewal succeeds. Skipping it would force a full sign-in on every reload. Phase 10 asserts that a null-token 401 renews once. |
| P8-3 | Several boundaries in one tab | **ACCEPTED (documented); the singleton guard rejected** | The `SessionBoundary` doc comment now says production creates exactly one and passes it to every client. A throw-on-second-call guard is rejected: it would be module-level mutable state (forbidden by the frozen Constraints), and it would break test isolation. Phase 10 adds the contrast case: two clients on one boundary renew once; two separate boundaries renew twice. |
| P8-4 | A timed-out renewal may complete later | **ACCEPTED (documented)** | A comment in `withTimeout`. `SessionTokens` has no cancellation, so a late result is ignored by the boundary. If the auth module still updates `current()`, later requests just use the newer token, and the tokens are compared, so nothing re-sends twice. Cancellation, if wanted, belongs to the auth task that implements `renew()`. |
| P8-5 | The hook's error becomes an unhandled rejection; wrap it in `console.error` | **REJECTED (premise false)** | None. `queueMicrotask(() => onRenewalFailed(reason))` makes a throw an **uncaught exception**, not a rejection, and the waiters are unaffected (phase 6 probe). That is the frozen finding 8 choice: it surfaces rather than being swallowed. A `console.error` wrapper would hide a defect in the hook. Phase 10's case captures the error deliberately. |
| P7-3 / P8-6 | The re-send drops `requestInitExt` | **ACCEPTED (documented)** | Comment at the re-send (line 130): `createAuthClient()` sets no init extensions. A future client that does must forward them. |
| P7-4 / P8-7 | The re-send bypasses `onError` | **ACCEPTED (documented)** | The same comment: a transport rejection on the re-send reaches the caller directly. |
| P7-2 | Each wave after a failure renews again | **No change** | Task 33's hook moves to clean re-auth and clears the session. A boundary latch would duplicate that state. |
| P7-5 | The re-send skips other middlewares' `onResponse` | **No change** | Frozen finding 6. Tasks 7–8 put 429 handling inside the boundary. |
| P7-6 | Every protected body is cloned | **No change** | It is required for re-send. MVP bodies are small JSON. |
| P8-8 | The named test doesn't exist yet | **No change** | Phase 10, per the T03/T04 precedent. |

## Verification after the change
- **Scratch probe** (deleted after the run):
  - **Failure case:** A's 401 starts renewal 1. B starts while it is pending, and renewal 1 fails. Result:
    `renewals 1 hooks 1`, both calls 401.
  - **Next wave:** w1's 401 starts renewal 2. w2 starts while it is pending, and renewal 2 succeeds. Result: 2 renewals
    in total, w1 and w2 both 200 (w2 waited, then was sent with the new token).
- `typecheck` exit 0. `lint`: 0 errors over 10 files, 2 rules. `build` exit 0. `test` 54/54.
- `git status`: only `frontend/src/api/sessionBoundary.ts` modified (and the user's untracked `prompt`).

## For phase 10 (on top of the frozen cases)
- The P7-1 regression case: a request that waited on a failed renewal leads to no second `renew()` and no second hook
  call.
- The P8-3 contrast: one shared boundary leads to 1 renewal; two boundaries lead to 2.
- The null-token case asserts one renewal (the P8-2 rejection).
- The throwing-hook case captures the deliberate uncaught error.
