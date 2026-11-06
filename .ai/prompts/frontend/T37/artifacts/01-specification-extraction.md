<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction) -->

# frontend · T37 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T37 — Signed-out landing |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

## The task

`spec/frontend/tasks.md:65`:

> **37. Signed-out landing.** After sign-out the user lands on `/app/signed-out`. Verifies
> `publicRouteSetIsExactlyTheDeclaredSet`. Cites R19, L14.

## What the spec actually says, quoted

| Source | Clause |
|---|---|
| `requirements.md:62-65` | **R19** — clear in-memory token state and end the SAS session with a `post_logout_redirect_uri` from the allowlist. **Says nothing about what the landing page contains.** |
| `design.md:53-56` | **L14** — the public route list includes *"and the signed-out landing"*, by name |
| `design.md:139` | §4c lists `/app/signed-out` among the public paths |
| `design.md:164` | auth-service registers `http://localhost:5173/app/signed-out` for `checky-spa` (fixed 2026-10-21) |
| `design.md:167` | *"`/connect/logout` … ends the SAS session and redirects to `/app/signed-out?state=…`; a non-allowlisted target is a 400"* |
| `design.md:21-26` | **L4** — copy, redirects, styling and timing identical across every account state and token state, on every public surface |
| `agents.md:35-36` | sign-out ends the SAS session and clears in-memory token state; propagates to every tab |

**The decisive negative finding: no clause in the package specifies this screen's content.** R19 is about
clearing and ending; L14 is about the route existing and being public. Searched `requirements.md`,
`design.md`, `package.md` and `agents.md` for copy guidance on the signed-out landing: there is none, and
there is no `signedOut` entry in `i18n/catalogue.ts`. `auth/signOut.ts`'s comment — *"Task 37's screen will
say what a deliberate sign-out means"* — is the only statement of intent, and it is a reason, not words.

So T37's content is **derived from L4 and the house pattern**, not extracted. Said plainly here because a
later reader should not mistake derived copy for specified copy. The nearest model is
`catalogue.ts`'s `signIn.sessionEnded`, which names an **event** and never a cause.

## Acceptance criteria

| # | Criterion |
|---|---|
| **AC1** | `/app/signed-out` renders a screen. Today the path is declared with no element, so nothing renders. |
| **AC2** | It is reachable **without a session** — no token held, nothing in `sessionStorage` — and renders the same thing either way (L14, R28). |
| **AC3** | Every string comes from `i18n/catalogue.ts` through `t()`. No literal user-facing copy in the component (L11). |
| **AC4** | The copy is **enumeration-safe**: it names the event and no cause, and is identical whether the visitor signed out deliberately, had a session expire, or was signed out by a revoked, locked, suspended or deleted account (L4). |
| **AC5** | It offers a way back to sign-in, and that is the **only** action it offers. |
| **AC6** | It does **not** depend on a query parameter. T36 sends no `state`, so the real arrival is `/app/signed-out` with an empty query — and the screen must render identically with a `state`, with an unknown parameter, and with none. |
| **AC7** | It holds no session state, reads no token, writes no storage, makes no backend call and starts no timer. |
| **AC8** | It does **not** clear anything. Clearing is R19 clause 1, task 35's, and already done by the time SAS redirects here. A landing that cleared would be a sign-out affordance reachable by typing a URL. |
| **AC9** | `SCREENS_ATTACHED_SO_FAR` in `routesResolveUnderAppPrefixOnly.test.tsx:17` grows from two to three; the route table's element set stays exactly the attached list. |
| **AC10** | T37 adds **no route**, no storage key, no backend call and no new dependency. The route already exists in `PUBLIC_ROUTE_PATHS`. |
| **AC11** | Renders no PII, no account identifier, no email address and no token — it has none to render, and the component may not acquire any. |
| **AC12** | Direct arrival by typing the URL, with no sign-out having happened, renders the screen without error and without redirecting. |

## Open questions

**OQ-T37-1 — the owner's, and the only one that changes visible product.** **Nothing specifies this screen's
copy.** T37 will derive it from L4 and the `signIn.sessionEnded` model: an event named, no cause, plus a
single way back to sign-in. The owner may want different words — but the *shape* is constrained by L4, so a
rewording is a copy change and not a redesign. Proceeding under that assumption rather than blocking.

**OQ-T37-2 — a claim the screen cannot verify.** A visitor who types `/app/signed-out` **while still holding a
token** sees a screen saying they are signed out, which is false for that tab. Three options:

- (a) accept it — the page is a *landing*, not a status view, and L4 forbids it from reporting account or
  session state anyway;
- (b) have it clear the session on arrival — **rejected on sight**: that makes a URL into a sign-out
  affordance, and it would let a cross-site link sign a visitor out;
- (c) have it redirect a token-holding tab to `/app/home`.

(c) is tempting and is **not** T37's to choose: no authenticated route has an element, so `/app/home` renders
nothing, and a redirect there would send a visitor from a page with words to a blank one. Phase 2 should
freeze **(a)**, with the reasoning recorded, and note that (c) becomes available once task 173/174's home
screen exists.

**OQ-T37-3 — the named test does not exist and is shared.** The task statement says T37 verifies
`publicRouteSetIsExactlyTheDeclaredSet`. Measured: it exists in no source file, and `tasks.md:71` assigns it
to **task 40** (*"Public route set is exact… Cites R28, L14"*). T40 owns the "exactly these routes are
reachable without a session" claim across all nine public paths; T37 owns one route's screen. Phase 2 must
decide whether T37 creates that file with a subset and T40 completes it, or whether T37 verifies through
`routesResolveUnderAppPrefixOnly` — which **does** exist, which already owns the attached-screen set, and
which T37 must edit anyway for AC9. **The second is the smaller claim and the honest one**, and it means T37
does not create a file whose name promises T40's scope.

**OQ-T37-4 — `design.md:167` describes `?state=…`; T36 sends none.** Not a conflict: T36 froze "no `state`"
(T36's OQ-T36-2, on the grounds that the allowlisted redirect is the security control and no screen needs
correlation), and the design note describes the backend test's URL, which supplies one. AC6 exists so the
screen is indifferent either way, and so a future task adding `state` breaks nothing here.

**OQ-T37-5 — still nothing calls `endSession()`.** So no visitor can arrive here by signing out; the screen is
reachable only by typing the URL. **OQ-T36-5**, now its eighth task. AC12 is the criterion that makes the
dormant path at least testable.

**Phase 1 complete.** Next is phase 2 — the task implementation brief.
