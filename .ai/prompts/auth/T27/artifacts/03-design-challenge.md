# auth · T27 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T27 — API-key integration tests |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |
| **Status** | Findings for Phase 4 human gate |

## Summary

The TIB is narrowly scoped and consistent with the locked decisions, but it has one material inconsistency: AC5 requires verifying `last_used_at`, yet the Scope's prescribed test sequence (create → exchange → revoke → exchange-fails) never calls `GET /api-keys`, the only HTTP way to observe that field. The second finding is that AC3's claim about the post-revocation 401 being uniform would be more strongly proven by comparing it to another rejection cause, not just asserting its shape in isolation.

---

## Findings

### 1. `last_used_at` verification (AC5) cannot be satisfied without adding `GET /api-keys` to the test sequence

- **Severity:** Medium
- **Evidence:**
  - The Scope defines the test flow as: `POST /api-keys` → `POST /api-keys/token` → `DELETE /api-keys/{keyUuid}` → `POST /api-keys/token` (fails).
  - AC5 states: "`last_used_at` reflects the pre-revocation exchange (proves the exchange step actually persisted its effect, not just returned 200)."
  - `last_used_at` is returned only in `ApiKeyService.ApiKeyMetadata`, which is exposed only through `GET /api-keys`. The exchange endpoint's 200 response contains only the JWT; the revoke endpoint returns 204 with no body. There is no other HTTP path that exposes `last_used_at`.
- **Recommended brief amendment:** Add explicit `GET /api-keys` steps to the Scope sequence — at minimum, one after create (assert `last_used_at` is null) and one after the pre-revocation exchange (assert `last_used_at` is non-null). This makes AC5 testable without relying on internal state or direct service access. Optionally also assert `revoked_at` is non-null after the revoke step, to prove revocation persisted.

---

### 2. AC3's "uniform 401" is asserted in isolation rather than proven by comparison

- **Severity:** Medium
- **Evidence:**
  - AC3 requires the post-revocation 401 body to "match the shape every other rejection cause produces (`ProblemTypes.API_KEY_EXCHANGE_REJECTED`, no detail) — not a distinguishable 'revoked' variant."
  - The Required Tests section only describes asserting the post-revocation 401 body shape by itself.
- **Recommended brief amendment:** Strengthen the test design to compare the post-revocation 401 body byte-for-byte against at least one other rejection cause within the same test — for example, a deliberately malformed key (`ck_live_malformed`) or a wrong-secret attempt on the same key before revocation. This proves the "uniform" property directly rather than inferring it from the handler's structure, and it guards against a future regression that accidentally introduces a revocation-specific response variant.

---

### 3. The test sequence does not assert `revoked_at` is set after revocation

- **Severity:** Low
- **Evidence:**
  - The flow verifies that exchange fails after revocation (AC2), which is the externally observable consequence of revocation, but it does not assert that the key row's `revoked_at` timestamp was actually written.
  - `GET /api-keys` (needed for Finding #1 anyway) returns `revoked_at` in each `ApiKeyMetadata` item.
- **Recommended brief amendment:** Once `GET /api-keys` is added to the sequence, include an assertion that `revoked_at` is non-null after the `DELETE` step. This closes the loop between the HTTP action and the persisted state change, and it prevents a false pass if some unrelated mechanism caused the second exchange to fail.

---

### 4. Hidden dependency: the test uses `ApiKeyTokenIssuer` to authenticate the CRUD calls

- **Severity:** Low / informational
- **Evidence:**
  - The Dependencies and Scope sections note that the test will use `ApiKeyTokenIssuer` to mint a bearer JWT for the authenticated `POST /api-keys` and `DELETE /api-keys/{keyUuid}` calls.
  - This means T27 cannot run successfully if T25's `ApiKeyTokenIssuer` or the `JwtEncoder` bean is broken.
- **Recommended brief amendment:** No code change needed, but document this as an explicit dependency in the Constraints or Dependencies section: "T27's ability to authenticate its CRUD calls depends on the already-implemented T25 token-issuance path." This sets expectations if T27 fails during initial execution — the root cause may lie in T25 infrastructure rather than in T27's test logic.

---

### 5. The test assumes an API-key JWT is acceptable for authenticating `POST /api-keys` and `DELETE /api-keys/{keyUuid}`

- **Severity:** Low / informational
- **Evidence:**
  - The test authenticates the create/revoke calls with a JWT minted by `ApiKeyTokenIssuer` — i.e., a token obtained via API-key exchange, not via interactive login.
  - The resource-server filter accepts any validly-signed, unexpired JWT with a UUID `sub`; the service-level `MERCHANT`/MFA checks inside `ApiKeyService.create` then pass because the account was seeded with both.
- **Recommended brief amendment:** Confirm at the gate whether a machine-client API-key JWT creating additional API keys is an intended use case. If it is, no change. If it is not, the test should instead obtain an interactive session/token via the SAS flow (much heavier) or document that T27 intentionally uses the API-key JWT as a convenience. This is not a functional blocker, but it is a behavior-shaping assumption worth surfacing.

---

## Non-Findings

- **No production code changes:** correctly scoped; T27 is test-only.
- **Test file placement:** creating a dedicated `ApiKeyLifecycleIntegrationTest.java` is justified because the flow spans T24/T25/T26 territory.
- **JWT claim assertion scope:** asserting only `sub`/`scope`/`amr` on the pre-revocation exchange is appropriate; exhaustive L9 claim coverage belongs in `ApiKeyTokenIssuerTest`/`ApiKeyExchangeIntegrationTest`.
- **Naming:** `shouldSupportFullCreateExchangeRevokeExchangeFailsLifecycle` is distinct from the four existing names; acceptable.

---

**Phase 3 complete — design challenge written.** Proceed to Phase 4 (human gate) on approval.
