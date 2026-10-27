# frontend · T20 · Phase 8 — Independent Code Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T20 — Code exchange sends the verifier |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/07-self-review.md`, Phase 6 implementation |
| **Produces** | `artifacts/08-independent-review.md` |

Reviewed with fresh eyes against the frozen brief (`artifacts/04-frozen-task-brief.md`) and `spec/frontend/`. The implementation is **not complete**: the named test for task 20 was not created, and two AC22/AC23 cases the brief mandates are absent from the committed suite. No logic bug in the production code was found that would change behaviour today, but the lack of pinning tests leaves the wire contract unguarded.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings below are from static review of source.

---

## I1 · The named test file for task 20 does not exist

**Issue.** `package.md` §8 names `callbackValidatesNonceAndKeepsAccessTokenInMemory` as the test that proves R7's verifier half. The frozen brief §4 lists `frontend/src/auth/callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx` under **Create**. It is not present in the working tree, and no file matches the name.

**Evidence.**
- `glob` for `**/callbackValidatesNonceAndKeepsAccessTokenInMemory*` under `frontend/` returns no files.
- `grep` for the string across `frontend/` returns no matches.
- `callbackValidatesNonce.test.tsx` and `callbackWithMismatchedStateIsDiscarded.test.tsx` both replace `./codeExchange` with `vi.doMock`, so the real `exchangeAuthorizationCode` is never exercised by the existing suites.
- `codeExchange.ts` itself is therefore committed without any test coverage.

**Recommendation.** Create the named test file and do not treat Phase 6 as complete without it. It must cover at least: verifier read/absent/empty/throwing; wire shape (POST, relative `/oauth2/token`, exact `Content-Type`, serialized body with exactly five fields and no `state`); no `Authorization` header and no client credential; failure normalization (400, 5xx, network rejection, malformed body, missing/non-string tokens); and the non-leakage scans required by AC10–AC13 and AC20.

**Confidence.** High — the absence is objective and the brief explicitly requires the file.

---

## I2 · AC22 form-encoding case is missing from `generatedClientCompiles.test.ts`

**Issue.** The frozen brief §5 adds AC22: "a case in `generatedClientCompiles.test.ts` drives `createOidcClient()` with a form-encoded request and asserts method, relative path, the exact `Content-Type`, and the serialized body — independent of any auth logic." No such case exists.

**Evidence.**
- `frontend/src/api/generatedClientCompiles.test.ts` imports only `createAuthClient` and auth types; `createOidcClient` is never imported or called.
- The existing form/POST test (`:149-161`) exercises `createAuthClient().POST('/accounts', { body })` with JSON.
- There is no test that stubs `fetch`, calls `createOidcClient().POST('/oauth2/token', …)` with `Content-Type: application/x-www-form-urlencoded`, and asserts the serialized body.

**Recommendation.** Add an AC22 case using the existing `stubBrowserFetch()` helper. It should prove the body is form-encoded and the header value is byte-identical to the constant in `codeExchange.ts`, so a case change or charset regression is caught independently of auth logic.

**Confidence.** High — the file contents do not match the brief.

---

## I3 · AC23 `oidc.ts` drift case is missing from `generatedClientCompiles.test.ts`

**Issue.** AC23 requires "`oidc.ts` reproduces byte-for-byte from `oidc.yaml` under the documented `generate` command." The test file only reproduces `auth.ts` from `auth.yaml` (`:62-84`); the `oidc.ts` drift mirror is absent.

**Evidence.**
- The `generate` script in `libs/ts/api-client/package.json` was updated to run both contracts, and `:69-72` asserts the exact command string.
- The only drift reproduction in the test file uses `contractPath = join(repoRoot, 'contracts/api/auth.yaml')` and `generatedPath = join(clientDir, 'src/generated/auth.ts')` (`:17-18`).
- No equivalent paths are declared for `oidc.yaml` / `oidc.ts`, and no second drift case exists.

**Recommendation.** Add the `oidc.ts` byte-for-byte drift case mirroring the `auth.ts` one. Keep the `SAS_PREFIXES` guard scoped to `auth.ts` as the brief requires.

**Confidence.** High — the file contents do not match the brief.

---

## I4 · `createOidcClient` is not listed in the L6 lint test's `ALLOWED` table

**Issue.** The Phase 4 ledger (finding 2 / frozen brief §2) states that `lintRejectsHandWrittenBackendFetch.test.ts` should add `createOidcClient` to the `ALLOWED` table so the sanctioned client is named. The entry is missing.

**Evidence.**
- `frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts` `ALLOWED` array (`:49-59`) contains `createAuthClient` but not `createOidcClient`.
- `codeExchange.ts` imports and calls `createOidcClient()`, but because it never mentions `fetch` or `XMLHttpRequest`, the missing ALLOWED entry does not cause a lint failure today.

**Recommendation.** Add the ALLOWED entry for `createOidcClient` so the table documents every sanctioned generated client, consistent with the frozen brief.

**Confidence.** Medium — the omission does not weaken the rule's enforcement, but it is a documented brief item that was not applied.

---

## I5 · AC6 — the absence of any client credential — is still unproven

**Issue.** The self-review flagged this (F6) and deferred it to Phase 10. Because the named test file does not exist, there is still no committed assertion that the token request carries no `Authorization` header, no `client_secret` field, and no cookie-borne credential. For a public client registered with `ClientAuthenticationMethod.NONE`, this absence is the whole security story.

**Evidence.**
- `codeExchange.ts` builds the body from five literal fields; `client_secret` is not among them and no `Authorization` header is set.
- No test currently inspects the outgoing request headers or body for a credential.
- The only wire observation is the deleted throwaway probe mentioned in `06-implementation-notes.md`.

**Recommendation.** Include the no-credential assertion in the named test (I1). Assert the request headers do not contain `Authorization`, the serialized body does not contain `client_secret`, and the source scan finds no literal credential string in `codeExchange.ts`.

**Confidence.** Medium — the code is correct, but the property is not pinned by a committed test.

---

## I6 · `usableTokens` reaches its cast with an array payload

**Issue.** `typeof [] === 'object'`, so an array 200 response passes the first guard and is cast to `Record<string, unknown>`. It then returns `null` because `id_token` and `access_token` are undefined, which is the right outcome — but the cast is reached by a value it does not describe.

**Evidence.**
- `codeExchange.ts:74-80`: the first guard is `typeof payload !== 'object' || payload === null`; `Array.isArray(payload)` is not excluded.
- The self-review F8 noted the same shape and decided "no code change" because the outcome is unchanged and Phase 10 will add an array-body case.

**Recommendation.** No behaviour change is required. To make the type-level claim match the runtime claim, add `Array.isArray(payload)` to the first guard. This is a one-line tightening that removes the misleading cast and costs nothing in coverage.

**Confidence.** Low — the outcome is correct today; this is cleanliness, not a defect.

---

## I7 · `CallbackScreen.tsx` effect dependency list remains empty after the exchange became real

**Issue.** The effect has an empty dependency array and a `handled` ref latch. This was correct when the exchange was a stub, but now it performs network I/O. A second set of search params during the same mount is still ignored, which is the intended behaviour for a callback arrival, but the rationale in the comment (`:42-44`) no longer mentions that the latch also prevents a duplicate token exchange if React or a parent re-renders before the async call settles.

**Evidence.**
- `CallbackScreen.tsx:40-47`: `handled.current` is set to `true` before `exchangeAuthorizationCode` is awaited.
- If the component unmounts while the exchange is in flight, the promise is not cancelled; on fulfillment the `navigate` call may run against an unmounted component. React Router's `navigate` from a disposed router is generally a no-op or warning, not a security issue, but it is untested.
- The existing StrictMode test (`callbackValidatesNonce.test.tsx:727-737`) covers double effect invocation but not an unmount during the network call.

**Recommendation.** No immediate code change is required because the latch prevents duplicate calls. In the named test, add a case that unmounts the screen while the exchange is pending and asserts no crash, no navigation, and no token leakage. Update the comment to record that the latch also guards against a second exchange during the async window.

**Confidence.** Low — defensive, not a confirmed bug.

---

## Summary

The production code in `codeExchange.ts`, `CallbackScreen.tsx`, `authorizeRequest.ts`, and `libs/ts/api-client/src/index.ts` is consistent with the frozen brief and the standing rules. The defect is **completeness of verification**: the task's named test and two mandated generated-client cases were not committed, leaving the wire behaviour, form encoding, drift guard, and no-credential property unproven. Fix I1–I5 before treating Phase 6 as complete.
