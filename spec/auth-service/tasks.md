# 7. Tasks — ordered execution plan

Execute in order. Each task should leave the module buildable and the test suite green.

## Foundation

1. **Schema V5.** Add `V5__lockout_cleanup_and_shedlock.sql` with the ShedLock table and lockout index. Run `mvn -pl services/auth flyway:migrate` against the local Docker Compose Postgres.
2. **Resolve Q1 (TOTP encryption).** Get an author decision on seed encryption, update this spec and `auth-decisions.md` with the chosen approach before writing `mfa/` code.
3. **Password policy domain + config.** Add `PasswordPolicyProperties` and `PasswordPolicy`. Implement length validation and the Have I Been Pwned range check using `RestClient`. Add unit tests.
4. **Breach-check audit event.** Wire `AuditService.record(...)` for `password.breach_check_failed` and unit-test the fail-open path.
5. **Verification token service.** Add `VerificationToken`, repository, and service. Implement issue, verify, consume, and TTL checks. Unit-test with a fixed `Clock`.

## Account module extensions

6. **Self-service verification endpoints.** Extend `AccountController` with `POST /accounts/verify-email` and `POST /accounts/resend-verification`. Emit `auth.email.requested` via `OutboxPublisher`. Update `EventTopics` with the `verification-token` aggregate mapping.
7. **Password reset flow.** Add `POST /accounts/password-reset-request` and `POST /accounts/password-reset`. Ensure uniform responses. On valid reset, update password and revoke all refresh-token families for the account.
8. **Change own password.** Add `POST /accounts/me/password`, protected by current password.
9. **Password policy enforcement.** Apply `PasswordPolicy` to registration, change-password, and password-reset. Update `AccountControllerTest` / `AccountServiceTest` accordingly.
10. **Enumeration safety tests.** Add tests that duplicate registration, invalid verification tokens, and invalid reset tokens produce identical responses.

## Lockout and authentication

11. **Lockout state machine.** Implement `LockoutStateMachine` with the 5-attempt / 30-min / 15-min rules. Unit-test boundaries.
12. **Lockout service.** Add `LockoutService` that loads/updates `lockout_state`, handles decay, and ties `Account.lock()` / `unlock()` to `AccountService`.
13. **Login failure/success tracking.** Integrate lockout counter increment into the SAS authentication failure path and reset-on-success into the success path. Record `login.failed` audit events.
14. **Admin unlock endpoint.** Add `POST /admin/accounts/{id}/unlock` (ADMIN or COMPLIANCE), transition `LOCKED → ACTIVE`, clear counters, and audit.
15. **Indistinguishable login response test.** Add a security test asserting that locked, suspended, deleted, and non-existent accounts all return the same body/status on password failure.

## MFA (after Q1 is resolved)

16. **TOTP seed handling.** Implement `TotpGenerator` (random secret, `otpauth://` URI) and the selected encryption primitive from Q1. If the chosen approach is local AES-GCM with an injected key, add `MfaSeedEncryption` + config; if KMS/Crypto Service, add the client and error handling.
17. **MfaEnrollment entity/repository.** Map the existing `mfa_enrollments` and `recovery_codes` tables. Enforce one confirmed enrollment per account.
18. **MFA service.** Implement begin-enroll, confirm, disable, and recovery-code generation/verification. Store only hashes of recovery codes.
19. **MFA controller.** Add `POST /accounts/me/mfa/totp`, `POST /accounts/me/mfa/totp/confirm`, `DELETE /accounts/me/mfa/totp`, and `POST /accounts/me/mfa/recovery-codes`. Require authentication; disable requires current password + TOTP.
20. **SAS MFA step integration.** Customize the SAS interactive authentication chain so that after password success the user is challenged for TOTP/recovery code before the authorization code is issued. Enforce mandatory MFA for `MERCHANT`/`ADMIN` and skip when not required.
21. **Token claim updates.** Update `TokenClaimsCustomizer` to emit `amr`/`acr` correctly for pwd-only, pwd+otp, and api-key grants.
22. **MFA integration tests.** Add Testcontainers tests: merchant without MFA cannot finish authorize flow; confirmed MFA requires code; correct code produces `amr: [pwd, otp]`.

## API keys

23. **ApiKey entity/repository.** Map the existing `api_keys` table. Add lookup-by-prefix method.
24. **Key service.** Implement create (require `MERCHANT` + confirmed MFA), list, revoke, constant-time hash compare, and exchange. Generate `ck_live_<suffix>.<secret>`.
25. **API-key exchange endpoint.** Add `POST /api-keys/token` (public). Validate the key, update `last_used_at`, issue a JWT via `ApiKeyTokenIssuer`, and audit.
26. **API-key CRUD controller.** Add `POST /api-keys`, `GET /api-keys`, `DELETE /api-keys/{keyUuid}`. Ensure responses never include the secret.
27. **API-key integration tests.** Test create→exchange→revoke→exchange-fails flow with Testcontainers.

## Sessions and cleanup

28. **Session listing/revocation.** Add `GET /accounts/me/sessions` and `DELETE /accounts/me/sessions/{familyId}` / `DELETE /accounts/me/sessions`. Query `refresh_token_family`; on revoke, remove the live SAS authorization via `OAuth2AuthorizationService`.
29. **SAS revoke integration.** Ensure `ReuseDetectingAuthorizationService` revokes the family when `/oauth2/revoke` is called with a refresh token.
30. **Scheduled cleanup job.** Add a ShedLock-annotated job that deletes expired verification tokens, old revoked families/archives, and stale ShedLock rows. Integration-test with Awaitility.

## Rate limiting, contracts, and hardening

31. **Rate limiting.** Implement per-account buckets for the paths in R41. Add `mvn` dependency if a library is chosen. Add 429 tests.
32. **Public endpoint sweep.** Update `ArchitectureTest` to assert that `/api-keys/token` is in the public list and that no new handler is permitAll outside the list.
33. **Contract files.** Author `contracts/api/auth.yaml` for all non-SAS endpoints and `contracts/events/auth/email-requested.v1.schema.json` / `security-audit.v1.schema.json`. Add contract tests using the existing `UserLifecycleEventPayloadContractTest` pattern.
34. **Token claims doc.** Write `contracts/api/token-claims.md` documenting the exact access-token claim set from L9.
35. **ArchUnit / module-boundary tests.** Ensure new modules do not import account entities and that controllers depend only on their module services.

## Final verification

36. **End-to-end integration test.** Using Testcontainers Postgres+Kafka, execute: register → verify email → login (password) → admin assigns MERCHANT → next login requires MFA enrollment → enroll TOTP → login with TOTP → create API key → exchange key for JWT → call session list → revoke session.
37. **Run full test suite.** `mvn -pl services/auth verify` must pass. Docker image must build from repo root.
38. **Review against gap analysis defect catalogue.** Verify plaintext credentials, unauthenticated admin routes, shared model artifact, `Long.getLong` config misread, and `allow-circular-references` classes of error are absent.
39. **Update `auth-decisions.md`.** Record decisions made while implementing (especially the resolution of Q1 and any O2–O5 choices).
40. **Bump spec status.** Once §11 questions are closed and tests pass, change this spec from `DRAFT` to `READY FOR IMPL` and version to `0.2`.
