<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# auth · T36 · Phase 11 — Test Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T36 — End-to-end integration test |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/10-test-generation.md` |
| **Produces** | `artifacts/11-test-review.md` |

Reviewed the committed test in `services/auth/src/test/java/com/themistra/auth/EndToEndLifecycleIntegrationTest.java` and the Phase 10 test manifest. Findings are recommendations only, formatted as **Gap · Why it matters · Suggested test**.

---

## Gap 1 — TOTP code is generated before the full authorize flow, creating window-boundary flakiness

**Why it matters.** The test generates the TOTP code at line 247 with `referenceGenerateCode(totpSecret, Instant.now())` and then submits it through the full `/oauth2/authorize` → `/login` → `/oauth2/authorize` → `/oauth2/token` round trip. If generation occurs near the end of a 30-second window and server validation crosses into the next window, the code may be rejected. This is the single most likely source of non-deterministic failure in an otherwise deterministic flow.

**Suggested test.** Move TOTP generation as close to submission as possible. Refactor `attemptFullAuthorizeFlow` (or add a wrapper) to accept the TOTP secret and generate the code inside the helper immediately before `postLoginForm`, matching `SasLoginIntegrationTest.attemptLoginWithFreshTotpCode`. Add a comment explaining that the 90-second server tolerance window is the only clock coupling.

---

## Gap 2 — AC5 does not assert all L9 access-token claims in the end-to-end test

**Why it matters.** The manifest acknowledges that full L9 coverage lives in `ApiKeyTokenIssuerTest` and deliberately does not duplicate it here. That is a reasonable trade-off, but this end-to-end test is the only place that proves an actually-exchanged API-key JWT carries the real, resolved claims (not just that the issuer unit produces them). `email_verified`, `roles`, `client_id`, `iss`, `aud`, `iat`, `nbf`, `jti`, and `exp` are all unverified here.

**Suggested test.** Add lightweight presence assertions for the claims an API-key token is expected to carry, at minimum `email_verified`, `roles`, and `client_id`, plus an `exp` assertion that bounds the 10-minute TTL. Keep the exhaustive claim-set test in `ApiKeyTokenIssuerTest`, but protect this integration boundary from regressions that only appear when a real token is decoded.

---

## Gap 3 — AC4 API-key create response field set is only partially asserted

**Why it matters.** The test now guards against a hash-shaped value leaking, but still only extracts `plaintextKey`. It does not assert that `keyUuid`, `name`, and `createdAt` are present with reasonable shapes. A regression that dropped one of those fields would not fail this test.

**Suggested test.** Add assertions mirroring `ApiKeyLifecycleIntegrationTest`:

```java
assertThat(created.fieldNames()).toIterable()
        .containsExactlyInAnyOrder("keyUuid", "plaintextKey", "name", "createdAt");
assertThat(created.get("keyUuid").asText()).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
assertThat(created.get("name").asText()).isEqualTo("e2e key");
assertThat(created.get("createdAt").asText()).isNotBlank();
```

---

## Gap 4 — MERCHANT role assignment is not asserted before the blocked-login check

**Why it matters.** The test assumes `assignRoleViaHttp` succeeded and proceeds to assert that the next login is blocked. If the role assignment silently failed (e.g., returned 204 due to a path bug but did not persist), the blocked-login assertion would still pass because the account has no MERCHANT role — but for the wrong reason. The test would then continue, enroll TOTP, and the subsequent successful login would not actually prove that a MERCHANT account with MFA can log in.

**Suggested test.** Add an assertion immediately after `assignRoleViaHttp` that the merchant account has the `MERCHANT` effective role, e.g.:

```java
assertThat(roleService.resolveEffectiveRoles(merchantUuid)).contains("MERCHANT");
```

---

## Gap 5 — Shared Kafka broker with `earliest` offset may become slow as the suite grows

**Why it matters.** The consumer replays both topics from the beginning for every test method. As more integration tests append messages, the time to reach the relevant records grows linearly. The 15-second Awaitility timeout may eventually become tight, producing flaky timeouts unrelated to the code under test.

**Suggested test.** This is a test-infrastructure issue rather than a missing test. Add a helper in `@BeforeEach` that seeks the consumer to the end of each topic before the flow produces new messages, then verify with a small test that the consumer no longer sees prior messages. Alternatively, document the assumption that the suite remains small enough for `earliest` to be viable.

---

## Gap 6 — No dedicated assertion that the exchanged JWT authorizes the session endpoints

**Why it matters.** The flow uses the API-key-exchanged JWT for session list/revoke, which implicitly proves the token is usable. However, if a future change made session endpoints accept only authorization-code-granted tokens, this test would fail somewhere in the session steps without a clear assertion stating the intent.

**Suggested test.** Add a clarifying assertion comment or a focused check before the session list call, e.g.:

```java
assertThat(exchangedClaims.getStringListClaim("amr")).contains("api_key");
```

This documents that the subsequent session calls are intentionally authenticated with an API-key-exchanged token.

---

## Gap 7 — No negative test that a pre-MERCHANT login succeeds without MFA

**Why it matters.** The flow includes a password-only login before the MERCHANT assignment, but the test does not explicitly assert that this login would have succeeded if continued through the authorize flow. The first login is effectively a throwaway step used only to show the form works. The task statement lists it as a distinct step.

**Suggested test.** Either:
- Document that the first login is intentionally not carried through the full authorize flow because it is subsumed by the later TOTP login, or
- Add a lightweight assertion after the first `attemptFullAuthorizeFlow` that the login response does not contain `/login?error` (or, more strongly, complete the flow and exchange for a token with `amr= pwd` only).

Given the frozen brief's "one composed test method" decision, the documentation option is acceptable if explicit.

---

## Gap 8 — The test does not verify the `auth.email.requested` event payload shape beyond two fields

**Why it matters.** The test reads `purpose` and `token` from the Kafka payload but does not assert that the record matches `contracts/events/auth/email-requested.v1.schema.json` (e.g., `accountUuid`, `occurredAt`). A malformed event that still contains `purpose` and `token` would pass.

**Suggested test.** Add assertions for the expected schema fields:

```java
assertThat(payload.get("accountUuid").asText()).isEqualTo(merchantUuid.toString());
assertThat(payload.get("occurredAt").asText()).isNotBlank();
```

---

## Summary

The Phase 9 fixes addressed the most serious Phase 8 findings (Kafka null-safety, status transition, response-shape assertions, session-revoke authorization removal). The remaining gaps are mostly incremental hardening: TOTP freshness, fuller claim/field coverage, role-assignment verification, and Kafka consumer efficiency. None of these appear to be correctness bugs against the current codebase, but closing them would make the test more robust and more clearly traceable to the acceptance criteria.

(End of Phase 11 test review.)
