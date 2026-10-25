# frontend · T15 — Phase 11 — Test Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T15 — Authorize request shape |
| **Spec section** | Phase 1a — Security properties |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

**Task statement (verbatim):**
> Assemble the OIDC authorization request: exactly the three declared scopes, `state`, `nonce`, `code_challenge_method=S256` and the PKCE challenge from T14 — and persist the three L16 items the callback tasks will read back.

---

## Method

Reviewed the Phase 10 test manifest for `authorizeRequestsExactlyTheDeclaredScopes` (45 cases, 23 of 23 mutants caught) against the frozen T15 acceptance criteria, the Phase 8 findings, and the brief's required tests. No code was rewritten.

The Phase 9/10 resolution is comprehensive: exact parameter set (S1), honest leak matcher (S2/S3), overwrite-on-retry (S4), fuller `location` stub plus source-level pin (S5), and robust drift guards including backend-anchored endpoint spelling (M10/M12). The suite is now a strong regression guard.

---

## Gaps

### 1. The required source scan for absolute URLs and `import.meta.env` is missing
**Gap ·** The frozen brief's Required Tests list says the file must include "a source scan for `'S256'`, absolute URLs, `import.meta.env` and `localStorage`." The current suite scans for quoted `'S256'` and `localStorage`/`Buffer`, but it does not assert that `authorizeRequest.ts` contains no absolute `http(s)` URL literal and no `import.meta.env` access.  
**Why it matters ·** These are standing project rules (T01, L10, R58) and the task's own AC5. A future edit could slip an absolute documentation URL or a `VITE_*` key read into the module and pass the current suite.  
**Suggested test ·** Add two source-level assertions:
```ts
expect(moduleSource).not.toMatch(/https?:\/\//);
expect(moduleSource).not.toContain('import.meta.env');
```
(Allow `http://` only inside comments if comments are stripped first, as they are for the `S256` scan.)

### 2. No negative control proving the exact-eight matcher would fail if a parameter were removed
**Gap ·** The exact-parameter-set case is now present and has a negative control that adds a ninth parameter (`login_hint`). It does not show that removing one of the eight would also fail the matcher.  
**Why it matters ·** The matcher is `[...keys()].sort()` equality; a regression that dropped a parameter would be caught by individual parameter tests, but the default-deny matcher itself should be shown to fail in both directions.  
**Suggested test ·** Add a case that builds a URL, removes one parameter with `params.delete('nonce')`, and asserts the resulting key list no longer equals the expected eight.

### 3. The leak matcher's limits are documented but not demonstrated
**Gap ·** The test file has an excellent comment stating that a re-encoded verifier (e.g., base64) or a verifier split across two parameters would not be detected. There is no case that actually feeds such a transformed value to the matcher and asserts it passes undetected.  
**Why it matters ·** A documented limitation that is never exercised can become stale or be ignored. Demonstrating it once proves the comment is accurate.  
**Suggested test ·** Add an informational case:
```ts
it('documents the limits of the substring matcher', async () => {
  const { url } = await buildAuthorizeRequest();
  const verifier = globalThis.sessionStorage.getItem(AUTHORIZE_STORAGE_KEYS.codeVerifier) as string;
  const reEncoded = btoa(verifier).replaceAll('=', ''); // base64 of verifier is not the verifier
  expect(urlLeaksVerifier(`${url}&x=${reEncoded}`, verifier)).toBe(false);
});
```
This is not a defect — it makes the documented limit a pinned fact.

### 4. No test that `buildAuthorizeRequest` calls `generatePkcePair` exactly once
**Gap ·** AC3 is guarded by reading the stored verifier back and recomputing the challenge. It does not verify that only one PKCE pair is generated per request.  
**Why it matters ·** A buggy implementation that generated one pair for the challenge and a different one for storage would fail AC3, but an implementation that generated two pairs and used the first for both would pass. The more subtle failure is generating the pair twice wastefully or inconsistently.  
**Suggested test ·** Optional: spy on `generatePkcePair` and assert it is called exactly once per `buildAuthorizeRequest()` call.

### 5. No test that `state`/`nonce` are generated from the shared helper rather than hardcoded
**Gap ·** The suite checks that `state` and `nonce` are 43 characters and fresh, but it does not assert that `authorizeRequest.ts` imports and calls `randomUrlSafeToken` for them. A hardcoded 43-character string would fail freshness but could evade a simpler check.  
**Why it matters ·** The shared helper exists precisely so `state`/`nonce` and the verifier share one CSPRNG path. The contract should be visible in the source.  
**Suggested test ·** Add a source assertion: `codeWithoutComments(moduleSource)` contains `randomUrlSafeToken` at least twice (one for `state`, one for `nonce`).

### 6. No test that the authorize endpoint and redirect URI share the same origin
**Gap ·** The suite checks that the authorize URL's origin matches `location.origin` and that the `redirect_uri` matches `${location.origin}${APP_BASENAME}/callback`. It does not assert that the two origins are equal to each other within a single request.  
**Why it matters ·** A bug that used different origins (e.g., a hardcoded origin in one place) could pass both checks if `location.origin` happened to match in the test environment.  
**Suggested test ·** Add one line to the request-shape case: parse both URLs and assert `authorizeOrigin === redirectOrigin`.

### 7. The backend-path drift guard does not assert the set of paths is exactly the two expected
**Gap ·** The guard extracts every `"/oauth2/..."` literal from the auth service tests and asserts the set contains `AUTHORIZE_ENDPOINT_PATH`. It also asserts the set is non-empty. It does not assert the set equals `{ '/oauth2/authorize', '/oauth2/token' }`.  
**Why it matters ·** A new unrelated `/oauth2/foo` path added to the backend would not fail the guard, even though the set would no longer be the minimal two-path set. The guard is directional, not exact.  
**Suggested test ·** Optional: assert the extracted set equals exactly `{ '/oauth2/authorize', '/oauth2/token' }`. This makes the guard a precise cross-module contract; if it becomes too brittle, the author must justify a new path.

---

## Non-gaps (acknowledged, no change needed)

- **45/45 cases pass** and **23/23 mutants caught** (after M10/M12 fixes).
- **M5 (`code_challenge_method: 'plain'`)** is caught — the suite's highest-value case, since the backend would accept it.
- **M6/M7 (verifier sent / different pair stored)** are caught by the stored-verifier challenge recomputation.
- **M10/M12 (hardcoded `/app`, misspelled endpoint)** are caught by the new route-table mocking and backend-anchored spelling cases.
- **M19 (ninth parameter)** demonstrates that the S1 fix is load-bearing.
- **M21/M22 (matchers weakened)** prove the matchers are not decorative.
- **M23/M24 (backend anchor regex / route table drift)** prove the drift guards are real.
- **AC1–AC11 are pinned** by the cases and mutants listed in the manifest.
- **The honest coverage limits are stated** in the manifest and the test file: substring matcher cannot catch encoded/split leaks, no browser runs, no server round-trip, `location` stub is narrower than a real `Location`.
- **Suite delta is measured** (521/523 → 524/526, +3 in this phase; 479/481 → 521/523 for T15's 42 cases overall).
- **T14's 67 cases still pass** after the `pkce.ts` change.
- **The two whole-suite failures** are the known environmental T07 `pnpm` failures, unchanged by this task.

---

## Summary

T15's test suite is excellent after Phase 10: 45 cases, all mutants caught, honest reporting of limitations, and strong drift guards. The remaining gaps are small and mostly about completeness of source scans and negative controls: add absolute-URL and `import.meta.env` source assertions, show the exact-eight matcher failing on parameter removal, demonstrate the leak matcher's documented limits, and optionally pin a few more structural facts (single PKCE call, shared helper usage, origin equality, exact backend path set). Adding these would make the suite a complete regression guard for the request shape and its standing-project constraints.
