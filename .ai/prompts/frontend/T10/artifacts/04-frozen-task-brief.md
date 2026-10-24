# frontend · T10 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T10 — End-session navigation bypasses the worker |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |
| **Status** | **FROZEN** — implementation follows this document. |

### Task
T10 — **End-session navigation bypasses the worker.** `/connect/logout` is never answered from cache. Verifies
`endSessionNavigationBypassesServiceWorker`. Cites R57.

---

## Gate decisions

### D1 — T10 is test-only. **(Open Question 1, settled.)**
`isDenylistedNavigation` reads `url.pathname` (`navigationDenylist.ts:55-58`) and `/connect/logout` is one of the
eleven, so every end-session URL shape is already declined. T10 adds **no production code**, on the T08-over-T07
precedent. Its value is proving the flow R19 depends on, and failing loudly if a later change narrows the policy to
bare paths. **If any AC fails against the current module, phase 6 reports a defect; it does not edit the module.**

### D2 — The URL shape is written into this brief, verbatim. **(Finding 1, accepted.)**
From `services/auth/src/test/java/com/themistra/auth/authn/SasLoginIntegrationTest.java:519-527` and `:488-490`:

```
GET /connect/logout?id_token_hint=<JWT>&post_logout_redirect_uri=http://localhost:5173/app/signed-out&state=s1
→ 302, Location: http://localhost:5173/app/signed-out?state=s1
```

The allowlisted default is `http://localhost:5173/app/signed-out`
(`services/auth/src/main/resources/application.properties:53`). A target outside the allowlist is `400` and the SAS
session survives (`SasLoginIntegrationTest:501-517`).

**The six AC4 variants, fixed here so the tester guesses nothing:**
1. all three parameters, as above;
2. no parameters — bare `/connect/logout`;
3. `id_token_hint` only;
4. a trailing slash — `/connect/logout/?id_token_hint=…`;
5. a percent-encoded `post_logout_redirect_uri`;
6. a foreign-origin `post_logout_redirect_uri` (the `400` case) — included for the *reason* (an error response must
   not be cached either), not as a URL the SPA constructs.

### D3 — `routes.tsx` is imported, not parsed. **(Finding 2, accepted.)**
`frontend/src/app/routes.tsx` has no module-level side effect — `createBrowserRouter` is called only inside
`createAppRouter()` — and T02's own test already imports its constants
(`routesResolveUnderAppPrefixOnly.test.tsx:6`). AC7 therefore imports `APP_BASENAME` and `PUBLIC_ROUTE_PATHS` and
asserts `'signed-out'` is among them, which is a typed check rather than a brittle regex over source text.

### D4 — The harness is duplicated, with a visible warning. **(Finding 3, accepted in part.)**
A shared helper would have to be imported by T09's test to avoid two copies, and that is an edit to a frozen file
(L12). A new helper file used by T10 alone would leave two copies anyway. So `simulateNavigationFallback` is copied
into T10's file, under a header comment that names T09's copy, says both are stand-ins for Workbox's navigation
fallback, and says task 12 replaces both with the real route. T09's file is not touched, so the warning can only
live in T10's — recorded here so the asymmetry is deliberate.

### D5 — The limits of a test-only task are stated plainly. **(Findings 4 and 6, accepted.)**
Written into the test file and into phase 13 in these terms: *T10 demonstrates that T09's policy, wired in the
denylist-before-cache order, keeps the end-session response and the ID token out of Cache Storage. It does not
constrain how task 12 wires the real worker, and the second call in the two-step case stands in for the browser
following SAS's 302 — jsdom has no `FetchEvent`, so the redirect chain itself is task 12's integration concern.*

### D6 — The precache carry-forward is locked, in testable words. **(Finding 5, accepted.)**
**Task 12 must assert that its generated precache manifest contains no URL matching `NAVIGATION_DENYLIST` —
`/connect/logout` in particular — and that its navigation route still answers `/app/signed-out` from the shell.**
Carried from T09 phase 13, now with the second half named, because `/app/signed-out` is a shell route that *will* be
precached and the two requirements pull in opposite directions.

### D7 — One cache-key assertion is added. **(Finding 7, accepted in a narrower form.)**
Kimi's leak scenario needs a token on `/app/signed-out`, which SAS never produces — it appends only `?state=…`. The
real risk the finding points at is a worker keying the cache by full URL, so AC5 asserts the shell is answered for
both `/app/signed-out` and `/app/signed-out?state=s1`, and that **every** cache key used in the whole file is the
shell's constant: no key ever contains `?`, `id_token_hint`, or the token value.

---

## Scope
**In** — `frontend/src/pwa/endSessionNavigationBypassesServiceWorker.test.ts`: the D2 shapes, AC1–AC8 below, a local
harness per D4, and the mutation breaks.
**Out** — any production change (D1); any edit to `navigationDenylist.ts` or T09's test (L12); registering or
emitting a worker (T09 D1 — task 12); the precache manifest guard (D6 — task 12); API NetworkOnly (task 11); the
sign-out control, token clearing and cross-tab propagation (later tasks own R19/R22's behaviour); any backend,
contract, `spec/` or `infra/` change.

## Business rules and locked decisions
R57 (`requirements.md:203-205`); R19 (`:62-65`), whose closing sentence is this task's subject; R22 (`:72-75`);
`agents.md:35-36`, `:88-89`; L17 (`design.md:61-62`), L3 (`:191`), §5 (`:194`), L14 (`:139`), L18 (`:63`), L12.

## Files
**Create** `frontend/src/pwa/endSessionNavigationBypassesServiceWorker.test.ts`. **Modify** none.
**Do not touch** `navigationDenylist.ts`, `sasNavigationBypassesServiceWorker.test.ts`, every T01–T08 file,
`vite.config.ts`, `.oxlintrc.json`, `libs/**`, `contracts/**`, `spec/**`, `services/**`, `infra/**`.

## Acceptance Criteria
- **AC1 (R57, R19)** — the full end-session navigation is declined: nothing from cache, no shell, network sees it.
- **AC2 (L3, §5)** — no cache read or write ever occurs with a key containing the ID token, `id_token_hint`, or
  `/connect/logout`, for any D2 variant.
- **AC3 (R19, L14)** — `/app/signed-out` and `/app/signed-out?state=s1` are answered with the shell.
- **AC4 (R57, R19)** — all six D2 variants are declined and uncached.
- **AC5 (R19, D7)** — the two-step flow in order: navigation 1 declined with no cache access, navigation 2 answered
  from the shell, and every cache key used in the file is the shell's constant — never one containing `?`.
- **AC6 (R22, L18)** — a repeated end-session navigation is declined identically, so a second tab is never served a
  cached response from the first tab's attempt.
- **AC7 (drift, D3)** — `/connect/logout` is still in `DENYLISTED_NAVIGATION_PATHS` and in `agents.md`'s rule, and
  `'signed-out'` is still in `PUBLIC_ROUTE_PATHS` under `APP_BASENAME`.
- **AC8 (L12)** — the whole frontend suite stays green, no T01–T09 file edited.

## Required test
`endSessionNavigationBypassesServiceWorker`, groups AC1–AC7, with a clearly synthetic JWT-shaped `id_token_hint`
so nothing resembling a credential is committed.

**AC-M — mutation breaks:** remove `/connect/logout` from the list; decide on `url.href` or `url.search` instead of
`url.pathname`; let the shell answer `/connect/logout`; read the cache before the denylist check (harness); make the
shell matcher reject `/app/signed-out`; key the harness cache by full URL.

## Open questions carried forward
D6's precache/shell tension, and task 12's obligation to apply the denylist before any cache lookup in the real
worker. Neither is actionable here.
