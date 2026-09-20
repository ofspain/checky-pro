> **STATUS: RESOLVED.** Human sign-off given 2026-08-09 ("move on") on the recommendation set presented alongside the Phase 8 findings. `mvn -pl services/auth -am compile`, `test-compile`, and the full suite (486 tests, same 6 pre-existing/unrelated errors as before this phase) all pass after every change.

# auth · T22 · Phase 9 — Review Resolution

11 findings from Phase 8 (`08-independent-review.md`), 2 of which independently re-derived Phase 7 self-review findings.

---

**1. `state` generated but never verified in the response.**
**Disposition: ACCEPTED.**
**Change:** `FullFlowResult` gained a third field, `returnedState`, populated from the final redirect the same way `authorizationCode` already was. The two token-exchange tests (`issuedTokenHasOtpAmrAndAcrAfterMfa`, `issuedTokenHasPwdOnlyAmrThroughFullFlowWhenMfaNotRequired`) now capture their `state` in a local variable and assert `result.returnedState()).contains(state)`.

**2. Success path doesn't assert `/oauth2/authorize` actually returned a redirect before reading `code` from it.**
**Disposition: ACCEPTED.**
**Change:** `attemptFullAuthorizeFlow` now checks `completedAuthorize.getStatusCode() == FOUND` and a non-null `Location` before extracting anything, throwing a specific `IllegalStateException` naming the expected redirect target otherwise — matching the same defensive pattern already used for the first `/oauth2/authorize` → `/login` redirect earlier in the same method.

**3. R25's negative branch only checks the code is absent, not the actual `/login?error` redirect.**
**Disposition: ACCEPTED.**
**Change:** `confirmedMfaRequiresCodeToFinishAuthorizeFlow`'s password-only branch now asserts the `/login?error` redirect too, matching R24's test rigor.

**4. `Cookie:` request header built from raw `Set-Cookie` values (including response-only attributes like `Path=`/`HttpOnly`), not RFC 6265-correct.**
**Disposition: ACCEPTED.**
**Change:** New `toRequestCookiePairs`/`cookieNameValuePair` helpers strip each cookie down to its `name=value` prefix before it's ever placed on an outgoing request, applied at both call sites (`getWithCookies`, `postLoginForm`). `mergeCookies` is unchanged — it still stores full `Set-Cookie` values internally for name-based de-duplication; stripping happens at the output boundary instead, so this one fix covers T20's original `attemptLogin` path too (now routed through the same `postLoginForm`), not just T22's new hops.

**5. V6 migration is a production change outside the frozen brief's test-only scope.**
**Disposition: ACCEPTED — already authorized.** This was flagged and explicitly approved by the human mid-Phase-6, before Kimi's review ran; recorded here for completeness, not a new decision. The self-review's `IF NOT EXISTS` recommendation (folded into disposition below) is the only actual code change this finding adds.

**6. V6 migration's column types match V1's translation convention.**
**Disposition: Confirmed, no action needed** (non-issue).

**7. Named test `shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled` implies recovery-code coverage the full-flow test doesn't provide.**
**Disposition: ACCEPTED — documentation only, no new test.** The frozen brief's disposition #7 already made this scope call deliberately (redundant with T20's `/login`-layer recovery-code test, three more HTTP hops per attempt for no new coverage) — re-litigating it wasn't asked for. What Kimi correctly caught is that the *comment* didn't say so explicitly enough. `confirmedMfaRequiresCodeToFinishAuthorizeFlow`'s Javadoc-style comment now states directly that it proves the TOTP branch only, with a pointer to exactly which T20 test covers the recovery-code branch and why it isn't duplicated here.

**8. `exchangeCodeForToken` doesn't check the HTTP status before parsing the response body.**
**Disposition: ACCEPTED** (self-review had set this aside as low-value; two independent reviews raising it plus a one-line fix changed that call). **Change:** an explicit status check now throws before any JSON parsing is attempted, with the response body included in the message.

**9. Success branch only checks the login redirect doesn't contain `/login?error`, not that it's a sensible success target.**
**Disposition: ACCEPTED IN SUBSTANCE, no additional code.** Finding #2's fix already covers the consequential half of this concern — any unexpected non-redirect or wrong-target response from the *re-issued* `/oauth2/authorize` now fails loudly and specifically. Adding a second assertion pinning the intermediate `postLoginLocation` to a specific value (e.g., `/`) was considered and declined: it would encode an assumption about SAS's default-target behavior that isn't load-bearing for anything this task needs to prove, and risks making the test brittle against a legitimate future change to that default.

**10. PKCE `redirect_uri` encoded via two different code paths (query-param builder vs. form encoder) could theoretically diverge.**
**Disposition: ACCEPTED, no action** — matches Kimi's own recommendation ("no code change required unless tests flake"). Both paths already read from the same `authClientsProperties.spa().redirectUris().getFirst()` source, so there's no risk of the two *values* diverging, only a theoretical encoding-mechanism difference that the passing tests already rule out in practice.

**11. No LOCKED-decision or `agents.md` conflicts.**
**Disposition: Confirmed, no action needed** (non-issue).

No findings rejected.

---

## Files changed this phase
- `authn/SasLoginIntegrationTest.java` — `FullFlowResult` gained `returnedState`; `attemptFullAuthorizeFlow` gained a redirect-status guard; two tests gained `state`-echo and `/login?error` assertions; `getWithCookies`/`postLoginForm` route cookies through new `toRequestCookiePairs`/`cookieNameValuePair` helpers; `exchangeCodeForToken` gained a status check; one comment strengthened for accuracy.
- `db/migration/V6__oauth2_authorization_device_and_user_code_columns.sql` — `ADD COLUMN` → `ADD COLUMN IF NOT EXISTS` on all 8 columns, matching `V5`'s established convention.

No production code outside the already-authorized V6 migration touched. No test renamed. No unrelated refactoring.
