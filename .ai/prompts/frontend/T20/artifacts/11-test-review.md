# frontend · T20 · Phase 11 — Test Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T20 — Code exchange sends the verifier |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Reviewed the Phase 10 test files against the frozen brief and the spec. The suite is broad and the critical paths are covered. The gaps below are about edge cases, structural pins, and behaviours that the existing cases imply but do not explicitly exercise.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of the committed test sources.

---

## Gap 1 · StrictMode is not exercised with the real exchange

**Why it matters.** `CallbackScreen.tsx` relies on a `handled` ref to fire the exchange only once, but the existing StrictMode cases in `callbackWithMismatchedStateIsDiscarded.test.tsx` and `callbackValidatesNonce.test.tsx` mock `./codeExchange`. The named test is the first place the latch can be observed against the real network stub. A regression that made the promise handler non-idempotent — for example, calling `setSignInNotice` or `navigate` twice before the latch resets — could pass the single-invocation mocks while firing two real requests.

**Suggested test.** Add a case under `through the screen` that mounts with `<StrictMode>` and a matching callback, then asserts `fetchSpy` is called exactly once, the screen holds on `/app/callback`, and `takeSignInNotice()` remains null.

---

## Gap 2 · The serializer's `===` content-type trap is not demonstrated at runtime

**Why it matters.** T20-D5 says the header must be exactly `application/x-www-form-urlencoded`; any charset parameter or case change silently falls through to `JSON.stringify`. The named test asserts the production code sends the exact header, and the source scan locks the constant, but no case proves the *trap* itself — i.e., that a slightly different header would produce JSON. A future developer could conclude the constant is merely stylistic.

**Suggested test.** In `generatedClientCompiles.test.ts` AC22, add a second case that calls `createOidcClient().POST('/oauth2/token', { headers: { 'Content-Type': 'application/x-www-form-urlencoded; charset=UTF-8' }, body: fields })` and asserts the sent body starts with `{` (JSON), not `grant_type=`. This pins T20-D5's mechanism independently of the auth code.

---

## Gap 3 · No source scan prevents the OIDC client from carrying middleware

**Why it matters.** `agents.md` L18 puts single-flight 401 renewal in the client boundary. `createOidcClient()` must never carry that boundary: a failed token exchange triggering renewal would perform another exchange, creating a loop (Phase 7 F4). The current source scan checks for `createOidcClient`, `fetch(`, and `XMLHttpRequest`, but it would not catch `createOidcClient().use(boundary.middleware(...))` or a replacement with `createAuthClient()`/`createAuthApi()`.

**Suggested test.** Extend the source scan in `the source itself` to assert `codeExchange.ts` contains no `.use(`, no `middleware`, and no `createAuthClient` or `createAuthApi`.

---

## Gap 4 · `access_token: null` is missing from the failure matrix

**Why it matters.** The `FAULTS` table includes `id_token: null` and `access_token: undefined`, `number`, and `''`, but not `access_token: null`. The current guard treats all non-strings identically, so the suite passes today. The matrix is the durable pin for AC12; omitting the mirror shape leaves room for a future refactor that special-cases `null` on one token differently from the other.

**Suggested test.** Add `{ label: 'an access_token that is null', answer: () => jsonResponse(tokensBody({ access_token: null })) }` to `FAULTS`.

---

## Gap 5 · Rejection-after-unmount path is not covered

**Why it matters.** The existing unmount test releases the pending promise with a success response and asserts no navigation. If the promise rejects after unmount — a dropped connection, a 5xx, or a malformed body — the `.then(onFulfilled, discard)` handler runs against a disposed screen. This could produce an unhandled rejection, a stale `setSignInNotice` mutation, or a warning from a disposed router. The success-only test does not rule that out.

**Suggested test.** Add a sibling to the unmount test that releases the pending promise with a rejection (or uses a stub that rejects after a delay), then asserts no unhandled rejection is observed, `router.state.location.pathname` is unchanged, `takeSignInNotice()` returns null, and no token appears in storage.

---

## Gap 6 · The exact serialized body is not pinned byte-for-byte

**Why it matters.** The implementation notes say the throwaway probe asserted the serialized body byte-for-byte. The committed test parses the body with `URLSearchParams` and compares field names and values. That is sufficient for functional correctness, but it is insensitive to field order and to whether `redirect_uri` is percent-encoded in the raw bytes. AC4's "serialized body" wording is stronger than a parsed comparison.

**Suggested test.** In the direct-layer wire block, add an assertion that compares `await sentRequest(spy).clone().text()` to the expected raw string `grant_type=authorization_code&code=THE-AUTHORIZATION-CODE&redirect_uri=<origin>%2Fapp%2Fcallback&client_id=checky-spa&code_verifier=<verifier>` (or to a canonical re-serialization if the contract does not mandate order). This documents the exact bytes the serializer emits.

---

## Gap 7 · The "no client credential" case does not cover cookie-bearing auth schemes

**Why it matters.** AC6 requires the absence of any client credential. The test checks headers and body for `Authorization`, `client_secret`, `client_assertion`, etc. It does not check that the request URL contains no embedded credentials (e.g., `https://user:pass@...`), and it cannot prevent the browser from sending the SAS session cookie, which is not a client credential but is a credential. The current assertion wording — "no client credential of any kind" — is slightly broader than what is actually verifiable.

**Suggested test.** Add an assertion that the request URL has no `username`/`password` userinfo component (`new URL(sent.url).username` and `.password` are empty) and clarify the test name to "carries no SPA-added client credential", since the httpOnly SAS session cookie is outside SPA control.

---

## Summary

The 64-case named file, the three `generatedClientCompiles` additions, and the `ALLOWED` lint entry close the Phase 8 findings and cover AC1–AC23. The gaps above are strengtheners rather than blockers: the suite is fit for purpose, but adding G1–G3 would remove structural assumptions that currently rely on source scans or mocked layers alone.
