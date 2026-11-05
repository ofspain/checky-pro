<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction) -->

# frontend · T33 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T33 — Clean re-auth on failed renewal |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

## Business rules

| ID | Rule, as it applies here |
|---|---|
| **R22** cl. 1 | IF renewal fails or the session is no longer valid, the SPA SHALL transition to clean re-auth. |
| **R22** cl. 2 | It SHALL NOT show a half-authenticated screen. |
| **R22** cl. 3 | It SHALL show neutral copy. |
| **R22** cl. 4 | Sign-out SHALL propagate to every open tab. — **`tasks.md:67` gives this to task 39**, not T33. Out of scope here. |
| **R22** reuse half | **Removed** (2026-10-10, Q5): no refresh token is issued to this client, so there is nothing to rotate or detect reuse of. |
| **R21** | At most one renewal per tab, shared among parallel 401s and sibling tabs, and on failure routed to clean re-auth **without an error loop**. T33 does not implement R21 — it is built — but T33 must not regress it. |
| **R19** | Sign-out clears in-memory token state. Tasks 35–36 own it. T33 touches the same clearing primitive and must not duplicate it. |

## Locked decisions that constrain this task

| ID | Constraint |
|---|---|
| **L18** `[ALL]` | At most one renewal per tab; tabs share renewal **and sign-out** through BroadcastChannel or a Web Locks leader. The sharing mechanism exists (`crossTabRenewal.ts`); T33's routing must behave correctly in a **follower** tab, which adopts a sibling's failure rather than renewing again. |
| **L4** `[ALL]` | Copy, redirects, styling and timing are identical across every account state and token condition on every public surface. The re-auth copy may not name a cause. The admin carve-out (Q14, D-033) is irrelevant here. |
| **L3** `[ALL]` | Access tokens never reach storage, logs or analytics. A failure path must not log the token or the reason anywhere a visitor or a log reader could see it. |
| **L16** `[ALL]` | The sessionStorage carve-out is exhaustive — `state`, `nonce`, `code_verifier`, the return target, and R29 form input. **A re-auth notice or flag may not be stored.** |
| **L11** `[ALL]` | Catalogue-only strings, live regions for async results, managed focus across redirect steps, WCAG AA. |
| **L14** `[ALL]` | The route set is verbatim in §4c. `sign-in` and `signed-out` are public; everything else needs a session. **A new public route requires an ADR**, so T33 may not invent a destination. |
| **L1** | Allowlisted post-login return target, falling back to the home route — relevant if clean re-auth preserves where the visitor was. |
| **L13** | Confirmed stack; nothing new may be added for this task. |

## Files involved

**Existing, to read and extend:**

| File | Why |
|---|---|
| `src/api/sessionBoundary.ts` | Owns `onRenewalFailed`, already runs it once per renewal in a microtask, and already suppresses a second renewal for callers that waited behind a failed one (`waitedOnFailedRenewal`). **Not to be redesigned.** |
| `src/auth/accessToken.ts` | `clearAccessToken()` is the one clearing primitive; `renew()` is the rejecting seam. Its doc already names T33 as the task that routes the failure. |
| `src/api/crossTabRenewal.ts` | `SiblingRenewalFailedError`, the reasonless `renewal:failed` outcome, `SharedRenewal.close()`. |
| `src/auth/signInNotice.ts` | The in-memory, single-read notice. Its union has one member and the file calls itself a forward-compatibility hook. |
| `src/i18n/catalogue.ts` | Where any copy must live (L11). `signIn.discarded` is the existing neutral precedent. |
| `src/app/routes.tsx` | `SIGN_IN_PATH`, `PUBLIC_ROUTE_PATHS`, `AUTHENTICATED_ROUTE_PATHS`, `APP_BASENAME`. |
| `src/auth/SignInScreen.tsx` | Renders the notice, once per mount, only while idle. |

**New, expected by the spec:**

| File | Why |
|---|---|
| `src/api/failedRenewalTriggersCleanReauthWithoutHalfSession.test.ts(x)` | The named test from `package.md:128`. Does not exist in any form. |
| A production module that routes a failed renewal to clean re-auth | Nothing implements `onRenewalFailed` today. Its name and placement are phase 2's. |

## Dependencies

- **Modules:** `sessionBoundary.ts` (`SessionBoundary`, `SessionTokens`, `RENEWAL_TIMEOUT_MS`),
  `crossTabRenewal.ts` (`LocalSession`, `SharedRenewal`, `SiblingRenewalFailedError`, `RENEWAL_CHANNEL`,
  `RENEWAL_LOCK`, `SIBLING_GRACE_MS`), `accessToken.ts` (`accessToken`, `clearAccessToken`),
  `signInNotice.ts`, `routes.tsx`, `catalogue.ts`.
- **Browser APIs:** `BroadcastChannel`, `navigator.locks`, `history`/`location` through react-router.
- **Config keys:** none new. `VITE_OIDC_*` and `VITE_API_BASE_PATH` are closed (Q6) and already consumed by
  `authorizeRequest.ts`.
- **Contracts:** none directly. A failed renewal is a client-side transition; no endpoint is called by the
  failure path itself. `oidc.yaml` matters only if phase 2 decides clean re-auth re-enters
  `/oauth2/authorize`.
- **No persistence, no entities, no repositories, no Flyway, no outbox.** The phase prompt's backend
  vocabulary does not apply to this package.

## Acceptance criteria

Draft, to be frozen in phase 4 after the phase 3 design challenge. Mapped to requirement IDs.

| AC | Criterion | Verifies |
|---|---|---|
| **AC1** | A rejected renewal leaves **no token held** — the in-memory token is cleared through the existing `clearAccessToken()` primitive, not a second copy of it. | R22 cl. 2 |
| **AC2** | The transition to clean re-auth runs **exactly once per renewal**, not once per waiting call, matching `sessionBoundary.ts`'s once-per-renewal hook contract. | R22 cl. 1, R21 |
| **AC3** | Every waiting call still receives its original 401, and a call that waited behind a failed renewal does **not** start another — no error loop. | R21 |
| **AC4** | The destination is an existing **public** route from `PUBLIC_ROUTE_PATHS`; no new route and no new literal spelling of one. | L14, R22 cl. 1 |
| **AC5** | The copy shown comes only from `i18n/catalogue.ts`, is neutral, and is **indistinguishable** from the copy for any other identity failure. | L4, L11, R22 cl. 3 |
| **AC6** | Nothing about the failure — no token, no reason, no flag — is written to `sessionStorage`, `localStorage`, the URL, `document.title`, `window.name` or any console channel. | L3, L16 |
| **AC7** | In a **follower** tab that adopts a sibling's failure, the same transition happens once, and no second renewal is started. | L18, R21 |
| **AC8** | No half-authenticated render: after the failure, no authenticated surface shows signed-in content. **Instrument scope depends on OQ-T33-2** — no route guard exists yet. | R22 cl. 2 |

## Tests required

**Named test from `package.md` §8:** `failedRenewalTriggersCleanReauthWithoutHalfSession` → R22
(`package.md:128`). This is the only one T33 owns.

**The other two in the prompt header are not this task's:**

- `signOutPropagatesToAllTabs` (`package.md:129`) → **task 39** (`tasks.md:67`).
- `reusedRefreshOrFailedRenewalTriggersCleanReauth` (`package.md:171`) → **task 34, struck out and
  removed** by `tasks.md:62` when Q5 resolved. It should not be written by anyone.

**Boundary tests implied by the ACs:** renewal rejects; renewal times out (`RENEWAL_TIMEOUT_MS`) — a
timeout is a different rejection reason reaching the same hook; a throwing `onRenewalFailed` hook must not
affect the waiters (the microtask scheduling in `sessionBoundary.ts:71` exists for exactly this, and it is
untested from the routing side); parallel 401s during one failed renewal; a follower tab adopting
`renewal:failed`; a leader that disappears mid-renewal; a second sign-in attempt in a tab that already
holds a token (the shape `clearAccessToken`'s own doc cites as the R22 violation it prevents).

## Open questions

- **OQ-T33-1 — where does clean re-auth land, and does it navigate?** R22 says "transition to clean
  re-auth" and **`design.md` has no session transition table**; §4c lists routes but not transitions.
  Candidates visible in the code: a route change to `/app/sign-in` (which already renders a notice and a
  "Continue to sign in" action), `/app/signed-out` (built for R19's sign-out landing, which is a different
  event), or a fresh `/oauth2/authorize` navigation (silent re-auth, which `accessToken.ts:38-40` says no
  task builds). L14 forbids inventing a route, so the choice is among the three. **This is a genuine
  blocker for phase 2's design and is the first thing the phase 3 challenge should test.**
- **OQ-T33-2 — is T33 expected to wire the boundary into the app, or only to deliver the routing the
  wiring will consume?** `createSessionBoundary` has **no production caller** (phase 0, §2). Without
  wiring, AC8 can only be instrumented against the module, not against a rendered screen; with wiring,
  T33 grows to include constructing the boundary, the shared renewal and every client in `mountShell`.
  `tasks.md:61` scopes T33 to "a failed renewal routes to re-auth with no half-session", which reads as
  the routing, not the wiring — but the task that does the wiring is not identified anywhere I can find.
- **OQ-T33-3 — does the re-auth notice reuse `signInNotice`'s `'discarded'` member or add one?** L4
  forbids copy that distinguishes causes, so two members with identical copy would be a distinction
  without a difference, while one member means the sign-in screen cannot tell the two paths apart (which
  may be exactly right). `signInNotice.ts` anticipates the question in its own doc.
- **Spec drift, not a blocker, but it should be recorded:** `package.md:171` still maps
  `reusedRefreshOrFailedRenewalTriggersCleanReauth` to R22 although `tasks.md:62` removed the task that
  verifies it; and **`package.md` §11's Q5 entry is not struck through** although `requirements.md:69-71`
  and `:74-76` both record it as resolved on 2026-10-10 and R22's reuse half as removed. Three places
  disagree about one closed decision.

**Phase 1 complete.** Phase 2 drafts the brief against these criteria and must take a position on
OQ-T33-1 and OQ-T33-2 rather than leaving them open.
