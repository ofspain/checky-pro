# frontend · T21 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T21 — Access token in memory only |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 returned **PASS**, so this phase proceeds. **T21 is complete: 14 of 14 phases.**

---

## Commit title

```
frontend T21: hold the access token in memory, in one variable, and nowhere else
```

## Commit message

```
frontend T21: hold the access token in memory, in one variable, and nowhere else

Add accessToken.ts: one module-scoped `let`, read through `current()`, written
by `retainAccessToken` and cleared by `clearAccessToken`. The callback screen
retains the token the exchange returned - on the far side of T19's nonce check,
never before it - and clears on every discard path.

The holder's interface was not designed here. Tasks 5 and 6 built the session
boundary and the cross-tab renewal against `LocalSession` before anything
satisfied it, so the shape was already fixed: `current()`, `adopt()`, `renew()`.
Neither of those files changed, and that is asserted rather than assumed.

Three decisions worth knowing:

- `renew()` is a seam, not a mechanism. Silent re-authentication is the chosen
  renewal model (R21) and no task in the package implements it, so `renew()`
  rejects with one fixed message that names no token, no status and no server
  detail. It returns a rejected promise rather than throwing, because the
  boundary's `attempt.catch(...)` sees the former and would not see the latter.
- Clearing is a separate export rather than `adopt(null)`. Widening `adopt` to
  `string | null` would have edited code tasks 5 and 6 are built on; a second
  export edits nothing. Task 35 (sign-out) is its next caller.
- `adopt` delegates to `retainAccessToken`, so there is exactly one place a
  non-null token is written. Both names exist for their callers, not for the
  write.

A token held once is a property with no observable difference, so three claims
are proven in the source and the mutants say so: the single mutable binding,
the retain-after-nonce ordering, and the un-widened `LocalSession`. The ordering
mutant - retain the token before the nonce check - passes every behavioural
assertion in the file, because the discard then clears what it retained.

The named test gains both halves it was named for: 69 cases to 108.

Suite 931 of 932, the one failure being the known oxlint-summary environmental
case. 25 mutants, 25 killed. tsc clean.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

| File | Lines | What |
|---|---|---|
| `frontend/src/auth/accessToken.ts` | **new**, 99 | The holder. `let held` at `:24`, `accessToken` satisfying `LocalSession` at `:45-68`, `retainAccessToken` at `:82-84`, `clearAccessToken` at `:97-99`, the seam's message at `:33`. |
| `frontend/src/auth/CallbackScreen.tsx` | +2 | `clearAccessToken()` at `:74` inside the shared discard closure; `retainAccessToken(response.accessToken)` at `:149`, after the nonce check's early return. |
| `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` | +39 cases (69 → 108) | The in-memory half. |
| `.ai/prompts/frontend/T21/artifacts/` | 14 | Pipeline artifacts. |

No contract, no dependency, no catalogue key, no `mountShell.tsx` change (T21-D5). `crossTabRenewal.ts` and
`sessionBoundary.ts` are byte-identical to their committed state.

## Summary

R7's last clause is the task: the access token is held **in memory only**. What makes it worth 39 cases is that
every way of getting it wrong is invisible — a token held in `sessionStorage` behaves identically until someone
reads the storage, a token retained before the nonce check behaves identically because the discard clears it, and
a second copy behaves identically until the first one is cleared and the second is not.

So the suite is built around that: nine failure paths each asserting nothing is held, a stale-token case proving a
forged callback *clears* rather than merely declining to set, the real boundary putting the token on the wire as a
bearer, the real cross-tab listener taking a sibling's broadcast token, and source scans for the properties with
no behavioural shadow.

Two things the pipeline caught that a reading would not have:

- **AC6 was passing against nothing.** `shareRenewalAcrossTabs(accessToken)` with no options looks for
  `navigator.locks`, and jsdom does not have it (measured), so the call took the degraded per-tab branch and
  returned a passthrough. The case proved `current()` delegates; the production listener never ran. It now injects
  a channel and a lock manager and asserts the posted token becomes `current()`, with a control that fails on the
  degraded branch.
- **AC9 as frozen was literally false.** "No second copy anywhere" is false of code already built and on the
  do-not-modify list: the boundary reads `current()` into a local, keeps it in a `Map` and clones a request
  carrying the bearer — and AC5 *requires* that. Corrected as T21-D8 to "T21 introduces no copy of its own and
  holds no durable second copy", which is the claim the suite proves.

## Testing performed

```
$ npx -y pnpm@12.14.0 -C frontend exec vitest run src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx
  Tests  108 passed (108)        exit 0

$ npx -y pnpm@12.14.0 -C frontend exec vitest run
  Tests  1 failed | 931 passed (932)   exit 1
  ↳ src/api/lintRejectsHandWrittenBackendFetch.test.ts:118 (AC4) — this host's oxlint@1.87.0 prints no
    summary line. Environmental, unchanged since T18, the only failure in the run.

$ npx -y pnpm@12.14.0 -C frontend run typecheck
  tsc --noEmit                   exit 0

Mutation: 25 mutants over accessToken.ts and the two T21 lines in CallbackScreen.tsx, run against the named
file plus callbackValidatesNonce and callbackWithMismatchedStateIsDiscarded (365 cases). 25 killed, 0 survived.
```

Exit codes are the evidence, not the counts: an unhandled rejection prints below the all-passed line, and both
the seam's rejection and the 401 path cross that line.

Four mutants carried the weight of a decision:

| Mutant | What it proves |
|---|---|
| **M11** `renew()` throws synchronously instead of rejecting | The boundary's `attempt.catch(...)` would not see a throw. The difference is behavioural, not stylistic. |
| **M21** the screen retains **before** the nonce check | Every behavioural assertion passes; only the source case kills it. This is why AC14 is structural. |
| **M9** `adopt` assigns rather than delegating | Behaviourally identical; dies to the single-binding case alone. |
| **M25** `adopt` keeps only the first token (`??=`) | Kills through the real cross-tab listener, which is the case Phase 11 revealed was not running. |

## Specification references

**Task 21** (`spec/frontend/tasks.md:33`). **Requirements:** R7 (the scoped one), with R19, R21 and R22 touched
only where the holder is their dependency. **LOCKED decisions:** L3 (the scoped one, `design.md:13`), with L13,
L16 and L18 unaffected and re-pinned. **Named tests:** `callbackValidatesNonceAndKeepsAccessTokenInMemory` (this
task's, both halves now built); `callbackValidatesNonce` (T19's, unchanged and green);
`tokensAreNeverWrittenToAnyStorage` (task 22, `tasks.md:34`); `returnTargetOutsideAllowlistFallsBackToHome`
(tasks 24-25, `tasks.md:36-37`). **Decisions frozen here:** T21-D1..D8, in
`artifacts/04-frozen-task-brief.md`, with D8 added at Phase 9.

Branch off `main`; `main` stays deployable. The change is additive — nothing in the running app reads `current()`
yet, because nothing wires the boundary.

## Carried forward

**Needs an owner, and is not T21's to take** (recorded at Phase 4 in the frozen brief's §6, re-raised by Phase 8
I6):
- **The composition root is unwired.** `mountShell.tsx` constructs no `createSessionBoundary`, no `createAuthApi`
  and no `shareRenewalAcrossTabs`. Until something does, the holder, the boundary and the cross-tab module have no
  production consumer. **No task in `tasks.md` 1-39 is visibly that task.** The natural owner is the first task
  that makes an authenticated call — task 30 or 31.
- **Silent re-authentication has no task.** R21 names it as the renewal model and nothing implements it, so every
  renewal fails today by construction.

**To task 33, which owns clean re-auth on failed renewal:** it will receive the seam's `Error` through
`onRenewalFailed`. **It must never render that message** — it is an engineering string and L4 forbids copy that
tells one failure from another.

**To task 35, which owns sign-out:** `clearAccessToken()` is the export it needs; it exists for this reason.

**To task 22, which owns the storage sweep:** T21 proves singleness for the access token. The package-wide sweep
across every storage API is still task 22's, and `tokensAreNeverWrittenToAnyStorage` is still unwritten.

**Open, gating nothing** (unchanged from T20 unless marked): `credentials: 'omit'` on the token request would be a
defensible tightening; CI enforcement of the suite total and of the `oxlint` failure's identity (T18 Gap 6 — still
the one claim in this package asserted from a single host); `contracts/api/token-claims.md` measured false on
email/name; the ID token has no contract document; `design.md` §4c's stale "PROPOSED, pending sign-off" heading;
`package.md:172` mapping `serviceWorkerUpdateWaitsForReload` to R57; **`expires_in` is still dropped, so the first
401 is the only expiry signal** (T21-D2); **`retainAccessToken` validates nothing by design, both callers
validating instead** (Phase 8 I4); and this template's stale `Claude Opus 4.8 (1M context)` trailer, which the
commit message above does not use.

---

## T21 — COMPLETE. 14 of 14 phases. Phase 12 verdict: **PASS**.
