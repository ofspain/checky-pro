# frontend · T15 — Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T15 — Authorize request shape |
| **Spec section** | Phase 1a — Security properties |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/09-review-resolution.md`, `frontend/src/auth/authorizeRequest.ts`, `frontend/src/auth/authorizeRequestsExactlyTheDeclaredScopes.test.ts` |
| **Produces** | `artifacts/10-test-generation.md` |

**Task statement (verbatim):**
> Assemble the OIDC authorization request: exactly the three declared scopes, `state`, `nonce`, `code_challenge_method=S256` and the PKCE challenge from T14 — and persist the three L16 items the callback tasks will read back.

---

## What the suite is

**45 cases** in `frontend/src/auth/authorizeRequestsExactlyTheDeclaredScopes.test.ts`, organised by acceptance
criterion: the scopes (AC1), the challenge method (AC2), the stored-verifier correspondence (AC3), state and nonce
(AC4), the request's own shape (AC5), the verifier's absence from the URL (AC6), session storage (AC7), failing
closed (AC11), the drift guards (AC8/AC12), and the shared token helper T15 added to `pkce.ts` (D-2).

**Measured on this host, at commit `f5c2a7e4`:**

| | |
|---|---|
| `src/auth` (T14 + T15) | **112 / 112 pass** |
| Whole frontend suite | **524 pass / 526 total** |
| The 2 failures | `lintRejectsHandWrittenBackendFetch` AC2 and AC4 — T07's known `spawn pnpm ENOENT`; bare `pnpm` is not on this host's PATH. Pre-existing, unrelated, unchanged by this task. |
| `tsc --noEmit` | clean |
| `oxlint --no-ignore -c .oxlintrc.json src` | clean |
| Suite delta | 521/523 → 524/526, exactly **+3** over the phase-9 commit (`b799b54a`), which was itself 479/481 → 521/523 for T15's 42 cases. |

Per **D-8**, the baseline is the measured count immediately before the task rather than a literal asserted in
advance. T14's suite was re-run as part of `src/auth` and is green at 67 cases.

---

## Mutation testing

24 mutants defined, 23 run (**M18 is a placeholder that was never used** — kept numbered so the earlier phases'
references stay valid). Each mutant is applied to real source, the `src/auth` suite is run, and the file is
reverted with `git checkout --`. **Tests were committed before the first mutant ran this time**, after T14's
phase 11 lost its edits to exactly that revert.

M1–M20 and M23–M24 mutate the production module (or, for M24, the route table it reads). M21–M22 are **guard
weakenings**: they damage the test's own matchers to check the matchers are load-bearing rather than decorative.

| # | Mutant | Result | Killed by |
|---|---|---|---|
| M1 | a fourth scope `offline_access` requested | CAUGHT | exact scope value, split-array equality, `scopeIsExactlyDeclared` |
| M2 | scopes reordered to `openid email profile` | CAUGHT | order is ours to fix (D-10) |
| M3 | the `email` scope dropped | CAUGHT | same three |
| M4 | comma-delimited scope instead of space | CAUGHT | exact string `'openid profile email'` |
| M5 | `code_challenge_method: 'plain'` | CAUGHT | AC2 — and this is the mutant that matters most, see below |
| M6 | the **verifier** sent in place of the challenge | CAUGHT | AC3 recompute-from-stored |
| M7 | stores a **different** pair than the one sent | CAUGHT | AC3 recompute-from-stored |
| M8 | `client_id` drifts to `checky-web` | CAUGHT | value check + single-occurrence guard |
| M9 | `response_type: 'token'` (implicit flow) | CAUGHT | AC5 |
| M10 | redirect base hardcoded `'/app'` | **SURVIVED → now CAUGHT** | the two new route-table cases |
| M11 | trailing slash on the redirect URI | CAUGHT | D-3 strip rule, `not.toContain('//')` |
| M12 | endpoint misspelled `/oauth2/authorise` | **SURVIVED → now CAUGHT** | the new backend-anchored spelling case |
| M13 | `nonce` reuses `state` | CAUGHT | distinctness |
| M14 | constant `state` across requests | CAUGHT | freshness over 50 requests |
| M15 | the three items written to `localStorage` | CAUGHT | key-set equality + runtime Storage spies |
| M16 | the pair contract no longer checked (`if (false)`) | CAUGHT | AC11 invalid-verifier case |
| M17 | derivation failure swallowed instead of propagated | CAUGHT | AC11 rejection case |
| M18 | *(placeholder, not used)* | — | — |
| M19 | a **ninth** parameter `login_hint=someone@example.com` | CAUGHT | the phase-9 S1 fix, exactly |
| M20 | the verifier appended to the URL | CAUGHT | 2 leak cases |
| M21 | the leak matcher always answers "clean" | CAUGHT | 5 cases (bidirectional matcher, D-9) |
| M22 | scope matcher becomes a `contains` check | CAUGHT | 3 cases |
| M23 | the backend anchor regex matches nothing | CAUGHT | the new case's own non-vacuity assertions |
| M24 | the **route table itself** drifts to `/console` | CAUGHT | 3 cases incl. the `routes.tsx` drift guard |

**23 run, 23 caught** — after the two fixes below. The first pass was 18/20.

### M19 is the phase-9 fix earning its place

The self-review's S1 finding — that nothing asserted the parameter set was *exactly* the eight — looked like
tidiness. M19 shows it is not: a ninth parameter carrying an email address into a URL that lands in browser
history, the edge's access log and any referrer is a real PII leak, and **no other case in the suite notices it.**
Every value check passes, the leak matcher passes, the scope matcher passes. Only `[...keys()].sort()` equality
fails. The finding paid for itself.

### M5 is the one the backend cannot catch

`code_challenge_method: 'plain'` downgrades PKCE to no protection at all, and it is **accepted by the server**:
`RegisteredClientSeeder.java:60` sets `requireProofKey(true)`, but `S256` appears nowhere in the auth service's
main source, and Spring Authorization Server offers no switch to forbid `plain`. The S256 rule is kept by this
test and nothing else in the platform. That is recorded here because it is the suite's single highest-value case.

---

## The two survivors, and why they survived

Both were the **same defect class**, and it is the class this project keeps meeting: *an assertion compared
against the very constant it is testing.* T12's AC7, T13's AC8, T14's AC1 and T15's own S1/S2 were all versions of
it. These two are the sharpest yet, because each looked like a real assertion.

### M10 — `const base = '/app'` instead of `APP_BASENAME`

The test asserted:

```ts
expect(redirect).toBe(`${globalThis.location.origin}${APP_BASENAME}/callback`);
```

Which is true of the mutant, because `APP_BASENAME` **is** `/app`. Hardcoding and deriving produce an identical
string, so **no assertion against the real route table can separate them.** Kimi's phase-3 finding 6 had asked for
exactly this guard and I built it as written; the guard is simply incapable of the thing it was meant to prove.

The only test that can tell them apart is one where the route table says something else:

```ts
it('follows the route table when the basename changes, rather than hardcoding /app', async () => {
  vi.doMock('../app/routes', async () => {
    const actual = await vi.importActual<typeof import('../app/routes')>('../app/routes');
    return { ...actual, APP_BASENAME: '/console' };
  });
  const { buildAuthorizeRequest: build } = await import('./authorizeRequest');
  const { url } = await build();
  expect(parameters(url).get('redirect_uri')).toBe(`${globalThis.location.origin}/console/callback`);
```

A second case mocks `'/console/'` **with** the trailing slash, so D-3's strip rule is measured on the module
rather than restated in the test — the pre-existing `would survive a trailing slash` case reimplements the rule
locally and therefore proves nothing about the module. Both cases kill M10.

`afterEach` now calls `vi.doUnmock` on both `'./pkce'` and `'../app/routes'`: `vi.resetModules()` drops loaded
instances but leaves the factory registered for the next dynamic import, and the fail-closed cases import
dynamically.

### M12 — `/oauth2/authorise`

Two guards both passed the misspelling:

- the edge drift guard asserts only `AUTHORIZE_ENDPOINT_PATH.startsWith('/oauth2/')` — which a misspelling satisfies;
- the URL case asserts `parsed.pathname === AUTHORIZE_ENDPOINT_PATH` — the constant against itself.

So the spelling needed an authority **outside this module**. Two were available, and both are used:

1. **A literal written out independently here** — `expect(AUTHORIZE_ENDPOINT_PATH).toBe('/oauth2/authorize')`.
   This is the same device as T14's RFC 7636 Appendix B vector: an expected value written from the specification
   rather than read from the code under test.
2. **The auth service's own integration tests.** `EndToEndLifecycleIntegrationTest.java:640` and
   `SasLoginIntegrationTest.java:669` both build `baseUrl + "/oauth2/authorize"` and drive it against a live
   Spring Authorization Server. The new case walks `services/auth/src/test/java`, collects every `"/oauth2/…"`
   literal, and asserts the set contains `AUTHORIZE_ENDPOINT_PATH`. The whole tree yields exactly two paths —
   `/oauth2/authorize` and `/oauth2/token` — so the containment is tight, and if SAS ever moved the endpoint those
   tests would be the first thing to change.

**The backend anchor is itself checked for vacuity**, because a cross-file scan that silently matches nothing is a
guard that always passes: the case asserts the file list exceeds 50 entries and the extracted set is non-empty.
**M23** damages the regex to match nothing and the case fails — so the anchor is load-bearing, not decorative.

### M24 — firing the drift guard

The `routes.tsx` drift guard had never actually been triggered. M24 moves `APP_BASENAME` to `/console` in the real
route table and three cases fail, including the guard itself. Worth noting what this means in practice: if someone
legitimately re-bases the SPA, **this suite fails loudly and tells them the server's registered redirect URI must
move with it** — which is the correct outcome, since `application.properties:52` holds
`SPA_REDIRECT_URI:http://localhost:5173/app/callback` and an exact-match comparison would otherwise reject every
sign-in.

---

## AC coverage

| AC | Covered by | Mutants proving it |
|---|---|---|
| AC1 — exactly the three scopes, in order | exact string, split-array equality, bidirectional `scopeIsExactlyDeclared` | M1, M2, M3, M4, M22 |
| AC2 — `code_challenge_method=S256` from T14's constant | imported constant, no quoted `'S256'` in source | **M5** |
| AC3 — the sent challenge belongs to the stored verifier | reads the stored verifier back and recomputes its challenge | M6, M7 |
| AC4 — `state` and `nonce` present, distinct, fresh, CSPRNG-sourced | presence, distinctness, length (literal 43 **and** `randomUrlSafeToken().length`), 50-request freshness, source scan | M13, M14 |
| AC5 — `response_type`, `client_id`, `redirect_uri`, endpoint path | value checks, single-occurrence `checky-spa`, changed-origin case, route-table derivation | M8, M9, M10, M11, M12, M24 |
| AC6 — the verifier never reaches the URL | 5 leak shapes through one matcher, parameter iteration, absent from the return type | M20, M21 |
| AC7 — exactly three sessionStorage keys, nothing else touched | key-set equality, overwrite-on-retry, runtime Storage/console spies | M15 |
| AC8/AC12 — drift guards | `agents.md` scope sentence, edge `@auth` matcher, `routes.tsx`, the backend path set | M12, M23, M24 |
| AC9 — non-vacuity | every matcher fed deliberately wrong input; exact-eight parameter set with a ninth-parameter control | **M19**, M21, M22, M23 |
| AC10 (D-8) — measured suite delta, no earlier file modified | counts above; `git show --stat` | — |
| AC11 — fails closed | invalid-verifier and derivation-failure cases, both asserting empty storage | M16, M17 |

---

## Coverage, honestly

**What this suite does not establish:**

- **The verifier leak matcher is a substring search.** It catches the verifier appearing literally — as a
  parameter, in a fragment, in a nested redirect URI, in a path segment, repeated — and that is all. A verifier
  split across two parameters, hashed, or re-encoded would pass. This is stated in the test file so the guard is
  not over-trusted; the **structural** defence is stronger and is the real one: the verifier is not in
  `AuthorizeRequest`, so a caller cannot put it in a URL, a log or a React prop by accident.
- **No browser runs these tests.** jsdom provides `crypto.subtle`, `getRandomValues`, `btoa` and `sessionStorage`,
  and the module uses only those four plus `URLSearchParams` and `location.origin` — but "works in jsdom" is not
  "works in Safari". The first real evidence arrives when T16's screen is put in front of the running stack.
- **Nothing here proves the server accepts this request.** Every parameter is checked against the *registered*
  client in `application.properties` and `RegisteredClientSeeder.java` by reading those files, not by exchanging
  anything. The authorize round trip is task 16's to demonstrate against the live backend.
- **The `location` stub is narrower than a real `Location`.** Mitigated, not solved, by the source-level assertion
  that the module reads `location.origin` and nothing else — which is a scan over source text, so a computed
  access (`location['pathname']`) would evade it.
- **M10's fix depends on module mocking**, which is a Vitest facility rather than a property of the code. It proves
  the module *reads* `APP_BASENAME` at call time; it cannot prove a future refactor will not capture it at import
  time in a way the mock still satisfies.
- **The two T07 failures remain environmental.** They are a missing `pnpm` on this host's PATH, not a defect, and
  they will stay red here until the host gets one. `npx -y pnpm@12.14.0` is the working recipe; T07's AC2 shells
  out to bare `pnpm`.

**What it does establish:** the request carries exactly eight parameters and exactly three scopes; the challenge
sent always belongs to the verifier stored; `plain` cannot ship even though the backend would accept it; the
verifier reaches session storage and nothing else; a failure anywhere in the derivation leaves storage empty; and
every constant in the URL answers to something outside this module — the route table, the edge map, `agents.md`,
or the auth service's own integration tests.

---

## Next

**Phase 11 is Kimi 2.7's test review.** Stopping here per the standing rule.
