# frontend · T17 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T17 — Missing state discarded |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` |
| **Reviewed** | commit `421df5f1` — 9 files, +526/−2 |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 17):**
> **Missing state discarded.** A callback without `state` is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

Findings only. Fixes are phase 9's. Severity is mine; the owner may disagree with any of it.

---

## Findings

### F1 · `void exchangeAuthorizationCode(...)` discards a rejection, and nothing notices

**Severity · Medium** — the only finding here I would act on before task 20 lands.

**Evidence ·** `src/auth/CallbackScreen.tsx:48`
```ts
void exchangeAuthorizationCode({ code, state });
```

**Issue ·** The seam returns `Promise<void>`. Today its body is empty so it can only resolve, and `void` is
harmless. Task 20 fills it with network I/O against the token endpoint, and that **will** reject — offline, a 400
from `/oauth2/token`, a clock skew, a revoked verifier. `void` discards the rejection: it becomes an unhandled
rejection, the visitor sits on "Checking your sign-in…" indefinitely with a polite live region and no way
forward, and no test in this suite observes it. T16's suite installs an unhandled-rejection guard on Node's
`process` for exactly this class of fault; T17's installs none, so the regression would land silently in task 20
and be attributed there rather than here.

This is a seam T17 introduced, so the obligation to say what happens when it fails is T17's even though the
failure cannot occur yet.

**Recommendation ·** Either (a) attach a `.catch()` now that routes to the same notice-and-return path as a
discard — which costs three lines and makes the failure mode defined rather than inherited — or (b) record a
locked decision that task 20 owns the rejection path, and add an unhandled-rejection guard to this suite so that
a task-20 implementation without one fails T17's tests. I prefer (a): the discard path already exists, the copy
is already neutral, and L4 makes "could not complete" the correct thing to say about a failed exchange anyway.

---

### F2 · The effect's dependency array and the `handled` latch contradict each other

**Severity · Low** — no behaviour is wrong; the code misstates its own intent.

**Evidence ·** `src/auth/CallbackScreen.tsx:27-29`, `:49`
```ts
useEffect(() => {
  if (handled.current) return;
  handled.current = true;
  …
}, [params, navigate]);
```

**Issue ·** The deps declare that the effect re-runs when `params` changes, and the first two lines guarantee it
will do nothing when it does. The deps are therefore inert. A reader reasonably concludes that a new `?code=…`
arriving at an already-mounted callback screen is handled; it is not. That cannot happen through the route table
as it stands — every arrival is a fresh navigation and a fresh mount — which is exactly why no test catches the
contradiction and why it will survive until someone relies on it.

**Recommendation ·** Either reduce to `[]` with a one-line comment stating that one mount handles one callback,
or key the latch on the raw query string so a genuinely different callback is handled. Both are no-ops today; the
first is honest about the design, the second makes the deps mean what they say. I prefer the first — a callback
screen handling two callbacks in one mount is not a scenario this flow should invite.

---

### F3 · A whitespace-only `state` or `code` passes the guard, against the comment's own reasoning

**Severity · Low**

**Evidence ·** `src/auth/CallbackScreen.tsx:34-38`
```ts
// An empty value counts as missing: it is what a stripped or truncated query
// produces, and it carries no proof the callback belongs to a request this tab made.
if (state === null || state === '' || code === null || code === '') {
```

**Issue ·** `/app/callback?code=abc&state=%20` yields `state === ' '`, which is neither `null` nor `''`, so it
reaches the seam. The comment justifies rejecting the empty case on two grounds — that it is what a mangled query
produces, and that it carries no proof of ownership — and **a whitespace value has both properties equally**. So
the code implements a narrower rule than the reasoning it states. The end state is still correct, because task
18's comparison against the stored value will reject `' '`, but T17's stated contract and T17's behaviour differ
by one case.

**Recommendation ·** Either trim before the emptiness test, or narrow the comment to say that only the literal
empty value is T17's and that any other unusable value is task 18's. The first is one call; the second is
honest. Not worth more than a line either way, but the mismatch between comment and code is the kind of thing
that reads as a bug later.

---

### F4 · No case asserts the seam is unreached on the discard path under StrictMode

**Severity · Info**

**Evidence ·** `src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx:283-296`

**Issue ·** The StrictMode block asserts the seam is reached exactly once on the success path, and that the
discard path navigates once. Seam-zero on the discard path is asserted only in the non-strict cases. A latch
defect where the second development invocation fell through past the discard branch to the seam would be caught
by the navigate-once assertion only if it also double-navigated — and a `replace` to the same path twice may not
change `historyAction`. The gap is narrow but it is in the one place D-8 exists to cover.

**Recommendation ·** One case: spy the seam, arrive at a discard shape under `{ strict: true }`, assert `calls`
is empty. Four lines, reuses both existing helpers.

---

### F5 · `'callback'` is a bare literal while `sign-in` got a constant — verified harmless

**Severity · Info** — readability only. **I tested this rather than asserting it.**

**Evidence ·** `src/app/routes.tsx:17`, `:50-51`
```ts
export const PUBLIC_ROUTE_PATHS = ['sign-in', 'callback', …] as const;
…
if (path === SIGN_IN_PATH) return { path, element: <SignInScreen /> };
if (path === 'callback') return { path, element: <CallbackScreen /> };
```

**Issue ·** T17 introduced `SIGN_IN_PATH` so the callback would not repeat a literal, then attached its own route
by a repeated literal. `PUBLIC_ROUTE_PATHS` also still spells `'sign-in'` rather than using the constant, so the
table and the constant can drift. AC17 pins `PUBLIC_ROUTE_PATHS` ⊇ `{SIGN_IN_PATH}`; nothing analogous protects
`'callback'`.

**Measured ·** mutating line 51 to `'callbackX'` fails **20 cases** across `routesResolveUnderAppPrefixOnly`
(T02) and most of T17's own suite. The drift is loudly caught, by T02's element-counting scan in particular. So
this is cosmetic, not a risk — but the protection lives in another task's suite and arrives only because T02 was
widened in this commit, which is worth knowing.

**Recommendation ·** A `CALLBACK_PATH` constant used at both sites, and `PUBLIC_ROUTE_PATHS` built from the two
constants. Symmetric, and makes the route table self-consistent rather than consistent-by-test.

---

### F6 · The AC15 negative control pairs its two lists by index

**Severity · Info**

**Evidence ·** `src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx:368-381`

**Issue ·** `violations[index]` is matched against `forbidden.entries()`. Reordering or inserting into one list
without the other mis-pairs every row below the change. It would almost certainly fail loudly rather than pass
vacuously, so the risk is a confusing failure rather than a missed defect.

**Recommendation ·** Key the violations to their labels in one record, so each pattern and its negative control
are declared together and an insertion cannot misalign them.

---

## Checked and clean

Against the phase-7 checklist, the dimensions that produced nothing:

- **Null-safety** — every `params.get` result is tested for `null` before use; the notice's `take` returns
  `null` explicitly and both call sites handle it.
- **Idempotency** — the `handled` ref makes the effect single-shot, asserted under StrictMode (and see F2 on what
  the deps claim).
- **Secret-handling / enumeration-safety** — no credential, token or account state appears in any copy, enforced
  by a word-list case with a positive control proving the matcher has reach. The code is never logged. The three
  `sessionStorage` items are neither read nor written, asserted by spies on `Storage.prototype`.
- **Module boundaries** — the three new files import nothing outside `src/auth` except `SIGN_IN_PATH`; the
  `routes → SignInScreen → authorizeRequest → routes` cycle is unchanged and still closed by the
  read-inside-a-function property, which has its own test.
- **Money types, transaction boundaries, thread-safety** — not applicable to this task.
- **Complexity** — the screen is one effect and four lines of JSX.

## Verified *not* to be a finding

I expected a defect here and did not find one, which is worth recording because the reasoning looked sound.

**`spyOnExchange()` appeared to depend on test order.** It calls `vi.doMock('./codeExchange')` and then
`await import('../app/routes')` — but `../app/routes` is already in the module registry from the static import
at line 9. My expectation was that the dynamic import would return the cached module graph built against the
*real* seam, so the four negative cases would assert `calls === []` against a spy that was never wired, passing
vacuously, and that this only works today because `afterEach` runs `vi.resetModules()` before the exchange block
is reached.

**Measured ·** running `-t "nothing is exchanged"` alone, so that no earlier `afterEach` has run and the block is
first, passes all six cases **including the positive control**. Vitest invalidates the importers of a `doMock`ed
module, so the re-import rebuilds the graph against the mock regardless of order. No finding, and the positive
control would have failed loudly if I had been right.

## Suite state at review

**608 passed · 1 failed (609).** The failure is `lintRejectsHandWrittenBackendFetch` AC4 — this host's
`oxlint@1.87.0` prints no summary line for the assertion to match. Environmental, pre-existing, unrelated to
T17. AC2 of the same file passes under an `npx -y pnpm@12.14.0` invocation and fails with `spawn pnpm ENOENT`
under a bare one; the two are different faults and neither is T17's.

## Open questions

**OQ-3 — the callback copy is unreviewed**, as T16's is. Properties enforced by test; wording is the owner's, in
one file. **Gates nothing.**

**Carried, gating nothing ·** T16's OQ-1 (the five sign-in strings); T12's OQ-1 (a refresh cannot promote a
waiting worker); `design.md` §4c's stale "PROPOSED, pending sign-off (Q6)" heading and its claim that auth T19 is
"not built" while L5 records it as built; `package.md:172` mapping `serviceWorkerUpdateWaitsForReload` to R57,
which does not describe it.

---

## Next

**Phase 8 — independent review. This is Kimi 2.7's phase, and I am stopping here for it.**

The T16 waiver was explicit and scoped to that task. T17's phase 3 was run by Kimi and handed back normally, and
no instruction has been given to self-produce phase 8.
