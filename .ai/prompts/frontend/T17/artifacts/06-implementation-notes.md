# frontend · T17 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T17 — Missing state discarded |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |
| **Commit** | `421df5f1` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 17):**
> **Missing state discarded.** A callback without `state` is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## What was built

| File | Change | Lines |
|---|---|---|
| `src/auth/CallbackScreen.tsx` | **new** — the screen the authorization server returns to | 57 |
| `src/auth/codeExchange.ts` | **new** — the seam (D-1, D-2) | 21 |
| `src/auth/signInNotice.ts` | **new** — in-memory, single-read (D-3) | 26 |
| `src/auth/callbackWithMismatchedStateIsDiscarded.test.tsx` | **new** — 20 blocks, 47 cases | 392 |
| `src/auth/SignInScreen.tsx` | takes and renders the notice | +7 |
| `src/app/routes.tsx` | `element` on `callback`; `SIGN_IN_PATH` export | +10/−2 |
| `src/i18n/catalogue.ts` | `callback.*`, `signIn.discarded` | +9 |
| `src/app/routesResolveUnderAppPrefixOnly.test.tsx` | `SCREENS_ATTACHED_SO_FAR` widened | +1/−1 |
| `src/app/bundleContainsNoSecretEnvKeys.test.ts` | one justified URL added | +4 |

All fourteen locked decisions D-1…D-11 and AC1…AC17 are implemented as frozen. Nothing deviated, so there is
nothing logged against the brief.

## The one thing worth explaining

**The task as written cannot fail.** "A callback without `state` is discarded with no exchange" asks for the
absence of a thing that does not exist yet — the exchange is task 20's. An assertion that no exchange happened
would pass against an empty file, and would keep passing after task 20 wired an exchange that fired
unconditionally.

`codeExchange.ts` exists to remove that vacuity. It is a real module with a real call site at
`CallbackScreen.tsx:48`, every discard case spies on it, and **one positive case drives the path that does call
it**. Without the positive case the four negative ones would be assertions about absent code rather than about a
collaborator that went unused.

## D-11, the regression obligation

`vitest run` over the whole suite: **608 passed, 1 failed (609)**, +47 against the 561 baseline.

The single failure is the known environmental one: `lintRejectsHandWrittenBackendFetch` AC4, `expected '' to
contain 'Found 0 warnings and 0 errors'` — this host's `oxlint@1.87.0` prints no summary line. Note that AC2 of
the same file, which fails with `spawn pnpm ENOENT` when the suite is invoked bare, **passes** here because the
run went through `npx -y pnpm@12.14.0` and so had `pnpm` on `PATH`. The two cases are different faults and only
one of them is invocation-dependent.

`tsc` and `oxlint` clean. T01 7/7, T02 9/9, T16 31/31.

## Verification in a real browser, against the live backend

The suite proves the logic; this proves the screen. Driven over CDP against the full Docker stack
(`infra/local`, 11 containers) through the Caddy edge on `localhost:5173`.

**The discard path:**

| URL | Landed | Rendered |
|---|---|---|
| `/app/callback` | `/app/sign-in` | `Sign in … Your sign-in did not complete. Try again.` |
| `/app/callback?code=injected-code` | `/app/sign-in` | `Sign in … Your sign-in did not complete. Try again.` |

The two strings are **byte-identical**, which is L4's neutrality holding outside the test harness: a forged link
carrying a code and a bookmark carrying nothing are indistinguishable to the visitor.

**The success path, through a real authorization:** signed in as a real MERCHANT account against the live auth
service — note that `/login` is single-step and takes `username`, `password` and `mfaCode` in one POST
(`infra/local/bin/smoke:100-103`), which the dev login page does not render a field for.

```
1 sign-in    /app/sign-in     stored: [code_verifier, nonce, state]
2 at SAS     /login           stored: [code_verifier, nonce, state]
  stored state  p_IbjfDiaID99nZbzFSa5k5CJqxWr3pGlOQo3fhqJAQ
3 submit     username + password + mfaCode
4 CALLBACK   /app/callback?code=Mmgwqniz4BESwCrI3Qr-T4GPi6X87oXhoYg7Y9mZ4T-...
             "Finishing sign-in  Checking your sign-in…"
  url state     p_IbjfDiaID99nZbzFSa5k5CJqxWr3pGlOQo3fhqJAQ   matches stored: true
  has code      true
```

Four things this establishes that no unit test can:

1. **A real authorization code reaches `/app/callback`** — the route, the basename and the registered redirect URI
   all agree with what the backend actually sends.
2. **The state round-trips byte-identical.** The value T15 stored came back unchanged in the query. That is
   precisely the comparison task 18 will make, and it now has a measured premise rather than an assumed one.
3. **T17's screen holds**, rendering "Checking your sign-in…" and navigating nowhere — D-6's deliberate no-op,
   observed rather than argued.
4. **`/login` is same-origin** (`localhost:5173`): the three `sessionStorage` items are readable there, which is
   L9's single origin confirmed, and is why the callback can read them at all.

The three stored items survive both the discard path and the success path, which is AC8/D-7 in the real browser.

## Open questions

**OQ-3 — the callback copy is unreviewed**, as T16's sign-in copy is. Neutral, names no cause, identical across
all three discard shapes, and those properties are enforced by test. The owner's to reword in one file. **Gates
nothing.**

---

## Next

Phase 7 — self review.
