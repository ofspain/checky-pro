# frontend · T21 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T21 — Access token in memory only |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

**No code in this phase.** Every file below traces to the frozen brief §4.

---

## Files to create

### `frontend/src/auth/accessToken.ts`

The holder. Named for what it holds rather than for a pattern, matching `signInNotice.ts` and `idTokenNonce.ts`
in this directory.

Its doc comment carries the justification, as `signInNotice.ts:1-12` does for its own module state — **this file
is the one place in the package where a module-level `let` is the requirement rather than a smell**, and a reader
who does not find that written down will either delete it or copy it somewhere it does not belong. The comment
states: the token is bearer credential material; L3 forbids storage, logs and analytics; L16's carve-out is
exhaustive and names no token; a tab reload therefore ends the session, which is correct and is what silent
re-authentication exists to recover from.

### Public surface

```ts
/** The tab's access token, and the LocalSession the client boundary and the cross-tab renewal consume. */
export const accessToken: LocalSession;

/** Retains the token from a completed exchange. */
export function retainAccessToken(token: string): void;

/** Drops the held token. Idempotent; safe with none held. */
export function clearAccessToken(): void;
```

`accessToken` is a single object literal satisfying `LocalSession` (imported from `../api/crossTabRenewal`, not
re-declared — `sessionBoundary.ts:4` sets the precedent of deriving a type from its owner rather than restating
it). Its three members:

| Member | Behaviour |
|---|---|
| `current()` | Returns the held token, or `null`. `null` is the ordinary state, not an error (R22). |
| `adopt(token)` | Replaces the held token unconditionally; the last writer wins (T21-D4). |
| `renew()` | **The seam.** Rejects with one fixed `Error` whose message names no token, no status and no server detail (T21-D1, AC16). |

`retainAccessToken` and `adopt` do the same thing to the same variable. **Both exist on purpose**, and that is a
decision Phase 7 should re-examine rather than a redundancy to tidy: `adopt` is the cross-tab contract's word for
it, and the callback screen is not adopting anything from a sibling. One implementation, two names at the
boundary, so neither caller has to read as the other.

**Module state:** exactly one `let held: string | null = null;`. Nothing else.

## Files to modify

| File | Change |
|---|---|
| `frontend/src/auth/CallbackScreen.tsx` | `retainAccessToken(response.accessToken)` after the nonce check passes; `clearAccessToken()` inside `discard`. |
| `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` | The in-memory half (Phase 10). |

### The call site, precisely

`CallbackScreen.tsx:130` currently reads:

```
if (!idTokenNonceMatchesStoredValue(response.idToken).ok) discard();
```

It becomes a check whose success branch retains and whose failure branch discards — **and the order is
load-bearing**. A token retained before the nonce check would be a token held for a login this tab did not start,
which is the whole attack T19 exists to stop. The retention must sit on the far side of that `if`, and a comment
must say why, because the two lines look interchangeable and are not.

`clearAccessToken()` goes **inside the `discard` closure** (`:69-75`), not at each of its five call sites. The
closure exists precisely so the five failure paths cannot drift apart, and `discard` is also where a stale token
from an earlier successful sign-in is cleared (T21-D3, AC3, AC10). It is called on paths where nothing is held —
a missing `state` arrives before any exchange — so the clear must be idempotent, which it is.

## Private functions

None. The module is one variable and three closures over it; a private helper would be indirection for its own
sake.

## Modules used

- **Entities · none.** No persistence, no domain model.
- **Repositories · none, deliberately.** The absence of a storage call is the requirement. No `sessionStorage`, no
  `localStorage`, no IndexedDB, no cookie, no cache. There is nothing to guard with a `try/catch` because there is
  nothing to read.
- **Services ·** `LocalSession` from `src/api/crossTabRenewal.ts:4-6` (type only); `CallbackScreen.tsx` as the only
  production caller today; `createSessionBoundary` and `shareRenewalAcrossTabs` as the consumers the tests wire it
  into.

## Tests required

All in the named file (Phase 10 writes them; this is the plan).

- **Retention (AC1, AC2, AC4)** — a token is held; **two unrelated values**; repeated reads stable; nothing held
  before an exchange.
- **Absence (AC3, AC10)** — `null` after a missing `state`, a mismatched `state`, a bad nonce, a refused code, a
  5xx, a dropped connection and an unusable response; **and the stale-token case**: a successful sign-in followed
  by a discarded second attempt in the same tab leaves `current()` null.
- **Conformance by use (AC5, AC6)** — the real holder into the real `createSessionBoundary`, asserting
  `Authorization: Bearer <token>` on an outgoing request; the real holder into the real `shareRenewalAcrossTabs`,
  asserting a broadcast token becomes `current()`.
- **The seam (AC16)** — `renew()` rejects with one fixed message carrying nothing; through the real boundary, a
  401 leads to exactly **one** `onRenewalFailed` call and the caller gets its original 401
  (`sessionBoundary.ts:70-77`, `:120-124`).
- **`adopt()` (AC7)** — over a held token, with none held, after retention (adopt wins) and before it (retention
  wins).
- **Non-leakage (AC8)** — both storages, IndexedDB, `document.cookie`, the URL, the DOM, the title and every
  console channel, by whole value **and by segment**.
- **Singleness (AC9, AC17)** — exactly one module-level mutable binding in the holder; no `globalThis` property
  carrying the token; the token in no thrown message; `crossTabRenewal.ts` unchanged.
- **Source scans (AC13)** — no storage API named in the holder, no `console.`, no `import.meta.env`, no absolute
  address; **each scan proving itself against a synthetic violation**.
- **Controls** — one per block, proving the assertion is reached and not merely satisfied.
- **Regression** — T20's 69, T19's 123, T18's 134, plus `parallel401sTriggerExactlyOneRenewal` and
  `siblingTabsShareOneRenewal` unchanged. Those last two build **fakes of the interface T21 now implements**
  (`parallel401sTriggerExactlyOneRenewal.test.ts:80`, `siblingTabsShareOneRenewal.test.ts:114`) and must keep
  passing untouched: if the real holder is shaped right, their fakes stay valid.

## Execution order

No schema, so the order runs from the thing with no dependencies outward.

1. **`accessToken.ts`.** One variable, three members, one seam, two exported functions. Nothing depends on it yet,
   so it can be typechecked alone.
2. **Typecheck immediately**, before touching the screen. The point is to learn from the compiler whether the
   object literal really satisfies `LocalSession` — that is the claim AC5 and AC6 rest on, and the cheapest place
   to find it false is here.
3. **`CallbackScreen.tsx`** — retention on the far side of the nonce check, the clear inside `discard`.
4. **Run T18's, T19's and T20's suites.** They drive the real screen, so a retention that throws or reorders
   anything shows up here, before any new test exists to argue with.
5. **Run `parallel401sTriggerExactlyOneRenewal` and `siblingTabsShareOneRenewal`.** Untouched by this task; this
   confirms it.
6. **The named test's in-memory half** (Phase 10's full form; Phase 6 writes only what it needs to see the code
   work).
7. **Whole suite, `tsc`, `oxlint`** — exit codes read, not counts.

**Commit before any mutation run.** The harness reverts with `git checkout --`, and uncommitted test edits are
destroyed silently — it has cost this package 24 cases across T14 and T15 already.

## Open questions

**None.** The frozen brief closed all four, and §6 of it records the two pieces of work the specification never
assigned (silent re-authentication, the composition root) — neither of which this plan depends on.

One thing to **watch rather than assume** at step 2: whether an object literal annotated as `LocalSession`
satisfies it with `renew` typed as returning `Promise<string>` while the implementation only ever rejects. If the
compiler wants a return path it cannot see, the fix is a `Promise.reject(...)` rather than a `throw` in an `async`
function — a shape question to settle by reading the error, not by guessing now.

---

## Next — Phase 6 — implementation.
