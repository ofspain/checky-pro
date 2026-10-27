# frontend · T20 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T20 — Code exchange sends the verifier |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

The exchange is built. Nine files, in the plan's order, no file the brief did not authorize.

---

## What changed

### `contracts/api/oidc.yaml` — new (T20-D1, T20-D2, T20-D6)

`/oauth2/token` only, shaped exactly like `auth.yaml`: `openapi: 3.0.3`, `servers: [{ url: / }]`, an `operationId`,
`security: []`. The `info.description` says in the file why there are two contracts rather than one, so a reader who
finds both does not take it for a mistake, and states that `/oauth2/authorize` is a navigation rather than a request
and is deliberately absent.

Three shape decisions, each carrying its reason in the file:

- **Request** `application/x-www-form-urlencoded`, the five fields all required, `additionalProperties: false`. The
  contract is the first place AC4's "exactly five" is stated.
- **Response 200** `application/json`, five fields required, **`additionalProperties` left open**. The asymmetry is
  deliberate: the SPA controls the request completely, SAS controls the response, and a closed response schema would
  be this repository asserting something it does not own.
- **Response 400** has a `description` and **no `content`**. SAS puts `error` and `error_description` there, and
  T20-D9 forbids that detail reaching anything — so the contract gives the generated client no typed way to read it.

### `libs/ts/api-client/src/generated/oidc.ts` — new, generated

The fact the plan said to **read rather than assume** came back favourable. `openapi-typescript@7.13.0` surfaces the
form-encoded body exactly as it does a JSON one — `requestBody.content['application/x-www-form-urlencoded']` is the
typed `TokenRequest` (`:72-76`) — and the schemaless 400 generates as `content?: never` (`:88-93`). The ban on
reading the server's error detail is therefore structural: there is no typed path to it to forget about.

### `libs/ts/api-client/package.json` and `src/index.ts` (T20-D3, T20-D4)

`generate` runs both contracts, as two explicit invocations rather than a glob, because the drift test asserts the
documented command verbatim and a glob would hide which contract produced which file. `createOidcClient()` sits
beside `createAuthClient()` — same construction, same relative paths, same absence of a base URL or env key — and
the `oidc` types are re-exported under `oidc`-prefixed aliases so no existing import changes meaning.

### `frontend/src/auth/authorizeRequest.ts` (T20-D7)

One keyword: `redirectUri()` is exported. Body untouched. Both requests now derive the value from one function, so
SAS's exact-match comparison cannot be broken by two derivations drifting apart.

### `frontend/src/auth/codeExchange.ts` — the seam becomes the exchange

`AuthorizationCodeGrant` is unchanged, `state` included and documented as not reaching the wire (T20-D8).
`TokenResponse` gains `accessToken` and nothing else; `expires_in`, `scope` and `token_type` stay on the wire until
a task needs them, because task 21 owns how long a token is good for.

Three private functions, as planned. `storedCodeVerifier()` wraps the read — `sessionStorage` can **throw**, there
is no error boundary in this application, and an uncaught throw here would blank the page after the visitor has
already authenticated — and normalizes `''` to `null` the way `idTokenNonce.ts` does. `usableTokens()` takes
`unknown` and checks it: the contract types the response and the compiler believes it, but the compiler cannot see
the network, which was T19's first self-review finding. `fail()` is the single rejection, a fixed string naming no
status, no server text and no part of any token.

The call itself is wrapped in `try/catch` because a dropped connection and an unparseable body both throw out of
`openapi-fetch`, and either escaping would be a second kind of failure with a different message — exactly what L4
forbids. The client is built per call rather than held at module scope: this runs once per sign-in, and a module
variable would be state task 21 has to untangle when it decides where the access token lives.

**No custom `bodySerializer`** (T20-D5). The request sets `Content-Type` and the stock serializer does the rest. The
header is a named constant whose comment records why the exact string matters: `openapi-fetch` compares with `===`,
so a charset parameter or a capital letter falls through to `JSON.stringify` and the verifier never arrives.

### `frontend/src/auth/CallbackScreen.tsx` — comment only (OQ-4)

`:112-115` invited task 20 to "route it somewhere more specific". OQ-4 declined that, so the comment now says the
discard **is** the whole outcome and why. No behaviour change; the two-argument `.then` form T19 settled is intact.

## Deviations, flagged

**1 · A T18 case had to be replaced rather than split.** `callbackWithMismatchedStateIsDiscarded.test.tsx:1093-1096`
asserted the seam "names task 20 in its own source, so it is not mistaken for a finished exchange". Task 20 finished
it, so the claim is false by construction and there is no surviving version of it to split from. T19-D6 asks for a
split wherever one exists; here one does not. The replacement asserts the stronger opposite property, which T18
actually depends on: **every "no exchange happened" assertion in that suite is worth nothing while the exchange is a
stub**, so the case now pins that the exchange is a real request through the generated client. The sibling cases in
that block are untouched. This is the only production-adjacent test edit in this phase.

**2 · `generatedClientCompiles.test.ts:69` was updated in Phase 6, not Phase 10.** It asserts `pkg.scripts.generate`
verbatim, so it fails the moment T20-D4 lands. Updating one string to keep the suite honest is maintenance, not test
authoring; the new drift case for `oidc.ts` (AC23), the form-encoding case (AC22) and the `ALLOWED` entry are left to
Phase 10 as the brief says.

**3 · `codeExchange.ts` names no refresh token, deliberately.** AC13's third pin is a source scan, and a scan with no
carve-out for comments is harder to erode than one with. The prose says "no refresh token at all" and points at
`contracts/api/oidc.yaml`, which is where the absence is recorded and explained.

**4 · The plan's step order was not followed exactly.** Step 4 said to prove the client on the wire *before* any auth
code depended on it; `codeExchange.ts` was written first and the proof run after. The proof was then run, and it
passed on every assertion, so nothing is owed — but the order was meant to make a content-type mistake cheap to find,
and writing the consumer first gave that up for no reason.

## Verification

A throwaway probe drove the real `exchangeAuthorizationCode` against a stubbed `fetch` and asserted the wire exactly,
then was deleted — Phase 10 writes the suite that keeps these facts. Every assertion passed on the first run:

- `POST`, and the relative path resolved to the page's own origin: `<origin>/oauth2/token` (AC3, L9).
- `Content-Type: application/x-www-form-urlencoded` exactly (T20-D5).
- Serialized body, byte for byte:
  `grant_type=authorization_code&code=CODE-1&redirect_uri=<origin>%2Fapp%2Fcallback&client_id=checky-spa&code_verifier=VERIFIER-ONE`
  — the five fields, no sixth, no `state` (AC4), the stored verifier on the wire (AC1), and `redirect_uri` equal to
  `redirectUri()`'s output (AC5).
- The returned value is `{ idToken, accessToken }` and nothing else.

Suite: **818 passed, 1 failed of 819**, the failure being the one known environmental case
(`lintRejectsHandWrittenBackendFetch` AC4 at `:113`, this host's `oxlint@1.87.0` printing no summary line), **and no
other** — confirmed by reading the failure list, not the counts. `vitest` exit **1**, which is that one failure and
**no unhandled rejection**: the run prints no `Errors` line. `tsc --noEmit` clean for both `frontend` and
`libs/ts/api-client`. `oxlint` exit **0**, so the real exchange trips neither L6 rule — the generated client is the
whole point of T20-D1.

## Open questions

**None.** OQ-2 and OQ-3 remain deferred with the owners Phase 4 named.

---

## Next — Phase 7 — self-review.
