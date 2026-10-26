# frontend · T17 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T17 — Missing state discarded |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

---

## Order

Same constraint as T16: **the catalogue first**, because its type is threaded into i18next's `CustomTypeOptions`,
so a component referencing a key that does not exist is a compile error rather than a runtime miss.

| # | Step | Why here |
|---|---|---|
| 1 | `catalogue.ts` — `callback.*` and `signIn.discarded` | typed keys must exist before any `t()` compiles |
| 2 | `codeExchange.ts` — the seam | the callback imports it |
| 3 | `signInNotice.ts` — in-memory, single-read | both screens import it |
| 4 | `CallbackScreen.tsx` | needs 1–3 |
| 5 | `SignInScreen.tsx` — render the taken notice | needs 3 |
| 6 | `routes.tsx` — `SIGN_IN_PATH` export, `element` on `callback` | makes the screen reachable, and breaks T02 |
| 7 | `routesResolveUnderAppPrefixOnly.test.tsx` — widen `SCREENS_ATTACHED_SO_FAR` | **same commit as 6**, so the suite is never knowingly red |
| 8 | the new test file | the 17 ACs |
| 9 | verification sweep | T01 → T02 → T16 → `src/auth` → `tsc`/`oxlint` → whole suite (D-11) |

Steps 6 and 7 are one commit, for the reason T16 established.

---

## Points to get right

**The effect's latch** is the first statement, before anything awaits or navigates, exactly as T16's `inFlight`
ref is. StrictMode invokes effects twice in development; the latch is what makes that harmless, and D-8 asserts
it rather than trusting it.

**The navigation uses a leading slash.** Measured in phase 4: `'/sign-in'` → `/app/sign-in`; bare `'sign-in'` →
`/app/callback/sign-in` → the catch-all, silently. The constant is `SIGN_IN_PATH` so the target cannot drift from
`PUBLIC_ROUTE_PATHS`, and AC17 asserts membership.

**The discard condition** is `state === null || state === '' || code === null || code === ''`. The `code` half is
D-4's locked extension of R8.

**No storage access of any kind** in the three new files — not read, not written. D-9's scan enforces it, and the
state comparison that will read `AUTHORIZE_STORAGE_KEYS.state` arrives with task 18.

**The seam is awaited with `void`**, because the callback does not yet have anything to do with the result; task
20 changes that. The empty function returns an already-resolved promise, so nothing is pending.

**`SignInScreen` takes the notice via `useState(() => takeSignInNotice())`** — an initializer, so the take happens
once per mount rather than on every render. The notice yields to `starting` and `failed`, because a fresh
attempt's status is more current than the previous attempt's epitaph.

---

## Test harness

`createMemoryRouter(appRoutes, { basename: APP_BASENAME, initialEntries: [...] })`, rendered through
`createRoot` + `act()` inside an `<I18nextProvider>`. **Routers are disposed in `afterEach`** — T02's convention,
the one T16's AC14 case had to break and recorded as a trade-off; T17 can follow it because it builds its own
router rather than going through `mountShell`.

`afterEach` also calls `takeSignInNotice()` unconditionally (D-3), so module state cannot cross cases even if a
case forgets.

The seam is spied by `vi.doMock('./codeExchange', …)` with a dynamic import of the screen, the pattern T16's
fail-closed cases use.

**Clicks and effects need macrotask turns**, not just `act()` — T16 measured that `act()` does not await work
leaving the microtask queue. The callback's effect navigates synchronously, but the seam is async, so the same
`turn()` helper applies.

---

## Risks

| Risk | Handling |
|---|---|
| **A relative navigation silently lands on the catch-all** | AC5 asserts the exact pathname; a mutant drops the slash |
| **Module-scoped notice leaking between cases** | `afterEach` drains it unconditionally (D-3) |
| **StrictMode double-invoking the effect** | the latch, asserted by D-8's case |
| **The catch-all renders nothing**, so a wrong navigation looks like an empty screen rather than an error | the pathname assertion, not a text assertion, is the primary check |
| **T13's bundle URL list** could fire if something new becomes reachable | nothing new is imported; verified by the whole-suite run |
| **The cycle gains a second strand** (`routes → CallbackScreen → codeExchange`) | `codeExchange.ts` imports nothing from `app/`, so the new strand does not close a loop; AC12 re-checks the existing guard |

---

## Next

Phase 6 — implementation.
