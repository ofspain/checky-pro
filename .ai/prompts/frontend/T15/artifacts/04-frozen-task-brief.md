# frontend · T15 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T15 — Authorize request shape |
| **Gate** | Human Approval |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` — **frozen; phases 5–13 may use nothing else** |

The phase 2 brief stands, amended below. Where a decision contradicts it, the decision wins. Three were settled by
running code; one of Kimi's recommended values was wrong and is corrected with the measurement.

## Disposition of Kimi's phase 3 findings

| # | Finding | Disposition |
|---|---|---|
| 1 | the authorize endpoint path is never named — **Medium** | **ACCEPTED — the best finding in the review** — D-1 |
| 2 | host dependencies missing, suite cannot run — **High** | **Premise rejected with evidence** — D-0. **Fifth occurrence.** |
| 3 | reopening `pkce.ts` needs an explicit locked decision | **Accepted** — D-2 |
| 4 | `redirect_uri` needs a trailing-slash rule | **Accepted** — D-3 |
| 5 | `client_id` has no declared home | **Accepted** — D-4 |
| 6 | the `routes.tsx` drift guard is underspecified | **Accepted; its proposed value corrected by measurement** — D-5 |
| 7 | no test for the rejection path | **Accepted** — D-6 |
| 8 | no runtime `localStorage` spy | **Premise wrong — already required; restated anyway** — D-7 |
| 9 | the `479/481` baseline is unverified | **Premise rejected; recommendation accepted** — D-8 |
| 10 | the no-verifier negative control is unspecified | **Accepted — and it is the subtlest point here** — D-9 |
| 11 | no guidance on parameter building | **Accepted** — D-10 |
| 12 | OQ-1 will still block T16 | **Accepted as tracking, with a correction** — D-11 |

Ten accepted, two premises rejected, one premise wrong but its substance already in the brief.

**One correction of my own, in Kimi's favour:** phases 0–2 cite `APP_BASENAME` at `routes.tsx:6`. It is at
**`:4`**, as finding 4 says; `callback` is at `:9`. My anchor was wrong and theirs was right.

## Decisions

### D-0 — Finding 2's premise is false, for the fifth time.

The review states `frontend/node_modules` is incomplete, `vite-plugin-pwa` is missing, and `vitest` fails with
`ERR_MODULE_NOT_FOUND`. **T12 closed this in its phase 6** (`ab9ec5c9`). The claim was raised and rejected with
evidence in T12 phase 3, T13 phase 3, T14 phase 3 and T14 phase 8; this is the fifth. Measured in this session
while verifying the findings below:

```
vitest run                  →  Tests  2 failed | 479 passed (481)
vitest run src/auth         →  Tests  67 passed (67)
tsc --noEmit / oxlint       →  clean
```

**There is no blocker.** The only residual host fact is that bare `pnpm` is not on PATH, which is why T07's AC2
case fails.

### D-1 — `AUTHORIZE_ENDPOINT_PATH` is published, and guarded against the edge map.

Accepted: the path was the one component of the request my brief left implicit while publishing every other.

```ts
export const AUTHORIZE_ENDPOINT_PATH = '/oauth2/authorize';
```

The URL is `${location.origin}${AUTHORIZE_ENDPOINT_PATH}`. The drift guard asserts the **`/oauth2/*` matcher is
still present in `infra/local/edge/Caddyfile`** (its `@auth path` line, `:12`), following T11's precedent of
guarding the edge map from the test that depends on it. If the edge stops routing `/oauth2/*` to auth, this task's
URL becomes wrong and the guard says so instead of sign-in failing silently.

`/oauth2/authorize` is Spring Authorization Server's default and the path the auth service's own tests drive
(`SasLoginIntegrationTest.java:86`, `:374`).

### D-2 — The `pkce.ts` modification is a locked decision, not an accidental boundary crossing.

Accepted and stated unmistakably:

> **`pkce.ts` gains exactly one export, `randomUrlSafeToken(byteLength = 32): string`, and
> `generateCodeVerifier()` becomes a caller of it. Nothing else in that file changes.**

Why there and not a new file: **T14's AC10 asserts `btoa(` appears in `pkce.ts`'s source**, so relocating the
encoder would break a passing test of a completed task. Why not duplicate: that repeats the `segmentPrefixPattern`
duplication the project has carried since T09/T11. Why not reuse `generateCodeVerifier()` for a nonce: it would be
actively misleading at the call site. **T11's phase 13 set the precedent** — the task that legitimately opens a
file is the moment to share one implementation.

**Phase 6 must re-run T14's suite and show 67/67 green.** T14 pins no exact export set, and its CSPRNG spy
("called once, with a `Uint8Array` of 32 bytes") still holds when `generateCodeVerifier` delegates.

### D-3 — The redirect URI derivation is trailing-slash safe.

Accepted. `APP_BASENAME` is `/app` today (`routes.tsx:4`), so naive concatenation works — but the derivation is
locked as: **strip any trailing `/` from `APP_BASENAME`, then append `/callback`**. A future `'/app/'` would
otherwise produce `/app//callback`, which the server's exact-match redirect URI would reject at sign-in with
nothing in this suite failing first.

### D-4 — `client_id` is a published constant.

Accepted:

```ts
export const CLIENT_ID = 'checky-spa';
```

It is a public identifier, not a secret, and fixed on the server
(`application.properties:51`, with **no** `${ENV:…}` override, unlike the redirect URIs below it). A case asserts
the URL carries it **and** that the literal `checky-spa` occurs exactly once in the module, so it cannot drift to
two values.

### D-5 — The drift guard is functional, and Kimi's proposed value is wrong.

Accepted in form, corrected in substance. The recommendation was to assert the redirect URI equals
`${location.origin}/app/callback` "for `location.origin = 'http://localhost'`". **Measured, jsdom's origin in this
suite is `http://localhost:3000`**, not `http://localhost`:

```
origin: "http://localhost:3000"   href: "http://localhost:3000/"
```

So a literal `http://localhost` would fail, and hardcoding `http://localhost:3000` would bake in a jsdom detail.
The guard is therefore:

1. import `APP_BASENAME` from `routes.tsx`, build the request, and assert the `redirect_uri` equals
   `` `${location.origin}/app/callback` `` — computed from the *live* origin, so it follows the environment;
2. assert `new URL(redirectUri).pathname === '/app/callback'` — the part that must not drift;
3. assert `APP_BASENAME` is a prefix of that pathname, so the derivation provably follows the route configuration
   rather than hardcoding `/app`.

**And a measurement that makes AC5's second half possible:** `vi.stubGlobal('location', { origin: … })` works
under this jsdom and `vi.unstubAllGlobals()` restores it (`configurable: true`). So "the redirect URI follows a
changed origin" can be tested directly, with no injection seam in the production module — which is better than
adding a parameter purely for testability.

### D-6 — The rejection path is a required test.

Accepted. `buildAuthorizeRequest` must reject rather than return a half-built request. Tested by mocking
`generatePkcePair` to yield an invalid verifier (`vi.mock` on the `pkce` module) and asserting rejection, **and**
that nothing was written to sessionStorage in that case — a partial write would leave a `state` with no verifier
and strand the next callback.

### D-7 — Finding 8's premise is wrong; the requirement was already there.

Phase 2's Required Tests read: *"sessionStorage holding exactly the three keys with `localStorage` untouched, **by
both a source scan and runtime spies (T14's pattern)**"*. The runtime spy was already required. Restated here so
it cannot be dropped: spies over **every `Storage.prototype` method** and every function on `console`, as T14's
`:404` does, with the same asymmetry noted — an indexed assignment onto a Storage object is a property set, not a
method call, so the source scan remains authoritative for that form.

### D-8 — The baseline is a rule, not a literal.

Finding 9's premise is wrong — **479/481 was measured**, repeatedly, through T14's phases 9–13. But its
recommendation matches what T13 (D-1) and T14 (D-1) already adopted, so AC10 becomes:

> **AC10.** Immediately before T15's own cases are judged, `vitest run` is recorded. The only failures are the two
> known environmental T07 cases. After T15, the total rises by exactly the number of cases T15 adds, the same two
> failures remain, and no other case changes state. **No file is modified except the one declared in D-2**, and
> T14's suite is shown green.

### D-9 — The no-verifier negative control, made concrete.

Accepted, and this is the subtlest point in the review. A matcher that searches the finished URL for the verifier
**cannot fail by accident** — the verifier is 43 random characters and will never appear by chance, so a green
result proves nothing about the matcher. Exactly the vacuity of T12's AC7, T13's AC8 and T14's AC1.

So the rule is asserted in both directions, through **one** matcher:

```ts
function urlLeaksVerifier(url: string, verifier: string): boolean;   // one implementation
```

- fed a **synthetic** URL carrying the verifier as a query parameter → must report a leak;
- fed the same with the verifier in the fragment, and in a nested `redirect_uri` parameter → must report a leak;
- fed the **real** URL → must report nothing.

Without the first three, the third is unfalsifiable.

### D-10 — `URLSearchParams`, and assertions parse rather than string-match.

Accepted. The module builds parameters with `URLSearchParams` (browser-native, no dependency, correct percent
encoding). Tests parse the URL with `new URL(...)` and read `searchParams`, so **no case depends on parameter
order**. The scope value is compared as the exact string `'openid profile email'` *and* as a split array equal to
`REQUESTED_SCOPES`, because OAuth2 scope is space-delimited and order is ours to fix.

### D-11 — OQ-1 is tracked, with a correction to the review's expectation.

Finding 12 says T16 or T20 "may need" a `VITE_*` key. On phase 0's evidence, **probably not**: a sign-in screen
needs the same authorize URL this task derives, and the token exchange (task 20) posts to `/oauth2/token` on the
same origin. The frozen position remains T13's — **the §4c allowlist is a ceiling, not a plan** — and the owner
decision stands open for whenever a task genuinely wants a key. **It does not gate T15.**

## Acceptance criteria, as frozen

AC1–AC9 stand as phase 1 wrote them, with **AC2 and AC5 extended by D-1 and D-4** (the endpoint path and
`client_id` are published constants with their own guards), **AC5 by D-3** (trailing-slash-safe) and **D-5** (the
functional drift guard plus the changed-origin case), **AC6 and AC9 by D-9** (the bidirectional matcher), and
**AC7 by D-7**. **AC10 is replaced** by D-8. Two criteria are added:

- **AC11** — `buildAuthorizeRequest` rejects on an invalid verifier and writes **nothing** to sessionStorage in
  that case (D-6).
- **AC12** — the `/oauth2/*` route is still present in the edge map, and `AUTHORIZE_ENDPOINT_PATH` sits under it
  (D-1).

## Open Questions

- **No blockers.** Finding 2's blocker does not exist (D-0).
- **OQ-1** — T01 `:130` versus §4c's four keys. Open for the owner, **gating nothing now** (D-11).
- **Recorded, no action:** the backend accepts `code_challenge_method=plain`
  (`RegisteredClientSeeder.java:60` requires a challenge; `S256` is absent from its main source). T14 published the
  literal-typed constant; **T15 is the task that actually sends it**, and nothing downstream checks.
