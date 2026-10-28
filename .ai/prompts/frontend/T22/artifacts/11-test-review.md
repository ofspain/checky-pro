# frontend · T22 · Phase 11 — Test Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T22 — No token in any storage |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Reviewed `frontend/src/auth/tokensAreNeverWrittenToAnyStorage.test.tsx` against the frozen brief and the 43-case manifest. The file is strong: it fixes every Phase 8 methodology issue, drives the real flow, self-proves every instrument, kills 20/20 mutants, and carries the honest limitations in its header. The gaps below are strengtheners; none suggests a real leak in the current production code.

> Note on verification: the local environment does not have `vite-plugin-pwa` installed, so `vitest` cannot start. Findings are from static review of the committed test source.

---

## Gap 1 · The sweep does not search decoded ID-token claim values

**Why it matters.** T22-D2 defines "a token" as "the ID token, whole, each of its three segments, **and any decoded claim value**". The test's `piecesOf()` searches the whole token and its three base64url-encoded segments, but it never decodes the payload and searches the raw claim values. A leak that wrote a decoded claim — for example the `nonce`, `sub`, or `iss` value — to `localStorage`, `document.title`, or `window.name` would not be caught by the current sweep.

**Evidence.**
- `tokensAreNeverWrittenToAnyStorage.test.tsx:222-225`: `piecesOf()` returns only `[token, ...segments]`.
- `tokensAreNeverWrittenToAnyStorage.test.tsx:300-309`: `tokensFor()` builds an ID token with claims `sub: 'the-subject'`, `iss: 'http://localhost:5173'`, and the live nonce.
- The success and failure cases call `piecesOf(body.id_token)` only.
- A surface containing the literal string `'the-subject'` would not contain the base64url segment that encodes it, so the segment search would miss it.

**Suggested test.** Add a `decodedClaimsOf(idToken)` helper that decodes the payload and returns its primitive string values. Add those values to the searchable set for every surface **except** the L16 `nonce` entry in `sessionStorage` (which legitimately contains the nonce). Include a self-proving control that writes one decoded claim value to `localStorage` and asserts the sweep catches it. This closes the gap without contradicting AC2.

---

## Gap 2 · The `window.name` source-scan pattern misses bracket-property writes

**Why it matters.** The source scan forbids `(?:window|globalThis|self)\.name\s*=`, which catches `window.name = token`. It does not catch `window['name'] = token` or `globalThis['name'] = token`. The runtime sweep would catch the resulting value, but the source scan is the net for code no test executes.

**Evidence.**
- `tokensAreNeverWrittenToAnyStorage.test.tsx:409-412`: the `window.name` pattern is anchored to dotted property access.
- The bracket-alias pattern covers `['localStorage']` and `['sessionStorage']` but not `['name']`.

**Suggested test.** Either extend the `window.name` pattern to include `\[(?:'name'|"name")\]\s*=` or add a separate `FORBIDDEN` row for it, with a synthetic violation proving the scan catches `window['name'] = token;`.

---

## Gap 3 · The two-sign-in and post-clear cases assert less than the success case

**Why it matters.** Both cases are important for AC1 coverage, but neither watches the console or asserts `localStorage` is empty. A regression that logged the first sign-in's token during the second sign-in, or during `clearAccessToken`, would not be caught.

**Evidence.**
- `tokensAreNeverWrittenToAnyStorage.test.tsx:581-604`: the two-sign-in case uses `watchStorage()` but not `watchConsole()`, and only asserts `sessionStorage` values.
- `tokensAreNeverWrittenToAnyStorage.test.tsx:606-618`: the post-clear case uses `watchStorage()` but not `watchConsole()`, and does not assert `localStorage` or the three L16 items remain.

**Suggested test.** Add `watchConsole()` to both cases and assert `loggedText(logs).toBe('')`. Assert `storeEntries(globalThis.localStorage).toEqual([])` in both. In the post-clear case, also assert the three L16 items remain in `sessionStorage` (to document that clearing the token does not disturb them).

---

## Gap 4 · No test exercises a `refresh_token` in the response

**Why it matters.** T22-D2 explicitly includes "any `refresh_token`, should one ever appear". L3 records that none is issued to this client, but the sweep should still prove it would catch one if the server ever sent it.

**Evidence.**
- `tokensFor()` never includes `refresh_token`.
- No failure or success case sends a `refresh_token`.
- The source scan forbids storage methods, but a runtime control would prove the sweep catches a token-shaped value that is not the access token.

**Suggested test.** Add one success case that calls `tokensFor(started.nonce, { refresh_token: 'REFRESH-TOKEN-...' })` and asserts the refresh token appears in no surface. Use a distinctive value so the control is unambiguous.

---

## Gap 5 · The source scan's `clear` pattern is broader than a storage method

**Why it matters.** The allowlist test uses `\bclear\s*\(` to detect `clear()` calls. `clear` is a common identifier prefix (e.g., `clearTimeout`, `clearInterval`, `clearSignInNotice`). The pattern as written matches `clearTimeout(` and `clearInterval(` because `\b` sits between the identifier start and the preceding non-word character. Today no production file has such a call outside the allowlist, but the pattern is brittle and could produce false positives or miss a real `storage.clear()` depending on context.

**Evidence.**
- `tokensAreNeverWrittenToAnyStorage.test.tsx:741-752`: the allowlist test uses `new RegExp('\\b${method}\\s*\\(')` for `setItem`, `removeItem`, and `clear`.
- A method named `clearFields()` would match and be flagged as a storage offence.

**Suggested test.** Change the `clear` pattern to `\.(?:clear)\s*\(` or require a storage object prefix, so it matches `sessionStorage.clear()` and `localStorage.clear()` but not `clearTimeout()` or `clearForm()`. Include a synthetic file with `clearTimeout(timer)` and assert it is not flagged.

---

## Gap 6 · The source scan does not catch `eval` or `Function` with a storage call

**Why it matters.** A dependency or a future regression could execute `eval("localStorage.setItem('k', token)")` or `new Function("localStorage.setItem('k', token)")()`. The runtime sweep would catch the resulting `Storage.prototype.setItem` call if the code runs, but the source scan would not see the storage API name if it lives inside a string literal.

**Evidence.**
- `FORBIDDEN` patterns match `localStorage`, `sessionStorage`, `console`, etc., only when they appear as identifiers in source text.
- A string literal containing `"localStorage.setItem"` would not match.

**Suggested test.** Add `eval` and `new Function(` to the source scan as shapes worth flagging, with synthetic violations. This is a low-priority defence-in-depth item; the runtime sweep remains the primary instrument.

---

## Summary

Gap 1 is the most significant because it is a direct incompleteness against T22-D2's own definition of "token". Gaps 2, 5, and 6 are source-scan precision items. Gaps 3 and 4 are coverage strengtheners for important but already partially covered areas. The suite as written is already a credible instrument; these changes would make it more complete and less brittle.
