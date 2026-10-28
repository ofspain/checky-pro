# frontend · T23 · Phase 6 — Implementation Notes

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T23 — PKCE state single-use |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | the production change, the inherited-assertion updates, this artifact |

The production change is nine lines; the rest of this phase is the inherited assertions that measured its absence.
Six of them had to invert, and each one inverting is the evidence the change took effect — T18, T21 and T22 all
pinned "nothing deletes these" **by name as task 23's**, so T23 cannot land without their failing first.

Order, deliberately, as the plan required: **the production change lands in the same commit as the assertions that
measure it.** A commit that changed the screen alone would leave the suite red, and a commit that changed the tests
alone would leave it red the other way, with the reason spread across two commits.

---

## 1 · What was built

**`auth/authorizeRequest.ts`** — `consumeAuthorizeState()` appended (31 lines, 9 of them code). Removes exactly the
three keys of `AUTHORIZE_STORAGE_KEYS`, each inside its own `try`/`catch`, returns nothing, idempotent (Kimi F7's
contract). Per-key guards rather than one block guard: `sessionStorage` has no transaction, so "all-or-nothing" can
only be honest as *one call site, three guarded removals* — in a denying context none of the three is removed, in a
normal context all three are (T23-D3, Kimi F5).

**`auth/CallbackScreen.tsx`** — one call, immediately after `retainAccessToken(response.accessToken)`, with the two
reasons it cannot move written beside it:

- **Not earlier.** The verifier is read *inside* the exchange (`codeExchange.ts:59`) and the nonce after it
  resolves. A deletion any sooner hands a consumer a `null` it cannot recover from, and the discard that followed
  would be indistinguishable from a real failure.
- **Not on any other path.** `/app/callback` is public, so a discard that deleted would let anyone who gets a
  visitor to open `/app/callback?state=x` end that tab's pending sign-in (T23-D2). Keeping the three costs nothing:
  the code behind a failed exchange is already spent server-side — T20 measured `400 invalid_grant` on a replay.

Nothing else in production changed. `idTokenNonce.ts` and `codeExchange.ts` stay pure (T23-D1); only their head
comments were corrected, because both said "deleting them is task 23's" and one said T18 had left a test that
"fails by design the moment task 23 lands". It landed.

## 2 · The six inherited assertions, and why each had to move

Measured, not predicted: the full suite was run with the production change in place *before* any test was edited.
It returned exactly these six failures (plus the one environmental failure in §5), and nothing else.

| File | Case | Was | Now |
|---|---|---|---|
| `tokensAreNeverWrittenToAnyStorage` | the success sweep | `expectThreeAuthorizeWrites`: three writes, zero removals | `expectAuthorizeStateConsumed`: three writes, then the same three keys removed in order |
| `tokensAreNeverWrittenToAnyStorage` | AC2 whole-store equality | the store holds exactly L16's three items | **split in two**: consumed → the store is empty; discarded → it holds all three |
| `tokensAreNeverWrittenToAnyStorage` | after `clearAccessToken()` | the three items survive the clear | the store is empty, because the consumed callback removed them first |
| `tokensAreNeverWrittenToAnyStorage` | the storage allowlist | `auth/authorizeRequest.ts` → `['setItem']` | `['setItem', 'removeItem']`, with the reason in the row |
| `callbackValidatesNonceAndKeepsAccessTokenInMemory` | "leaves the three L16 items exactly as task 15 wrote them (AC11)" | all three present after a held token | "consumes the three L16 items once the token is held", all three `null` |
| `callbackWithMismatchedStateIsDiscarded` | OQ-2, T18's tripwire | the same callback matches twice | it is refused the second time: zero exchanges, and the replay lands on the sign-in route |

**The helper split (T23-D10, Kimi F2).** `expectThreeAuthorizeWrites` asserted "three writes, no removal", which
was true of *every* path until now. It is now `expectAuthorizeWrites` (the three writes, shared) wrapped by
`expectAuthorizeWritesOnly` (discards: zero removals) and `expectAuthorizeStateConsumed` (the consumed path: the
three keys, in order). This keeps T22's discard claim exactly as strong as it was — a discard that deleted still
fails — rather than weakening one assertion to accommodate both paths, which is what a `consumed: boolean` flag
would have invited.

**One case was added, not changed**: the discard counterpart to the AC2 whole-store equality. Without it, "the
store is empty after a consumed callback" would be the only whole-store claim in the file, and T23-D2 — the
decision that discards delete *nothing* — would rest on removal spies alone. Suite total 979 → 980.

**Vacuity controls (Kimi F8).** The emptiness assertions are only worth something if the three items were there to
delete. `startSignIn()` already asserts each is truthy; the AC2 case now also asserts the store holds exactly three
entries before the arrival, so an empty store afterwards is a deletion rather than a flow that wrote nothing.

## 3 · Four assertions that did NOT break, which is the other half of the measurement

- `callbackValidatesNonce.test.tsx:556-577` — the pure check still touches no storage. **This is T23-D1 being
  verified by an inherited test**: had the deletion gone into `idTokenNonceMatchesStoredValue`, this case would
  have failed. Kimi F1 warned against "fixing" it; it needed no fix, only a comment that now says why it passes.
- `callbackWithMismatchedStateIsDiscarded.test.tsx:396-409` and `:1001-1012` — a discarded callback still leaves
  all three in place. T23-D2 in two inherited cases.
- `callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:678-691` — the exchange, called directly, still
  writes and removes nothing.

Their comments all said "deleting them is task 23's" as a *pending* fact. Each now states the standing reason the
assertion survives, so a reader does not go looking for an unfinished task.

## 4 · An intermittent failure found, explained and fixed

`signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx:466` failed **once**, in the first
full-suite run after the production change: `expect(assign).not.toHaveBeenCalled()` — the real `buildAuthorizeRequest`
had run and redirected, so `vi.doMock('./authorizeRequest')` had not taken effect for that render.

Measured before concluding anything:

| Measurement | Result |
|---|---|
| that file alone, with the production change | 31/31, twice |
| that file alone, production change stashed | 31/31 |
| full suite, production change stashed | 1 failed (the environmental one), 978 passed |
| full suite, with the change | failed on run 1, **passed on runs 2 and 3** |

So it is not a consequence of T23's change — but it is real, and the cause is visible in the file: `./SignInScreen`
is imported **statically** at line 13, and the two helpers that `vi.doMock` then `await import('./SignInScreen')`
rely on the registry having been cleared by a *previous* test's `afterEach` (`vi.resetModules()` is in `afterEach`,
not `beforeEach`). The mock's effect therefore depended on test order rather than on the helper. Fixed by calling
`vi.resetModules()` inside each helper, immediately before the dynamic import — the precondition stated locally.
Nothing either case asserts changed. Six consecutive full-suite runs since: clean.

This is a T16-file fix made from T23, which is outside this task's scope and recorded here for that reason. The
alternative was leaving a known intermittent red for the next task to trip over.

## 5 · Verification

- `tsc --noEmit` → exit **0**.
- `vitest run` → exit **1**, `Tests 1 failed | 979 passed (980)`, **six consecutive runs**, the single failure being
  the one known environmental one: `lintRejectsHandWrittenBackendFetch.test.ts:118` (AC4), where this host's
  `oxlint@1.87.0` prints no summary line. Unchanged by T23, and the same failure the baseline shows.
- Exit code read, not pass counts — an unhandled rejection prints *below* the all-passed line.

## 6 · What phase 10 still owns

The named test `stateSurvivesRedirectAndIsConsumedOnce.test.tsx` does not exist yet, and nothing above is a
substitute for it. The six inverted assertions prove the deletion happens on the consumed path and not on the
discard paths; they do not yet prove:

- **AC6, ordering, with a real mechanism** (Kimi F6). The brief's phase-2 hedge that this "may have to be
  structural" was wrong and was corrected at the gate: a deletion before the exchange makes `codeExchange.ts:59`
  read `null` and the success case fail outright, so ordering is behavioural. Phase 10 should pin it with spies —
  the last `getItem` for each key before the first `removeItem` for it — and the mutation harness should carry a
  mutant that moves the call above the exchange.
- **AC7 under StrictMode** (Kimi F4): one handling, one deletion, and — the control Kimi asked for — the second
  pass must not land on the discard route. T23's deletion is what makes the second pass observable for the first
  time, so the `handled` ref's survival stops being an assumption.
- **A storage-denied context**: `consumeAuthorizeState` swallowing a throw, with the visitor's outcome unchanged
  (T23-D4), and all three items still in place.
- **Idempotence**: calling it twice removes nothing more and throws nothing.

## 7 · Carry-forwards, unchanged by this phase

Nothing wires the composition root (`mountShell.tsx`); silent re-authentication has no task; no task owns a
real-browser end-to-end check (IndexedDB, Cache Storage and cookies beyond `document.cookie` stay unprovable
here); `credentials: 'omit'`; CI enforces neither the suite total nor the identity of the `oxlint` failure
(T18 Gap 6); `contracts/api/token-claims.md` is false on email/name; there is no ID-token contract document;
`design.md` §4c's heading is stale; `package.md:172`'s mapping is wrong; `expires_in` is dropped (T21-D2).
