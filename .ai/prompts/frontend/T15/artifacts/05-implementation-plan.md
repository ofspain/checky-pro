# frontend · T15 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T15 — Authorize request shape |
| **Model** | Claude Sonnet |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

No code written in this phase. Every file traces to the frozen brief's Files sections.

## Files to create

| File | Content |
|---|---|
| `frontend/src/auth/authorizeRequest.ts` | 5 constants, 1 type, 1 public function, 2 private helpers |
| `frontend/src/auth/authorizeRequestsExactlyTheDeclaredScopes.test.ts` | the named test (`package.md:174`) |

## Files to modify

**One, per D-2:** `frontend/src/auth/pkce.ts` — adds `randomUrlSafeToken(byteLength = 32): string`;
`generateCodeVerifier()` becomes `return randomUrlSafeToken(VERIFIER_BYTE_LENGTH);`. Nothing else changes, and
`btoa(` stays in that file so T14's AC10 keeps passing.

## Public API

```ts
// authorizeRequest.ts
export const AUTHORIZE_ENDPOINT_PATH = '/oauth2/authorize';   // D-1
export const CLIENT_ID = 'checky-spa';                         // D-4
export const REQUESTED_SCOPES = ['openid', 'profile', 'email'] as const;
export const AUTHORIZE_STORAGE_KEYS = {
  state: 'themistra.auth.state',
  nonce: 'themistra.auth.nonce',
  codeVerifier: 'themistra.auth.code_verifier',
} as const;

export type AuthorizeRequest = { url: string; state: string; nonce: string };

export function buildAuthorizeRequest(): Promise<AuthorizeRequest>;
```

```ts
// pkce.ts, added
export function randomUrlSafeToken(byteLength?: number): string;
```

**The verifier is absent from `AuthorizeRequest` by design** (frozen brief, Outputs): it goes to sessionStorage and
is read back by task 20. AC6 is therefore structural before it is asserted.

## Private helpers

| Helper | Why private |
|---|---|
| `redirectUri(): string` | `location.origin` + `APP_BASENAME` with any trailing `/` stripped + `/callback` (D-3). Private because the published surface is the request, not its parts. |
| `authorizeUrl(params: URLSearchParams): string` | `${location.origin}${AUTHORIZE_ENDPOINT_PATH}?${params}` — one place the origin and path meet. |

## Entities / repositories / services used

None. **State changes: three sessionStorage writes** under `AUTHORIZE_STORAGE_KEYS` — the only side effect in the
module.

## Tests required

One file. Cases grouped by criterion:

| # | Case | AC |
|---|---|---|
| 1 | `scope` is exactly `'openid profile email'`, and split equals `REQUESTED_SCOPES` | AC1 |
| 2 | **negative control:** a scope matcher fed a four-scope and a two-scope value reports both wrong | AC1, AC9 |
| 3 | `code_challenge_method` is `PKCE_CODE_CHALLENGE_METHOD`, imported; module source holds no `'S256'` literal | AC2 |
| 4 | **the challenge sent derives from the verifier that was stored** — read sessionStorage, recompute with `deriveCodeChallenge`, compare | **AC3** |
| 5 | `state` and `nonce` present, mutually distinct, 43 chars; distinct across a 200-request sample | AC4 |
| 6 | `crypto.getRandomValues` is called, and `Math.random` is absent from both module sources | AC4 |
| 7 | `response_type=code`; `client_id` is `CLIENT_ID`; `checky-spa` occurs exactly once in the module | AC5, D-4 |
| 8 | `redirect_uri` equals `` `${location.origin}/app/callback` ``; pathname is `/app/callback`; `APP_BASENAME` prefixes it | AC5, D-5 |
| 9 | with `vi.stubGlobal('location', …)`, the redirect URI and the authorize URL both follow the new origin | AC5, D-5 |
| 10 | trailing-slash safety: the derivation yields no `//` in the path | AC5, D-3 |
| 11 | **the no-verifier matcher, both directions** — synthetic URLs with the verifier in a query parameter, in the fragment, and inside a nested `redirect_uri` all report a leak; the real URL reports none | **AC6, AC9, D-9** |
| 12 | sessionStorage holds exactly the three keys, with the stored verifier valid per `isValidCodeVerifier` | AC7 |
| 13 | runtime spies: no `localStorage`/other `Storage.prototype` method, no `console` function called during the build | AC7, D-7 |
| 14 | source scan: no absolute URL, no `import.meta.env`, no `localStorage`, no `Buffer` in either module | AC5, AC7 |
| 15 | drift guard: `agents.md:37-38`'s scope sentence still says `openid profile email` | AC8 |
| 16 | drift guard: the `/oauth2/*` matcher is still in the edge `Caddyfile`, and `AUTHORIZE_ENDPOINT_PATH` sits under it | **AC12**, D-1 |
| 17 | rejection: `generatePkcePair` mocked to an invalid verifier → rejects, **and sessionStorage is untouched** | **AC11**, D-6 |
| 18 | `randomUrlSafeToken` returns 43 base64url characters by default and honours a byte length | D-2 |

Cases 5 and 11 share one sample/one request built in a `beforeEach`, so the file does not rebuild needlessly.

**Regression obligation:** `vitest run src/auth` must show **T14's 67 cases still passing** alongside T15's.

## Execution order

1. **`pkce.ts`: add `randomUrlSafeToken`, delegate `generateCodeVerifier`.** First, because T15 depends on it and
   because **T14's suite must be green before anything else is built on top** — a regression here would otherwise
   be discovered late and blamed on T15's own code.
2. **Run `vitest run src/auth`** — expect 67/67 before writing a line of T15.
3. **Constants** in `authorizeRequest.ts`: endpoint path, client id, scopes, storage keys.
4. **`redirectUri()`** — the trailing-slash rule, derived from `APP_BASENAME`.
5. **`buildAuthorizeRequest()`** — generate `state`/`nonce`, get the PKCE pair, build params with
   `URLSearchParams`, **write the three storage items, then** return the URL. Order matters: nothing is stored
   until the challenge has been derived successfully, so a rejection leaves storage untouched (AC11).
6. **The test file**, case 4 first (the stored-verifier correspondence is the criterion most easily got wrong),
   then case 11's matcher, then the rest.
7. **Gates:** `vitest run src/auth`, `tsc --noEmit`, `oxlint`, whole suite for AC10's delta.

## Risks carried into phase 6

1. **The storage-write ordering in step 5 is load-bearing for AC11.** If `state` is written before the challenge is
   derived, a derivation failure strands a `state` with no verifier and the next callback fails in a way nothing
   here would catch. Phase 6 must write all three only after the pair is in hand.
2. **`vi.stubGlobal('location')` must be unstubbed.** Measured to restore cleanly, but a leaked stub would corrupt
   later cases in the same file — and worse, the shared `beforeEach` request. Case 9 must restore in a `finally`.
3. **The `Caddyfile` drift guard reads outside `frontend/`.** T11's precedent covers this, and the path is resolved
   from `expect.getState().testPath` rather than assumed, so it survives a different working directory.
4. **`checky-spa` occurs exactly once** (case 7) will also match the constant's own declaration — the assertion
   must count occurrences in the module source, not merely assert presence, or it proves nothing.

## Open Questions

**No blockers.** OQ-1 is carried for the owner and gates nothing here (D-11).
