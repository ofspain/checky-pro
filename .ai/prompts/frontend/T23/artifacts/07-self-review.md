# frontend · T23 · Phase 7 — Self Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T23 — PKCE state single-use |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` |

Reviewed the Phase 6 diff — nine lines of production code, one call site, and nine files' worth of inherited
assertions — against the frozen brief, `agents.md`, and the decisions T23-D1 … T23-D13. Findings only; fixes are
Phase 9's.

**Nothing in the diff is incorrect against the frozen brief.** The deletion is in one place, on one path, guarded,
spelled once, and after every consumer has read. The findings below are two scope breaches to adjudicate, one
latent coupling, one swallowed-error blind spot, and a status finding that matters more than any of them: **five of
the fourteen acceptance criteria are not verified yet**, because the named test does not exist until Phase 10.

---

## S1 · Five acceptance criteria are not verified, and the green suite does not say otherwise

**Severity.** Medium — status, not a defect, but it is the honest reading of "980 passed".

**Evidence.** The suite proves AC2 (consumed → empty store, `tokensAreNeverWrittenToAnyStorage`), AC3 (replay
refused, `callbackWithMismatchedStateIsDiscarded` OQ-2), AC4 (no discard deletes, across the failure matrix and two
inherited cases), AC9 (allowlist, `clear` never called, no hand-spelled key), AC12, AC13 and AC14. It proves
nothing about:

| Unverified | Why the current suite cannot see it |
|---|---|
| **AC5** a store that throws for every call leaves all three | no case denies storage during a *successful* callback |
| **AC6** deletion only after every consumer has read | the success case is a necessary control, not the ordering assertion F6 asked for; no `getItem`/`removeItem` ordering case and no mutant moving the call |
| **AC7** StrictMode: one handling, **one** deletion, final route not the discard route | no case counts the removals under a double invoke |
| **AC8** a throwing `removeItem` leaves the visitor's outcome unchanged | ditto |
| **AC10** a new sign-in after a consumed callback works end to end | the two-sign-in case in T22 discards the second arrival rather than completing it |

**Recommendation.** Carry these five into Phase 10 as the named test's spine, and do not let the inverted inherited
assertions stand in for them in the Phase 12 verification — they measure the *consequence* of the deletion, not its
ordering, its guard, or its arity.

---

## S2 · AC14's literal wording is violated: two production files outside the two named were edited

**Severity.** Low — comments only, and required by another decision, but AC14 says what it says.

**Evidence.** `codeExchange.ts:95-103` and `idTokenNonce.ts:20-22` were edited. AC14: *"no production change
outside `authorizeRequest.ts` and `CallbackScreen.tsx`."* The edits are head comments, not code: both said
"deleting them is task 23's", and `idTokenNonce.ts` said T18 had left a test that "fails by design the moment task
23 lands". Leaving those in place would have left two production files asserting the task was still pending.

**Recommendation.** Amend AC14 at the Phase 9 gate to *"no production **behaviour** change outside the two named
files; the two pure readers' head comments are corrected under T23-D9, which requires the reason to be restated
wherever it was stated."* `tsc` and the two purity cases are the evidence that nothing else moved.

---

## S3 · A test file belonging to T16 was changed from T23

**Severity.** Low — disclosed in §4 of the Phase 6 notes, and the alternative was worse.

**Evidence.** `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx` gained
`vi.resetModules()` in two helpers. It is not an inherited *assertion* under AC12; it is a robustness fix to an
intermittent failure observed once while measuring T23 (one full-suite run in five; six clean runs since).

**Recommendation.** Record it at the Phase 9 gate as an accepted out-of-scope fix with the measurements, or revert
it and open it as a standalone item. Reverting restores a known order-dependent flake to the suite, so accepting it
is the recommendation — but it should be a gate decision, not a silent one.

---

## S4 · `Object.values(AUTHORIZE_STORAGE_KEYS)` couples the delete set to the writer's constant

**Severity.** Medium — nothing is wrong today; the trap is one task away.

**Evidence.** `authorizeRequest.ts:121` iterates every value of `AUTHORIZE_STORAGE_KEYS` (`:35-39`). That is
correct while the constant holds exactly the three single-use items, and the iteration is deliberate (T23-D3: a
fourth single-use key cannot be left behind). But L16's carve-out has a **fourth** item — the post-login return
target — which is **task 24's**, and the obvious place for task 24 to put its key is that same constant. The moment
it does, `consumeAuthorizeState()` deletes it at callback. Whether that is right depends on when task 24 reads it:
if the return target is read *after* the token is held — which is the natural place, since that is where the
navigation happens — the deletion would land before or after the read depending only on statement order inside this
screen, with nothing failing to say so.

**Recommendation.** Phase 10 asserts `Object.keys(AUTHORIZE_STORAGE_KEYS)` is exactly
`['state', 'nonce', 'codeVerifier']`, so a fourth key forces a decision in a failing test rather than inheriting
this function's behaviour by accident. The function's doc comment already *says* the fourth item is task 24's;
nothing enforces it.

---

## S5 · The guard cannot distinguish a denied store from a missing API

**Severity.** Low.

**Evidence.** `authorizeRequest.ts:122-126` wraps `globalThis.sessionStorage.removeItem(key)` in a bare `catch {}`.
The intended failure is a context that denies site data, which throws on access (T23-D4, and the same shape as
every read in this package). A `TypeError` from `globalThis.sessionStorage` being `undefined` — a packaging or
environment mistake — is swallowed identically, and the result is a flow that silently never deletes while every
assertion about the visitor's outcome still passes.

**Recommendation.** Phase 10's storage-denied case should assert, with a `Storage.prototype.removeItem` spy, that
**three removal attempts were made** and all three threw, not merely that the outcome was unchanged. That
distinguishes "denied" from "never tried", which is the failure this guard would otherwise hide.

---

## S6 · A replayed callback now clears a token the tab legitimately holds, without a server round trip

**Severity.** Low — pre-existing in T17's discard design, but T23 shortens the path to it.

**Evidence.** `CallbackScreen.tsx:78` has `discard()` call `clearAccessToken()` (correct, and R22's reason is
written there). After T23, a second arrival at the same callback URL finds no stored state and discards
immediately — so an in-SPA navigation back to `/app/callback` after a successful sign-in clears the held token.
Before T23 the same arrival re-exchanged, received `400 invalid_grant` (T20 measured it) and discarded anyway, so
the **outcome is unchanged**; what changed is that it now happens locally and instantly.

In a real browser this is largely moot — reopening the callback URL is a full page load, which loses the in-memory
token regardless. It bites only on a router-only navigation back to the callback route.

**Recommendation.** Not T23's to fix: the real remedy is for **task 24** to navigate away from `/app/callback` once
the token is held, so the route is not reachable with a back navigation at all. Record it as a handoff with that
sentence, and let task 24's brief carry it.

---

## S7 · The success path still ends on the holding screen

**Severity.** Low — out of scope, noted because T23 is the last task to touch this screen before task 24.

**Evidence.** After `consumeAuthorizeState()` (`CallbackScreen.tsx:165`) nothing navigates. The visitor who signs
in successfully stays on the callback screen; only discards navigate (`:82`). That is T21's state, unchanged here,
and task 24 owns the post-login target.

**Recommendation.** No change. It is named so that Phase 12's verification does not read "the callback flow is
complete" into a green T23.

---

## What the review found sound

- **One call site, after every read.** The verifier is read inside the exchange (`codeExchange.ts:59`), the nonce
  after it resolves; the call sits after both and after `retainAccessToken`, with both "cannot move" reasons
  written at the site rather than left to a reader to reconstruct.
- **T23-D1's purity held, and was verified by someone else's tests.** Three inherited cases assert the two readers
  take no action on storage; all three still pass, untouched. Had the deletion gone into either reader — the
  tempting place, since each already has the key in hand — they would have failed.
- **T23-D2 is asserted on both sides.** The consumed path's emptiness and the discard paths' survival are each a
  whole-store equality, so neither claim rests on removal spies alone.
- **The helper split strengthens rather than relaxes.** Replacing one "no removals ever" helper with
  `expectAuthorizeWritesOnly` / `expectAuthorizeStateConsumed` keeps the discard claim exactly as strong as T22
  made it; a `consumed: boolean` parameter would have made one assertion weaker for both paths.
- **No new string, no catalogue key, no dependency, no `clear`, no hand-spelled key** (AC9, AC11, AC14). `tsc`
  exit 0; `vitest` 979/980 over six consecutive runs, the one failure being the known environmental `oxlint`
  summary case, read by exit code.

## Open questions for the gate

1. **S2** — amend AC14's wording, or revert two comments that now misstate the state of the code?
2. **S3** — accept the T16 flake fix inside T23's commit, or split it out?
3. **S4** — is pinning the key set to exactly three the right guard, or should `consumeAuthorizeState` take the
   three keys explicitly so task 24 cannot enrol a fourth by accident? The brief chose iteration deliberately
   (T23-D3); this asks only whether the pin belongs with it.
