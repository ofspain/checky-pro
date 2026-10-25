# frontend · T17 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T17 — Missing state discarded |
| **Spec section** | Phase 1 — Auth & account (gatekeeper): OIDC and session |
| **Model** | Claude Opus 5 |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 17):**
> **Missing state discarded.** A callback without `state` is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.

---

## Where this sits

T16 landed the first screen and sent the browser to Spring Authorization Server. The browser comes back to
`/app/callback`, and **nothing is there** — that route has an entry in the table and no element, so a completed
sign-in currently lands on a blank page. T17 is the first half of answering it.

The half T17 owns is deliberately narrow: **a callback that arrives with no `state` parameter at all.** Everything
else about the callback belongs to a later task, and the list is unusually long — see the boundary table below.

---

## Architecture as it stands

Unchanged from T16 in structure; what matters here is what already exists to be consumed.

| Module | Relevant contents |
|---|---|
| `src/auth/` | `pkce.ts` (T14), `authorizeRequest.ts` (T15), `SignInScreen.tsx` + its suite (T16) |
| `src/app/` | `routes.tsx`, `mountShell.tsx` (now with `<I18nextProvider>`), `AppShell.tsx`, T01/T02/T13 suites |
| `src/i18n/` | `createI18n()`, the `en` catalogue — now with `rateLimit` and `signIn` blocks |
| `src/api/` | the generated-client wrapper, session boundary, cross-tab renewal |
| `src/pwa/` | worker source and policies; **the worker is still not registered** (T09's gate D1, task 12 owns it) |

**Security posture for this task:** `state` is the CSRF defence for the authorization-code flow. A callback
carrying a code but no `state` is either a bug or an injection attempt, and R8's answer is the same for both —
discard, do not exchange, send the visitor back to the start.

---

## What T17 consumes unchanged

| From | What |
|---|---|
| `authorizeRequest.ts:35-39` | `AUTHORIZE_STORAGE_KEYS` — `state`, `nonce`, `codeVerifier`. T17 reads the stored `state` by name rather than copying the string, and the carve-out task (26) will enumerate the same object. |
| `routes.tsx:9` | `'callback'` is already in `PUBLIC_ROUTE_PATHS`. T17 attaches an element; it adds no path. |
| `routes.tsx` (T16) | `SCREENS_ATTACHED_SO_FAR` in T02's suite — **T17 must widen it deliberately**, which is exactly what T16 designed it to force. |
| `mountShell.tsx` (T16) | the `<I18nextProvider>` already in place, so a second screen needs no provider work. |
| `catalogue.ts` | the `en` catalogue, which T17 extends with its own copy. |
| `SignInScreen.tsx` (T16) | the established shape of a screen here: a `<main>`, one `<h1>`, catalogue-only strings, a `role="status"` live region, no styling. |

## What is new in T17

A callback screen under `src/auth/`, its element on the `callback` route, catalogue keys, and the named test
file. Nothing else — and in particular **no token exchange**, because that is task 20.

---

## The boundary, read off `tasks.md` rather than judged

| Not T17 | Owner |
|---|---|
| A callback whose `state` **differs** from the stored value | **18** |
| Validating the ID token's `nonce` | **19** |
| The token request, and sending the stored `code_verifier` | **20** |
| Holding the access token in memory only | **21** |
| Proving no token reaches any storage | **22** |
| Deleting the three items at callback, single-use | **23** |
| The post-login return target and its allowlist | **24, 25** |
| `sessionStorage` holds only the L16 items | **26** |
| The SAS MFA challenge resuming at the callback | **27** |
| The uniform SAS-failure copy | **28, 29** |

Two of these need stating plainly because they are the ones most likely to get absorbed by accident:

- **Task 18 is the mismatch case.** T17 is *only* the missing case. Both tasks name the same test,
  `callbackWithMismatchedStateIsDiscarded`, so the file T17 creates is the file task 18 extends.
- **Task 23 owns deleting the stored items.** T17 must not consume them, which is a real temptation — a discarded
  attempt leaving its `state` and verifier behind looks untidy. It is not T17's to clean up.

---

## The problem this task has, which I want recorded before any design

**"A callback without `state` is discarded with no exchange" is vacuously true today.**

There is no exchange. `authorizeRequest.ts` builds a request; nothing in the repository posts to `/oauth2/token`,
because that is task 20. So a test asserting "no exchange happened" would pass against a screen that does nothing
whatsoever, against an empty file, and against a screen that exchanges happily once task 20 lands — because the
assertion has nothing to observe.

This is the self-comparison trap in a new costume, and this project has now met it six times (T12 AC7, T13 AC8,
T14 AC1, T15 S1/S2 and M10/M12, T16 M18). The difference here is that it is not a test-writing slip — it is
inherent in the task's position in the order. **Phase 2 has to design against it deliberately**, and the obvious
shape is a seam: a named exchange function the callback calls on the success path, which T17 defines and does not
implement, so "not called" is a fact about a real collaborator rather than about the absence of code.

I am flagging it here rather than solving it here, because phase 0 is not design.

---

## Established patterns to follow

- **Screens:** function components, no library, no CSS, plain elements. `SignInScreen.tsx` is the precedent.
- **Copy:** catalogue only (`agents.md:96`); the catalogue's type is threaded into i18next so a missing key is a
  compile error.
- **Accessibility (L11):** keyboard-operable, one `h1`, a `main` landmark, async results in a live region.
  T16's suite counts these rather than checking existence — two `main` elements is worse than none.
- **Tests:** `createRoot` + `act()`, no `@testing-library/react`. **Clicks must be followed by macrotask turns**,
  because `act()` does not await work that leaves the microtask queue — T16 measured this when a real
  `crypto.subtle` call made one case's async work land in the next.
- **Reading the URL:** T16 stubs `location` with `vi.stubGlobal` and supplies `origin` because T15's module reads
  it. A callback screen reads `location.search`, so the stub grows a `search` field.

## Known traps, inherited

- **T01 scans every non-test file under `src/**` for `https?://` and `import.meta.env`.** No absolute address in
  the new source, including comments.
- **T02's `SCREENS_ATTACHED_SO_FAR`** must be widened, with the predicate still shown to count.
- **T13's `KNOWN_ABSOLUTE_URLS`** fails if a newly-reachable dependency drags a documentation URL into the
  bundle. T16 tripped this by making `useTranslation` reachable. T17 should expect it if it reaches for anything
  new — though it should need nothing new.
- **The circular import T16 closed** (`app/routes → auth/SignInScreen → auth/authorizeRequest → app/routes`)
  gains a second strand when `routes.tsx` imports a callback screen that imports `authorizeRequest`. The existing
  guard — `APP_BASENAME` is read only inside a function — still covers it, but T17 should check rather than
  assume.

---

## Testing conventions and the measured baseline

Vitest 5.0.5 + jsdom 30.1.3. No Testcontainers, no fixed `Clock`, no ArchUnit in this package.

| | |
|---|---|
| Whole suite before T17 | **560 pass / 562 total** |
| The 2 failures | `lintRejectsHandWrittenBackendFetch`, two **different** environmental faults: AC4 because this host's `oxlint@1.87.0` prints no summary line, AC2 because bare `pnpm` is not on PATH |

---

## Known gaps and unknowns

1. **What the callback screen shows while it is working.** A callback is a transient screen — it reads a URL and
   moves on. Whether it should render anything at all beyond a live region, I do not know; nothing in the spec
   says.
2. **Where "return the visitor to the sign-in start" is implemented.** R8 requires it. Whether that is a router
   navigation inside T17, or belongs with tasks 28/29's failure surface, is not obvious to me from `tasks.md`.
   Phase 1 must settle it from the requirement rather than by preference.
3. **What "neutral copy" means concretely**, and whether it is the same string tasks 28/29 will introduce. If it
   is, T17 would be inventing copy that another task owns.
4. **Whether a discarded callback should clear the stored items.** R8 says discard the attempt; L16 says the items
   are single-use and deleted at callback; task 23 owns that. Those can be read as agreeing or conflicting, and I
   do not know which is intended.
5. **Whether the callback route should be reachable directly**, with no stored `state` at all — a visitor who
   bookmarks `/app/callback`. Functionally identical to the missing-`state` case, but the *copy* might reasonably
   differ, and nothing says.

---

## Next

Phase 1 — specification extraction: R8, the locked decisions that bear on a transient screen (L1, L11, L14, L16),
and the five unknowns above turned into resolved decisions or explicit open questions.
