# frontend · T23 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T23 — PKCE state single-use |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 returned **PASS**, so this phase proceeds. **T23 is complete: 14 of 14 phases.**

---

## Commit title

```
frontend T23: consume the PKCE state once the callback has used it
```

## Commit message

```
frontend T23: consume the PKCE state once the callback has used it

Add consumeAuthorizeState() to authorizeRequest.ts - the module that
writes L16's three single-use items now also removes them - and call it
from CallbackScreen once the token is held. Nine lines of production
code, one call site, each removal guarded.

The moment is the whole task. The verifier is read INSIDE the exchange
and the nonce after it resolves, so a deletion one line earlier hands a
consumer a null it cannot recover from, and the discard that follows is
indistinguishable from a real failure - the worst shape a bug can take on
this screen. Both "cannot move" reasons are written at the call site.

Deletion happens on the consumed path ONLY. L16 says "deleted at
callback" without qualifying which callbacks; /app/callback is a public
route, so a deleting discard would let anyone who gets a visitor to open
it end that tab's pending sign-in - a denial of service on every sign-in
in progress, triggered by a link. The replay that single-use defends
against is a callback that SUCCEEDED, and that one is now refused with no
network call at all. This narrowing is declared as a deviation in the
Phase 12 artifact, with its price: the strict reading is a one-line move
plus seven test rows.

Add stateSurvivesRedirectAndIsConsumedOnce.test.tsx: 29 cases. Both ways
of getting this wrong are invisible in an end state - a deletion one line
early and a deletion at the right time leave the same empty store - so
the file measures the ORDER and the PATH, not the end state. An ordered
getItem/removeItem log, the exchange's actual code_verifier, seven
discard shapes that keep all three, a store that throws on every removal
and one that throws on exactly one, StrictMode arity, and five direct
cases on the function itself.

Invert the inherited assertions in the same commit as the change that
makes them false: T18's OQ-2 tripwire ("task 23 will make this an empty
array" - it did), T21's AC11 survival case, and four in T22. T22's
expectThreeAuthorizeWrites splits into expectAuthorizeWritesOnly and
expectAuthorizeStateConsumed so the discard claim stays exactly as strong
as T22 made it. Four more inherited assertions deliberately do NOT
change: the two pure readers still touch no storage, which is this task's
purity decision verified by someone else's tests.

Also fix an intermittent failure found while measuring: the T16 sign-in
test's two doMock helpers relied on a previous test's afterEach having
reset the module registry, so the mock's effect depended on test order.
Out of scope, accepted at the Phase 9 gate, measured rather than guessed.

Suite 1008 of 1009 by exit code, the one failure being the known
environmental oxlint-summary case; tsc --noEmit exit 0; 16 of 16 ordering
and scope mutants killed.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

**Production (2 files, 9 lines of code):**
- `frontend/src/auth/authorizeRequest.ts` — `consumeAuthorizeState()` added (+31 lines, 9 of them code; the rest
  is T23-D12's contract: removes exactly the three keys `AUTHORIZE_STORAGE_KEYS` names, idempotent, never throws,
  never clears, never touches another key, and is not a general cleanup helper)
- `frontend/src/auth/CallbackScreen.tsx` — one call after `retainAccessToken`, the head comment rewritten (it
  said "deleting all three is task 23's")

**Production comments only (2 files, 8 lines):**
- `frontend/src/auth/codeExchange.ts`, `frontend/src/auth/idTokenNonce.ts` — both stated, in production, that
  deletion was still task 23's; one said T18 had left a test that "fails by design the moment task 23 lands". It
  landed. The two readers stay **pure** (T23-D1), which three inherited cases assert.

**Tests (5 files):**
- `frontend/src/auth/stateSurvivesRedirectAndIsConsumedOnce.test.tsx` — **new**, 849 lines, 29 cases
- `frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx` — the helper split, the `removeItem` allowlist
  row, the whole-store equality split in two (consumed → empty; discarded → all three), the post-clear case
- `frontend/src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` — OQ-2 inverted, four comments restated
- `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` — AC11 survival → consumption
- `frontend/src/auth/callbackValidatesNonce.test.tsx` — comment only (T23-D9: the case passes unchanged)
- `frontend/src/auth/signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField.test.tsx` — the flake fix
  (T23-D15)

**Artifacts:** `.ai/prompts/frontend/T23/artifacts/00` … `13`.

## Summary

L16 calls `state`, `nonce` and `code_verifier` "single-use and deleted at callback". Until this task nothing
deleted them, and three test files said so **by name as task 23's** — T18 even left a tripwire whose message read
"task 23 will make this an empty array". This task makes them an empty array.

The implementation is deliberately small and in one place: the module that writes the three removes them, from a
single call site in the screen, after every consumer has read. What took the work was establishing *where* that
call may stand and *which* arrivals may make it, because neither constraint is visible in an end state. The
ordering is pinned behaviourally — an ordered read/removal log, and the `code_verifier` the exchange actually
sent — and the path is pinned by seven discard shapes plus the case the decision exists for: a stranger's arrival
at the public callback route is discarded, the three survive, and the real sign-in still completes.

Six inherited assertions inverted in the same commit as the production change, because a commit that changed only
one side would leave the suite red with the reason spread across two commits. Four others deliberately did not
change, and that is evidence rather than absence: had the deletion gone into either pure reader — the tempting
place, since each already holds the key — those four would have failed.

## Testing performed

| | |
|---|---|
| **named test** | `stateSurvivesRedirectAndIsConsumedOnce.test.tsx`, **29 passed (29)**, exit 0 |
| **whole suite** | **1008 passed of 1009**, read **by exit code**; the single failure is the known environmental `oxlint`-summary case (`lintRejectsHandWrittenBackendFetch.test.ts:118`), identical to the pre-change baseline |
| **typecheck** | `tsc --noEmit` exit **0** |
| **mutation** | **16 killed of 16** on ordering and scope, baseline 442 tests / exit 0. M2 (above the exchange) 44 failures, M3 (above the nonce check) 36, M5 (only a discard deletes) 49, M13 (a fourth key joins the constant) 9, M17 (a second caller) 35 |
| **the one survivor** | M16, moving the call above `retainAccessToken`: semantically identical, 0 failures either way. Argued in the manifest **and** in the test file, not excused — killing it would require the structural assertion AC6 was corrected away from |
| **flake** | the T16 sign-in case was measured in isolation (31/31 twice), with the change stashed, and over six consecutive full-suite runs before being called fixed |
| **reviews** | Kimi Phase 8: nine findings, all accepted, every cited `file:line` verified first, two claims corrected on the facts. Kimi Phase 11: six gaps, all accepted — four became cases or controls, one a comment, one a handoff |

## Specification references

- **Task:** `spec/frontend/tasks.md:35` — task 23, PKCE state single-use
- **Requirements:** **R8** (`requirements.md:25-26`) — a missing or mismatched `state` discards without
  exchanging; "single-use" is its reason for deleting
- **LOCKED decisions:** **L16** (`design.md:58-60`) — the only sessionStorage carve-out; its three existing items
  are single-use and deleted at callback. Honoured for every item that exists; the fourth, the post-login return
  target, is task 24's (`tasks.md:36-37`)
- **Named tests:** `stateSurvivesRedirectAndIsConsumedOnce` (new), `callbackWithMismatchedStateIsDiscarded`
  (inherited, OQ-2 inverted)
- **Standing rules:** `spec/frontend/agents.md` — unviolated; no token moved, no copy added, no dependency added
- **Declared deviations** (Phase 12 §2): deletion on the consumed path only (T23-D2); "survives the full-page
  redirect" verified structurally, not end to end (T23-D5)

## Carried forward

**Opened by this task:**
- **T23-D16** → task 24: navigate away from `/app/callback` once the token is held, so the route is not reachable
  by back/forward navigation while a token is held. Until then, an in-SPA return to the callback route clears the
  held token — the same outcome as before T23, reached locally instead of via `400 invalid_grant`.
- **Gap 5** → task 24: either navigate **after** `consumeAuthorizeState()` returns, or read the return target
  **before** the deletion point. Adding that key to `AUTHORIZE_STORAGE_KEYS` enrols it in the deletion, which the
  key-set pin now fails on with a message saying what to decide.

**Still unowned by any task** (unchanged, and all four predate T23): the composition root (`mountShell.tsx`) is
wired by nothing; silent re-authentication has no task; **no task owns a real-browser end-to-end check** — the
largest verification gap in this package, and the only place IndexedDB, Cache Storage, a real cookie jar and a
real full-page redirect can be proven; `credentials: 'omit'`.

**Open items from earlier tasks:** CI enforces neither the suite total nor the identity of the `oxlint` failure
(T18 Gap 6); `contracts/api/token-claims.md` is false on email/name; there is no ID-token contract document;
`design.md` §4c's heading is stale; `package.md:172`'s mapping is wrong; `expires_in` is dropped (T21-D2).

**One process note:** the Phase 13 prompt template still asks for a `Claude Opus 4.8 (1M context)` trailer. The
trailer used here is the one the repository's own commits carry. The template is generated by `.ai/generate.py`
and should be corrected there rather than by hand.

---

## T23 — COMPLETE. 14 of 14 phases. Phase 12 verdict: **PASS**.
