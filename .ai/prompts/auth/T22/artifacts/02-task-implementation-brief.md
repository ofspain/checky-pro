# auth · T22 — Task Implementation Brief (TIB)

## Task
Add Testcontainers integration tests proving three MFA behaviors through the real, fully-wired SAS interactive flow: a MERCHANT/ADMIN account without confirmed TOTP enrollment cannot complete `/oauth2/authorize`; a confirmed enrollment requires a valid TOTP/recovery code to complete it; and a correct code produces an issued access token with `amr: ["pwd","otp"]`.

## Purpose
T20 verified R24/R25 at the `/login` layer and R26 at the `TokenClaimsCustomizer` unit layer, deliberately not chasing the full `/oauth2/authorize`→`/oauth2/token` round trip — the same category of test `auth-decisions.md` D-023 declined earlier as unverifiable without running it. Docker/Testcontainers now reliably works in this environment (confirmed this session, alongside fixing the `existsByEmail`/citext bug and its two cascading failures that had silently blocked `SasLoginIntegrationTest` this entire project). This task is where that deferred verification actually happens.

## Scope

**In:**
- Full-stack proof of R24: MERCHANT/ADMIN, no confirmed enrollment → `/oauth2/authorize` never yields an authorization code.
- Full-stack proof of R25: confirmed enrollment → password alone doesn't complete the flow; a valid TOTP or recovery code does.
- Full-stack proof of R26: a completed password+TOTP flow's issued JWT has `amr: ["pwd","otp"]`, `acr: urn:themistra:acr:otp`.
- Extending `SasLoginIntegrationTest.java` with a session-cookie-capturing mechanism so a successful `/login` can be followed by an authenticated `/oauth2/authorize` call, plus PKCE generation and JWT claim parsing helpers.

**Out:**
- Any production code change (test-only task, per its own statement).
- Consent-screen handling (SPA client has `requireAuthorizationConsent(false)` — confirmed, no consent step exists to test).
- API-key or service-client (`client_credentials`) grant flows — out of scope, unrelated to MFA.
- Re-diagnosing or fixing any of the six remaining pre-existing, already-documented test failures elsewhere in the suite (breach-check/audit-FK ordering bug, two Mockito strict-stubbing issues, one Kafka-timing issue) — confirmed unrelated to this task.
- R27/R29 — already fully covered by T20 at the layers appropriate to each; this task's header scopes only R24/R25/R26.

## Business Rules
- **R24.** MERCHANT/ADMIN, no confirmed TOTP enrollment → the interactive authorize flow must not yield an authorization code.
- **R25.** Confirmed TOTP enrollment → a valid TOTP code or unused recovery code is required before an authorization code is issued.
- **R26.** Password + TOTP login → issued access token has `amr: ["pwd","otp"]`, `acr: urn:themistra:acr:otp`.

## Locked Decisions
- **L10.** MFA mandatory for MERCHANT/ADMIN, optional for USER/COMPLIANCE — governs which role the R24 test must use.
- **L9** (consumed, not re-verified). Fixed claim set — this task inspects the issued token's `amr`/`acr`, doesn't re-litigate what else is in it.

## Dependencies
- `authn/SasLoginIntegrationTest.java` — existing `TestRestTemplate`/CSRF/cookie-handling infrastructure, `registerAndActivate`/`seedConfirmedTotpEnrollment`/`ensureRoleExists`/`referenceGenerateCode` helpers (all confirmed working against a real server this session).
- `token/RegisteredClientSeeder`/`AuthClientsProperties` — SPA client id (`checky-spa`), redirect URI (`http://localhost:5173/auth/callback` default), PKCE-required + consent-disabled configuration.
- `com.nimbusds:nimbus-jose-jwt` (already on the classpath) — parsing the issued JWT's claims.
- Standard JDK `MessageDigest`/`Base64` — PKCE `code_verifier`/`code_challenge` (S256).

## Inputs
- Seeded accounts (via `AccountService`, `RoleService`, `MfaService`) exercising each of the three scenarios.
- A generated PKCE code-verifier/challenge pair and `state` per flow attempt.

## Outputs
- Three new (or extended) test methods, each asserting on real HTTP responses (`/oauth2/authorize`'s redirect, `/oauth2/token`'s JSON body) and, for R26, the parsed JWT's claims — not mocked or unit-level assertions.

## State Changes
None — test-only, no production code or schema changes.

## Files to Create
None.

## Files to Modify
- `authn/SasLoginIntegrationTest.java` — add: (a) a session-cookie-capturing variant of the login step (the current `attemptLogin` discards `Set-Cookie` after building its return value); (b) a PKCE code-verifier/challenge generator; (c) an `/oauth2/authorize` call using the captured session cookie; (d) an `/oauth2/token` exchange call; (e) JWT claim parsing via `nimbus-jose-jwt`; (f) the three test methods themselves.

## Files NOT to Modify
- Any production code under `services/auth/src/main/java/` — this task is test-only by its own statement.
- Any other test file — this task's three scenarios all belong in `SasLoginIntegrationTest.java`, per Phase 1's file-involvement finding (same fixture helpers, same established pattern).
- `spec/` — never modified by any phase.

## Acceptance Criteria
| ID | Criterion |
|---|---|
| R24 | MERCHANT/ADMIN, unconfirmed enrollment → `/oauth2/authorize` completes without ever yielding an authorization code |
| R25 | Confirmed enrollment → password-only does not complete the flow; valid TOTP/recovery code does |
| R26 | Completed password+TOTP flow → the token obtained from `/oauth2/token` has `amr: ["pwd","otp"]`, `acr: urn:themistra:acr:otp` |

## Required Tests
- `shouldRequireMfaEnrollmentForMerchantAdminBeforeAuthorization` (R24, named test) — full-authorize-flow version.
- `shouldRequireValidTotpOrRecoveryCodeWhenMfaIsEnrolled` (R25, named test) — full-authorize-flow version.
- `shouldIssueTokenWithOtpAmrAndAcrAfterMfa` (R26, named test) — the genuinely new capability: an actually-issued JWT's claims, not a unit-level assertion on `TokenClaimsCustomizer`.
- Boundary, not separately named: a non-mandatory-role account with no enrollment completes the full flow successfully (`amr: ["pwd"]`) — a positive control matching this file's existing pattern of pairing every restrictive test with confirmation that ordinary accounts are unaffected.

## Constraints
- **Security:** none of this task's new code touches production security logic — it only observes real HTTP/JWT behavior. No new secrets or credentials introduced; PKCE verifier/challenge are per-test-run, discarded values.
- **Transactional:** none — test-only, no repository-level calls beyond what the existing seeding helpers already do correctly.
- **Thread-safety:** not applicable — no concurrent test scenarios in this task.
- **Module boundaries (L12):** not applicable — no production code touched.
- **Null handling:** the JWT-claim-parsing helper must fail loudly (not silently return null/empty) if a claim is missing, so a regression in `TokenClaimsCustomizer` fails the test clearly rather than passing on an absent assertion.
- **Test isolation:** follows this file's established pattern — real Postgres/Kafka via Testcontainers, unique emails per test, no `@Transactional` rollback (matches every other test in this class).

## Open Questions
No blockers. The two items Phase 1 flagged as "no longer open" (session-cookie name, exact `/oauth2/authorize` redirect shape for this client configuration) are implementation-time discoveries, not scope-affecting blockers — Docker now makes them directly observable rather than something to guess at before writing code.
