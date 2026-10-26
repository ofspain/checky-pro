# frontend · T17 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T17 — Missing state discarded |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` (Kimi 2.7) |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 17):**
> **Missing state discarded.** A callback without `state` is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## Disposition of Kimi's ten findings

**Nine accepted, one accepted with a different resolution, one premise corrected.**

| # | Finding | Disposition |
|---|---|---|
| 1 | module-scoped notice leaks across tests | **Accepted** → D-3 |
| 2 | the seam type is too narrow for task 20 | **Accepted, resolved differently** → D-2 |
| 3 | missing `code` as discard should be locked | **Accepted** → D-4 |
| 4 | the navigation path assumes basename handling | **Accepted, and measured** → D-5 |
| 5 | the empty seam creates a temporary behaviour | **Accepted; Kimi's reading of my sentence was wrong, and the sentence was ambiguous** → D-6 |
| 6 | OQ-2 should be settled now | **Accepted** → D-7 |
| 7 | the `useRef` latch should be asserted | **Accepted** → D-8 |
| 8 | no source scan forbids `history.state`/`localStorage` | **Accepted, with one correction to the recommendation** → D-9 |
| 9 | the union type is premature | **Accepted** → D-10 |
| 10 | the four-file ripple needs a regression obligation | **Accepted** → D-11 |

---

## Locked decisions

### D-1 — The seam stands, and it is the reason this task is testable
*(unchanged from phase 2)* `exchangeAuthorizationCode` exists so that "the callback did not exchange" is a fact
about a call site rather than about absent code. T17 declares it; task 20 fills it.

### D-2 — The seam takes `{ code, state }`, and T17's tests assert with `objectContaining`
*(finding 2)*

Kimi is right that task 20 needs more than `code` and `state` — at minimum the `code_verifier`. Kimi's two options
were to widen the type now, or to let the seam read storage internally. **Both are rejected, for the same reason:
they lock a shape for a task whose design has not happened.**

The real problem Kimi identified is not the type's width — it is that **a widened type would break T17's tests**.
That is only true if T17 asserts the argument *exactly*. So the frozen rule is about the assertion, not the type:

```ts
expect(exchangeSpy).toHaveBeenCalledWith(expect.objectContaining({ code: 'abc', state: 'xyz' }));
```

Task 20 may then add `codeVerifier`, `redirectUri` or anything else to the grant without touching a T17 test. T17
asserts only what T17 knows: the code and state it read from the URL were handed on. A comment in `codeExchange.ts`
records that the type is expected to grow and that callers must not depend on its exact shape.

### D-3 — The notice's test-isolation rule is locked
*(finding 1)*

`signInNotice.ts` holds module state, and Vitest reuses the module graph within a file. Locked:

- every case that sets a notice either renders the sign-in screen (which consumes it) or calls `takeSignInNotice()`
  in `afterEach`;
- the suite's `afterEach` calls `takeSignInNotice()` unconditionally, so a leak cannot cross cases even if a case
  forgets;
- one case asserts the drain works: set, take, take again → `null`.

In production the single-read is the point: the notice survives exactly one sign-in mount and is lost on reload,
which is correct, because a refreshed sign-in page should not keep scolding the visitor.

### D-4 — A callback without `code` is a discard
*(finding 3)*

Locked as an explicit extension of R8, which speaks only of `state`. **The seam is never called with an
incomplete grant.** The alternative — handing task 20 a `null` code — moves a known-bad input across the seam and
makes the seam's contract dishonest. Recorded so it is deliberate rather than incidental.

### D-5 — The navigation target is `/${SIGN_IN_PATH}`, and it is measured, not assumed
*(finding 4)*

Kimi asked whether React Router 8.4.0 resolves a leading-slash path against the basename. **Measured on this host
with `createMemoryRouter({ basename: '/app' })` from `/app/callback`:**

| `navigate(…)` | resolves to | renders |
|---|---|---|
| `'/sign-in'` | `/app/sign-in` | the sign-in screen |
| `'sign-in'` | `/app/callback/sign-in` | **the catch-all** |
| `'../sign-in'` | `/app/sign-in` | the sign-in screen |

So the brief's form is correct — and Kimi's recommendation stands anyway, because **the relative form fails
silently**: no error, no 404, just the catch-all element, which today renders nothing at all. An AC asserts the
landed pathname is exactly `/app/sign-in`, and a mutant drops the leading slash to prove the assertion catches it.

`historyAction` was also observed as `REPLACE`, which is AC5's mechanism confirmed rather than assumed.

### D-6 — The deployment assumption, with my own sentence corrected
*(finding 5)*

**Kimi misread my brief, and the sentence invited it.** I wrote that "task 18 lands first", meaning *before task
20*. Kimi read it as *before task 17* and asked "T18 is supposed to land before T17?". The ordering is
17 → 18 → 19 → 20; nothing lands before T17.

The substance of the finding is accepted and locked: **between T17 and T20 the callback's success path is a
deliberate no-op.** A callback carrying a `state` reaches the seam, the seam returns immediately, and the visitor
stays on the holding screen. That is the honest state of a partially-built flow, and it is why the screen says
"Checking your sign-in…" rather than anything that implies completion. Recorded for whoever deploys: T17 alone
does not complete a sign-in, and is not expected to.

### D-7 — A discard does **not** delete the stored items
*(finding 6, OQ-2 closed)*

Locked, with the reasoning stated so a later task can overturn it knowingly:

- **Task 23 owns "deleted at callback" by name.** Two tasks deleting one thing gives one behaviour two owners.
- **Nothing is leaked by keeping them.** The three items are same-origin `sessionStorage`, readable only by this
  tab; a discarded attempt leaves a verifier whose challenge was never exchanged, and T15's next request
  overwrites all three (its own suite proves the overwrite is clean).
- **Premature deletion would hide a diagnosis.** A discarded callback followed by empty storage is
  indistinguishable from a callback that arrived with no stored attempt at all — which is the very case AC3
  covers.

AC8 pins it: after a discard, the three items are still present and unchanged. If the owner prefers deletion, the
change is one branch and AC8 reverses.

### D-8 — The StrictMode latch is asserted, not assumed
*(finding 7)*

A case renders the callback inside `<StrictMode>` and asserts the seam is called **once** on the success path and
the navigation happens **once** on the discard path. Without it, removing the `handled` ref would pass under a
plain render and fail only in a development browser.

### D-9 — Source scans on all three new files, with one correction
*(finding 8)*

Kimi's recommendation said to forbid `sessionStorage.setItem` "except in `CallbackScreen.tsx` (which is
allowed)". **That is wrong and would contradict AC7.** T17 writes no `sessionStorage` at all — L16's carve-out is
exhaustive and T15 already owns the three writes. Locked as: **none of the three new files may contain
`localStorage`, `history.state`, `sessionStorage.setItem`, an absolute address, or `import.meta.env`.** The
callback *reads* the stored state only once task 18 adds the comparison; T17 reads nothing from storage either.

### D-10 — `SignInNotice` stays a union, documented as a forward-compatibility hook
*(finding 9)*

Kept, with a comment saying it is a hook and **not a commitment**: task 18 may add a value, or may reuse
`'discarded'` because L4 forbids distinguishable copy. Either is open. A single-member union costs nothing and
makes the eventual addition a type change rather than a string-literal convention.

### D-11 — The regression obligation is explicit
*(finding 10)*

After phase 6: `vitest run` over the whole suite, with the delta measured against 560/562 and the only failures
the two known environmental `lintRejectsHandWrittenBackendFetch` cases. Naming T01/T02/T13/T16 is not enough —
T17 touches four existing files.

---

## Acceptance criteria, as frozen

AC1–AC14 stand as phase 1 wrote them, with:

- **AC4 extended by D-2** — the seam assertion uses `objectContaining`.
- **AC5 extended by D-5** — the landed pathname is exactly `/app/sign-in`, and `historyAction` is `REPLACE`.
- **AC8 closed by D-7** — the three items are present and unchanged after a discard.
- **AC14 extended by D-8** — the StrictMode double-invocation case.

Added:

- **AC15** — the three new files pass the D-9 source scans, each fed a synthetic violation as a negative control.
- **AC16** — `takeSignInNotice()` is single-read: a second take returns `null` (D-3).
- **AC17** — `SIGN_IN_PATH` is a member of `PUBLIC_ROUTE_PATHS`, so the callback's target cannot drift from the
  route table.

---

## Files

| File | Change |
|---|---|
| `src/auth/CallbackScreen.tsx` | **new** |
| `src/auth/codeExchange.ts` | **new** — the seam (D-1, D-2) |
| `src/auth/signInNotice.ts` | **new** — in-memory, single-read (D-3) |
| `src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` | **new** — task 18 extends this file |
| `src/auth/SignInScreen.tsx` | renders the taken notice |
| `src/app/routes.tsx` | `element` on `callback`; `SIGN_IN_PATH` export |
| `src/i18n/catalogue.ts` | `callback.*` keys and `signIn.discarded` |
| `src/app/routesResolveUnderAppPrefixOnly.test.tsx` | `SCREENS_ATTACHED_SO_FAR` widened to include `callback` |

---

## Open questions

**OQ-3 — the copy is unreviewed**, as T16's is. Neutral, names no cause, identical across all three discard
shapes, properties enforced by test. The owner's to reword in one file. **Gates nothing.**

**OQ-1, OQ-2, OQ-4 — closed** by D-3, D-7 and D-4.

**Carried, gating nothing:** T16's OQ-1 (the five sign-in strings); T12's OQ-1 (a refresh cannot promote a
waiting worker); `design.md` §4c's stale "PROPOSED, pending sign-off (Q6)" heading and its claim that auth T19 is
"not built" while L5 records it as built.

---

## Next

Phase 5 — implementation plan.
