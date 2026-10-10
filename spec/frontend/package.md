# Feature Spec: Frontend — Checky Pro PWA (all phases)

| Field | Value |
|---|---|
| Spec ID | `FRONTEND-ROADMAP` |
| Version | `0.2` |
| Author (senior/owner) | `<name>` |
| Implementer | `TBD` |
| Status | `DRAFT` (least-advanced phase governs; per-phase status in §11). **Phase 1a itself is spec-complete** — every one
of its own open questions is resolved; execution remains deliberately deferred (`agents.md`'s own process rule, pending
`spec/payment-service`), not blocked on anything left undecided. |
| Target repo / service | `frontend/` |
| Skills to load | `spec-authoring` (`references/frontend.md`), `code-review` |
| Standing rules | [`agents.md`](agents.md) in this directory is authoritative. This spec references it and does not restate or override it. |

## 0. TL;DR

A React and TypeScript mobile-first PWA, served from one origin behind the edge, with all SPA routes under `/app`. The auth and
account gate ships first. Phase 1 payment verification follows and is the first marketable slice. Phases 2 to 5 add consumers and
surfaces without rewriting the shell.

| Phase | Slice | Status |
|---|---|---|
| Phase 1a | Auth and account gatekeeper | **SPEC COMPLETE** (2026-10-10). Every own open question (Q1, Q5, Q6, Q10, Q12, Q13, Q14, Q15) resolved. Execution is still deliberately deferred per `agents.md`'s own process rule, pending `spec/payment-service`. |
| Phase 1b | Payment verification and invoicing | DRAFT. Q3 (notification stream) resolved 2026-10-08. Blocked on Q2. |
| Phase 2 | Intelligence engine | DRAFT. Blocked on Q4. |
| Phase 3 | AI-assisted dispute resolution | DRAFT. Blocked on Q4. |
| Phase 4 | Reputation and trust | DRAFT. Blocked on Q4. |
| Phase 5 | Fraud intelligence and institutional API | DRAFT. Blocked on Q4. |

## 1. Context & why now

The backend auth service is the identity issuer for the whole product, and its UI does not yet exist. Stakeholders want to see the
product working. The auth gate is the first shippable slice, because every later phase depends on a secure session, the MFA-first
rule for privileged accounts, and enumeration-safe copy.

## 2. Scope

**In (by phase):**
- **Phase 1a**: registration and verification, OIDC login through the SAS-hosted flow, voluntary MFA enrollment and management,
  password reset and change, session list and revocation, merchant API keys, and role-matrix admin actions. R1–R30.
- **Phase 1b**: invoices, the payment state machine, receipts, wallet monitoring setup, address-poisoning warnings, in-app
  notifications, and history and exports. R31–R39, R55, R56.
- **Phases 2–5**: evidence, disputes, reputation, and fraud surfaces. R40–R53. Each is specified to the Phase 1 standard and gated
  on its own contracts.
- **Platform properties**: service-worker boundaries (R57), bundle secret rule (R58), and the notification stream (R59).
- **Cross-phase**: capability links (R54), which need an ADR before any build.

**Out:**
- Native mobile applications.
- A proprietary SPA login form. Confirmed by the author: login is SAS-hosted at launch.
- Lockout timers and enumeration-revealing copy. Confirmed by the author: uniform copy only.
- First-login enrollment inside the SPA. The SAS flow refuses unenrolled privileged accounts (L5). How they enroll is
  resolved (Q13, 2026-10-08): the normal USER-level self-service wizard, before promotion — not a separate first-login flow.
- Backend requirements with no UI surface: the per-account rate-limit backstop (backend R42), the cleanup job (backend R40), and
  the audit mirror topics (backend R44, R45). They belong to `spec/auth-service/`.
- Any backend code, contract, or endpoint.

## 3. Requirements — acceptance criteria (EARS)

See [`requirements.md`](requirements.md). IDs R1–R59 are globally unique.

## 4. Design — how to build it

See [`design.md`](design.md). §4a locks, §4b open decisions, §4c verbatim routes and contract paths.

## 5. Data model & schema changes

None on the backend. On-device state is defined in `design.md` §5.

## 6. Package & file map

See `design.md` §6.

## 7. Tasks — ordered execution plan

See [`tasks.md`](tasks.md).

## 8. Test plan — named tests

Every requirement R1–R59 maps to at least one named test. Security properties from `agents.md` and the locks in `design.md` have
their own named tests.

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
- `sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy` → R10
- `voluntaryEnrollmentShowsRecoveryCodesExactlyOnce` → R11
- `disableMfaRequiresPasswordAndCode` → R12
- `regenerateRecoveryCodesShowsOnce` → R13
- `passwordResetRequestAcknowledgementIsUniform` → R14
- `passwordResetSuccessSaysSignInOnThisDeviceOnly` → R15
- `changePasswordRequiresCurrentPassword` → R16
- `breachRejectionShowsServerMessageWithoutClientLookup` → R17
- `sessionListShowsFallbackLabelRotationAndRevokeActions` → R18
- `signOutRevokesTokensClearsMemoryAndEndsSasSession` → R19
- `signOutThenSignInRequiresCredentials` → R19
- `profileIsReadFromUserinfoAndAccountStatusFromMe` → R20
- `parallel401sTriggerExactlyOneRenewal` → R21
- `siblingTabsShareOneRenewal` → R21
- `failedRenewalTriggersCleanReauthWithoutHalfSession` → R22
- `signOutPropagatesToAllTabs` → R22
- `rateLimitedResponseShowsGenericCopyWithoutReplay` → R23
- `apiKeyCreationRejectedWithoutMfaShowsFixedCopy` → R24
- `apiKeyIsShownExactlyOnceWithAcknowledgement` → R24
- `apiKeyListShowsOnlyContractFieldsAndNeverSecrets` → R25
- `apiKeyRevokeRequiresConfirmation` → R26
- `adminOperation_adminGetAccount_matchesRoleMatrix` → R27
- `adminOperation_adminDeleteAccount_matchesRoleMatrix` → R27
- `adminOperation_adminActivateAccount_matchesRoleMatrix` → R27
- `adminOperation_adminSuspendAccount_matchesRoleMatrix` → R27
- `adminOperation_adminReinstateAccount_matchesRoleMatrix` → R27
- `adminOperation_adminUnlockAccount_matchesRoleMatrix` → R27
- `adminOperation_getEffectiveRoles_matchesRoleMatrix` → R27
- `adminOperation_assignRole_matchesRoleMatrix` → R27
- `adminOperation_removeRole_matchesRoleMatrix` → R27
- `adminOperation_assignRoleTemplate_matchesRoleMatrix` → R27
- `adminOperation_removeRoleTemplate_matchesRoleMatrix` → R27
- `adminOperation_createRole_matchesRoleMatrix` → R27
- `adminOperation_listRoles_matchesRoleMatrix` → R27
- `adminOperation_createRoleTemplate_matchesRoleMatrix` → R27
- `adminOperation_listRoleTemplates_matchesRoleMatrix` → R27
- `adminOperation_listAuditEvents_matchesRoleMatrix` → R27
- `unauthenticatedRoutesRedirectToSignIn` → R28
- `publicRouteSetIsExactlyTheDeclaredSet` → R28
- `expiredSessionMidTaskPreservesOnlyNonSensitiveInput` → R29
- `identityErrorsIndistinguishableAcrossAccountStates` → R30

**Phase 1a — Security properties**
- `tokensAreNeverWrittenToAnyStorage` → R7
- `returnTargetOutsideAllowlistFallsBackToHome` → R7
- `stateSurvivesRedirectAndIsConsumedOnce` → R8
- `sessionStorageHoldsOnlyTheL16Items` → R29
- `bundleContainsNoSecretEnvKeys` → R58
- `serviceWorkerNeverCachesApiResponses` → R57
- `sasNavigationBypassesServiceWorker` → R57
- `endSessionNavigationBypassesServiceWorker` → R57

**Phase 1a — Unit-level tests (added with the decomposition of `tasks.md`)**
- `reusedRefreshOrFailedRenewalTriggersCleanReauth` → R22
- `shellBuildsAndMounts` → L13
- `routesResolveUnderAppPrefixOnly` → L14
- `generatedClientCompiles` → R20
- `lintRejectsHandWrittenBackendFetch` → L6
- `serviceWorkerUpdateWaitsForReload` → R57
- `pkceVerifierMeetsRfc7636` → R6
- `authorizeRequestsExactlyTheDeclaredScopes` → R20
- `callbackValidatesNonce` → R7
- `emailVerifiedClaimIsNotAccountState` → R20
- `passwordResetFailureUsesSameUniformMessage` → R15
- `registrationAcceptsTwelveAndOneTwentyEightCodePoints` → R1
- `passwordLengthCountsCodePoints` → R1
- `noClientSideBreachLookup` → R17
- `identityErrorsIdenticalAcrossTokenStates` → R30
- `sessionListShowsDeviceLabel` → R18
- `sessionListShowsFallbackLabel` → R18
- `sessionListShowsRotatedAt` → R18
- `revokeOneSessionRemovesIt` → R18
- `revokeAllSessionsRemovesAll` → R18
- `silentReauthAfterRevokeAllFails` → R15
- `enrollmentBeginShowsProvisioningUriAndQr` → R11
- `enrollmentConfirmRequiresLiveCode` → R11
- `recoveryCodesNeverPersistedClientSide` → R11
- `recoveryCodesRequireAcknowledgement` → R11
- `privilegedBootstrapPathEnrollsWithoutEnrollmentHint` → R10
- `apiKeyNotRedisplayedAfterDismiss` → R24
- `apiKeyRevokeCallsDeleteAfterConfirm` → R26
- `adminAreaHiddenFromOtherRoles` → R27
- `adminOperation_adminGetAccount_matchesRoleMatrix` → R27
- `adminOperation_adminDeleteAccount_matchesRoleMatrix` → R27
- `adminOperation_adminActivateAccount_matchesRoleMatrix` → R27
- `adminOperation_adminSuspendAccount_matchesRoleMatrix` → R27
- `adminOperation_adminReinstateAccount_matchesRoleMatrix` → R27
- `adminOperation_adminUnlockAccount_matchesRoleMatrix` → R27
- `adminOperation_getEffectiveRoles_matchesRoleMatrix` → R27
- `adminOperation_assignRole_matchesRoleMatrix` → R27
- `adminOperation_removeRole_matchesRoleMatrix` → R27
- `adminOperation_assignRoleTemplate_matchesRoleMatrix` → R27
- `adminOperation_removeRoleTemplate_matchesRoleMatrix` → R27
- `adminOperation_createRole_matchesRoleMatrix` → R27
- `adminOperation_listRoles_matchesRoleMatrix` → R27
- `adminOperation_createRoleTemplate_matchesRoleMatrix` → R27
- `adminOperation_listRoleTemplates_matchesRoleMatrix` → R27
- `adminOperation_listAuditEvents_matchesRoleMatrix` → R27

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
- `addressPoisoningFlagShowsWarningOnPaymentAndReceipt` → R55
- `heldWithoutReceiptShowsNeutralCopyRevealingNoReason` → R56

**Notification stream**
- `streamNeverPlacesTokenInUrlAndReopensAfterRenewal` → R59

**Phase 1b — Unit-level tests (added with the decomposition of `tasks.md`)**
- `invoiceAmountRenderedAsDecimalStringOnly` → R31
- `underpaymentDiscrepancyShown` → R31
- `overpaymentDiscrepancyShown` → R31
- `expiredInvoiceShownAsExpired` → R31
- `invoiceDetailOpensFromList` → R31
- `invoiceCreateSubmitsThroughGeneratedClient` → R31
- `paymentForwardPathRendersInOrder` → R32
- `reorgSeenToWatchingShownTruthfully` → R32
- `heldFromAnyStateShown` → R32
- `heldNeverLabelledPending` → R32
- `paymentStateUpdatesWithoutReload` → R32
- `finalizedShowsReceiptPendingCopy` → R33
- `receiptShowsVerifiableFieldsAtAttested` → R34
- `receiptNeverCachedClientSide` → R34
- `walletMonitoringStateShown` → R38
- `confirmationCountShownAgainstRequired` → R39
- `addressPoisoningWarningOnReceipt` → R55
- `notificationArrivesInList` → R35
- `streamDropShowsReconnectingState` → R35
- `streamReopensAfterRenewal` → R59
- `exportRequestScopedToMerchant` → R36
- `historyAmountsAreDecimalStrings` → R36
- `amountScalingUsesNoNumberType` → R37
- `zeroDecimalTokenShowsWholeAmount` → R37
- `oidcRoundTripEndToEnd` → L1
- `phase1ScreensPassAccessibilityChecks` → L11

**Phase 2–5 — Unit-level tests (added with the decomposition of `tasks.md`)**
- `evidenceUploadShowsProgress` → R40
- `evidenceFailureHidesServerInternals` → R40
- `analysisShowsFieldsWithIntegrity` → R41
- `collectedEvidenceLabelledAsCollected` → R42
- `stillNeededEvidenceMarkedAsUploadNeeded` → R42
- `evidenceGraphRendersNodesAndEdges` → R43
- `narrativeShownWithGraph` → R43
- `claimShowsItsEvidence` → R44
- `claimantStatementSeparatedFromFacts` → R44
- `aiTextVisuallyDistinctFromFacts` → R45
- `narrativeShowsConfidenceAndRecommendation` → R45
- `timelineMissingEventNotInferred` → R46
- `walletSignalShowsSource` → R48
- `passportMetricShowsValueWithWindow` → R49
- `counterpartyGraphRendersRelationships` → R50
- `fraudAlertShowsEvidence` → R51
- `confirmedAndSuspectedVisuallyDistinct` → R51
- `portalKeyShownExactlyOnce` → R53
- `capabilityLinkIdIsUnguessable` → R54
- `capabilityLinkExposesNoPii` → R54
- `capabilityLinkNotCached` → R54

**Phases 2–5** — named tests for R40–R54 are authored when each phase's contracts close (Q4, Q11):
- `evidenceUploadShowsSafeFailures` → R40
- `analysisLabelsLowConfidenceAgainstContractThreshold` → R41
- `txHashCollectsEvidenceAndMarksUploadNeeded` → R42
- `evidenceGraphMarksPartialAnalysis` → R43
- `claimStatusShownForEachClaim` → R44
- `narrativeKeepsAiTextDistinct` → R45
- `timelineShowsEventsInDeclaredOrder` → R46
- `smartContractFailureMarkedObservedNotInferred` → R47
- `walletProfileShowsSignalsNotOnlyScore` → R48
- `trustPassportShowsSourceWindows` → R49
- `counterpartyGraphShowsClusterEvidence` → R50
- `fraudAlertDistinguishesConfirmedFromSuspected` → R51
- `crossChainRelationshipShowsLinkingEvidence` → R52
- `apiPortalKeysFollowPlaintextOnceRule` → R53
- `capabilityLinkUsesUnguessableIdNoPiiNoCache` → R54

## 9. Verification checklist — implementer self-checks before raising PR

- [ ] No token, PIN, recovery code, API key, or PII reaches storage (beyond the L16 items), logs, or analytics.
- [ ] Every identity error screen was compared side by side across all account and token states in R30.
- [ ] Every backend call goes through `libs/ts/api-client`, except the named stream exception (R59).
- [ ] The OIDC callback validates `state` and `nonce`, and redirect URIs are exact-match.
- [ ] The service worker's denylist (R57) matches `agents.md` exactly.
- [ ] Amounts were checked for base-unit scaling (R37), not raw display.
- [ ] Each new screen has been keyboard-tested and checked against the live-region rule.

## 10. Migration, rollout & rollback

- **Flag source**: a build-time flag for flows that exist in the bundle, and a remote configuration document fetched NetworkOnly for
  runtime kill switches. The remote document is never cached.
- **Rollout**: the auth gate cannot target a cohort before identity is known. Phase 1a therefore ships to everyone once it is
  `READY FOR IMPL`, behind a build-time flag used for staging. Phase 1b ships behind its own remote flag, so it can be switched off per
  cohort after sign-in.
- **Kill switch**: a remote flag disables a named flow (for example, API key creation) without a redeploy. Public routes are never
  disabled by a kill switch.
- **Service-worker update policy (one worker-wide policy)**: prompt-to-reload. `skipWaiting` and `clients.claim` are not used.
- **Forced update for rollback**: the remote configuration carries a denylist of build identifiers. On load, a build that appears on
  the denylist forces a reload. Rollback rebuilds the previous commit under a new build identifier, and the denylist entry for the
  broken build makes already-open clients reload.

## 11. Open questions for the author

Each item blocks the phase it names. A phase moves to `READY FOR IMPL` only when its own blockers are closed.

- Q1. **MFA self-service contract and T19 (blocks R11–R13 and the Phase 1a MFA screens).** ~~`accounts/me/mfa/totp` (enroll),
  `…/confirm`, `DELETE accounts/me/mfa/totp`, and `accounts/me/mfa/recovery-codes` are specified (backend R22, R23, R28; auth T19) but
  are not in `contracts/api/auth.yaml` and not built. T20 (the SAS MFA step) is built. Owner action: confirm T19's delivery, then paste
  or confirm the contract.~~ **Resolved (2026-10-08): T19 is built, all 14 phases.** All four endpoints
  (`POST .../totp`, `POST .../totp/confirm`, `DELETE .../totp`, `POST .../recovery-codes`, the last covering the new R49)
  are implemented and documented in `contracts/api/auth.yaml` with explicit `security: [bearerAuth]`. 90 tests green,
  including real HTTP integration tests for every named path and failure path. Closed.
- Q2. **Payments contract and payment-service Q1 (blocks R31–R39, R55, R56).** `contracts/api/payments.yaml` does not exist.
  `spec/payment-service` defines invoice endpoints and the invoice states `OPEN`, `PAID`, `UNDERPAID`, `OVERPAID`, `EXPIRED`, `HELD`,
  but payment-service's own design O1 marks those outcomes as proposed, pending its Q1. Owner action: close payment-service Q1, then
  author or supply `payments.yaml`.
- Q3. **Notification stream and transport (blocks R35, R59, O7).** ~~`contracts/api/notifications.yaml` does not exist, and the
  transport and authentication are decided in `spec/notification-service` O3 and Q3. Owner action: confirm the SSE choice and the
  stream authentication option (O7).~~ **Resolved (2026-10-08).** The transport is SSE, already built
  (`InappStreamController`, task 13) — `notification-service` design.md O3 updated to record this. `contracts/api/notifications.yaml`
  now exists, documenting both real endpoints (`GET /notifications/stream`, `GET /notifications/unread`), each
  Bearer-JWT-authenticated with no specific scope. O7's stream-authentication option is resolved to a fetch-based reader
  carrying the real `Authorization` header (see `design.md` O7) — no ticket endpoint, no token in a URL. Closed.
- Q4. **Phase 2–5 APIs (block R40–R53).** No contracts exist. Owner action: supply each phase's surface when its backend spec is
  authored.
- Q5. **Refresh-token issuance to the public client (blocks R18's refresh families, the reuse half of R22, and R19's revoke).**
  D-012 selects a rotating refresh token through the OIDC client. The seeder grants the refresh-token grant with
  `reuseRefreshTokens(false)`. No auth prompt verifies that SAS issues a refresh token to `checky-spa`, a client registered with
  `ClientAuthenticationMethod.NONE`. Owner action: verify against the running auth service. If refresh tokens are not issued, choose
  between a custom token generator with an ADR, or accepting the D-012 silent re-authorization model with R18 and the reuse half of R22
  removed.
- Q6. **Environment keys and the SPA route set (design §4c).** **Resolved (2026-10-10): confirmed as proposed.**
  `VITE_OIDC_ISSUER`, `VITE_OIDC_CLIENT_ID`, `VITE_OIDC_REDIRECT_URI`, `VITE_API_BASE_PATH`, and the `/app/...` route list in
  `design.md` §4c are final. Closed.
- Q7. **Design system (O2).** **Resolved (2026-10-10): Tailwind CSS + headless/unstyled components** (e.g. Radix UI) for
  accessible primitives (dialogs, dropdowns, etc.). Fully custom look, no fighting a pre-themed library. Closed.
- Q8. **i18n approach (O3).** **Resolved (2026-10-10): `react-i18next`, English-only at launch.** Every string still goes
  through the catalogue (the existing LOCKED platform rule) so a second language later is adding a translation file, not a
  component rewrite. Fallback rule: a missing key falls back to English, never shows a raw key to the user. Closed.
- Q9. **Analytics (O4).** **Resolved (2026-10-10): none at launch**, matching the design's own existing default. Revisit
  when there's an actual product need to measure something — any future vendor needs a privacy review against L3/agents.md
  first. Closed.
- Q10. **Confirm the L5 consequence.** ~~Owner action: confirm that the SPA never performs first-login enrollment, given that T20
  refuses rather than enrolls.~~ **Resolved (2026-10-10), as a direct consequence of L5/D-031's own resolution**: the SPA never
  performs first-login enrollment — enrollment always happens while an account holds only `USER`, before promotion, so a
  privileged account reaching login already has a confirmed enrollment by construction. Closed.
- Q11. **Capability links (R54) and public routes.** Owner action: decide whether the payer invoice view and the shareable trust passport
  ship, and in which phase. Either one requires an ADR amending L14.
- Q12. **Auth contract additions (blocks R5's consistency, R23, R24, R25).** All three parts now closed (2026-10-10).
  (a) ~~Backend R6 requires a signed-in caller for resend-verification, but `auth.yaml` declares it public with
  `security: []`.~~ **Resolved: `auth.yaml` and the real code were both already correct** — `AccountController.resendVerification`
  takes no `Authentication` parameter and is listed in `PublicEndpoints`; it is deliberately public and email-identified,
  an explicit, human-approved deviation T06's own Phase 0 artifact already disclosed (mirrors R12/R13's shape — no token
  exists yet for an unauthenticated caller to present). It was `requirements.md`'s own R6 *text* that was stale, saying
  "an authenticated caller" — fixed directly in `spec/auth-service/requirements.md`. No contract or code change needed.
  (b) ~~`Retry-After` and a 429 response are undocumented.~~ **Resolved: consistent with this repo's own, already
  three-times-confirmed convention** (`auth.yaml`/`crypto-internal.yaml`/`notifications.yaml` all document only success
  responses — see auth T19 Phase 4/9/11's own repeated, re-verified disposition of the identical question). Not an
  oversight; documenting it here would make this one case inconsistent with every other endpoint in the same file.
  (c) ~~the MFA-not-confirmed problem type [is] undocumented.~~ **Resolved: this problem type does not exist, and the
  premise was wrong.** `TotpAuthenticationProvider`'s rejection (R24) throws a plain `BadCredentialsException`, handled
  by Spring Security's own form-login failure flow (`/login?error`) — never an RFC 9457 JSON response. There is nothing
  to document because `auth.yaml` already explicitly excludes SAS's own protocol/login surface from its scope (its own
  stated R47/T33 scope note). (The key-prefix half of (c) was separately resolved 2026-10-08:
  `ApiKeyMetadata.prefix` added.) Closed.
- Q13. **Privileged-account bootstrap (blocks R10's enrollment path, R11 for MERCHANT and ADMIN, and task 19).**
  ~~T20 refuses an unenrolled MERCHANT or ADMIN with the wrong-password error and does not enroll them. T19 is not
  built. Options: (a) enroll while the account holds USER, before MERCHANT is granted, by an admin-controlled step;
  (b) a pre-authentication enrollment step in SAS (new backend work); (c) admin-assisted enrollment. Owner action:
  choose one, and record it as an auth ADR.~~ **Resolved (2026-10-08): option (a), chosen directly by the product
  owner.** T19 is now built. An account enrolls MFA while it still holds only `USER` (the SPA's own voluntary-enrollment
  wizard, no separate bootstrap UI needed — T19's endpoints require only an authenticated bearer token, not a
  privileged role), and only afterward is it granted MERCHANT/ADMIN. This is enforced server-side, not left to admin
  discipline: `RoleService.assignRole`/`assignRoleTemplate` now refuse the grant (409,
  `RoleRequiresConfirmedMfaException`) unless a confirmed TOTP enrollment already exists. Recorded as
  `auth-decisions.md` D-031. Closed.
- Q14. **Admin status view versus the enumeration lock (blocks R27's `adminGetAccount` view).** ~~L4 is `[ALL]` and has no
  carve-out. Showing an account's status to an administrator is a deliberate, authenticated disclosure. Owner action: decide
  whether to accept an ADR that scopes an authenticated ADMIN status view, or remove the status view from the SPA.~~
  **Resolved (2026-10-10): keep it, with a scoped ADR** (`auth-decisions.md` D-033) — L4's own text updated to apply only to
  unauthenticated/low-trust callers; the authenticated, RBAC-gated admin status view is a deliberate disclosure outside that
  scope, not a violation of it. No backend change (the endpoint already returns this field). Closed.
- Q15. **Session revocation semantics.** ~~Backend R14 revokes refresh-token families only. Backend R37 and R38 remove the
  stored authorization record, not the browser's SAS session. If the SAS session cookie survives, a revoked device can
  silently re-authenticate. Owner action: decide whether revoke-all and password reset must also end SAS HTTP sessions, and
  specify the backend behaviour.~~ **Decided (2026-10-10): yes, both should also end the real SAS session — but this is
  not yet built.** Recorded as `auth-decisions.md` D-032, including a real complication discovered while investigating Q5:
  no refresh-token family even exists for a `checky-spa` login, so the fix is not a small extension of the existing
  family-revocation path — it needs a new, direct session-invalidation mechanism (Spring Session, not currently a
  dependency of this service at all), tracked as an unnumbered future backend task. `requirements.md`'s own R15 keeps its
  current, accurate (conservative) wording until that work lands. The decision itself is closed; the implementation is an
  open, tracked follow-up, not something this spec assumes exists yet.

**Per-phase status**: Phase 1a is `DRAFT` — every one of its own blockers (Q1, Q5, Q6, Q10, Q12, Q13, Q14, Q15) is now
resolved (2026-10-08/10); Q15's own backend improvement is a tracked follow-up, not a Phase 1a blocker. Phase 1b is `DRAFT`
(Q2 — Q3 resolved). Phases 2–5 are `DRAFT` (Q4).
Q7-Q9 resolved 2026-10-10 (design system, i18n, analytics). Q11 (capability links) remains open, tied to payment-service.

## 12. Execution handoff — for an agent who starts implementation later

**Status of this package.** Specification only. No frontend code exists, and no generated execution prompts are committed.
Execution is deferred until `spec/payment-service` is fixed and its contracts exist. This section tells the agent what it must read
and what it must not assume.

**Read in this order.**
1. `agents.md`: the platform rules, all tagged `[ALL]`. These override any feature spec.
2. `package.md` §0 and §11: the phase status and the open questions Q1–Q15. Each unit's blocker is listed there.
3. `requirements.md`: R1–R59, in EARS form. Each requirement names the backend requirement or contract it depends on.
4. `design.md`: §4a locks (L1–L18), §4b open decisions (O1–O7), and §4c verbatim contract paths and the `/app` route set.
5. `tasks.md`: 172 units. Each unit names one test in §8 and cites its requirements. Units marked "Blocked on Qn" must not start
   until that question closes.
6. `artifacts/review-resolution.md`: both adversarial review passes, with every disposition. The second pass was not re-reviewed.
7. Backend context the spec relies on: `contracts/api/auth.yaml`, `contracts/api/token-claims.md`,
   `services/auth/docs/architecture/auth-decisions.md` (especially D-012 and D-025), and `spec/auth-service/requirements.md`.
8. For Phase 1b onwards: `spec/payment-service/` and its design open question Q1; also
   `contracts/api/notifications.yaml` and `spec/notification-service/design.md` O3 (resolved: SSE).

**What is decided and what is not.**
- Decided (confirmed by the author): public OIDC/PKCE client; SAS-hosted login; enumeration-safe uniform copy with no lockout timers;
  React, TypeScript, Vite, React Router, TanStack Query, Zustand, vite-plugin-pwa, Vitest, Playwright; "Checky Pro" as the displayed
  name; auth gate before payments; the single-package layout; the privileged-account bootstrap (Q13, 2026-10-08 — enroll as USER,
  then promote, enforced server-side, `auth-decisions.md` D-031); refresh-token/renewal reality (Q5, 2026-10-10 — no refresh token
  is issued to this client, confirmed empirically; silent re-auth via the SAS session cookie only, `auth-decisions.md` context in
  D-032); the admin status view (Q14, 2026-10-10 — scoped carve-out from L4, `auth-decisions.md` D-033); session revocation (Q15,
  2026-10-10 — should also end the real SAS session, not yet built, `auth-decisions.md` D-032); the design system, i18n, analytics,
  and env keys (Q6–Q9, 2026-10-10 — Tailwind + headless components, `react-i18next` English-only, none at launch, proposed names
  confirmed).
- Not decided: the payments contract (Q2); Phases 2–5 contracts (Q4); capability links / the payer invoice view and shareable
  trust passport (Q11, tied to Q2/payment-service).
- Known facts that differ from earlier drafts: the SAS MFA step (auth T20) is built and refuses unenrolled MERCHANT and ADMIN accounts
  with wrong-password copy. **Updated 2026-10-08:** the self-service MFA endpoints (auth T19, Q1) are now built and documented in
  `auth.yaml`; the notification stream (Q3) is now documented in the new `contracts/api/notifications.yaml`, authenticated via a
  fetch-based reader carrying a standard `Authorization` header (O7, resolved).

**Process an executing agent must follow** (the 14-phase pipeline, as in `.ai/`). Start from Phase 0 for one unit at a time. Human
approval gates are Phases 4 and 9. Adversarial review runs in Phases 3, 8, and 11 with a different model, not the implementing agent.
Never advance a phase without its artifact, and never skip the approval gate. Verify each reviewer's claims against source before
accepting them.

**Regenerating the execution prompts.** `.ai/generate.py` builds `.ai/prompts/frontend/` from this package. Its only mode is a full
regenerate. Before running it, do a read-only comparison of the existing services' output against disk. At spec-authoring time the
four existing services matched byte-for-byte, so only the frontend folder changed. Generated READMEs list every test that shares a
requirement ID, which is broader than the unit's own test. The unit's own test is named in its task line.

**Before starting any unit.** Check the blockers for that unit, check the current state of `spec/payment-service` if the unit is a
Phase 1b or later unit, and re-verify any contract path against the live `contracts/` tree, since contracts may have changed since this
spec was written.
