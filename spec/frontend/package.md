# Feature Spec: Frontend — Checky Pro PWA (all phases)

| Field | Value |
|---|---|
| Spec ID | `FRONTEND-ROADMAP` |
| Version | `0.1` |
| Author (senior/owner) | `<name>` |
| Implementer | `TBD` |
| Status | `DRAFT` (least-advanced phase governs; see per-phase status below) |
| Target repo / service | `frontend/` |
| Skills to load | `spec-authoring` (`references/frontend.md`), `code-review` |
| Standing rules | [`agents.md`](agents.md) in this directory is authoritative. This spec references it and does not restate or override it. |

## 0. TL;DR

A React and TypeScript mobile-first PWA, served from one origin behind the edge. The auth and account
gate ships first, because nothing else is reachable without a session. Phase 1 payment verification follows
and is the first marketable slice. Phases 2 to 5 add consumers and surfaces without rewriting the shell.

| Phase | Slice | Status |
|---|---|---|
| Phase 1a | Auth and account gatekeeper (register, verify, OIDC login, MFA, reset, sessions, API keys, admin) | DRAFT: blocked on Q1 for MFA enroll and disable; O1 open |
| Phase 1b | Payment verification and invoicing (invoices, state machine, receipts, notifications, history) | DRAFT: blocked on Q2 and Q3 |
| Phase 2 | Intelligence engine (evidence, integrity, graph) | DRAFT: blocked on Q4 |
| Phase 3 | AI-assisted dispute resolution | DRAFT: blocked on Q4 |
| Phase 4 | Reputation and trust | DRAFT: blocked on Q4 |
| Phase 5 | Fraud intelligence and institutional API | DRAFT: blocked on Q4 |

## 1. Context & why now

The backend auth service is the identity issuer for the whole product, and its UI does not yet exist. Stakeholders
want to see the product working. The first shippable thing is the auth gate, because every later phase depends on a
secure session, the MFA-first rule for privileged accounts, and enumeration-safe copy.

## 2. Scope

**In (by phase):**
- **Phase 1a**: registration and verification, OIDC login through the SAS-hosted flow (password and MFA steps),
  forced MFA enrollment for MERCHANT and ADMIN, password reset and change, session list and revocation, merchant API
  keys, and admin account actions. Requirements R1–R30.
- **Phase 1b**: invoices, the payment state machine display, receipts, in-app notifications, and tax history and
  exports. Requirements R31–R37.
- **Phases 2–5**: evidence, disputes, reputation, and fraud surfaces, as consumers of existing events. Requirements
  R38–R46. Each is specified to the same standard as Phase 1 but gated on its own contracts.

**Out:**
- Native mobile applications (React Native is not part of this package).
- A proprietary SPA login form. Confirmed by the author: the SAS-hosted flow is used at launch.
- Lockout timers and any enumeration-revealing copy. Confirmed by the author: uniform copy only.
- Any backend code, contract, or endpoint. Those belong to `services/` and `contracts/`.

## 3. Requirements — acceptance criteria (EARS)

See [`requirements.md`](requirements.md). IDs R1–R46 are globally unique across all phases.

## 4. Design — how to build it

See [`design.md`](design.md). §4a locks, §4b open decisions, §4c verbatim contract paths.

## 5. Data model & schema changes

None on the backend. On-device state is defined in `design.md` §5. The SPA persists no tokens and no secrets.

## 6. Package & file map

See `design.md` §6.

## 7. Tasks — ordered execution plan

See [`tasks.md`](tasks.md). Phase 1a tasks come first, then Phase 1b, then Phases 2–5 in release order.

## 8. Test plan — named tests

Each named test maps to the requirements it proves. Enumeration safety, token handling, and redirect validation are
tested explicitly, not by happy-path coverage alone.

**Phase 1a — Auth gate**
- `registrationAcknowledgementIsIdenticalForExistingAndNewEmails` → R2
- `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCharacters` → R1
- `verifyFromLinkShowsOneUniformFailureForInvalidExpiredAndUsedTokens` → R4, R15, R30
- `resendVerificationShowsSameAcknowledgementRegardlessOfOutcome` → R5
- `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField` → R6
- `callbackWithMismatchedStateIsDiscardedWithoutExchange` → R8
- `tokensAreNeverWrittenToLocalOrSessionStorage` → R7, R19
- `loginChallengeRunsInsideSasFlowAndResumesAtCallback` → R9
- `mfaEnrollmentWizardBlocksAuthorizationUntilConfirmed` → R10, R11 (blocked on Q1)
- `recoveryCodesAreShownOnceAndNotPersistedClientSide` → R11
- `disableMfaRequiresCurrentPasswordAndValidCode` → R12 (blocked on Q1)
- `passwordResetRequestIsUniformForAnyEmail` → R13
- `passwordResetRevokesSessionsAndRequiresSignInAgain` → R14
- `changePasswordRequiresCurrentPassword` → R16
- `sessionListShowsDeviceLabelAndRotationAndRevokeActions` → R18
- `signOutRevokesRefreshTokenAndClearsMemoryState` → R19
- `profileIsReadFromUserinfoNotFromAccessToken` → R20
- `expiredAccessTokenRefreshesOnceThenRoutesToCleanReauth` → R21
- `reusedRefreshTokenTriggersCleanReauthWithoutHalfSession` → R22
- `rateLimitedResponseShowsWaitingStateWithoutRevealingAccountState` → R23
- `apiKeyIsShownExactlyOnceWithAcknowledgement` → R24
- `apiKeyListNeverDisplaysSecretMaterial` → R25
- `apiKeyRevokeRequiresConfirmation` → R26
- `adminAreaIsHiddenFromNonAdminRoles` → R27
- `unauthenticatedRoutesRedirectToSignIn` → R28
- `expiredSessionMidTaskPreservesInputButNotSecrets` → R29
- `identityErrorsAreIndistinguishableAcrossAccountStates` → R30

**Phase 1b — Payment verification**
- `receiptIsNotShownBeforeFinalized` → R33
- `stateMachineShowsReorgReversalsTruthfully` → R32
- `notificationsReconnectWithoutDuplicates` → R35
- `merchantSeesOnlyOwnHistory` → R36
- `amountsAreNeverParsedToNumber` → R37

**Phases 2–5** — named tests are authored when each phase's contracts close (Q4).

## 9. Verification checklist — implementer self-checks before raising PR

Limited to what lint and CI will not enforce:
- [ ] No token, PIN, recovery code, API key, or PII reaches storage, logs, or analytics.
- [ ] Every identity error screen was compared side by side for unknown, locked, suspended, and missing accounts.
- [ ] Every backend call goes through `libs/ts/api-client`. Grep for hand-written `fetch` to backend routes returns nothing.
- [ ] The OIDC callback validates `state` and exact-match redirect URIs.
- [ ] Each new screen has been keyboard-tested and checked against the live-region rule.

## 10. Migration, rollout & rollback

- **Rollout**: ship Phase 1a behind a feature flag to a closed merchant cohort first. Phase 1b follows once Phase 1a is
  stable. Each later phase ships behind its own flag.
- **Service worker**: versioned cache names. A kill switch can disable a flow without a full redeploy. A forced
  update path handles a broken auth flow, since a stale worker can serve old auth code.
- **Rollback**: revert the release artifact and bump the service-worker version so clients pick up the revert.
  Tokens are in memory, so no client migration is needed.

## 11. Open questions for the author

Each item blocks the phase it names. A phase moves to `READY FOR IMPL` only when its own blockers are closed.

- Q1. **MFA management contract (blocks Phase 1a MFA screens: R10, R11, R12).** `accounts/me/mfa/totp` (enroll),
  `accounts/me/mfa/totp/confirm`, and `DELETE accounts/me/mfa/totp` are specified in the backend requirements
  (R22, R23, R28) but are not in `contracts/api/auth.yaml`. Owner action: paste the contract, or confirm the
  auth T16–T22 delivery date. Until then, the enrollment wizard and disable screens stay DRAFT. No endpoint is invented.
- Q2. **Payments contract (blocks Phase 1b: R31–R37).** `contracts/api/payments.yaml` does not exist. Owner action: supply
  the invoice, payment-state, receipt, and export endpoints, or the payment-service task that will author them.
- Q3. **Notifications contract (blocks R35).** `contracts/api/notifications.yaml` does not exist, and the SSE route and
  event payload are unspecified. Owner action: supply the SSE endpoint and its event shape.
- Q4. **Phase 2–5 APIs (block R38–R46).** No contracts exist for evidence, intelligence, disputes, reputation, or fraud.
  Owner action: supply each phase's surface when its backend spec is authored.
- Q5. **Token storage mechanism (O1; blocks the Phase 1a auth gate).** Choose in-memory access token with SAS-managed
  session renewal, or a backend-for-frontend with an HttpOnly refresh cookie. Recommendation in `design.md` O1.
- Q6. **Environment key names (proposed in `design.md` §4c).** Confirm `VITE_OIDC_ISSUER`, `VITE_OIDC_CLIENT_ID`,
  `VITE_OIDC_REDIRECT_URI`, and `VITE_API_BASE_PATH`, or supply the names the platform already uses.
- Q7. **Design system (O2).** Choose a component approach. Blocks visual implementation of every phase, not the
  contracts.
- Q8. **i18n approach (O3).** Choose the catalogue library and the locale-fallback rule.
- Q9. **Analytics (O4).** Confirm none at launch, or name a vendor for separate privacy review.
- Q10. **Forced MFA enrollment placement (O5; depends on Q1).** Inside the SAS step, or as an SPA route after callback.
  Cannot close until Q1 closes.

**Per-phase status**: Phase 1a is `DRAFT` (Q1, Q5, Q10). Phase 1b is `DRAFT` (Q2, Q3). Phases 2–5 are `DRAFT` (Q4).
The Phase 1a auth gate becomes the first `READY FOR IMPL` candidate once Q1, Q5, and Q10 close, with the non-MFA
screens eligible earlier if the author accepts splitting the MFA screens into a follow-on slice.
