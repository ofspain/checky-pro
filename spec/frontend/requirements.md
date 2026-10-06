# Requirements — Frontend (EARS)

IDs are globally unique across all phases. Platform rules are in `agents.md` and referenced, never restated.
Backend behaviour cited as "backend R#" is in `spec/auth-service/requirements.md`. Items marked PROPOSED depend on
a contract that does not yet exist; they are blocked by the `Q#` shown.

## Phase 1 — Auth & account (gatekeeper)

- R1. WHEN a visitor submits the registration form, THEN the SPA SHALL check that the password is 12 to 128 Unicode
  code points as advisory guidance only, and SHALL submit to `POST /accounts` through the generated client; the server
  is the authority. (backend R1, R8)
- R2. WHEN the registration request returns, THEN the SPA SHALL show the same "check your email" screen whether or not
  the email already exists, with identical copy and layout, and SHALL NOT add client-side branching or delay. (backend R2)
- R3. WHEN a visitor opens a verification link and the token is valid, THEN the SPA SHALL show the verified success state.
  (backend R4)
- R4. IF the verification request fails for any reason, THEN the SPA SHALL show one uniform failure message with a neutral
  resend action. (backend R5)
- R5. WHEN a visitor enters an email on the resend-verification form, THEN the SPA SHALL call the public
  `POST /accounts/resend-verification` and SHALL show the same acknowledgement regardless of outcome. The form requires
  no signed-in session. (`auth.yaml` declares this operation with `security: []`. The divergence from backend R6, which
  requires a signed-in caller, is raised as Q12.)
- R6. WHEN a visitor chooses sign-in, THEN the SPA SHALL start the OIDC authorization code flow with PKCE and SHALL NOT
  render a password field. (backend R14; agents.md identity rules)
- R7. WHEN the SAS login returns with a matching `state`, THEN the SPA SHALL validate `nonce`, exchange the code at
  `/oauth2/token` with the PKCE verifier, and SHALL hold the access token in memory only (L3).
- R8. IF the callback `state` is missing or does not match, THEN the SPA SHALL discard the attempt, SHALL NOT complete the
  exchange, and SHALL return the visitor to the sign-in start with neutral copy.
- R9. WHEN the SAS login requires a second factor, THEN the TOTP or recovery-code challenge SHALL run inside the SAS-hosted
  flow, and the SPA SHALL resume at the callback once it completes. (backend R25)
- R10. WHEN a MERCHANT or ADMIN account has no confirmed TOTP enrollment at interactive login, THEN the SAS-hosted flow SHALL
  require enrollment before issuing an authorization code, so the SPA never handles first-login enrollment. Blocked on Q1 and
  Q10. (backend R24; agents.md MFA rule)
- R11. WHEN an authenticated user voluntarily enrolls in TOTP, THEN the SPA SHALL show the `otpauth://` URI and QR code,
  require a live code to confirm, and then show the 10 recovery codes exactly once with an explicit acknowledgement; the
  SPA SHALL NOT store them afterwards. Blocked on Q1. (backend R22, R23)
- R12. WHEN an authenticated user with confirmed TOTP asks to disable MFA, THEN the SPA SHALL require the current password and
  a valid TOTP code before calling the disable operation. Blocked on Q1. (backend R28)
- R13. WHEN an authenticated user asks to regenerate recovery codes, THEN the SPA SHALL show the new set exactly once, as in
  R11. Blocked on Q1. (backend T19)
- R14. WHEN a visitor submits any email to the password-reset request form, THEN the SPA SHALL call
  `POST /accounts/password-reset-request` and SHALL show the same uniform acknowledgement. (backend R12)
- R15. WHEN a visitor submits a reset token with a new password, THEN the SPA SHALL call `POST /accounts/password-reset`. On
  success it SHALL tell the user to sign in again, since all sessions are revoked. On any failure it SHALL show the uniform
  failure of R4. (backend R14, R15)
- R16. WHEN a signed-in user changes their password, THEN the SPA SHALL require the current password and a new password that
  meets policy. (backend R11)
- R17. WHEN the server rejects a new password for breach or policy, THEN the SPA SHALL display the server's message as
  returned. The SPA SHALL NOT perform a client-side breach lookup. (backend R8, R9)
- R18. WHEN a signed-in user views sessions, THEN the SPA SHALL list each family with device label, `createdAt`, and
  `rotatedAt`, showing a neutral fallback label whenever `deviceLabel` is null. It SHALL offer revoke-one and revoke-all.
  (backend R36, R37, R38; `auth.yaml` SessionResponse)
- R19. WHEN the user signs out, THEN the SPA SHALL call `/oauth2/revoke` for any token it holds, SHALL clear in-memory token
  state, and SHALL end the SAS session through the OIDC end-session endpoint with a `post_logout_redirect_uri` from the
  allowlist. (backend R39; agents.md sign-out rule)
- R20. WHEN the SPA needs profile data, THEN it SHALL read it from `/userinfo` (scopes `openid profile email`) and account
  status from `GET /accounts/me`, and SHALL NOT read PII or verification state from the access token. (backend R48;
  `token-claims.md`)
- R21. WHEN an API call returns 401, THEN the client boundary SHALL perform single-flight renewal once per tab, and on failure
  SHALL route to clean re-auth without an error loop. Parallel 401s SHALL share one renewal. (agents.md client boundary)
- R22. IF renewal fails or a superseded token is presented, THEN the SPA SHALL transition to clean re-auth, SHALL NOT show a
  half-authenticated screen, and SHALL show neutral copy. Sign-out SHALL propagate to every open tab. (backend R39)
- R23. WHEN an SPA-originated call returns HTTP 429, THEN the SPA SHALL show fixed, generic waiting copy with no countdown or
  interval, SHALL NOT replay non-idempotent POST requests automatically, and SHALL resume only on user action. Rate-limit
  responses from the SAS-hosted pages are out of SPA scope. The `Retry-After` header is not in `auth.yaml`; using it is
  PROPOSED and blocked on Q12. (backend R41)
- R24. WHEN a merchant creates an API key, THEN the SPA SHALL submit `POST /api-keys`. IF the server rejects the request because
  MFA is not confirmed, THEN the SPA SHALL show fixed copy directing enrollment. On success it SHALL show the `ck_live_` key
  exactly once with a copy action and an acknowledgement. The rejection problem type is PROPOSED pending Q1. (backend R30)
- R25. WHEN the SPA lists API keys, THEN it SHALL show `name`, `scopes`, `createdAt`, `lastUsedAt`, `expiresAt`, and `revokedAt`
  from `ApiKeyMetadata`, and SHALL never display secret material. A visible key prefix is PROPOSED pending Q1. (backend R34)
- R26. WHEN a user revokes an API key, THEN the SPA SHALL require confirmation and then call `DELETE /api-keys/{keyUuid}`.
  (backend R35)
- R27. WHEN an authenticated user holds the ADMIN role, THEN the SPA SHALL offer exactly these operations, each with its own named
  test: activate, unlock, suspend, reinstate, delete account, role assignment, role-template assignment, role and template
  management, and audit listing. WHEN a user holds the COMPLIANCE role, THEN the SPA SHALL offer only the audit listing and the
  account status view. Lookup SHALL be by `accountUuid`, because no search operation exists. Other roles SHALL NOT see the area.
  (`auth.yaml` admin operations and bearer roles)
- R28. WHILE a visitor is unauthenticated, THE SPA SHALL serve only the public routes and SHALL redirect any other route to
  the sign-in start. (agents.md; design L14)
- R29. IF the session expires during a task, THEN the SPA SHALL keep non-sensitive form input in sessionStorage for at most
  15 minutes, SHALL re-authenticate, and SHALL NOT keep tokens, passwords, recovery codes, or API keys in any storage.
- R30. WHEN an identity-related screen renders an error, THEN the copy, redirect target, and visual treatment SHALL be identical
  across unknown or missing, locked, suspended, and deleted accounts, and across bad, expired, and already-used tokens.
  (agents.md enumeration rule)

## Phase 1 — Payment verification & invoicing

- R31. WHEN a merchant views invoices, THEN the SPA SHALL show each invoice's state from `OPEN`, `PAID`, `UNDERPAID`,
  `OVERPAID`, `EXPIRED`, and `HELD`, with its amount as a decimal string, and SHALL show under- and overpayment discrepancies.
  The state names are PROPOSED from payment-service design and are blocked on Q2.
- R32. WHEN a payment moves through the verification state machine, THEN the SPA SHALL show the current state truthfully,
  including reorg reversals (`CONFIRMING → SEEN`, `SEEN → WATCHING`) and `HELD`. Blocked on Q2.
- R33. IF a payment has not reached `ATTESTED`, THEN the SPA SHALL NOT display a receipt. At `FINALIZED` it SHALL show
  "finalized, receipt pending". Blocked on Q2.
- R34. WHEN a payment reaches `ATTESTED`, THEN the SPA SHALL show the signed receipt and its verifiable fields. IF the
  payment is later placed in `HELD` after attestation, THEN the receipt view SHALL show the hold state alongside it. Blocked on Q2.
- R35. WHEN an in-app notification arrives over SSE, THEN the SPA SHALL show it in the list. IF the connection drops, THEN the
  SPA SHALL show a reconnecting state and SHALL resume without duplicates. Blocked on Q3.
- R36. WHEN a merchant requests history or an export, THEN the SPA SHALL show only that merchant's data and present every amount
  as a decimal string. Blocked on Q2.
- R37. WHERE a monetary value is displayed, THEN the SPA SHALL scale the token base unit by its token decimals using string or
  bigint shifting and SHALL NOT use JS `Number`. Blocked on Q2.
- R38. WHEN a merchant registers a wallet for monitoring, THEN the SPA SHALL show the wallet and its monitoring state. The
  operation is PROPOSED and blocked on Q2.
- R39. WHEN a payment is in an unknown-token or insufficient-confirmation condition, THEN the SPA SHALL show the condition
  and the confirmation count truthfully. The outcome name is PROPOSED and blocked on Q2.

## Phase 2 — Intelligence engine

- R40. WHEN a user uploads an evidence file, THEN the SPA SHALL show upload progress and surface failure reasons without server
  internals. Blocked on Q4.
- R41. WHEN evidence has been analysed, THEN the SPA SHALL show the extracted fields beside the integrity assessment, and SHALL
  label low-confidence results. Blocked on Q4.
- R42. WHEN a user enters a transaction hash, THEN the SPA SHALL show the automatically collected evidence and mark anything
  still needed as a user upload. Blocked on Q4.
- R43. WHEN related evidence is correlated, THEN the SPA SHALL show the evidence graph and narrative, and SHALL mark partial
  analysis as partial. Blocked on Q4.

## Phase 3 — Dispute resolution

- R44. WHEN a dispute is opened, THEN the SPA SHALL show each claim as supported, contradicted, or incomplete, with the
  evidence behind it. Blocked on Q4.
- R45. WHEN a dispute narrative is shown, THEN the SPA SHALL present the timeline, verified facts, conflicting and supporting
  evidence, confidence, and recommended resolution, and SHALL keep AI-generated text visibly distinct from verified facts.
  Blocked on Q4.
- R46. WHEN a transaction timeline is shown, THEN the SPA SHALL reconstruct it from creation through confirmations and
  merchant acknowledgement. Blocked on Q4.
- R47. WHEN a dispute involves a smart contract, THEN the SPA SHALL show the contract execution evidence and mark any failure
  as observed, not inferred. Blocked on Q4.

## Phase 4 — Reputation & trust

- R48. WHEN a wallet reputation profile is viewed, THEN the SPA SHALL show the contributing signals, not only a score. Blocked on Q4.
- R49. WHEN a merchant Trust Passport is viewed, THEN the SPA SHALL show each metric with its source window. Blocked on Q4.
- R50. WHEN the counterparty behaviour graph is viewed, THEN the SPA SHALL show relationships and cluster evidence. Blocked on Q4.

## Phase 5 — Fraud intelligence & institutional API

- R51. WHEN a fraud alert is shown, THEN the SPA SHALL show its classification and evidence, and SHALL distinguish confirmed from
  suspected. Blocked on Q4.
- R52. WHEN a cross-chain relationship is shown, THEN the SPA SHALL present the chains involved and the evidence linking them.
  Blocked on Q4.
- R53. WHERE an institution uses the API portal, THEN the SPA SHALL apply the plaintext-once rule of R24 to portal keys. Blocked on Q4.

## Cross-phase — Public capability links

- R54. WHERE a payer or the public is granted a capability link (a payer invoice view or a shareable trust passport), THEN the
  SPA SHALL serve it only through an unguessable identifier, SHALL expose no PII, and SHALL NOT cache it. Requires an ADR
  amending L14 and is blocked on Q11.
