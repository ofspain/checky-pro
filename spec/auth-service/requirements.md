# 3. Requirements — acceptance criteria (EARS)

Each requirement is independently testable and maps to a named test in `spec.md` §8.

## Account lifecycle & email verification

- R1. WHEN a `POST /accounts` request contains a valid email and a password that meets the platform policy, THEN the system SHALL create an account in `PENDING_VERIFICATION` and return a `202 Accepted` registration acknowledgement.
- R2. IF the normalized email already exists at `POST /accounts`, THEN the system SHALL return the identical `202 Accepted` registration acknowledgement as for a newly created account.
- R3. WHEN `AccountService.register` succeeds, THEN the system SHALL emit an `auth.email.requested` outbox event with purpose `verify_email` in the same transaction.
- R4. WHEN a caller submits a valid, unused verification token to `POST /accounts/verify-email` within the configured TTL, THEN the system SHALL transition the account to `ACTIVE`, emit an `auth.user.registered` event, and return success.
- R5. IF a verification token is invalid, expired, already used, or belongs to a deleted/suspended account, THEN the system SHALL return a uniform failure response that reveals no information about account existence or state.
- R6. WHEN an authenticated caller whose account is in `PENDING_VERIFICATION` requests `POST /accounts/resend-verification`, THEN the system SHALL generate a new verification token, emit an `auth.email.requested` event, and return an acknowledgement.
- R7. WHEN an admin calls `POST /admin/accounts/{accountUuid}/activate`, THEN the system SHALL transition the account to `ACTIVE` and record an `account.activated` audit event with the acting admin.

## Password policy & credentials

- R8. WHEN a password is set or changed, THEN the system SHALL reject it if it is shorter than 12 characters or longer than 128 characters.
- R9. WHEN a password is set or changed, THEN the system SHALL query the Have I Been Pwned k-anonymity range API with the first 5 characters of the password's uppercase SHA-1 hash; IF the trailing hash suffix appears in the range with a count greater than zero, THEN the system SHALL reject the password.
- R10. IF the breached-password range API is unreachable, THEN the system SHALL allow the password change and record a `password.breach_check_failed` audit event.
- R11. WHEN an authenticated caller submits their current password and a new password meeting policy to `POST /accounts/me/password`, THEN the system SHALL update the password hash.
- R12. WHEN a caller submits any email address to `POST /accounts/password-reset-request`, THEN the system SHALL return the same uniform acknowledgement regardless of whether the account exists.
- R13. WHEN the submitted email exists and belongs to an active or locked (not deleted/suspended) account, THEN the system SHALL emit an `auth.email.requested` outbox event with purpose `password_reset` in the same transaction.
- R14. WHEN a caller submits a valid, unused password-reset token and a policy-compliant new password to `POST /accounts/password-reset` within the configured TTL, THEN the system SHALL update the password hash, revoke all refresh-token families for that account, and record a `password.reset` audit event.
- R15. IF a password-reset token is invalid, expired, already used, or belongs to a deleted/suspended account, THEN the system SHALL return a uniform failure response indistinguishable from a valid token.

## Login, lockout, and unlock

- R16. IF a password login attempt fails for an `ACTIVE` account, THEN the system SHALL increment the per-account failed-attempt counter and record a `login.failed` audit event.
- R17. WHEN the failed-attempt counter reaches 5 failed attempts within a rolling 30-minute window, THEN the system SHALL transition the account to `LOCKED` for 15 minutes, increment `lock_count`, and record an `account.locked` audit event.
- R18. WHEN a locked account's lockout interval has elapsed, THEN the system SHALL allow the next authentication attempt; IF it succeeds, THEN the system SHALL transition the account to `ACTIVE` and reset the failed-attempt counter and `lock_count`.
- R19. IF the failed-attempt counter does not reach 5 within 30 minutes of the last failure, THEN the counter SHALL decay to zero.
- R20. WHEN an admin calls `POST /admin/accounts/{accountUuid}/unlock`, THEN the system SHALL transition the account to `ACTIVE` and clear the failed-attempt counter and `lock_count`.
- R21. IF an account is `LOCKED`, `SUSPENDED`, `DELETED`, or does not exist, THEN password authentication SHALL fail with a response that is indistinguishable from bad credentials and SHALL reveal no account state.

## TOTP MFA

- R22. WHEN an authenticated user without a confirmed TOTP enrollment calls `POST /accounts/me/mfa/totp`, THEN the system SHALL generate a random TOTP secret, encrypt it, persist it as unconfirmed, and return an `otpauth://` provisioning URI.
- R23. WHEN the user submits the correct first TOTP code to `POST /accounts/me/mfa/totp/confirm`, THEN the system SHALL confirm the enrollment, generate 10 single-use recovery codes, store only hashes, and return the recovery codes exactly once.
- R24. IF a user holds the `MERCHANT` or `ADMIN` role and has no confirmed TOTP enrollment at the time of interactive login, THEN the system SHALL require them to complete TOTP enrollment before issuing an authorization code.
- R25. IF a user has a confirmed TOTP enrollment, THEN the system SHALL require a valid TOTP code or an unused recovery code during the SAS interactive authentication flow before issuing an authorization code.
- R26. WHEN an interactive login completes with password + TOTP, THEN the issued access token SHALL contain `amr: ["pwd","otp"]` and `acr: urn:themistra:acr:otp`.
- R27. WHEN an interactive login completes with password only and MFA is not required for that account, THEN the access token SHALL contain `amr: ["pwd"]` and `acr: urn:themistra:acr:pwd`.
- R28. WHEN an authenticated user supplies their current password and a valid TOTP code to `DELETE /accounts/me/mfa/totp`, THEN the system SHALL remove the enrollment, invalidate all recovery codes, and record an `mfa.disabled` audit event.
- R29. IF a TOTP code or recovery code verification fails, THEN the system SHALL record an `mfa.failed` audit event and deny authentication.

## Merchant API keys

- R30. WHEN an authenticated user with the `MERCHANT` role and confirmed MFA calls `POST /api-keys` with a name, THEN the system SHALL create an API key with prefix `ck_live_`, store a SHA-256 hash of the full key, return the plaintext key exactly once, and record an `api_key.created` audit event.
- R31. WHEN a caller presents a valid, non-expired, non-revoked API key in the `Authorization` header to `POST /api-keys/token`, THEN the system SHALL issue a 10-minute JWT whose `sub` is the merchant account UUID, whose `scope` contains `merchant.api`, and whose `amr` contains `api_key`.
- R32. WHEN a valid API key is presented for exchange, THEN the system SHALL update the key's `last_used_at` timestamp.
- R33. IF an API key is revoked, expired, malformed, or the presented hash does not match the stored hash, THEN the key exchange SHALL return a uniform `401 Unauthorized`.
- R34. WHEN an authenticated user calls `GET /api-keys`, THEN the system SHALL return their keys with metadata but no secret material.
- R35. WHEN an authenticated user calls `DELETE /api-keys/{keyUuid}`, THEN the system SHALL revoke the key and record an `api_key.revoked` audit event.

## Sessions & token lifecycle

- R36. WHEN an authenticated user calls `GET /accounts/me/sessions`, THEN the system SHALL return the active refresh-token families with device label, creation time, and last-rotation time.
- R37. WHEN an authenticated user calls `DELETE /accounts/me/sessions/{familyId}`, THEN the system SHALL revoke the family and remove the live SAS authorization.
- R38. WHEN an authenticated user calls `DELETE /accounts/me/sessions` (no `familyId`), THEN the system SHALL revoke all refresh-token families for the caller and remove the live SAS authorizations.
- R39. WHEN the SAS `/oauth2/revoke` endpoint receives a valid refresh token, THEN the decorated `ReuseDetectingAuthorizationService` SHALL also revoke the associated family.
- R40. WHEN a scheduled cleanup job runs, THEN the system SHALL hard-delete verification tokens whose `expires_at` has passed, refresh-token families/archives older than the configured retention, and stale ShedLock rows.

## Rate limiting

- R41. WHEN per-account request rates on login, `/oauth2/token`, password-reset confirmation, or MFA verification exceed the configured thresholds, THEN the service SHALL reject the request with `429 Too Many Requests`.
- R42. WHERE the ingress edge provides IP-level rate limiting, THEN the service-level limits SHALL act as a per-account backstop rather than the primary defense.

## Audit, events, and contracts

- R43. WHEN any security-relevant action occurs (login success/failure, lock, unlock, MFA events, password/key changes, token reuse, API-key operations), THEN the system SHALL append an `auth_audit` row and mirror a reduced event to `auth.security.audit` via the outbox.
- R44. WHEN an `auth.email.requested` event is emitted, THEN `EventTopics` SHALL route it to the `auth.email.requested` Kafka topic.
- R45. WHEN an `auth.security.audit` mirror event is emitted, THEN `EventTopics` SHALL route it to the `auth.security.audit` Kafka topic.
- R46. WHEN the service returns a `4xx` error, THEN the response SHALL be an RFC 9457 `application/problem+json` body with no stack traces, no internal details, and no hints about account or key existence.
- R47. WHERE `contracts/api/auth.yaml` is authored, THEN the service responses and generated client models SHALL conform to it.
- R48. WHERE `contracts/api/token-claims.md` is authored, THEN every access token SHALL contain exactly the claims listed in it and no PII beyond `email_verified`.
