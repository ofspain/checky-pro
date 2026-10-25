# frontend · T15 · Phase 2 — Task Implementation Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T15 — Authorize request shape |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-task-implementation-brief.md` |

## Task

Assemble the OIDC authorization request: exactly the three declared scopes, `state`, `nonce`,
`code_challenge_method=S256` and the PKCE challenge from T14 — and persist the three L16 items the callback tasks
will read back.

## Purpose

This is the task that actually starts the flow. T14 built the PKCE primitive and published `S256` as a literal
type; **T15 is what sends it, and nothing else enforces it end to end** — the backend requires a challenge
(`requireProofKey(true)`) but accepts `plain` and has no switch to forbid it. The scope rule is the opposite case:
the server registers exactly `openid`, `profile`, `email`, so a fourth scope is refused on both sides.

T15 is also the **writer** of the L16 carve-out. Tasks 17–20 compare the callback's `state` against *the stored
value* and exchange the code with *the stored verifier*; no task between 15 and 17 could have written them. Every
silent renewal runs through here too (`sessionBoundary.ts:7-14`), so this is not a sign-in-only path.

## Scope

**In**
- Generating `state` and `nonce` from the platform CSPRNG.
- Obtaining a PKCE pair from T14 and sending **its challenge**, with the method constant imported, not spelled.
- Requesting exactly `openid profile email`.
- Building the authorize URL on the SPA's own origin, with `redirect_uri` derived from `APP_BASENAME`.
- Writing `state`, `nonce` and `code_verifier` to sessionStorage under published keys, so task 26's carve-out guard
  and tasks 17–20's readers have one source for the names.

**Out**
- Navigating the browser — T16's sign-in screen does that; this returns a URL.
- Rendering anything.
- Reading the callback, validating `state` or `nonce`, deleting the stored items — tasks 17–20.
- The post-login return target and its allowlist — tasks 24 and 25, even though it is L16's fourth item.
- Asserting the carve-out holds nothing else — task 26.
- The token exchange — task 20.
- Discovery via `/.well-known/openid-configuration` — see Constraints.
- Any npm dependency, any configuration key.

## Business Rules

- **R20** (`requirements.md:66-67`) — the requested scopes are exactly `openid profile email`.
- **R6** (`requirements.md:21-22`) — the flow started is authorization code **with PKCE**.
- **R7 / R8** — not implemented here, but the reason T15 must persist: those tasks read *stored* values.

## Locked Decisions

- **L1** (`design.md:8-10`) — public client, PKCE, `state` and `nonce`, exact-match redirect URI.
- **L16** (`design.md:58-60`) — sessionStorage may hold only `state`, `nonce`, `code_verifier`, return target.
  T15 writes the first three and nothing else.
- **L9** (`design.md:44`) — one origin, the edge owns the backend routes. This is why the URL is derivable.
- **L14** (`design.md:52-55`) — `/app/callback` is already a declared route (`routes.tsx:9`).
- **L3**, **L2**, **L13**, **L12** — tokens in memory only; login is SAS-hosted; no OIDC library; additive.
- `agents.md:37-38` — the scope sentence, and the drift guard's anchor.
- `agents.md:79-81` — the challenge may enter a URL; **the verifier may not**.

## Dependencies

| Kind | Item |
|---|---|
| Internal | `src/auth/pkce.ts` — `generatePkcePair`, `PKCE_CODE_CHALLENGE_METHOD`, and a new shared token generator (below) |
| Internal | `src/app/routes.tsx` — `APP_BASENAME` |
| Platform | `crypto.getRandomValues`, `sessionStorage`, `location.origin` — all measured present under this jsdom (T14) |
| npm / config keys / contracts | **none** |

## Inputs

None from the caller. Nothing is read from configuration, the network or the URL. The only ambient input is
`location.origin`.

## Outputs

```ts
export type AuthorizeRequest = { url: string; state: string; nonce: string };
```

The URL to navigate to, plus the two public request parameters. **The verifier is deliberately absent from the
return type** — it is written to sessionStorage and read back by task 20, and a caller has no business holding it.
That makes AC6 structural rather than only asserted.

Also published, because other tasks need the names rather than copies of them:

```ts
export const REQUESTED_SCOPES = ['openid', 'profile', 'email'] as const;
export const AUTHORIZE_STORAGE_KEYS = {
  state: 'themistra.auth.state',
  nonce: 'themistra.auth.nonce',
  codeVerifier: 'themistra.auth.code_verifier',
} as const;
```

## State Changes

**Three sessionStorage writes** — `state`, `nonce`, `code_verifier`, under `AUTHORIZE_STORAGE_KEYS`. Nothing else:
no `localStorage`, no cookie, no module-level mutable state, no log. Each call overwrites the previous attempt,
which is correct — only the most recent authorize request may be completed.

## Files to Create

- `frontend/src/auth/authorizeRequest.ts`
- `frontend/src/auth/authorizeRequestsExactlyTheDeclaredScopes.test.ts`

## Files to Modify

**One, and it needs stating plainly:** `frontend/src/auth/pkce.ts` gains one export —
`randomUrlSafeToken(byteLength = 32): string` — and `generateCodeVerifier()` becomes a caller of it. `state` and
`nonce` need the same mechanism (CSPRNG bytes → base64url) under a different contract, and the alternatives are
worse: duplicating the encoder repeats the mistake the project is still carrying with `segmentPrefixPattern`
(T09/T11), and calling `generateCodeVerifier()` for a nonce would be actively misleading. **T11's phase 13 set the
precedent**: the task that legitimately opens a file is the moment to share one implementation.

The helper stays **inside `pkce.ts`** rather than moving to a new file, because T14's AC10 asserts
`btoa(` appears in that source — relocating the encoder would break a passing test of a completed task. Adding an
export breaks nothing: T14 pins no exact export set, and its CSPRNG spy ("called once, with 32 bytes") still holds
when `generateCodeVerifier` delegates. **Phase 6 must re-run T14's suite and show it green.**

## Files NOT to Modify

`frontend/src/api/**`, `frontend/src/app/**`, `frontend/src/pwa/**`, `frontend/src/i18n/**`,
`frontend/src/main.tsx`, `frontend/vite.config.ts`, `frontend/.oxlintrc.json`, `frontend/package.json`,
`frontend/src/auth/pkceVerifierMeetsRfc7636.test.ts`, `spec/**`, `contracts/**`, `services/**`, `infra/**`, and
every T01–T13 test.

## Acceptance Criteria

AC1–AC10 as phase 1 states them. In summary, with the two that are easiest to get wrong called out:

- **AC1** exactly `openid profile email`, by equality, not `contains`.
- **AC2** `code_challenge_method` is T14's imported constant; the module holds no `'S256'` literal.
- **AC3** — *the sharp one* — the `code_challenge` sent derives from **the verifier that was stored**, recomputed
  from sessionStorage and compared. A module that stored one pair and sent another's challenge would pass every
  other criterion and fail every real login.
- **AC4** `state` and `nonce` present, mutually distinct, CSPRNG, fresh per call.
- **AC5** `response_type=code`, `client_id=checky-spa`, `redirect_uri` derived; no absolute URL literal, no
  environment key (T01 `:130-131`).
- **AC6** the verifier appears in no parameter and nowhere in the URL.
- **AC7** the three items in sessionStorage and nowhere else.
- **AC8** drift guards on `agents.md:37-38` and on `routes.tsx`'s `APP_BASENAME`/`callback`.
- **AC9** non-vacuity: the scope equality and the no-verifier rule are fed deliberately wrong inputs through the
  same matcher.
- **AC10** suite green against **479/481**, delta exactly the cases added, no T01–T14 file modified **except the
  one declared above**.

## Required Tests

One file, `authorizeRequestsExactlyTheDeclaredScopes.test.ts`, covering: the scope equality and its negative
control; the method constant and the absence of a local `'S256'`; the stored-verifier/sent-challenge
correspondence; `state`/`nonce` presence, distinctness and freshness over a sample; the derived `redirect_uri`,
including that it follows a changed origin; the verifier's absence from every parameter and from the whole URL
string, with a negative control; sessionStorage holding exactly the three keys with `localStorage` untouched, by
both a source scan and runtime spies (T14's pattern); both drift guards; and a source scan for `'S256'`, absolute
URLs, `import.meta.env` and `localStorage`.

**Plus one regression obligation:** `vitest run src/auth` must show T14's 67 cases still passing after the
`pkce.ts` change.

## Constraints

**Construct the URL; do not discover it.** `/.well-known/openid-configuration` is routed (`Caddyfile:12`) and
would be more correct in principle, but **`.oxlintrc.json` forbids naming `fetch` in `src/**` and T07's AC5
asserts the config has no `overrides` key and runs exactly 2 rules**, so no exemption is available; the only
sanctioned transport is `@themistra/api-client`, generated from `auth.yaml`, and the SAS endpoints are
deliberately outside that contract (`design.md:133-135`). Construction from `location.origin` needs no transport,
no error path and no round trip before sign-in. **If a reviewer wants discovery, it needs a new contract entry and
an owner decision — that is the one thing in this brief most worth attacking.**

**Security.** `state` and `nonce` from `crypto.getRandomValues` only, 32 bytes each, never `Math.random`. The
verifier is written to sessionStorage and never returned, logged, or placed in the URL. The challenge is the only
PKCE half that may appear in a URL.

**Module boundaries.** `src/auth/` imports from `src/auth/` and `src/app/routes.tsx` (for `APP_BASENAME`) only —
nothing from `src/pwa/`, `src/api/` or `src/features/`.

**Null handling.** `location.origin` is assumed present; under jsdom it is. The function is async (the challenge
derivation is) and rejects rather than returning a half-built request if the derivation rejects.

**Lint.** The module names neither `fetch` nor `XMLHttpRequest`.

**No `Buffer`** — Vite does not polyfill it, so it would pass under Vitest and fail in a browser (T14's lesson).

## Open Questions

**No blockers.**

- **For phase 3 to attack:** construction versus discovery, above — stated with its evidence so the review has
  something specific to argue with.
- **For phase 3 to attack:** the one declared modification to `pkce.ts`. The alternative readings are duplicate the
  encoder, or reuse `generateCodeVerifier()` under a misleading name; both are worse, but the choice reopens a
  file a completed task froze and the review should test that reasoning.
- **Settled in phase 1, recorded here:** T15 persists the three L16 items (tasks 18 and 20 read *stored* values);
  the return target is tasks 24/25'; the carve-out guard is task 26's.
- **Carried, owner, not blocking:** **OQ-1** — T01 `:130` versus §4c's four keys. **T15 needs no key**, so the
  conflict stays latent. Phase 0 corrected my earlier claim that this blocked T15.
