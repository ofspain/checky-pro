# frontend · T20 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T20 — Code exchange sends the verifier |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

This review challenges the Phase 2 TIB before it is frozen. Findings are listed in the form **Issue · Severity · Evidence · Recommended brief amendment**. No redesign or implementation is proposed.

---

## 1. The new contract's scope and name are undefined

**Issue ·** The addendum states `/oauth2/token` will be described in "a **new contract file** under `contracts/api/`", but it does not name the file, state what else it contains, or say who owns it. This leaves the implementer guessing and creates a conflict risk with `auth.yaml:4-7` (SAS endpoints intentionally absent) and `contracts/README.md` (one OpenAPI spec per service).

**Severity ·** Medium — blocks a clean Phase 4 freeze and may force a contract refactor after implementation starts.

**Evidence ·**
- TIB addendum, lines 221-240: "`/oauth2/token` is described in a **new contract file** under `contracts/api/`" but no filename is given; "Still for Phase 3 to settle … whether it carries only the token endpoint."
- `contracts/README.md`: "`api/` — OpenAPI specs per service." A frontend-authored SAS protocol contract is unprecedented in this directory.
- `auth.yaml:4-7`: the SAS protocol surface is intentionally excluded; the new file must not accidentally re-introduce it into `auth.yaml`.

**Recommended brief amendment ·** Freeze the filename (e.g. `contracts/api/oidc.yaml`) and explicitly state:
- the file contains **only** `/oauth2/token` (or list any additional SAS paths);
- the file is owned/co-owned by the auth-service team even though the frontend is the first consumer;
- `auth.yaml:4-7` is left untouched.

---

## 2. Generated client publication shape is unresolved

**Issue ·** The addendum leaves "whether the generated module is published from `libs/ts/api-client` as a second export or a second entry point" for Phase 3. This is not a cosmetic choice: it determines whether `codeExchange.ts` imports `@themistra/api-client` once or twice, whether the two generated clients share a `fetch` instance/middleware, and whether `lintRejectsHandWrittenBackendFetch.test.ts`'s `ALLOWED` table stays accurate.

**Severity ·** Medium — affects module boundaries and the existing L6 lint guard.

**Evidence ·**
- TIB addendum, lines 238-240: "whether the generated module is published from `libs/ts/api-client` as a second export or a second entry point" is listed as still open.
- `libs/ts/api-client/src/index.ts` currently exports `createAuthClient()` and types from `./generated/auth`.
- `frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts:54-55` explicitly allows `createAuthClient`. A new client name or entry point will not be covered by that example.

**Recommended brief amendment ·** Decide and freeze the publication shape, e.g.:
- export `createOidcClient()` from the same entry point (`libs/ts/api-client/src/index.ts`); or
- add a second subpath export.
Then list the import path and update the L6 lint test's `ALLOWED` examples so the sanctioned client is explicitly allowed.

---

## 3. `libs/ts/api-client/package.json` `generate` script must change, but the brief treats it as optional

**Issue ·** The `generate` script currently regenerates only `auth.yaml` (`openapi-typescript ../../../contracts/api/auth.yaml -o src/generated/auth.ts`). Under Exit 1 a second contract exists, so a second generated file must be produced. The brief lists `libs/ts/api-client/package.json` as modified "only under option 1 of the blocker," but now that option 1 is chosen, the change is mandatory, not optional.

**Severity ·** High — without this, CI drift detection will fail or the new client will be hand-maintained.

**Evidence ·**
- `libs/ts/api-client/package.json:9`: `"generate": "openapi-typescript ../../../contracts/api/auth.yaml -o src/generated/auth.ts"`.
- `frontend/src/api/generatedClientCompiles.test.ts:69`: the test verifies the `generate` script reproduces the committed client byte-for-byte. It will not cover the new contract unless updated.

**Recommended brief amendment ·** Move `libs/ts/api-client/package.json` from "possibly modify" to "Files to Modify" and require the `generate` script to generate both contracts (e.g. two `openapi-typescript` invocations or a glob).

---

## 4. `generatedClientCompiles.test.ts` must be updated but is not listed

**Issue ·** Adding a second contract and generated module breaks the existing client compile test's assumptions: (a) the test asserts `auth.ts` reproduces `auth.yaml` byte-for-byte, and (b) the test asserts no SAS prefix appears in `auth.ts`. A new `oidc.ts` is expected to contain SAS paths, so the test's `SAS_PREFIXES` assertion must be scoped to `auth.ts` only, and a new drift check must cover `oidc.ts`.

**Severity ·** High — the test will fail as soon as the new contract lands.

**Evidence ·**
- `frontend/src/api/generatedClientCompiles.test.ts:46-47`: `SAS_PREFIXES` lists `/oauth2` and the test asserts no generated path starts with it.
- `frontend/src/api/generatedClientCompiles.test.ts:62-81`: regenerates `auth.ts` from `auth.yaml` and compares byte-for-byte.

**Recommended brief amendment ·** Add `frontend/src/api/generatedClientCompiles.test.ts` to "Files to Modify" and require it to:
- keep the `auth.ts` byte-for-byte check scoped to `auth.yaml`;
- add an equivalent byte-for-byte check for the new generated file;
- restrict the `SAS_PREFIXES` assertion to `auth.ts` only.

---

## 5. `redirect_uri` "derived once rather than spelled twice" is under-specified

**Issue ·** AC5 requires the token request's `redirect_uri` to be byte-identical to the authorize request's and "derived once rather than spelled twice." The brief says modify `authorizeRequest.ts` "only if the redirect URI must be published rather than derived twice." This is a conditional that Phase 4 should close. If both modules derive independently from `APP_BASENAME` + `globalThis.location.origin`, they will match today, but any future change to derivation logic risks divergence and a broken SAS exact-match check.

**Severity ·** Medium — a divergence would fail at the token endpoint with no useful visitor-facing error.

**Evidence ·**
- `frontend/src/auth/authorizeRequest.ts:59-62`: `redirectUri()` is private.
- TIB AC5: "`redirect_uri` is byte-identical to the authorize request's, derived once rather than spelled twice."
- `RegisteredClientSeeder` registers `checky-spa` with exact-match redirect URIs (L1).

**Recommended brief amendment ·** Remove the conditional and require `authorizeRequest.ts` to export a shared `redirectUri()` function (or equivalent) used by both `buildAuthorizeRequest()` and `exchangeAuthorizationCode()`. State that deriving it twice is not acceptable.

---

## 6. Token endpoint error format is not RFC 9457

**Issue ·** `agents.md` and `sessionBoundary.ts` assume backend errors are RFC 9457 `application/problem+json`. The SAS `/oauth2/token` endpoint returns OAuth2 error responses (`400 invalid_grant` with `error`/`error_description` in the body, not a `problem+json` document). The brief does not say how the generated client or `codeExchange.ts` should map these errors, yet AC11 requires every failure to produce the same visitor outcome. A naive mapping could leak `error_description` or treat the OAuth2 shape as a network exception.

**Severity ·** Medium — affects L4 (enumeration safety) and AC11.

**Evidence ·**
- `agents.md:66-67`: "Errors are RFC 9457 `application/problem+json`. The UI maps problem types to copy and never shows stack traces or internal detail."
- TIB measured response: "`400 invalid_grant`" and no `Authorization` header.
- TIB AC11: every failure produces the same visitor outcome as R8's discard.

**Recommended brief amendment ·** Add a requirement that the token exchange normalizes OAuth2 errors (4xx/5xx, network rejection, malformed body) into a single opaque rejection with no server detail, and explicitly forbid using `error_description` in copy, logs, or the console.

---

## 7. AC12 only mentions `id_token`; `access_token` validation is missing

**Issue ·** The named test is shared with T21 and the brief says T20 hands back the `access_token`. AC12 requires rejecting a missing/non-string `id_token`, but it does not require the same for `access_token`. A response with a missing or non-string `access_token` would currently be unhandled by the brief's acceptance criteria, yet T21 will rely on it being a string.

**Severity ·** Low — T21 could catch this, but T20 is the task that consumes the wire response.

**Evidence ·**
- TIB outputs, lines 98-99: "On success: the token response's usable fields, including the `id_token` … and the `access_token`."
- TIB AC12: only "a response missing `id_token`, or carrying a non-string one, is a rejection."

**Recommended brief amendment ·** Extend AC12 to require that a missing or non-string `access_token` also rejects (while still not throwing), or add a new AC covering `access_token` validation.

---

## 8. Strategy for pinning the absence of `refresh_token` is not specified

**Issue ·** AC13 says "No `refresh_token` is read, stored or expected, and its absence is pinned by a test rather than a comment." The brief lists "Response — the measured success shape; `400 invalid_grant`" as test cases but does not say how the absence of `refresh_token` will be pinned. Without a concrete strategy, an implementer may rely on the type system (which does not prove absence) or on a comment.

**Severity ·** Low — important for L3 but unlikely to ship a bug immediately.

**Evidence ·**
- TIB AC13.
- `frontend/src/auth/codeExchange.ts:24-25`: the current comment documents the absence, which AC13 explicitly rejects as sufficient.

**Recommended brief amendment ·** Specify the pinning mechanism, e.g.:
- a unit test that stubs a 200 response containing `refresh_token` and asserts it is not returned/exposed;
- a source scan for the literal `refresh_token` in `codeExchange.ts` and the new test file; or
- a contract-level test asserting the generated schema does not define `refresh_token`.

---

## 9. Per-request `bodySerializer` assumption is untested

**Issue ·** The addendum relies on `openapi-fetch@0.17.0`'s per-request `bodySerializer` to send form-encoded data. The brief does not require a test proving that a generated client call with the new contract actually produces `Content-Type: application/x-www-form-urlencoded` and a form-encoded body. The existing `generatedClientCompiles.test.ts` only tests JSON bodies.

**Severity ·** Medium — if the types or serializer do not compose as expected, the implementation will not satisfy AC4.

**Evidence ·**
- TIB addendum, lines 234-236: "The form-encoded body is expressible through the generated client without hand-written transport, via `openapi-fetch`'s `bodySerializer`."
- `frontend/src/api/generatedClientCompiles.test.ts:146-158`: existing POST test sends JSON and asserts `Content-Type: application/json`.
- `openapi-fetch@0.17.0` default serialization is JSON; per-request override is required.

**Recommended brief amendment ·** Add a test requirement (in the brief and in `generatedClientCompiles.test.ts`) that exercises the new OIDC client with a form-encoded request and asserts `Content-Type`, method, path, and body fields. This also serves as the wire test for the verifier without hand-writing `fetch`.

---

## 10. Storage read of the verifier must be wrapped, but the brief implies it rather than requiring it

**Issue ·** The brief lists "a storage read that throws" as a verifier test case and says AC9 requires the three L16 items to survive failure. However, it does not explicitly require `codeExchange.ts` to wrap `sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier)` in `try/catch`. The call site (`CallbackScreen.tsx`) catches a rejection, but a synchronous throw before the promise starts would escape unless wrapped.

**Severity ·** Low — the call site uses `.then(..., discard)` on a promise, so a synchronous throw in `exchangeAuthorizationCode` would still be caught if the function is `async`. But an explicit requirement removes ambiguity.

**Evidence ·**
- TIB Required Tests, verifier row: "a storage read that throws."
- `frontend/src/auth/idTokenNonce.ts:94-102` and `CallbackScreen.tsx:92-97`: existing guarded storage reads.
- TIB AC9: "Nothing written, deleted or cleared; all three L16 items survive success **and** failure."

**Recommended brief amendment ·** Explicitly require `codeExchange.ts` to wrap the verifier read (and any other storage read it performs) in `try/catch` and treat a thrown read as a rejection, with no storage mutation.

---

## 11. Silent renewal reuse of the exchange is hand-waved

**Issue ·** AC18 says the exchange must be "callable by something other than the callback screen, so silent renewal (L18) can reuse it." The current `AuthorizationCodeGrant` type is `{ code; state }`, and the brief says the call site passes `state` after R8 has already validated it. Silent renewal will obtain a new `code` (via hidden iframe/navigation) and will also have a stored `state`, but it is unclear whether renewal should re-validate `state` itself or delegate that to the callback screen. If `exchangeAuthorizationCode` requires `state`, renewal must either pass a state it has already validated or the function signature is callback-specific.

**Severity ·** Low — not blocking for T20, but AC18 is cited as a T20 acceptance criterion.

**Evidence ·**
- TIB AC18.
- `frontend/src/auth/codeExchange.ts:14-17`: `AuthorizationCodeGrant` currently requires `state`.
- `agents.md:25-34`: renewal is silent re-authentication through `/oauth2/authorize` and exchanges the returned code.

**Recommended brief amendment ·** Clarify whether `exchangeAuthorizationCode` accepts `{ code }` only (state is irrelevant to the wire) or `{ code, state }`, and how renewal is expected to call it without duplicating R8 logic. If the signature keeps `state`, state that renewal must supply the stored value and that the exchange does not re-validate it.

---

## 12. The token contract's response content type is not stated

**Issue ·** The measured response is JSON (`access_token`, `expires_in`, `id_token`, etc.), while the request is form-encoded. The brief does not state that the new contract should declare the **response** as `application/json`. An implementer might model the response as form-encoded to match the request, producing wrong generated types.

**Severity ·** Low — easy to get wrong in the contract.

**Evidence ·**
- TIB Dependencies, measured response: `access_token`, `expires_in`, `id_token`, `scope`, `token_type`.
- TIB AC4: request body is form-encoded.

**Recommended brief amendment ·** Add to the contract requirements: request body content type is `application/x-www-form-urlencoded`; successful response content type is `application/json`; no `refresh_token` field is defined in the response schema.

---

## 13. No ADR is required, but the design record is thin

**Issue ·** The addendum says "no ADR" because Exit 1 satisfies L6's glob wording. That is correct, but the repository uses `docs/adr/` for material design changes. Adding the first frontend-owned contract and a second generated client module is a material change to the client boundary. The brief should at least require a design decision record inside the artifact (Phase 4's `T20-D` decisions) so future maintainers understand why `/oauth2/token` is not in `auth.yaml`.

**Severity ·** Low — process/documentation risk.

**Evidence ·**
- `agents.md:104`: "Material design changes require an ADR in `docs/adr/`."
- TIB addendum, lines 226-236: records what Phase 4 should freeze, but does not explicitly require it to be written down as numbered decisions.

**Recommended brief amendment ·** Require Phase 4 to freeze `T20-D1` (Exit 1 chosen), `T20-D2` (contract filename and scope), and `T20-D3` (client publication shape), and to record them in the Phase 4 artifact or in `docs/adr/` if the project conventions treat this as material.

---

**End of findings.** The brief is otherwise consistent with the locked decisions and `agents.md`.
