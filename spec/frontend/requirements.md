# Requirements — Frontend (EARS)

IDs are globally unique across all five phases. Platform rules are in `agents.md` and are referenced,
never restated. Backend behaviour cited as "backend R#" is in `spec/auth-service/requirements.md`.

## Phase 1 — Auth & account (gatekeeper)

- R1. WHEN a visitor submits the registration form with a valid email and a password of 12 to 128
  characters, THEN the SPA SHALL submit it through the generated client to `POST /accounts` and SHALL show
  the registration acknowledgement screen. (backend R1)
- R2. WHEN the registration request returns the acknowledgement, THEN the SPA SHALL show the same
  "check your email" screen whether or not the email already exists, with identical copy, layout, and timing.
  (backend R2)
- R3. WHEN a visitor opens a verification link, THEN the SPA SHALL submit the token to
  `POST /accounts/verify-email` and SHALL show the success state on success. (backend R4)
- R4. IF the verification request fails for any reason (invalid, expired, used, or deleted/suspended), THEN
  the SPA SHALL show one uniform failure message with a neutral resend action. (backend R5)
- R5. WHEN a signed-in account is `PENDING_VERIFICATION` and the user requests a new link, THEN the SPA SHALL
  call `POST /accounts/resend-verification` and SHALL show the same acknowledgement regardless of outcome.
  (backend R6)
- R6. WHEN a visitor chooses sign-in, THEN the SPA SHALL start the OIDC authorization code flow with PKCE and
  SHALL NOT render a password field of its own. (backend R14; `agents.md` identity rules)
- R7. WHEN the SAS login returns with a valid `state` and an authorization code, THEN the SPA SHALL exchange
  the code at `/oauth2/token` with the PKCE verifier and SHALL hold the tokens in the mechanism chosen at
  `design.md` O1. (agents.md identity rules)
- R8. IF the callback `state` is missing or does not match, THEN the SPA SHALL discard the attempt, SHALL NOT
  complete the exchange, and SHALL return the visitor to the sign-in start with neutral copy.
- R9. WHEN the SAS login requires a second factor, THEN the challenge step (TOTP or recovery code) SHALL run
  inside the SAS-hosted flow, and the SPA SHALL resume at the callback once the flow completes. (backend R25)
- R10. WHEN a MERCHANT or ADMIN account has no confirmed TOTP enrollment, THEN the SPA SHALL run the in-app
  enrollment wizard before any authorization code is accepted, in the order: show the `otpauth://` URI and QR
  code, confirm with a live code, then show the recovery codes. Blocked on Q1. (backend R22, R23, R24)
- R11. WHEN the enrollment wizard confirms, THEN the SPA SHALL display the 10 recovery codes exactly once, SHALL
  require explicit acknowledgement before continuing, and SHALL NOT store them client-side afterwards. (backend R23)
- R12. WHEN a user with a confirmed TOTP enrollment asks to disable MFA, THEN the SPA SHALL require the current
  password and a valid TOTP code before calling the disable endpoint. Blocked on Q1. (backend R28)
- R13. WHEN a visitor requests a password reset, THEN the SPA SHALL show the same uniform acknowledgement for any
  email. (backend R12)
- R14. WHEN a visitor opens a reset link and submits a new password of 12 to 128 characters, THEN the SPA SHALL
  call `POST /accounts/password-reset` and SHALL, on success, direct the user to sign in again, since all sessions
  are revoked. (backend R14)
- R15. IF the reset token is invalid, expired, used, or belongs to a deleted or suspended account, THEN the SPA
  SHALL show the same uniform failure as R4. (backend R15)
- R16. WHEN a signed-in user changes their password, THEN the SPA SHALL require the current password and a new
  password that meets policy. (backend R11)
- R17. WHEN the new password is rejected as breached or too short or too long, THEN the SPA SHALL show policy
  guidance without revealing whether the password appears in a breach list beyond the server's uniform message.
  (backend R8, R9)
- R18. WHEN the SPA holds a valid session, THEN it SHALL show the session list from `GET /accounts/me/sessions`
  with device label, creation time, and last rotation, and SHALL offer revoke-one and revoke-all. (backend R36, R37, R38)
- R19. WHEN the user signs out, THEN the SPA SHALL call `/oauth2/revoke` for the refresh token and SHALL clear
  all in-memory token state. (backend R39)
- R20. WHEN the SPA needs profile data, THEN it SHALL read it from `/userinfo` and SHALL NOT parse PII from the
  access token. (backend R48, `token-claims.md`)
- R21. WHEN an access token expires mid-use, THEN the client boundary SHALL make one refresh attempt and, on
  failure, SHALL route to clean re-auth without displaying an error loop. (agents.md; backend R31 family)
- R22. IF a refresh token is detected as reused, THEN the SPA SHALL transition to clean re-auth, SHALL NOT show a
  half-authenticated screen, and SHALL show neutral copy. (backend R39; agents.md identity rules)
- R23. WHEN the server returns HTTP 429, THEN the SPA SHALL show a neutral waiting state for the interval given by
  `Retry-After`, SHALL NOT reveal account state, and SHALL resume automatically. (backend R41)
- R24. WHEN the user creates a merchant API key with MFA confirmed, THEN the SPA SHALL call `POST /api-keys` and
  SHALL show the `ck_live_`-prefixed plaintext key exactly once, with an explicit copy action and a
  "I have stored this" acknowledgement. (backend R30)
- R25. WHEN the SPA shows the API key list, THEN it SHALL show name, prefix, and metadata only and SHALL never
  show secret material. (backend R34)
- R26. WHEN a user revokes an API key, THEN the SPA SHALL require confirmation and SHALL call
  `DELETE /api-keys/{keyUuid}`. (backend R35)
- R27. WHEN an admin opens an account in the admin area, THEN the SPA SHALL offer activate, unlock, suspend,
  reinstate, and role actions only as defined by the admin contract, and SHALL hide the area from non-admin roles.
  (backend R7, R20; `auth.yaml` admin paths)
- R28. WHILE a visitor is unauthenticated, THEN the SPA SHALL serve only the public routes and SHALL redirect every
  other route to the sign-in start. (agents.md phase discipline)
- R29. IF the session expires while the user is mid-task, THEN the SPA SHALL preserve non-sensitive form input,
  SHALL re-authenticate, and SHALL NOT preserve tokens, passwords, or recovery codes in any storage.
- R30. WHEN any identity-related screen renders an error, THEN the copy, redirect target, and visual treatment
  SHALL be identical across unknown, locked, suspended, and missing accounts. (agents.md enumeration safety)

## Phase 1 — Payment verification & invoicing

- R31. WHEN a merchant creates an invoice, THEN the SPA SHALL show it in the invoice list and detail views with its
  amount as a decimal string and its state. Blocked on Q2.
- R32. WHEN a payment moves through the verification state machine, THEN the SPA SHALL show the current state
  truthfully, including reorg reversals (`CONFIRMING → SEEN`, `SEEN → WATCHING`) and `HELD`. Blocked on Q2.
- R33. IF a payment is not in `FINALIZED` or `ATTESTED`, THEN the SPA SHALL NOT display a receipt, and SHALL show
  the pending state instead. Blocked on Q2.
- R34. WHEN a payment reaches `FINALIZED` or `ATTESTED`, THEN the SPA SHALL show the tamper-proof receipt with its
  verifiable fields. Blocked on Q2.
- R35. WHEN an in-app notification arrives over SSE, THEN the SPA SHALL show it in the notification list; IF the
  connection drops, THEN the SPA SHALL show a reconnecting state and SHALL resume without duplicating items.
  Blocked on Q3.
- R36. WHEN a merchant requests tax-ready history or an export, THEN the SPA SHALL show only that merchant's own
  data and SHALL present amounts as decimal strings. Blocked on Q2.
- R37. WHERE any monetary value is displayed or entered, THEN the SPA SHALL NOT perform arithmetic on it using JS
  `Number`. (agents.md money rule)

## Phase 2 — Intelligence engine

- R38. WHEN a user uploads an evidence file, THEN the SPA SHALL show upload progress and SHALL surface failure
  reasons without exposing server internals. Blocked on Q4.
- R39. WHEN evidence has been analysed, THEN the SPA SHALL show extracted structured fields alongside the integrity
  assessment, and SHALL label low-confidence results as such. Blocked on Q4.
- R40. WHEN related evidence is correlated, THEN the SPA SHALL show the evidence graph and the unified transaction
  narrative, and SHALL mark partial analysis as partial. Blocked on Q4.

## Phase 3 — Dispute resolution

- R41. WHEN a dispute is opened, THEN the SPA SHALL show the claim, the claimant statement, and each evidence item
  with its status: supported, contradicted, or incomplete. Blocked on Q4.
- R42. WHEN a dispute narrative is shown, THEN the SPA SHALL present the timeline, verified facts, conflicting and
  supporting evidence, confidence, and recommended resolution, and SHALL keep AI-generated text visibly distinct
  from verified facts. Blocked on Q4.

## Phase 4 — Reputation & trust

- R43. WHEN a wallet reputation profile is viewed, THEN the SPA SHALL show the contributing signals, not only a
  score. Blocked on Q4.
- R44. WHEN a merchant Trust Passport is viewed, THEN the SPA SHALL show the metrics and their source windows.
  Blocked on Q4.

## Phase 5 — Fraud intelligence & institutional API

- R45. WHEN a fraud alert is shown, THEN the SPA SHALL show its classification and the evidence behind it, and SHALL
  distinguish confirmed from suspected. Blocked on Q4.
- R46. WHERE an institution uses the API portal, THEN the SPA SHALL present key management with the same
  plaintext-once rule as R24. Blocked on Q4.
