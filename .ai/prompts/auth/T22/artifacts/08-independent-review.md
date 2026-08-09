# auth · T22 · Phase 8 — Independent Code Review

| | |
|---|---|
| **Service** | `auth-service` |
| **Task** | T22 — MFA integration tests |
| **Consumes** | `artifacts/07-self-review.md` + Phase 6 implementation |
| **Produces** | `artifacts/08-independent-review.md` |

Independent adversarial review of the T22 implementation. Findings only.

**Review limitation:** `mvn` is not available in this environment, so I could not re-run the Testcontainers suite. Findings below are based on static analysis of `authn/SasLoginIntegrationTest.java`, `db/migration/V6__oauth2_authorization_device_and_user_code_columns.sql`, and the artifacts in `.ai/prompts/auth/T22/artifacts/`.

---

## 1. `state` parameter is generated but never verified in the authorization response

- **Issue.** The full-flow tests generate a fresh `state` per attempt (`UUID.randomUUID().toString()`) and send it to `/oauth2/authorize`, but they never assert that the same `state` is echoed back in the final redirect to the SPA callback. Verifying `state` is a mandatory OAuth2 client responsibility for CSRF protection of the authorization response; a regression that drops or rewrites `state` on the redirect would not be caught.
- **Evidence.** `SasLoginIntegrationTest.java:350-363`, `:368-383`, `:391-408`, `:412-425` all call `attemptFullAuthorizeFlow(..., UUID.randomUUID().toString())` but only assert `authorizationCode()` is present/absent. `extractQueryParam` at `:612-614` can read any query parameter, but is only called with `"code"`. The `FullFlowResult` record does not carry `state` back to callers.
- **Recommendation.** Add `state` echo verification in at least `issuedTokenHasOtpAmrAndAcrAfterMfa` and `issuedTokenHasPwdOnlyAmrThroughFullFlowWhenMfaNotRequired` (the two tests that already parse the final redirect). Either thread `state` through `FullFlowResult` or assert it inline inside `attemptFullAuthorizeFlow` when an authorization code is present. Keep the failure-path tests focused on "no code" if desired.
- **Confidence.** High.

---

## 2. Success path in `attemptFullAuthorizeFlow` does not assert that `/oauth2/authorize` returns a redirect

- **Issue.** After a successful login, the helper re-issues `/oauth2/authorize` and immediately calls `completedAuthorize.getHeaders().getLocation()`. If SAS returned a non-redirect response (e.g., 200 OK with an error page, or a redirect to an unexpected host), the helper silently returns `Optional.empty()` and the calling test fails with "authorization code should be present" — a symptom, not the actual failure mode.
- **Evidence.** `SasLoginIntegrationTest.java:603-605`: no status-code assertion, no assertion that `finalLocation` is non-null before `extractQueryParam` is called. `code` becomes `null` when `finalLocation` is null, and `Optional.ofNullable(code)` becomes empty.
- **Recommendation.** Before extracting `code`, assert `completedAuthorize.getStatusCode() == HttpStatus.FOUND` and that `finalLocation` is non-null and starts with the configured SPA `redirect_uri`. This makes unexpected SAS behavior explicit and prevents empty-code assertions from masking redirect bugs.
- **Confidence.** High.

---

## 3. R25 negative branch only asserts "no code," not the expected `/login?error` redirect

- **Issue.** `confirmedMfaRequiresCodeToFinishAuthorizeFlow` asserts that `passwordOnly.authorizationCode()` is empty, but it does not also assert that the login response redirected to `/login?error`. A future bug that silently drops the authorization code while still authenticating the user (or that redirects somewhere else entirely) would satisfy the current assertion.
- **Evidence.** `SasLoginIntegrationTest.java:374-377` checks only `authorizationCode().isEmpty()`. The R24 test at `:359-362` checks both empty code and `/login?error`; R25's negative branch should be equally explicit.
- **Recommendation.** Add the same `loginResponse().getHeaders().getLocation()` `/login?error` assertion to the password-only branch of `confirmedMfaRequiresCodeToFinishAuthorizeFlow`.
- **Confidence.** High.

---

## 4. Cookie helper sends full `Set-Cookie` attributes in the `Cookie` request header

- **Issue.** `mergeCookies` stores entire `Set-Cookie` header values (including `Path=/`, `HttpOnly`, `SameSite`, etc.) and `getWithCookies` / `postLoginForm` emit those full strings as `Cookie` header values. The `Cookie` request header should contain only `name=value` pairs. This happens to work because servlet containers parse leniently, but it is incorrect and could break with a stricter HTTP client or server in the future.
- **Evidence.** `SasLoginIntegrationTest.java:507-525` (`mergeCookies` / `cookieName`) extracts only the name for de-duplication but preserves the full header value. `getWithCookies` at `:494-499` and `postLoginForm` at `:482-487` send `cookies` unchanged. The same pattern exists in pre-T20 `attemptLogin`, so this is not new to T22, but T22 extends it with `mergeCookies` and multi-hop cookie state.
- **Recommendation.** Parse each `Set-Cookie` value to extract only the `name=value` portion (stopping at the first `;`) before sending it as a `Cookie` header. This is a correctness fix that applies to both the pre-existing helper and the new T22 paths; fixing it now prevents latent fragility as the suite grows.
- **Confidence.** Medium-High.

---

## 5. V6 migration adds production schema changes outside the frozen brief's file scope

- **Issue.** The frozen brief explicitly lists "Files NOT to Modify: Any production code" and scopes the task as test-only. The Phase 6 notes record that `V6__oauth2_authorization_device_and_user_code_columns.sql` was added because `JdbcOAuth2AuthorizationService` unconditionally references device-grant columns. This is a justified deviation, but it is a deviation from the approved brief.
- **Evidence.** `artifacts/04-frozen-task-brief.md` §91-94; `artifacts/06-implementation-notes.md` §6, §22-28; the new file `services/auth/src/main/resources/db/migration/V6__oauth2_authorization_device_and_user_code_columns.sql`.
- **Recommendation.** Record the deviation formally in the task artifact chain (already done in `06-implementation-notes.md`) and ensure the human approval gate (Phase 4/9) explicitly accepted it. As a minor hardening, change the 8 `ADD COLUMN` clauses to `ADD COLUMN IF NOT EXISTS` to match the defensive style used in `V5__lockout_cleanup_and_shedlock.sql` (self-review finding #2).
- **Confidence.** High.

---

## 6. V6 migration column types are consistent with V1

- **Issue/Non-issue.** The new columns use `TEXT` for values/metadata and `TIMESTAMPTZ` for timestamps, matching the translation V1 already applied to the other `oauth2_authorization` columns. No finding — confirmed correct.
- **Evidence.** Comparison of `V1__auth_baseline_schema.sql` lines 167-184 with `V6__...sql` lines 13-20.
- **Confidence.** High.

---

## 7. Named test `shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled` does not cover recovery codes through the full flow

- **Issue.** `package.md` §8 names the test `shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled`. The implementation's full-flow R25 test (`confirmedMfaRequiresCodeToFinishAuthorizeFlow`) exercises only the TOTP branch. The frozen brief explicitly scoped recovery-code full-flow coverage out (disposition #7), citing T20's `/login`-layer recovery-code test as sufficient. This creates a literal mismatch between the named test string and what the full-flow test proves.
- **Evidence.** `package.md` §8 line 98; `artifacts/04-frozen-task-brief.md` §26; `SasLoginIntegrationTest.java:365-383`.
- **Recommendation.** Either rename the named test in `package.md` to `shouldRequireValidTotpCodeWhenMfaIsEnrolled` for the full-flow layer, or add a second full-flow test using a recovery code. Do not leave a test name that promises behavior the test does not exercise.
- **Confidence.** High.

---

## 8. `exchangeCodeForToken` does not check the HTTP status code before parsing

- **Issue.** The helper parses the `/oauth2/token` response body without first checking the response status. In practice, a missing `access_token` field throws `IllegalStateException`, which is loud enough for most failures. But a 200 OK with an unexpected body shape that happens to contain a field named `access_token` would be accepted, and a non-2xx with an `access_token` field (admittedly unlikely) would also be accepted.
- **Evidence.** `SasLoginIntegrationTest.java:620-645`. The self-review (finding #4) already evaluated this and accepted it.
- **Recommendation.** Add `assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK)` before parsing. This is a one-line robustness improvement that makes failures clearer without changing production code.
- **Confidence.** Medium.

---

## 9. `attemptFullAuthorizeFlow` silently treats a successful login that lands on `/` as success

- **Issue.** After `postLoginForm`, the helper checks only that the `Location` does not contain `/login?error`. It does not verify that login actually succeeded (e.g., redirect to `/` as documented in the implementation notes, or any other success target). This is mostly fine because a failed login is the only expected non-error case, but a successful login that redirects to `/login?error` for an unexpected reason would be caught, while a successful login that redirects elsewhere would proceed to re-issue `/oauth2/authorize`.
- **Evidence.** `SasLoginIntegrationTest.java:596-600`.
- **Recommendation.** Optionally assert that the post-login `Location` is either `/` (the documented default-target behavior) or the original `/oauth2/authorize` URL, so unexpected redirect targets fail loudly. This is lower priority than finding #2.
- **Confidence.** Low-Medium.

---

## 10. PKCE `redirect_uri` encoding could theoretically mismatch between authorize and token requests

- **Issue.** `authorizeUrl` encodes `redirect_uri` as a URL query parameter via `UriComponentsBuilder.encode()`, while `exchangeCodeForToken` sends it as a form value via Spring's form encoder. Both are standard URL-encoding, but the encoders are different code paths. If they ever diverge (e.g., one encodes `:` and `/` and the other does not), SAS would reject the token exchange. The tests passed in the implementation environment, so this is theoretical.
- **Evidence.** `SasLoginIntegrationTest.java:549-561` and `:620-626`.
- **Recommendation.** No code change required unless tests flake. Consider sharing the exact `redirect_uri` string in a constant/field to guarantee the same value reaches both call sites (already true in practice via `authClientsProperties.spa().redirectUris().getFirst()`).
- **Confidence.** Low.

---

## 11. No new LOCKED-decision or `agents.md` conflicts observed

- L10 (MFA mandatory for `MERCHANT`/`ADMIN`) is respected: R24 uses `MERCHANT`.
- L9 (fixed claim set) is respected: tests inspect only `amr`/`acr`, consistent with the frozen brief's scope reduction.
- L5 (enumeration-safe responses) is respected: tests observe redirect shape, not internal state.
- No production security logic was modified; the only production file touched is the V6 migration, which is schema-only and additive.
- `spec/` files were not modified.

---

## Open Questions

None. All findings are additive fixes or documentation/traceability clarifications; none indicate that the implementation fails to satisfy R24/R25/R26 as currently verified.

(End of artifact)
