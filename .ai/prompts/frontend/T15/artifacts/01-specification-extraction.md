# frontend · T15 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T15 — Authorize request shape |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

## Business Rules

| ID | Sentence | T15's part |
|---|---|---|
| **R6** (`requirements.md:21-22`) | On sign-in the SPA starts the OIDC authorization code flow **with PKCE** and renders no password field. | **Starting the flow** — assembling the request that does it. T14 built the PKCE primitive; T16 owns the screen. |
| **R20** (`requirements.md:66-67`) | Profile from `/userinfo` with scopes **`openid profile email`**; account status from `GET /accounts/me`; no PII or verification state read from the access token. | **The scope clause only.** `/userinfo` and `/accounts/me` are later tasks; T15 requests exactly the three scopes that make `/userinfo` usable. |
| **R7** (`requirements.md:23-24`) | On a callback with matching `state`, validate `nonce`, exchange the code with the PKCE verifier, hold the token in memory. | **Not implemented here — but it is why T15 must persist.** R7 and tasks 19–20 read *stored* values. |
| **R8** | A callback whose `state` is missing or differs from **the stored value** is discarded. | Same: the stored value must exist, and T15 is the only task that has it. |

## Locked Decisions

| ID | What it constrains |
|---|---|
| **L1** (`design.md:8-10`) | Public client, authorization code with PKCE, **`state` and `nonce` validated**, exact-match redirect URIs. T15 sends all three. The post-login return-target allowlist is also L1's, but see Scope below. |
| **L16** (`design.md:58-60`) | sessionStorage may hold, and only hold, `state`, `nonce`, `code_verifier` and the return target — **single-use, deleted at callback**. T15 writes the first three. |
| **L9** (`design.md:44`) | One origin; the edge owns every backend route. **This is what makes the authorize URL derivable** and why no environment key is needed. |
| **L3** (`design.md:13`) | Tokens in memory only. No token exists yet at authorize time; the rule bites on what T15 may store. |
| **L14** (`design.md:52-55`) | `/app/callback` is a declared public route (`routes.tsx:9`). The redirect URI is built from `APP_BASENAME`, not configured. |
| **L2** (`design.md:11-12`) | Interactive login is SAS-hosted — T15 hands off to the server and renders nothing. |
| **L13** (`design.md:50-51`) | No OIDC library in the confirmed stack; hand-written. |
| **L12** | Additive: new files only. |
| `agents.md:37-38` | "The requested scopes are `openid profile email`." The sentence a drift guard should anchor to. |
| `agents.md:79-81` | Never in a URL, `localStorage`, logs or analytics; sessionStorage only for L16's items. **The `code_challenge` goes in the URL by design; the verifier must not.** |

## Files involved

**To read, not modify:**

| File | Why |
|---|---|
| `src/auth/pkce.ts` | `generatePkcePair()` and `PKCE_CODE_CHALLENGE_METHOD`. T15 **imports** the constant; it may not spell `'S256'` itself. |
| `src/app/routes.tsx:6,9` | `APP_BASENAME` and the `callback` path — the two halves of the redirect URI. |
| `src/api/sessionBoundary.ts:7-14` | `renew()` re-opens `/oauth2/authorize`, so this builder serves renewal too. |

**New:**

| File | Basis |
|---|---|
| `frontend/src/auth/<module>.ts` | `design.md:203` plans `src/auth/` as the OIDC client. |
| `frontend/src/auth/authorizeRequestsExactlyTheDeclaredScopes.test.ts` | `package.md:174` maps the name to R20. |

## Dependencies

| Kind | Item | Status |
|---|---|---|
| Internal | `src/auth/pkce.ts` — `generatePkcePair`, `PKCE_CODE_CHALLENGE_METHOD` | built, T14 |
| Internal | `src/app/routes.tsx` — `APP_BASENAME` | built, T01 |
| Platform | `crypto.getRandomValues` (for `state` and `nonce`), `sessionStorage`, `location.origin` | all present under this jsdom (measured, T14) |
| Config keys | **none** — see phase 0 §5 | — |
| Contracts | **none** — `/oauth2/authorize` is a SAS endpoint, deliberately outside `auth.yaml` (`design.md:133-135`) | — |
| npm | **none added** | — |
| Backend facts | `client_id` = `checky-spa` (`application.properties:51`, no env override); registered scopes exactly `openid`/`profile`/`email` (`RegisteredClientSeeder.java:56-58`); `requireProofKey(true)` but **`S256` not required** (`:60`); no consent screen (`:61`) | verified |

## Acceptance Criteria

| # | Criterion | Requirement |
|---|---|---|
| **AC1** | The request carries **exactly** `openid profile email` as its `scope` — no fourth scope, none missing, and the assertion is a set/sequence equality rather than a "contains" check. | R20, `agents.md:37-38` |
| **AC2** | `code_challenge_method` is **the constant imported from `pkce.ts`**, and its value is `S256`. The module does not contain the literal `'S256'` of its own. | R6, L1 |
| **AC3** | `code_challenge` equals `deriveCodeChallenge(state-bound verifier)` — i.e. the challenge sent is genuinely the one derived from the verifier that is stored, not a fresh unrelated pair. | R6, R7 |
| **AC4** | `state` and `nonce` are present, distinct from each other, unpredictable (CSPRNG, not `Math.random`), and different on every call. | L1, R8 |
| **AC5** | `response_type=code`, `client_id=checky-spa`, and `redirect_uri` is `${origin}${APP_BASENAME}/callback` — derived, with **no absolute URL literal and no environment key** in the source (T01 `:130-131`). | L1, L9, L14 |
| **AC6** | The **verifier never appears in the request** — not as a parameter, not in the URL, not in any value. Only its challenge does. | `agents.md:79-81` |
| **AC7** | `state`, `nonce` and `code_verifier` are written to **sessionStorage and nowhere else** — never `localStorage`, never a cookie, never a log. | L16, L3 |
| **AC8** | A drift guard: if `agents.md:37-38`'s scope sentence changes, or `routes.tsx`'s `callback` path or `APP_BASENAME` changes, the test fails rather than silently sending the wrong thing. | R20, L14 |
| **AC9** | **Non-vacuity.** The "exactly these scopes" and "no verifier in the request" rules are asserted against deliberately wrong inputs fed to the same matcher, not only against our own output. | the T12/T13/T14 lesson |
| **AC10** | Suite green against the recorded baseline (**479/481**, both failures the known environmental T07 pair); the total rises by exactly the cases T15 adds; no T01–T14 file modified. | L12 |

## Tests required

**Named test (`package.md:174`):** `authorizeRequestsExactlyTheDeclaredScopes` → R20. One file.

**Boundary cases the ACs imply:**

- scope string is exactly the three, in a stated order, and a fourth/missing scope fails the matcher (AC1, AC9);
- `code_challenge_method` is the imported constant, and the module source contains no `'S256'` literal (AC2);
- the challenge in the request derives from the stored verifier — recomputed from storage and compared (AC3);
- `state` and `nonce`: present, mutually distinct, distinct across calls over a sample, drawn from the CSPRNG (AC4);
- `redirect_uri` built from `APP_BASENAME` and `location.origin`, and changes when the origin does (AC5);
- the verifier appears in **no** parameter value — asserted over the whole URL and every parameter (AC6, AC9);
- after building, sessionStorage holds exactly the expected keys and `localStorage` is untouched (AC7);
- drift guards on `agents.md`'s scope sentence and on `routes.tsx`'s path (AC8);
- a source scan: no `'S256'` literal, no absolute URL, no `import.meta.env`, no `localStorage` (AC2, AC5, AC7).

**Not T15's:** `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField` (T16),
`callbackWithMismatchedStateIsDiscarded` (T17/T18), `callbackValidatesNonce` (T19),
`profileIsReadFromUserinfoAndAccountStatusFromMe` and `emailVerifiedClaimIsNotAccountState` (later R20 tasks),
`returnTargetOutsideAllowlistFallsBackToHome` (tasks 24/25), `sessionStorageHoldsOnlyTheL16Items` (task 26).

## Open Questions

**No blockers.** Three of phase 0's four questions are now settled from the spec; one remains for phase 2.

1. **Does T15 persist `state`, `nonce` and `code_verifier`? — YES, settled.** `tasks.md` task 18 reads "a callback
   whose `state` differs from **the stored value**", and task 20 "the token request carries **the stored**
   `code_verifier`". Those values must be written by whoever builds the authorize request, and **no task between 15
   and 17 could do it**. Task 26 (`sessionStorageHoldsOnlyTheL16Items` → R29, L16) is the *guard* on the carve-out,
   not its writer. So T15 writes the three L16 items, single-use; deletion at callback belongs to T17–T20.
2. **Is the post-login return target in scope? — NO, settled.** `tasks.md` tasks **24 and 25** own it
   (`returnTargetOutsideAllowlistFallsBackToHome`, citing R7 and L1). T15 neither reads nor writes it, even though
   it is L16's fourth item.
3. **Discovery or direct construction? — settled in favour of construction**, and recorded for phase 2 to confirm.
   `/.well-known/openid-configuration` is routed (`Caddyfile:12`), but: **`.oxlintrc.json` forbids naming `fetch`
   in `src/**` and T07's AC5 asserts the config has no `overrides` key and runs exactly 2 rules**, so no exemption
   exists; the only sanctioned transport is `@themistra/api-client`, generated from `auth.yaml`, and the SAS
   endpoints are **deliberately not in that contract** (`design.md:133-135`). Construction from `location.origin`
   needs no transport, no error path and no round trip before sign-in. **I am not treating this as closed** — it is
   a design decision phase 2 must state, with this as the evidence.
4. **Still genuinely open — a URL, a parameter record, or both?** "Carries" admits either. A record is far easier
   to assert over; the browser needs a URL. **Phase 2's decision**, and it shapes most of the test file.

**Carried, owner, not blocking:** **OQ-1** — T01 `:130` versus `design.md` §4c's four keys. Phase 0 established
that **T15 needs no key**, so the conflict stays latent. It is still unresolved and still worth the owner's
attention, but it no longer gates this task and probably not T16 either.
