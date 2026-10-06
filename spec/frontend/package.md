# Feature Spec: Frontend — Checky Pro PWA (all phases)

| Field | Value |
|---|---|
| Spec ID | `FRONTEND-ROADMAP` |
| Version | `0.1` |
| Author (senior/owner) | `<name>` |
| Implementer | `TBD` |
| Status | `DRAFT` (least-advanced phase governs; see per-phase status in §11) |
| Target repo / service | `frontend/` |
| Skills to load | `spec-authoring` (`references/frontend.md`), `code-review` |
| Standing rules | [`agents.md`](agents.md) in this directory is authoritative. This spec references it and does not restate or override it. |

## 0. TL;DR

A React and TypeScript mobile-first PWA, served from one origin behind the edge. The auth and account gate ships first,
because nothing else is reachable without a session. Phase 1 payment verification follows and is the first marketable
slice. Phases 2 to 5 add consumers and surfaces without rewriting the shell.

| Phase | Slice | Status |
|---|---|---|
| Phase 1a | Auth and account gatekeeper | DRAFT: blocked on Q1 (MFA endpoints), Q10 (confirm), Q12 (contract additions) |
| Phase 1b | Payment verification and invoicing | DRAFT: blocked on Q2 and Q3 |
| Phase 2 | Intelligence engine | DRAFT: blocked on Q4 |
| Phase 3 | AI-assisted dispute resolution | DRAFT: blocked on Q4 |
| Phase 4 | Reputation and trust | DRAFT: blocked on Q4 |
| Phase 5 | Fraud intelligence and institutional API | DRAFT: blocked on Q4 |

## 1. Context & why now

The backend auth service is the identity issuer for the whole product, and its UI does not yet exist. Stakeholders want to
see the product working. The first shippable thing is the auth gate, because every later phase depends on a secure session,
the MFA-first rule for privileged accounts, and enumeration-safe copy.

## 2. Scope

**In (by phase):**
- **Phase 1a**: registration and verification, OIDC login through the SAS-hosted flow, voluntary MFA enrollment and management,
  password reset and change, session list and revocation, merchant API keys, and role-scoped admin actions. R1–R30.
- **Phase 1b**: invoices, the payment state machine, receipts, wallet monitoring setup, in-app notifications, and history and
  exports. R31–R39.
- **Phases 2–5**: evidence, disputes, reputation, and fraud surfaces, as consumers of existing events. R40–R53. Each is
  specified to the Phase 1 standard but gated on its own contracts.
- **Cross-phase**: capability links (R54), which require an ADR before any build.

**Out:**
- Native mobile applications (React Native is not part of this package).
- A proprietary SPA login form. Confirmed by the author: the SAS-hosted flow is used at launch.
- Lockout timers and enumeration-revealing copy. Confirmed by the author: uniform copy only.
- First-login MFA enrollment inside the SPA. It runs in the SAS-hosted flow (L5).
- Backend requirements with no UI surface (for example, the internal rate-limit backstop backend R42, the cleanup job backend
  R40, and the audit mirror topics backend R44–R45). They belong to `spec/auth-service/`.
- Any backend code, contract, or endpoint. Those belong to `services/` and `contracts/`.

## 3. Requirements — acceptance criteria (EARS)

See [`requirements.md`](requirements.md). IDs R1–R54 are globally unique across all phases.

## 4. Design — how to build it

See [`design.md`](design.md). §4a locks, §4b open decisions, §4c verbatim contract paths.

## 5. Data model & schema changes

None on the backend. On-device state is defined in `design.md` §5. The SPA persists no tokens and no secrets.

## 6. Package & file map

See `design.md` §6.

## 7. Tasks — ordered execution plan

See [`tasks.md`](tasks.md). Phase 1a tasks come first, then Phase 1b, then Phases 2–5 in release order.

## 8. Test plan — named tests

Every requirement R1–R54 maps to at least one named test. Security properties from `agents.md` and the locks in `design.md`
have their own named tests.

**Phase 1a — Auth gate**
- `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints` → R1
- `registrationAcknowledgementIsIdenticalForExistingAndNewEmails` → R2
- `verifyFromLinkShowsSuccessState` → R3
- `verifyFailureShowsOneUniformMessage` → R4
- `resendVerificationAcknowledgementIsUniformAndNeedsNoSession` → R5
- `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField` → R6
- `callbackValidatesNonceAndKeepsAccessTokenInMemory` → R7
- `callbackWithMismatchedStateIsDiscarded` → R8
- `loginChallengeRunsInsideSasFlowAndResumesAtCallback` → R9
- `firstLoginPrivilegedEnrollmentRunsInsideSasBeforeAuthorizationCode` → R10
- `voluntaryEnrollmentShowsRecoveryCodesExactlyOnce` → R11
- `disableMfaRequiresPasswordAndCode` → R12
- `regenerateRecoveryCodesShowsOnce` → R13
- `passwordResetRequestAcknowledgementIsUniform` → R14
- `passwordResetRevokesSessionsAndRequiresSignInAgain` → R15
- `changePasswordRequiresCurrentPassword` → R16
- `breachRejectionShowsServerMessageWithoutClientLookup` → R17
- `sessionListShowsFallbackLabelRotationAndRevokeActions` → R18
- `signOutRevokesTokensClearsMemoryAndEndsSasSession` → R19
- `signOutThenSignInRequiresCredentials` → R19
- `profileIsReadFromUserinfoAndAccountStatusFromMe` → R20
- `parallel401sTriggerExactlyOneRenewal` → R21
- `reusedRefreshOrFailedRenewalTriggersCleanReauth` → R22
- `rateLimitedResponseShowsGenericCopyWithoutReplay` → R23
- `apiKeyIsShownExactlyOnceWithAcknowledgement` → R24
- `apiKeyListShowsOnlyContractFieldsAndNeverSecrets` → R25
- `apiKeyRevokeRequiresConfirmation` → R26
- `adminOperationsFollowRoleMatrix` → R27
- `unauthenticatedRoutesRedirectToSignIn` → R28
- `publicRouteSetIsExactlyTheDeclaredSet` → R28
- `expiredSessionMidTaskPreservesInputNotSecrets` → R29
- `identityErrorsIndistinguishableAcrossAccountStates` → R30

**Phase 1a — Security properties**
- `tokensAreNeverWrittenToLocalStorage` → R7
- `returnTargetOutsideAllowlistFallsBackToHome` → R7
- `stateSurvivesRedirectAndIsConsumedOnce` → R8
- `serviceWorkerNeverCachesApiResponses` → R7
- `sasNavigationBypassesServiceWorker` → R9
- `bundleContainsNoSecretEnvKeys` → R6

**Phase 1b — Payment verification**
- `invoiceListShowsStatesAndDecimalStringAmounts` → R31
- `stateMachineShowsReorgReversalsTruthfully` → R32
- `receiptNotShownBeforeAttested` → R33
- `receiptShowsHoldAfterAttestedReorg` → R34
- `notificationsReconnectWithoutDuplicates` → R35
- `merchantSeesOnlyOwnHistory` → R36
- `baseUnitAmountsScaledByTokenDecimalsWithoutNumber` → R37
- `walletMonitoringSetupShowsState` → R38
- `unknownTokenAndConfirmationCountShownTruthfully` → R39

**Phases 2–5** — the named tests for R40–R54 are authored when each phase's contracts close (Q4, Q11):
- `evidenceUploadShowsSafeFailures` → R40
- `analysisLabelsLowConfidence` → R41
- `txHashCollectsEvidenceAndMarksUploadNeeded` → R42
- `evidenceGraphMarksPartialAnalysis` → R43
- `claimStatusShownForEachClaim` → R44
- `narrativeKeepsAiTextDistinct` → R45
- `timelineReconstructsFromCreationToAcknowledgement` → R46
- `smartContractFailureMarkedObservedNotInferred` → R47
- `walletProfileShowsSignalsNotOnlyScore` → R48
- `trustPassportShowsSourceWindows` → R49
- `counterpartyGraphShowsClusterEvidence` → R50
- `fraudAlertDistinguishesConfirmedFromSuspected` → R51
- `crossChainRelationshipShowsLinkingEvidence` → R52
- `apiPortalKeysFollowPlaintextOnceRule` → R53
- `capabilityLinkUsesUnguessableIdNoPiiNoCache` → R54

## 9. Verification checklist — implementer self-checks before raising PR

Limited to what lint and CI will not enforce:
- [ ] No token, PIN, recovery code, API key, or PII reaches storage, logs, or analytics.
- [ ] Every identity error screen was compared side by side across unknown, missing, locked, suspended, and deleted accounts,
  and across bad, expired, and used tokens.
- [ ] Every backend call goes through `libs/ts/api-client`. A grep for hand-written `fetch` to backend routes returns nothing.
- [ ] The OIDC callback validates `state` and `nonce`, and redirect URIs are exact-match.
- [ ] Each new screen has been keyboard-tested and checked against the live-region rule.
- [ ] Amounts were checked for base-unit scaling (R37), not raw display.

## 10. Migration, rollout & rollback

- **Flag source**: a build-time flag for flows that exist in the bundle, and a remote configuration document fetched with
  NetworkOnly for runtime kill switches. The remote document is never cached.
- **Rollout**: the auth gate cannot target a cohort before identity is known. Phase 1a therefore ships to everyone once Phase 1a
  is `READY FOR IMPL`, behind a build-time flag used for staging. Phase 1b ships behind its own remote flag, so it can be
  switched off per cohort after sign-in.
- **Kill switch**: a remote flag disables a named flow (for example, API key creation) without a redeploy. Public routes are
  never disabled by a kill switch.
- **Service worker updates**: the update policy is prompt-to-reload. `skipWaiting` and `clients.claim` are not used for
  identity flows, so an open tab never receives a new auth bundle mid-flow.
- **Rollback**: rebuild the previous commit under a new service-worker revision, so clients install the rollback instead of
  the broken build. Reverting the artifact alone is not sufficient, because clients may already hold the newer worker.

## 11. Open questions for the author

Each item blocks the phase it names. A phase moves to `READY FOR IMPL` only when its own blockers are closed.

- Q1. **MFA contract and SAS MFA step (blocks R10–R13 and the Phase 1a MFA screens).** `accounts/me/mfa/totp` (enroll),
  `accounts/me/mfa/totp/confirm`, `DELETE accounts/me/mfa/totp`, and `accounts/me/mfa/recovery-codes` are specified in
  backend R22, R23, R28, and auth T19, but are not in `contracts/api/auth.yaml`. The controller (T19) and the SAS MFA step
  (T20) are not built. Owner action: confirm the delivery of auth T19 and T20, then paste the contract. No endpoint is invented.
- Q2. **Payments contract (blocks R31–R39).** `contracts/api/payments.yaml` does not exist. `spec/payment-service` already
  defines invoice endpoints (`POST/GET /api/v1/invoices`), an export endpoint, and the invoice states `OPEN`, `PAID`, `UNDERPAID`,
  `OVERPAID`, `EXPIRED`, `HELD`. Owner action: confirm the payment-service design as the source for the R31 state names, then
  author or supply `payments.yaml`. Until then the R31–R39 paths are PROPOSED.
- Q3. **Notifications contract (blocks R35).** `contracts/api/notifications.yaml` does not exist, and the SSE route and event shape
  are unspecified. Owner action: supply the endpoint and event shape.
- Q4. **Phase 2–5 APIs (block R40–R53).** No contracts exist for evidence, intelligence, disputes, reputation, fraud, or the
  institutional API. Owner action: supply each phase's surface when its backend spec is authored.
- Q5. **Refresh-token issuance to the public client (O1; blocks any refresh-family behaviour).** D-012 selects silent
  re-authorization through the SAS session. Whether SAS issues rotating refresh tokens to `checky-spa` is unconfirmed. Owner
  action: verify the SAS 1.5.1 behaviour against the running service. If refresh tokens are required, decide between a custom
  token generator and an ADR.
- Q6. **Environment key names (proposed in `design.md` §4c).** Confirm `VITE_OIDC_ISSUER`, `VITE_OIDC_CLIENT_ID`,
  `VITE_OIDC_REDIRECT_URI`, and `VITE_API_BASE_PATH`, or supply the names the platform already uses.
- Q7. **Design system (O2).** Choose a component approach. Blocks visual implementation, not the contracts.
- Q8. **i18n approach (O3).** Choose the catalogue library and the locale-fallback rule.
- Q9. **Analytics (O4).** Confirm none at launch, or name a vendor for privacy review.
- Q10. **Confirm L5 placement.** First-login enrollment is locked inside the SAS-hosted flow. Owner action: confirm this, since it
  depends on auth T20 delivering the SAS MFA step.
- Q11. **Capability links (R54) and public routes.** The payer invoice view and the shareable trust passport need an ADR that
  amends L14. Owner action: decide whether these ship, and in which phase.
- Q12. **Auth contract additions (blocks R5 consistency, R23, R24, R25).** Three points: (a) backend R6 requires a signed-in caller
  for resend-verification, but `auth.yaml` declares it public with `security: []`; (b) `Retry-After` and a 429 response are not
  documented; (c) the API-key prefix and the MFA-not-confirmed problem type are not in the contract. Owner action: reconcile (a) and
  add (b) and (c), or confirm the fixed-copy behaviour in R23 and R24 without them.

**Per-phase status**: Phase 1a is `DRAFT` (Q1, Q5, Q10, Q12). Phase 1b is `DRAFT` (Q2, Q3). Phases 2–5 are `DRAFT` (Q4). Phase 1a
becomes the first `READY FOR IMPL` candidate once its blockers close. Non-MFA screens can be split into an earlier slice only with
the author's explicit sign-off.
