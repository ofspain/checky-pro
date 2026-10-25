# frontend · T15 — Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T15 — Authorize request shape |
| **Spec section** | Phase 1a — Security properties |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

**Task statement (verbatim):**
> Assemble the OIDC authorization request: exactly the three declared scopes, `state`, `nonce`, `code_challenge_method=S256` and the PKCE challenge from T14 — and persist the three L16 items the callback tasks will read back.

---

## Scope and method

This is an adversarial review of `artifacts/02-task-implementation-brief.md` for **T15 — Authorize request shape**. Findings are against the brief itself; no code was changed.

The brief is clear about the boundary with T14/T16/T17–T20 and correctly identifies construction-from-origin as the only viable path under the current lint and contract constraints. The main risks are unspoken details in the URL shape, the precedent of reopening `pkce.ts`, and the usual host/baseline gaps.

---

## Findings

### 1. The authorize endpoint path is not specified
**Issue ·** The brief says the URL is built on the SPA's own origin but never states the path segment (`/oauth2/authorize`). Every other constant in the request (`client_id`, scopes, method, redirect path) is either derived or published, yet the most important URL component is implicit. A drift in the edge routing or SAS configuration would silently break sign-in.  
**Severity ·** Medium — the path is the core of the request.  
**Evidence ·** `infra/local/edge/Caddyfile` routes `/oauth2/*` to the auth service; Spring Authorization Server's default authorize endpoint is `/oauth2/authorize`; the brief never names it.  
**Recommendation ·** Add a published constant in the frozen brief:
```ts
export const AUTHORIZE_ENDPOINT_PATH = '/oauth2/authorize';
```
Derive the full URL as `${location.origin}${AUTHORIZE_ENDPOINT_PATH}` and add a drift guard that asserts the path appears in the edge Caddyfile or in a documented SAS endpoint list.

### 2. The host dependency gap from T12–T14 is still present
**Issue ·** The suite cannot be executed on this host because `frontend/node_modules` is incomplete (`vite-plugin-pwa` is missing). Phase 6 implementation and the AC10 baseline verification cannot be checked here.  
**Severity ·** High — T15 adds no package, but its tests run in the existing suite.  
**Evidence ·** Earlier attempts to run `vitest` in `frontend/` fail with `ERR_MODULE_NOT_FOUND` for `vite-plugin-pwa`.  
**Recommendation ·** Close the T12 OQ-C blocker before Phase 6. Record in the frozen brief that the environment must have `frontend/node_modules` installed.

### 3. Reopening `pkce.ts` sets a precedent that needs explicit justification
**Issue ·** The brief declares that `pkce.ts` will gain a new export `randomUrlSafeToken`. While the reasoning is good (avoid duplication and misleading names), it reopens a file from a completed task. The frozen brief must make this decision unmistakable so Phase 6 does not treat it as an accidental boundary violation.  
**Severity ·** Low — the alternative (duplication) is worse, but the choice must be locked.  
**Evidence ·** Brief §Files to Modify; §Constraints.  
**Recommendation ·** Add a locked decision: "`randomUrlSafeToken` lives in `pkce.ts` because T14's AC10 asserts `btoa(` appears in that source. T15's tests must re-run T14's suite and show it green." Also document why a new file was rejected (breaking T14's source-scan invariant).

### 4. The `redirect_uri` derivation needs a trailing-slash rule
**Issue ·** `APP_BASENAME` is `/app` (no trailing slash). The redirect URI must be `${location.origin}${APP_BASENAME}/callback`. If a future change makes `APP_BASENAME` `/app/` (with trailing slash), the naive concatenation would produce `/app//callback`. The brief says "derived from `APP_BASENAME`" but does not say how.  
**Severity ·** Low — the current value works, but the derivation is not robust.  
**Evidence ·** `frontend/src/app/routes.tsx:4`.  
**Recommendation ·** Lock the derivation in the frozen brief: strip any trailing slash from `APP_BASENAME`, then append `/callback`. Add a drift guard that asserts the computed redirect URI matches `/app/callback` for the current `APP_BASENAME`.

### 5. `client_id = 'checky-spa'` is hardcoded without a declared home
**Issue ·** AC5 says `client_id=checky-spa`, but the brief does not say whether this is a published constant, a literal in the URL builder, or derived from anywhere. It is a public client identifier, not a secret, but it is still a magic string.  
**Severity ·** Low — not a leak, but should be explicit.  
**Recommendation ·** Publish it as a constant:
```ts
export const CLIENT_ID = 'checky-spa';
```
Add a test that the authorize URL contains it and that the module source contains no other occurrence of `checky-spa` (so it cannot drift to two different values).

### 6. The drift guard on `routes.tsx` is underspecified
**Issue ·** AC8 mentions a drift guard on `routes.tsx`'s `APP_BASENAME`/`callback`. The brief does not say how it is implemented. It could be a brittle string match against the file contents or a functional assertion.  
**Severity ·** Low.  
**Recommendation ·** Define the guard: import `APP_BASENAME` from `routes.tsx`, compute `redirect_uri`, and assert it equals `${location.origin}/app/callback` for `location.origin = 'http://localhost'`. This proves the derivation follows the route configuration rather than hardcoding `/app`.

### 7. The required tests omit an explicit rejection path
**Issue ·** The brief states under Null handling that "the function is async … and rejects rather than returning a half-built request if the derivation rejects." Required tests do not include a case for this.  
**Severity ·** Low — challenge derivation only rejects on an invalid verifier, which `generatePkcePair` prevents, but the contract is public.  
**Recommendation ·** Add to Required tests: if `generatePkcePair` somehow returns an invalid verifier, `buildAuthorizeRequest` rejects (or, more practically, mock `generatePkcePair` to return an invalid verifier and assert rejection).

### 8. No test that `localStorage` is untouched at runtime
**Issue ·** Required tests mention sessionStorage holding exactly three keys and a source scan for `localStorage`. They do not explicitly require a runtime spy on `localStorage.setItem` during `buildAuthorizeRequest`, the way T14 added runtime spies.  
**Severity ·** Low — source scan is the primary guard.  
**Recommendation ·** Add a runtime assertion: spy on `localStorage.setItem` (and other Storage methods) during the request build and assert none are called. This mirrors T14's defense-in-depth pattern.

### 9. The baseline `479/481` in AC10 is unverified
**Issue ·** AC10 asserts a whole-suite baseline of 479 passing / 481 total. This cannot be confirmed on this host because dependencies are missing, and it depends on the final counts from T14.  
**Severity ·** Low — typical for a pre-implementation brief, but should be measured rather than asserted.  
**Recommendation ·** Replace the literal baseline with a rule measured during Phase 6: "baseline equals the count from `vitest run` immediately before this task, plus the new T15 cases, and the only failures are the two known environmental T07 cases."

### 10. The negative control for the no-verifier rule is not specified
**Issue ·** AC9 says the no-verifier rule must be fed deliberately wrong inputs through the same matcher. The brief does not say what the wrong input looks like. A test that simply checks the URL string for the verifier pattern could be vacuous if the verifier is random and unlikely to appear.  
**Severity ·** Low — the rule is structural (verifier not returned), but AC9 asks for proof.  
**Recommendation ·** Add a concrete negative control: build a synthetic URL containing the verifier as a query parameter and assert the matcher reports it. Then build the real URL and assert the matcher reports nothing. This proves the scanner can catch a leak.

### 11. No guidance on parameter ordering or encoding
**Issue ·** The authorize URL will contain multiple query parameters. The brief does not specify whether the implementation uses `URLSearchParams` or string concatenation, or whether parameters are sorted.  
**Severity ·** Info.  
**Recommendation ·** Lock the implementation to `URLSearchParams` (browser-native, no dependency) and assert parameter values by parsing the URL rather than by string equality. This avoids brittle ordering failures.

### 12. OQ-1 is correctly carried but will still block T16
**Issue ·** T01 forbids `import.meta.env` while §4c permits three public OIDC keys. T15 needs no key, so it is not a blocker here. T16 (sign-in screen) or T20 (token exchange configuration) may need one.  
**Severity ·** Info.  
**Recommendation ·** Keep OQ-1 in the frozen brief as an explicit owner decision required before any task consumes a `VITE_*` key.

---

## Open Questions

- **OQ-C (host dependencies)** remains the only true blocker and must close before Phase 6.
- **OQ-1** is correctly carried for the owner.
- The authorize endpoint path, `redirect_uri` derivation, `client_id` home, drift-guard shape, and parameter-building method are design decisions that should be locked in the frozen brief.
