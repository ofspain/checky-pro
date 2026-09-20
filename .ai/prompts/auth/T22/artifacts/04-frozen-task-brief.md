> **STATUS: FROZEN.** Approved 2026-08-09. Downstream phases (5 onward) treat this brief as non-renegotiable — any change to scope, files, or acceptance criteria from here on requires reopening this gate, not a silent deviation in a later phase.

# auth · T22 — Frozen Task Brief

## Governing correction (resolves Phase 3 #1, #11)

The TIB's described mechanism was wrong: "login succeeds, capture the session cookie, then call `/oauth2/authorize`" only works for a scenario that already succeeds — it can't produce the two failure proofs (R24, R25) at all, since a failed login never yields a usable session. The correct, standard OAuth2 shape — and the only one that actually exercises "cannot finish the authorize flow," not just "cannot finish `/login`" — is:

1. **Unauthenticated** `GET /oauth2/authorize` (with PKCE `code_challenge`, `state`, the SPA's `client_id`/`redirect_uri`, `scope=openid`) → SAS redirects to `/login`, saving the original request.
2. `POST /login` with credentials (and, when required, `mfaCode`), reusing this file's existing CSRF-scraping/cookie-propagation helpers.
3. **Failure** (R24, R25): redirects to `/login?error`. No authorization code exists anywhere. This *is* the proof — no further request needed.
4. **Success** (R26, positive control): `LoginSuccessHandler` (`SavedRequestAwareAuthenticationSuccessHandler`) redirects back to the original saved `/oauth2/authorize` request, which completes and redirects to the SPA's `redirect_uri` with `code=...&state=...` in the query string (`TestRestTemplate`'s redirect-following stays disabled, per this file's established pattern — the code is read straight off the `Location` header, never actually followed to `localhost:5173`, which nothing in the test is listening on).
5. `POST /oauth2/token`: `grant_type=authorization_code`, `code`, `redirect_uri` (must match step 1 byte-for-byte), `client_id=checky-spa`, `code_verifier` — no `Authorization` header, no client secret (public client, `ClientAuthenticationMethod.NONE`).
6. Parse the returned JWT's claims (`nimbus-jose-jwt:9.47:compile` — confirmed via `mvn dependency:tree -Dincludes=com.nimbusds`, a real resolved dependency, not assumed) for `amr`/`acr`. **Claims only, no signature verification** — fetching and verifying against live JWKS is out of scope; this test is about `TokenClaimsCustomizer`'s output, not JWKS/signing correctness (already covered elsewhere).

**Contingency, not the primary plan:** if `SavedRequestAwareAuthenticationSuccessHandler`'s redirect doesn't cleanly resume the original `/oauth2/authorize` request with all parameters intact (framework-internal behavior, unverified before today), the fallback is to re-issue `/oauth2/authorize` using the now-authenticated session cookie instead of relying on the saved-request replay. Try the standard mechanism first; only fall back if Phase 6 finds it doesn't work as expected — Docker being available now means this gets resolved empirically, not guessed at.

## Phase 3 findings — disposition

1. **Flow mechanism backwards for failure cases — ACCEPTED, resolved above.**
2. **`package.md` §8 numbering stale vs. `requirements.md` — ACCEPTED.** `requirements.md`'s numbering (R24/R25/R26, matching this task's own header) is authoritative — same resolution T20 already established for this exact recurring drift. Not re-litigated further.
3. **`token-claims.md` contract missing — ACCEPTED, scoped down.** R26's test asserts `amr`/`acr` only, not the full claim set L9/R48 describe — consistent with T20's identical reasoning (SAS endpoints fall outside `auth.yaml`'s scope; R48 activates only once the contract exists). Not a blocker; not re-litigated.
4. **`nimbus-jose-jwt` assumed, not verified — ACCEPTED, now verified properly.** `mvn -pl services/auth dependency:tree -Dincludes=com.nimbusds` confirms `nimbus-jose-jwt:9.47:compile` — a real, resolved dependency (my earlier check, presence in the local `~/.m2` cache, didn't actually prove this; Kimi was right to flag it).
5. **SPA redirect URI is a different origin than the test server — ACCEPTED, stated explicitly** in the governing mechanism above: redirects are never followed, the code is read from the `Location` header.
6. **Positive-control test unnamed — ACCEPTED.** Named explicitly below: `shouldIssueTokenWithPwdAmrThroughFullAuthorizeFlowWhenMfaNotRequired`.
7. **R26 TOTP-vs-recovery-code ambiguity — ACCEPTED, resolved.** The R26 named test uses TOTP (the task statement's literal "correct code," the common path). Recovery-code coverage through the full authorize flow is not duplicated here — it's already proven at the `/login` layer (T20's `merchantCanLoginWithAnUnusedRecoveryCodeButNotWithItASecondTime`), and re-proving it through three more HTTP hops adds runtime without adding coverage of anything R25/R26 don't already establish.
8. **`/oauth2/token` exchange shape unspecified — ACCEPTED, specified above** (step 5).
9. **Signature-verification scope unclear — ACCEPTED, resolved above:** claims-only parsing, no JWKS fetch.
10. **TOTP window-crossing risk in the longer full-flow round trip — ACCEPTED, noted.** Generate the code immediately before the `/login` POST (same discipline as `attemptLoginWithFreshTotpCode`), accept the small residual risk inherent to any live-clock TOTP test — if flakiness appears, it means re-running, not a product bug, and production code is never touched to widen tolerance.
11. **`SavedRequestAwareAuthenticationSuccessHandler` behavior unverified — ACCEPTED, folded into the governing mechanism's contingency plan above.**
12. **No LOCKED-decision conflicts — confirmed, noted, no action needed.**

No findings rejected.

---

## Task
Add Testcontainers integration tests proving R24, R25, and R26 through the real, fully-wired SAS interactive flow — `/oauth2/authorize` → `/login` → (resumed) `/oauth2/authorize` → `/oauth2/token` — closing the gap T20 and `auth-decisions.md` D-023 both deliberately left as unverified-without-running-it.

## Purpose
T20 verified R24/R25 at `/login` and R26 at the `TokenClaimsCustomizer` unit layer. Docker/Testcontainers now reliably works in this environment (confirmed this session). This task is where the previously-deferred full-flow verification actually happens.

## Scope

**In:**
- R24 full-flow: MERCHANT/ADMIN, no confirmed enrollment → `/oauth2/authorize` never yields a code.
- R25 full-flow: confirmed enrollment → password alone doesn't complete the flow; a valid TOTP code does.
- R26 full-flow: completed password+TOTP flow → the actually-issued JWT has `amr: ["pwd","otp"]`, `acr: urn:themistra:acr:otp`.
- Positive control: non-mandatory-role account, no enrollment, completes the full flow, `amr: ["pwd"]`.
- New test infrastructure in `SasLoginIntegrationTest.java`: unauthenticated-`/oauth2/authorize`-first flow helper, PKCE generation, JWT claim parsing.

**Out:**
- Any production code change.
- Consent-screen handling (disabled for this client).
- API-key/`client_credentials` flows.
- Re-diagnosing the six remaining pre-existing, already-documented test failures elsewhere in the suite.
- Recovery-code coverage through the full flow (already covered at `/login`, see disposition #7).
- JWT signature verification against live JWKS.

## Business Rules
- **R24.** MERCHANT/ADMIN, no confirmed TOTP enrollment → the authorize flow must not yield an authorization code.
- **R25.** Confirmed TOTP enrollment → a valid TOTP code or unused recovery code required before an authorization code is issued.
- **R26.** Password + TOTP login → issued access token has `amr: ["pwd","otp"]`, `acr: urn:themistra:acr:otp`.

## Locked Decisions
- **L10.** MFA mandatory for MERCHANT/ADMIN, optional for USER/COMPLIANCE — governs the R24 test's role choice.
- **L9** (consumed). Fixed claim set — this task inspects `amr`/`acr` only, doesn't re-verify the full set (contract file doesn't exist yet, disposition #3).

## Dependencies
- `authn/SasLoginIntegrationTest.java` — existing CSRF/cookie/seeding helpers, all confirmed working against a real server.
- `token/RegisteredClientSeeder`/`AuthClientsProperties` — SPA client id `checky-spa`, redirect URI `http://localhost:5173/auth/callback` (default), PKCE mandatory, consent disabled.
- `com.nimbusds:nimbus-jose-jwt:9.47` (confirmed real dependency) — JWT claim parsing.
- Standard JDK `MessageDigest`(SHA-256)/`Base64` — PKCE.

## Inputs
- Seeded accounts per scenario (via existing `AccountService`/`RoleService`/`MfaService` helpers).
- Generated PKCE `code_verifier`/`code_challenge` and `state` per flow attempt.

## Outputs
- Test methods asserting on real `/oauth2/authorize` redirects, real `/oauth2/token` responses, and (for R26/positive-control) real parsed JWT claims.

## State Changes
None — test-only.

## Files to Create
None.

## Files to Modify
- `authn/SasLoginIntegrationTest.java` — add the unauthenticated-first authorize-flow helper (steps 1–5 of the governing mechanism), PKCE generation, JWT claim parsing, and four test methods (R24, R25, R26, positive control).

## Files NOT to Modify
- Any production code.
- Any other test file.
- `spec/`.

## Acceptance Criteria
| ID | Criterion |
|---|---|
| R24 | MERCHANT/ADMIN, unconfirmed enrollment → authorize flow completes with no authorization code ever issued |
| R25 | Confirmed enrollment, password-only → no code issued; confirmed enrollment + valid TOTP → code issued |
| R26 | Completed password+TOTP flow's token has `amr: ["pwd","otp"]`, `acr: urn:themistra:acr:otp` |

## Required Tests
- `shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization` (R24, named)
- `shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled` (R25, named — TOTP branch only through the full flow, per disposition #7)
- `shouldIssueTokenWithOtpAmrAndAcrAfterMfa` (R26, named)
- `shouldIssueTokenWithPwdAmrThroughFullAuthorizeFlowWhenMfaNotRequired` (positive control, named per disposition #6)

## Constraints
- **Security:** no production security logic touched; test only observes real HTTP/JWT behavior. PKCE verifier/challenge are per-test-run, discarded.
- **Redirects:** `TestRestTemplate`'s redirect-following stays disabled throughout (matches this file's established pattern) — the authorization code is read from the `Location` header's query string, the SPA redirect target is never actually requested.
- **Timing:** TOTP code generated immediately before the `/login` POST; small residual live-clock risk accepted, not fixed by widening production tolerance.
- **Null handling:** JWT claim parsing fails loudly on a missing claim (no silent null/empty pass).
- **Test isolation:** matches this file's established pattern — real Postgres/Kafka, unique emails per test, no `@Transactional` rollback.

## Open Questions
No blockers. All 12 Phase 3 findings resolved above.
