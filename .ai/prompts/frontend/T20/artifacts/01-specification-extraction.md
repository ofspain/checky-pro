# frontend · T20 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T20 — Code exchange sends the verifier |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

**Task statement (verbatim from `spec/frontend/tasks.md`, task 20):**
> **Code exchange sends the verifier.** The token request carries the stored `code_verifier`. Verifies `callbackValidatesNonceAndKeepsAccessTokenInMemory`. Cites R7.

---

## Business Rules

| ID | One sentence | T20's share |
|---|---|---|
| **R7** (`requirements.md:23-24`) | When the SAS login returns with a matching `state`, the SPA shall validate `nonce`, **exchange the code at `/oauth2/token` with the PKCE verifier**, and hold the access token in memory only (L3). | **The middle clause, and only it.** The `nonce` half is T19, already built. "Hold in memory only" is **task 21**, and "no token in any storage" is **task 22**. |
| **R8** (`requirements.md:25-26`) | A missing or mismatched callback `state` discards the attempt, completes **no exchange**, and returns the visitor to the sign-in start with neutral copy. | Not T20's to build, but T20 is what makes "no exchange" falsifiable: a real transport behind the seam is what the R8 assertions watch. T20 must not weaken the ordering. |

R7 is the only requirement the task header scopes, and nothing in the task statement requires widening. R8 is
listed because T20 changes the thing R8's existing tests observe, not because T20 implements any part of it.

## Locked Decisions

| ID | Constraint on T20 |
|---|---|
| **L1** (`design.md:8-10`) | Public OIDC client, authorization code **with PKCE**, **no client secret in the bundle**, exact-match redirect URIs. `RegisteredClientSeeder` registers `checky-spa` with `ClientAuthenticationMethod.NONE`, so the token request carries **no client authentication** — the verifier is the proof. |
| **L3** (`design.md:13-20`) | Tokens never reach storage, logs or analytics. Measured and recorded there: a real exchange for `checky-spa` issues **no `refresh_token`**, so renewal is silent re-authentication only. T20 is the first task that can *observe* the response, so it is the first that could leak one. |
| **L6** (`design.md:38-39`) | **Every backend call uses `libs/ts/api-client`, generated in CI from `contracts/api/*.yaml`**, with one named exception (the notification stream, R59/O7). This is T20's hard constraint and its one open blocker — see Open Questions. |
| **L4** (`design.md:21-26`) | Enumeration-safe UI: the visitor may not learn **which** failure occurred. A `400 invalid_grant`, a network failure and a bad nonce must be indistinguishable in copy, path, history action and query. |
| **L9** (`design.md:44-45`) | Single origin, **including the SAS endpoints**. The token request is a relative, same-origin path; no per-service URL. |
| **L10** (`design.md:46`) | No secret and no non-public configuration key in the bundle. `VITE_OIDC_*` keys are *permitted* by §4c (Q6, closed) but `src/auth` reads **none** of them today and its suites scan for `import.meta.env`. |
| **L11** (`design.md:47-48`) | Catalogue-only strings; any visitor-visible text comes from `src/i18n/catalogue.ts`. |
| **L13** (`design.md:51-52`) | Confirmed stack. Adds nothing: no new runtime dependency is needed, and `openapi-fetch` is already present through the client package. |
| **L14** (`design.md:53-56`) | SPA routes live under `/app`, disjoint from every edge-mapped backend prefix — which is why `/oauth2/token` is not and cannot be a SPA route. |
| **L16** (`design.md:58-60`) | The sessionStorage carve-out: `state`, `nonce`, `code_verifier`, return target — **single-use and deleted at callback**. T20 **reads** the verifier; deleting all three is **task 23**, so T20 must leave them in place. |
| **L17** (`design.md:61-62`) | API routes are NetworkOnly. Already satisfied by construction: `isNetworkOnlyRequest` is the negation of a narrow cacheable set, so the token response can never be cached. No work, but it must not regress. |
| **L18** (`design.md:63-64`) | One renewal per tab, shared across tabs. Not T20's, but the exchange T20 builds is the same operation silent renewal will reuse, so the seam must not be shaped so that only a sign-in can call it. |

## Files involved

**Existing, to extend:**

| File | Why |
|---|---|
| `frontend/src/auth/codeExchange.ts` | The seam. `AuthorizationCodeGrant` and `TokenResponse` are both documented as expected to grow here (`:10-12`, `:20-25`); `exchangeAuthorizationCode` currently throws (`:43-45`). |
| `frontend/src/auth/authorizeRequest.ts` | Source of `CLIENT_ID` (`:26`), `AUTHORIZE_STORAGE_KEYS.codeVerifier` (`:38`) and the **private** `redirectUri()` (`:59-62`), which the token request must reproduce exactly or SAS refuses the grant. |
| `frontend/src/auth/CallbackScreen.tsx` | The call site (`:116-129`). It already routes a rejection to `discard`; its comment says "Task 20 may route it somewhere more specific." |
| `libs/ts/api-client/src/index.ts` | The only hand-written client source — the one place a token-endpoint client could be published (`createAuthClient` is the existing precedent). |
| `libs/ts/api-client/package.json` | The `generate` script names `auth.yaml` alone, while L6 says `contracts/api/*.yaml`. |

**New, expected by the spec:**

- `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` — the named test
  (`package.md:111` → R7). **Shared with task 21**; T20 owns the verifier half.
- **A contract and a generated client for the token endpoint**, if the blocker below resolves that way.

**Must not be touched:** `libs/ts/api-client/src/generated/*` (generated, CI fails on drift) and anything under
`spec/`.

## Dependencies

- **Contracts.** `contracts/api/auth.yaml` — and the point of interest is what it *excludes*: its own description
  (`:4-7`) says SAS's protocol endpoints "are governed by SAS itself and are **intentionally not documented
  here** (R47/T33 scope)". So `/oauth2/token` has no contract anywhere in the repository.
- **Measured request shape** (`infra/local/bin/smoke:113-116`): `POST /oauth2/token`,
  `Content-Type: application/x-www-form-urlencoded`, body `grant_type=authorization_code`, `code`,
  `redirect_uri`, `client_id`, `code_verifier`. No `Authorization` header.
- **Measured response** (T19 phase 0/1 probes, re-confirmed in T19 phase 11): `access_token`, `expires_in`,
  `id_token`, `scope`, `token_type` — **no `refresh_token`**. A replayed code returns `400 invalid_grant`. The
  `id_token` echoes the nonce exactly, and its claim set differs from the access token's.
- **Edge routing** (`infra/local/edge/Caddyfile:12-16`): `/oauth2/*` → `auth:8080`, same origin as `/app*`.
- **Library capability** (`openapi-fetch@0.17.0`, `dist/index.d.ts:13-14,115`): `bodySerializer` is settable
  globally and per request, so a form-encoded body is expressible through a generated client.
- **Config keys.** None. §4c permits `VITE_OIDC_*` (Q6 closed) but `src/auth` reads none and must keep reading none.
- **No new runtime dependency.**

## Acceptance Criteria

Mapped to R7's middle clause. The verifier is the heart of the task; everything else is the conditions under which
sending it is honest.

| AC | Criterion | Requirement |
|---|---|---|
| **AC1** | The token request carries the **stored** `code_verifier`, read from `AUTHORIZE_STORAGE_KEYS.codeVerifier` — not a fresh one, not a copied literal, not a value passed down from the authorize request in memory. | R7 |
| **AC2** | The verifier that goes on the wire is proven to be the stored value over **at least two unrelated stored values**, so no assertion passes against a constant. | R7 |
| **AC3** | The request is `POST` to the relative path `/oauth2/token` — no absolute address, no per-service URL. | R7, L9 |
| **AC4** | The body is form-encoded and carries `grant_type=authorization_code`, `code`, `redirect_uri`, `client_id` and `code_verifier`, matching what the running server accepts. | R7 |
| **AC5** | `redirect_uri` is **the same value the authorize request sent**, derived from the route table, not spelled twice. | L1 |
| **AC6** | The request carries **no client secret and no client authentication** of any kind. | L1 |
| **AC7** | The call goes through the generated client; no hand-written `fetch`/`XMLHttpRequest`, and `lintRejectsHandWrittenBackendFetch` still reports the L6 rule for every banned form. | L6 |
| **AC8** | The `code` sent is the one from the callback query, after R8's checks admitted it — never read from storage, never re-derived. | R7, R8 |
| **AC9** | Nothing is written, deleted or cleared in session storage: all three L16 items survive the exchange on both the success and the failure path. Deleting them is task 23. | L16 |
| **AC10** | No token value — access token, ID token, or any segment of either — reaches storage, a URL, the DOM or a log. | L3 |
| **AC11** | A failed exchange (`400 invalid_grant`, a 5xx, a network failure, an unparseable body) ends in the **same** visitor outcome as R8's discard: identical copy, path, history action and query. | L4 |
| **AC12** | The response is consumed as data, not trusted as shape: a response missing `id_token`, or carrying a non-string one, is a rejection rather than a throw out of the exchange. | R7, AC18 of T19 |
| **AC13** | **No `refresh_token` is read, stored or expected** — and its absence from the real response is pinned by a test rather than by a comment. | L3, T19's I5 |
| **AC14** | T19's nonce check still runs on the real response, in the same place, and still cannot be hoisted above R8's state check. | R7, R8 |
| **AC15** | No new user-visible string; `signIn.discarded` is reused unchanged. | L11 |
| **AC16** | No `import.meta.env` read, no absolute address, no new dependency. | L10, L13 |
| **AC17** | The whole suite passes with the one known environmental failure and **no other**, and the run's **exit code** is checked, not only the pass/fail counts. | `package.md` §9 |
| **AC18** | The exchange is callable by something other than the callback screen, so silent renewal (L18, R21) can reuse it without a redesign. | L18 |
| **AC19** | The token response is never cached by the service worker — `isNetworkOnlyRequest` still answers true for `/oauth2/token`. | L17, R57 |

## Tests required

**Named test (`package.md` §8, `:111`):** `callbackValidatesNonceAndKeepsAccessTokenInMemory` → R7. Shared with
task 21. T20 owns: the verifier on the wire, the request shape, the failure outcome, and the absence of a
`refresh_token`. Task 21 owns the in-memory hold; task 22 owns the storage sweep across every store.

**Boundary tests implied:**

- **The verifier.** Stored value present and sent; **two unrelated stored values** proving the assertion is not
  against a constant; verifier absent from storage; verifier present but empty; a storage read that **throws**
  (it can, and there is no error boundary in production source).
- **The wire.** Method, relative path, content type, and every form field — observed through the established
  `stubBrowserFetch` pattern (`generatedClientCompiles.test.ts:26-43`), which stubs `Request` for browser-style
  relative URL resolution and `fetch` for recording. The L6 lint test's ALLOWED table explicitly permits
  `vi.spyOn(globalThis, 'fetch')` (`lintRejectsHandWrittenBackendFetch.test.ts:58`), so observing the transport is
  a sanctioned pattern rather than a hole in L6.
- **The response.** The measured success shape; `400 invalid_grant` (a replayed code, measured); a 5xx; a network
  rejection; a 200 with a non-JSON body; a 200 missing `id_token`; a 200 whose `id_token` is not a string.
- **Ordering.** No exchange at all when `state` is missing or mismatched — T17/T18's property, now against a real
  transport rather than a throwing stub.
- **Non-leakage.** No token part in either storage, the URL, the DOM, the tab title or the console.
- **Source scans.** No absolute address, no `import.meta.env`, no hand-spelled storage key, no `refresh_token`
  reference, no `console.`, no module-level mutable state in the new code.
- **Regression.** T19's 123 cases and T18's 134 must keep passing; any assertion they make about the seam's
  behaviour that T20 legitimately changes is **split, not rewritten** (T19-D6's rule, which T19 followed for T18).

## Open Questions

**OQ-1 · The token endpoint has no contract, and L6 requires one. This is a genuine blocker for implementation.**

L6 (`design.md:38-39`) requires every backend call to go through a client **generated from
`contracts/api/*.yaml`**, and names exactly one exception. `/oauth2/token` appears in no contract, and
`auth.yaml:4-7` says the SAS protocol surface is *intentionally* absent — a position `package.md` Q12(c) re-affirmed
when it closed, calling the same scope note authoritative. So this is not an oversight to be corrected by editing
`auth.yaml`; editing it would contradict a closed decision.

Three ways out, with the costs I can state from the repository rather than guess:

1. **A second contract file** (e.g. `contracts/api/oidc.yaml`) with just the token endpoint, a second generated
   module, and a published `createOidcClient()`. Satisfies L6's own wording — `*.yaml` is a glob — adds no
   exception and needs no ADR, and leaves `auth.yaml`'s scope note intact because the surface is documented
   elsewhere. Costs: a new file in a CODEOWNERS-reviewed directory whose changes "trigger CI for every consumer"
   (`contracts/README.md`), and it would be the first contract authored from the frontend side.
2. **An ADR adding a second named exception to L6**, then a hand-written request. Costs: an ADR under `docs/adr/`,
   plus a lint suppression that `lintRejectsHandWrittenBackendFetch` is built to notice — the rule bans `fetch`
   through every global alias, so the suppression is visible and permanent.
3. **Add the path to `auth.yaml`.** Cheapest mechanically, and **contradicts** that file's stated scope and Q12(c).

**I am not deciding this in Phase 1** — extraction, not design. Phase 2 will recommend one and Phase 3 (Kimi) is
the right place for it to be attacked. Recorded against `package.md` §11's convention: this blocks T20 only, not
the phase.

**OQ-2 · Where the access token goes is undecided, and T20 is the first task that holds one.** `SessionTokens`
(`src/api/sessionBoundary.ts:11-14`) declares the shape, but **nothing outside tests constructs it** and
`mountShell.tsx` builds no composition root. Task 21's statement says the token is "held in a module-scoped
variable" without saying whose module. T20 must hand the access token *somewhere* or deliberately drop it; which
of those is correct depends on task 21's shape. **Not blocking** if T20 returns it and lets task 21 decide where
it lives — but it is a real seam decision for Phase 2, and the wrong answer would have task 20 build storage that
task 21 then has to move.

**OQ-3 · `refresh_token`: pinning versus merely not reading.** Kimi's I5 (T19 phase 8) carried here: the absence is
currently asserted by a comment in `codeExchange.ts:19-25`. T20 can pin it against the real server, but a test
that asserts a *negative* about a live service is a test that fails when the service changes — which is the point,
but it must be the **contract** test's job, not a unit test's. Phase 2 decides which suite owns it. Not blocking.

**OQ-4 · What a failed exchange shows the visitor.** T19 routes a rejection to `discard` and its comment says T20
"may route it somewhere more specific." L4 forbids distinguishing causes, so the visitor-visible answer is already
fixed; the only open part is whether anything beyond the discard happens at all (a retry, a notice variant). The
conservative reading — nothing changes — needs no decision, so this is **not blocking**. Logged so the comment's
invitation is answered deliberately rather than by omission.

**Carried, not T20's and gating nothing:** `contracts/api/token-claims.md` is measured false on email/name and
`/userinfo` returns only `{"sub"}`; discovery advertises one scope for a three-scope client; the suite total and
the `oxlint` AC4 failure are still unenforced on CI (T18 Gap 6); `/oauth2/revoke` for a rejected token (T19 OQ-3,
the author); the ID token has no contract document (T19 OQ-4).

---

## Next — Phase 2 — task implementation brief.
