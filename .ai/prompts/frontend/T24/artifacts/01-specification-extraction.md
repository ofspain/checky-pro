# frontend · T24 · Phase 1 — Specification Extraction

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T24 — Return target inside allowlist |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

**Task statement** (`tasks.md:36`): *A return target on the allowlist is honoured. Verifies
`returnTargetOutsideAllowlistFallsBackToHome`. Cites R7, L1.*

**The first extraction finding, because it changes how the rest is read: R7 says nothing about a return target.**
Its text is the callback's token handling — validate `nonce`, exchange at `/oauth2/token` with the verifier, hold
the token in memory. The normative sentence this task exists to satisfy is **not an EARS requirement at all**; it
is a LOCKED decision (**L1**) and a standing rule (`agents.md:21`). So the acceptance criteria below are traced to
L1 and `agents.md` as their primary authority, with R7 as the *host* — the callback is where the rule takes
effect — and that is recorded rather than smoothed over.

---

## Business Rules

| ID | One sentence | Bearing on T24 |
|---|---|---|
| **R7** (`requirements.md:21-22`) | When the callback's `state` matches, validate `nonce`, exchange the code with the PKCE verifier, and hold the access token in memory only. | The **host**: T24's navigation happens at the end of this path, after T21's hold and T23's consumption. R7's own clauses are already built and must not change. |
| **R8** (`:25-26`) | A missing or mismatched `state` discards, exchanges nothing, and returns the visitor to the sign-in start with neutral copy. | The **boundary**: no discard may honour a return target, or the public callback route becomes a way to send a stranger's browser somewhere. |
| **R28** (`:...`) | While unauthenticated, serve only §4c's public routes and redirect everything else to the sign-in start. | **Not T24's** (it is T40/T41's), but it is the *producer* of a return target: the redirect R28 describes is what would capture where the visitor was going. Named because T24 cannot be understood without it. |
| **R29** (`:...`) | On session expiry keep only non-sensitive form input in sessionStorage with a 15-minute TTL; never tokens, passwords, recovery codes, API keys or PII. | The storage discipline a stored return target must satisfy: a path is not PII, but a path **with a query string or fragment can carry anything**. |
| **R30** | An identity-screen error keeps copy, redirect target and visual treatment identical across causes. | A fallback to home must not be a distinguishable "your target was rejected" outcome. |

## Locked Decisions

| ID | What it constrains here |
|---|---|
| **L1** (`design.md:8-10`) | **The primary authority.** "Authorization code with PKCE; … exact-match redirect URIs; **allowlisted post-login return target, falling back to the home route**." Two obligations: an allowlist check, and a fallback that is the home route specifically — not the current route, not the sign-in screen, not an error. |
| **`agents.md:21-22`** | The same rule, with the sharper clause: *"If it is outside the allowlist, the SPA falls back to the home route, **never to an arbitrary URL**."* "Never an arbitrary URL" is what makes this a security control rather than a convenience. |
| **L16** (`design.md:58-60`) | sessionStorage may hold *"`state`, `nonce`, `code_verifier`, **and the post-login return target**, single-use and deleted at callback"*. So storing it there is **sanctioned by name**, and it is **single-use and deleted at callback** on the same terms as the other three. |
| **L14** (`design.md:53-56`) | Every SPA route lives under `/app`, the paths are listed verbatim in §4c, and a new public route needs an ADR. Any allowlist must be a subset of that set — which is why deriving it from `routes.tsx` rather than writing a second list is the package's own pattern. |
| **L4** | Neutral copy; no outcome distinguishes its cause. |
| **L11** | Catalogue-only strings, managed focus across the redirect. |
| **L3** | Tokens in memory only — untouched, but T24 runs in the same effect that holds the token. |
| **L12** | Phase additivity: T24 adds to the Phase 1 shell and auth model, it does not replace them. |

## Files involved

**Existing, to read and extend:**
- `frontend/src/auth/CallbackScreen.tsx` — the successful path ends at `:165` with no navigation. T24's navigation goes after it.
- `frontend/src/app/routes.tsx` — `APP_BASENAME`, `PUBLIC_ROUTE_PATHS`, `AUTHENTICATED_ROUTE_PATHS` (includes `home`), `SIGN_IN_PATH`, `CALLBACK_PATH`. The only existing enumeration of legal destinations.
- `frontend/src/auth/authorizeRequest.ts` — `AUTHORIZE_STORAGE_KEYS` and `consumeAuthorizeState()`, which deletes **every** value of that constant (T23). Any storage key for the return target interacts with this directly.
- `frontend/src/auth/signInNotice.ts` — the package's existing single-read module pattern.

**New, expected by the spec:**
- `frontend/src/auth/returnTargetOutsideAllowlistFallsBackToHome.test.tsx` — the named test (`package.md:158` maps it to R7). **Shared with T25.**
- Whatever module owns the allowlist check. The spec does not name a file; the package's pattern suggests the module that owns the keys (`authorizeRequest.ts`) or a new small `auth/returnTarget.ts`. **Phase 2's decision, not Phase 1's.**

**Not to modify:** `spec/**`; `auth/codeExchange.ts` and `auth/idTokenNonce.ts` (pure readers, T23-D1, three tests assert it); `auth/accessToken.ts`; `.oxlintrc.json` (T22-D12).

## Dependencies

| Kind | What |
|---|---|
| Route constants | `APP_BASENAME = '/app'`, `AUTHENTICATED_ROUTE_PATHS`, `PUBLIC_ROUTE_PATHS`, `SIGN_IN_PATH`, `CALLBACK_PATH` (`app/routes.tsx`) |
| Storage keys | `AUTHORIZE_STORAGE_KEYS` and `consumeAuthorizeState()` (`auth/authorizeRequest.ts`) |
| Navigation | `useNavigate()` from react-router 8.4.0, as `CallbackScreen` already uses for discards (`:82`, with `{ replace: true }`) |
| Session signal | `accessToken.current()` (`auth/accessToken.ts`) — the only "am I signed in" signal that exists |
| Config keys | **none.** The home route is a route path, not configuration; L10 keeps new keys out of the bundle |
| Contracts | **none apply.** A return target is purely client-side; it is not sent to the authorization server (unlike `post_logout_redirect_uri`, which is T36's and *is* server-allowlisted) |
| i18n | no new catalogue key expected — T24 navigates, it does not announce |

## Acceptance Criteria

| AC | Criterion | Traces to |
|---|---|---|
| **AC1** | A return target that is on the allowlist is navigated to after a successful callback, instead of the visitor being left on `/app/callback`. | **L1**, `agents.md:21`, R7 (host) |
| **AC2** | A target that is **not** on the allowlist falls back to **the home route** — not the current route, not sign-in, not an error screen. | **L1** ("falling back to the home route"), `agents.md:22` |
| **AC3** | An **absent** return target also lands on the home route, by the same path as a rejected one, so a visitor who signed in directly is not left on the callback screen. | L1, R7 |
| **AC4** | No **discarded** callback honours a return target, on any of its five failure shapes. | **R8**, `agents.md:22` |
| **AC5** | An absolute or protocol-relative URL is never honoured — `//evil.example`, `https://evil.example`, `javascript:`, `\\evil`, a backslash-prefixed path, and a target that merely *starts with* an allowlisted path (`/app/homeward`, `/app/home.evil.com`) all fall back. | **`agents.md:22`** "never to an arbitrary URL" |
| **AC6** | The allowlist is **derived from the route table**, not a second hand-written list, and a route added to the table cannot silently become unreachable (or a stale entry silently legal). | L14, and the package's derive-never-duplicate pattern |
| **AC7** | A stored return target is **single-use and deleted at callback**, on the same terms as the other three L16 items, and is readable **before** it is deleted. | **L16** |
| **AC8** | Nothing else enters storage; no token, no PII, and no query string or fragment survives into a stored target unless Phase 2 decides explicitly that it may. | L16, R29, L3 |
| **AC9** | The fallback is indistinguishable from an honoured target in copy and in treatment — there is no "your destination was rejected" message. | **L4**, R30 |
| **AC10** | No new catalogue string, no new dependency, and no change to the two pure readers or to the token holder. | L11, T23-D1, L3 |
| **AC11** | Suite green by **exit code** with the one known environmental failure; `tsc --noEmit` exit 0. | house rule |

## Tests required

**Named test (shared with T25):** `returnTargetOutsideAllowlistFallsBackToHome` — `package.md:158` → R7.
`tasks.md:36` and `:37` both name it, so one file serves both halves: **T24 the honoured case, T25 the rejected
case.** Phase 2 must partition the cases explicitly so T25 inherits work rather than a fait accompli.

**Boundary tests implied** (not optional — each is a line in the ACs above):

1. an allowlisted target → navigated to; the control being that the visitor would otherwise have stayed on `/app/callback`
2. no target at all → home
3. a target outside the allowlist → home (T25's half, but the mechanism is T24's)
4. a prefix-match attack: `/app/homeward`, `/app/home/../admin`, `/app/home%2f..` → home
5. an absolute URL, a protocol-relative URL, a `javascript:` URL, a backslash path → home
6. each of the five discard shapes with a target stored → sign-in, never the target
7. the target deleted after use, and readable before deletion (AC7's ordering, the shape T23 proved behaviourally)
8. a storage read that **throws** → treated as no target, never a crash (the package's standing rule)
9. a target whose path is legal but which carries a query or fragment → whatever Phase 2 decides, asserted either way
10. the allowlist derived from the route table: adding a path to the table makes it legal with no second edit

**Inherited assertions that will move:** T22's `tokensAreNeverWrittenToAnyStorage` asserts the whole session store
holds **exactly** L16's three items on the discard path and **nothing** on the consumed path; a fourth stored item
changes both. T23's named test pins `Object.keys(AUTHORIZE_STORAGE_KEYS)` to exactly three **with a message telling
task 24 what to decide**. Those are T24's to update, with their reasons restated — the discipline T23 used.

## Open Questions

**OQ-1 (genuine fork, needs the owner). Does T24 build the whole return-target mechanism, or only the honouring
half?** Nothing in the repository captures a return target, and R28/T41 — the redirect that would capture one — is
seventeen tasks away. Two coherent readings:

- **(a) T24 owns the mechanism** (capture helper + storage + allowlist + navigate), and T41 later calls it. The
  task becomes testable end to end, L16's named item comes into existence, and T41 is left a one-line call.
- **(b) T24 owns only the honouring** (read + allowlist + navigate), and the test seeds the stored value itself.
  Smaller, but the producer stays hypothetical and nothing proves the two halves agree.

My recommendation is **(a)**, because (b) cannot satisfy AC1's control — "instead of being left on the callback
screen" needs a real producer to be a real journey — and because L16 already names the item, so creating it is
not new scope, it is scope the spec assigned and no task has claimed. **This is a scope decision, so it is the
owner's.**

**OQ-2 (needs the owner, smaller). What is "the allowlist"?** Neither L1 nor `agents.md` names its contents.
`AUTHENTICATED_ROUTE_PATHS` is the obvious derivation and the only existing enumeration, but two of its entries are
parameterised (`invoices/:id`, `receipts/:id`), which a path-equality check cannot honour without pattern matching.
Phase 2 can decide this (exact paths now, patterns when a task needs them) and freeze it — I flag it here because
it is the difference between an allowlist of 11 strings and a matcher.

**OQ-3 (not a blocker, recorded).** `/app/home` has **no screen** — the route is registered with no element. So
"honoured" and "fell back to home" are verifiable as **navigations** (`router.state.location`), not as rendered
pages, for every target in the authenticated set. No test may claim more than that.

**OQ-4 (not a blocker, carried).** `oidcRoundTripEndToEnd` (`package.md:254` → L1) is in this task's named-test
list but is a Playwright-level test; no Playwright suite exists and no task owns one. Unchanged from T20–T23.

**Nothing here is blocked on `package.md` §11.** Q6, which confirmed the §4c route set, is closed; Q1–Q4 are
closed or moot; Q5 bears on renewal, not on this task.

## Next — Phase 2 — task implementation brief.
