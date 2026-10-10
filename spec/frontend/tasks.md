# 7. Tasks — ordered execution plan

Execute in order. Each unit is one observable behaviour, proven by the named test in `package.md` §8 (the backtick name after
"Verifies"). A unit that depends on an open blocker is marked "Blocked on Qn" and is not started until its blocker closes. Each unit is
one physical line so the generator can read its cites. Phase 1a is decomposed to unit level. Phases 1b to 5 are still coarse and
will be decomposed after this granularity is accepted.

## Phase 1 — Foundation

1. **Shell renders.** The Vite, React, and TypeScript app builds and mounts an empty shell. Verifies `shellBuildsAndMounts`. Cites L13.
2. **/app prefix.** Every SPA route resolves under `/app`, and a path outside it is not served by the SPA. Verifies `routesResolveUnderAppPrefixOnly`. Cites L14.
3. **Generated client.** `libs/ts/api-client` is generated from `contracts/api/auth.yaml` and compiles. Verifies `generatedClientCompiles`. Cites L6, R20.
4. **No hand-written backend fetch.** A lint rule fails a build that calls `fetch` against a backend route. Verifies `lintRejectsHandWrittenBackendFetch`. Cites L6.
5. **Single-flight 401.** A 401 triggers exactly one renewal attempt. Verifies `parallel401sTriggerExactlyOneRenewal`. Cites R21.
6. **Shared renewal across tabs.** Sibling tabs wait on the one renewal instead of starting their own. Verifies `siblingTabsShareOneRenewal`. Cites R21, L18.
7. **429 generic copy.** An SPA-originated 429 shows fixed copy with no countdown and no interval. Verifies `rateLimitedResponseShowsGenericCopyWithoutReplay`. Cites R23.
8. **No POST replay.** A 429 on a non-idempotent POST is never retried automatically. Verifies `rateLimitedResponseShowsGenericCopyWithoutReplay`. Cites R23.
9. **Denylisted navigations bypass the worker.** Navigations to the paths in `agents.md` are never answered from cache or with the shell. Verifies `sasNavigationBypassesServiceWorker`. Cites R57, L17.
10. **End-session navigation bypasses the worker.** `/connect/logout` is never answered from cache. Verifies `endSessionNavigationBypassesServiceWorker`. Cites R57.
11. **API routes are NetworkOnly.** No API response is served from cache. Verifies `serviceWorkerNeverCachesApiResponses`. Cites R57, L17.
12. **Prompt-to-reload update policy.** The worker never calls `skipWaiting` or `clients.claim`, and a new build waits for a user reload. Verifies `serviceWorkerUpdateWaitsForReload`. Cites L17, `package.md` §10.
13. **No secret in the bundle.** The production bundle contains no secret value and no non-public environment key. Verifies `bundleContainsNoSecretEnvKeys`. Cites R58, L10.

## Phase 1 — Auth & account (gatekeeper): OIDC and session

14. **PKCE verifier.** The code verifier is 43–128 characters from the RFC 7636 unreserved set, and its S256 challenge is sent on authorize. Verifies `pkceVerifierMeetsRfc7636`. Cites R6, L1.
15. **Authorize request shape.** The authorize request carries `state`, `nonce`, `code_challenge_method=S256`, and exactly `openid profile email`. Verifies `authorizeRequestsExactlyTheDeclaredScopes`. Cites R6, R20.
16. **No password field.** The sign-in screen renders no password input. Verifies `signInStartsAuthorizationCodeFlowWithPkceAndRendersNoPasswordField`. Cites R6.
17. **Missing state discarded.** A callback without `state` is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.
18. **Mismatched state discarded.** A callback whose `state` differs from the stored value is discarded with no exchange. Verifies `callbackWithMismatchedStateIsDiscarded`. Cites R8.
19. **Nonce validated.** The ID token's `nonce` must equal the stored value, or the exchange is rejected. Verifies `callbackValidatesNonce`. Cites R7.
20. **Code exchange sends the verifier.** The token request carries the stored `code_verifier`. Verifies `callbackValidatesNonceAndKeepsAccessTokenInMemory`. Cites R7.
21. **Access token in memory only.** The token is held in a module-scoped variable and nowhere else. Verifies `callbackValidatesNonceAndKeepsAccessTokenInMemory`. Cites R7, L3.
22. **No token in any storage.** No token is written to local storage, session storage, IndexedDB, a cookie, a URL, or a log. Verifies `tokensAreNeverWrittenToAnyStorage`. Cites R7, L3.
23. **PKCE state single-use.** `state`, `nonce`, and `code_verifier` survive the full-page redirect in session storage and are deleted at callback. Verifies `stateSurvivesRedirectAndIsConsumedOnce`. Cites R8, L16.
24. **Return target inside allowlist.** A return target on the allowlist is honoured. Verifies `returnTargetOutsideAllowlistFallsBackToHome`. Cites R7, L1.
25. **Return target outside allowlist.** A return target off the allowlist falls back to the home route. Verifies `returnTargetOutsideAllowlistFallsBackToHome`. Cites R7, L1.
26. **Session storage holds only L16 items.** Session storage contains nothing beyond the L16 list. Verifies `sessionStorageHoldsOnlyTheL16Items`. Cites R29, L16.
27. **SAS MFA challenge resumes at callback.** After the challenge completes, the SPA resumes at the callback. Verifies `loginChallengeRunsInsideSasFlowAndResumesAtCallback`. Cites R9.
28. **Unenrolled privileged refusal copy.** A SAS refusal for an unenrolled MERCHANT or ADMIN shows the wrong-password copy. Verifies `sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy`. Cites R10, L5.
29. **Uniform sign-in failure.** Every SAS failure shows one uniform message. Verifies `identityErrorsIndistinguishableAcrossAccountStates`. Cites R10, R30.
30. **Profile from userinfo.** Profile data is read from `/userinfo` and never from the access token. Verifies `profileIsReadFromUserinfoAndAccountStatusFromMe`. Cites R20.
31. **Account status from accounts/me.** Account status is read from `GET /accounts/me`. Verifies `profileIsReadFromUserinfoAndAccountStatusFromMe`. Cites R20.
32. **email_verified is not account state.** The access-token `email_verified` claim never drives verification UI. Verifies `emailVerifiedClaimIsNotAccountState`. Cites R20.
33. **Clean re-auth on failed renewal.** A failed renewal routes to re-auth with no half-session. Verifies `failedRenewalTriggersCleanReauthWithoutHalfSession`. Cites R22.
34. **Reuse detection path.** A reused refresh token routes to clean re-auth. Verifies `reusedRefreshOrFailedRenewalTriggersCleanReauth`. Cites R22. Blocked on Q5.
35. **Sign-out revokes and clears.** Sign-out calls `/oauth2/revoke` for held tokens and clears memory. Verifies `signOutRevokesTokensClearsMemoryAndEndsSasSession`. Cites R19.
36. **Sign-out ends the SAS session.** Sign-out calls the end-session endpoint with an allowlisted `post_logout_redirect_uri`. Verifies `signOutRevokesTokensClearsMemoryAndEndsSasSession`. Cites R19.
37. **Signed-out landing.** After sign-out the user lands on `/app/signed-out`. Verifies `publicRouteSetIsExactlyTheDeclaredSet`. Cites R19, L14.
38. **Sign-out then sign-in requires credentials.** After sign-out, sign-in shows the password step. Verifies `signOutThenSignInRequiresCredentials`. Cites R19. Blocked on Q15.
39. **Sign-out propagates to every tab.** Signing out in one tab signs out all open tabs. Verifies `signOutPropagatesToAllTabs`. Cites R22, L18.

## Phase 1 — Auth & account: routes

40. **Public route set is exact.** Exactly the public routes in `design.md` §4c are reachable without a session. Verifies `publicRouteSetIsExactlyTheDeclaredSet`. Cites R28, L14.
41. **Unauthenticated redirect.** An unauthenticated request for any other route redirects to sign-in. Verifies `unauthenticatedRoutesRedirectToSignIn`. Cites R28.
42. **Authenticated shell.** A session renders the shell and the authenticated routes. Verifies `unauthenticatedRoutesRedirectToSignIn`. Cites R28.

## Phase 1 — Auth & account: registration and reset

43. **Registration accepts 12 characters.** A 12-character password is accepted client-side as advisory. Verifies `registrationAcceptsTwelveAndOneTwentyEightCodePoints`. Cites R1.
44. **Registration accepts 128 characters.** A 128-character password is accepted client-side as advisory. Verifies `registrationAcceptsTwelveAndOneTwentyEightCodePoints`. Cites R1.
45. **Registration rejects out-of-range passwords.** A password of 11 or 129 code points shows the advisory guidance. Verifies `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints`. Cites R1.
46. **Code points, not UTF-16.** Length is counted in Unicode code points. Verifies `passwordLengthCountsCodePoints`. Cites R1.
47. **Registration acknowledgement is identical.** Existing and new emails show the same acknowledgement screen. Verifies `registrationAcknowledgementIsIdenticalForExistingAndNewEmails`. Cites R2.
48. **Verify success state.** A valid token shows the verified success state. Verifies `verifyFromLinkShowsSuccessState`. Cites R3.
49. **Verify failure is uniform.** Invalid, expired, used, or deleted-account tokens show one uniform failure. Verifies `verifyFailureShowsOneUniformMessage`. Cites R4.
50. **Resend is public.** The resend form works without a session. Verifies `resendVerificationAcknowledgementIsUniformAndNeedsNoSession`. Cites R5.
51. **Resend acknowledgement is uniform.** The resend ack is identical whatever the outcome. Verifies `resendVerificationAcknowledgementIsUniformAndNeedsNoSession`. Cites R5.
52. **Reset request is uniform.** Any email shows the same reset-request acknowledgement. Verifies `passwordResetRequestAcknowledgementIsUniform`. Cites R14.
53. **Reset success is same-device.** The reset success message says sign in on this device only. Verifies `passwordResetSuccessSaysSignInOnThisDeviceOnly`. Cites R15.
54. **Reset failure is uniform.** A reset with a bad, expired, used, or deleted-account token shows the uniform failure. Verifies `passwordResetFailureUsesSameUniformMessage`. Cites R15, R4.
55. **Change password needs current.** Change-password requires the current password first. Verifies `changePasswordRequiresCurrentPassword`. Cites R16.
56. **Breach rejection shows server copy.** A breach rejection shows the server's message as returned. Verifies `breachRejectionShowsServerMessageWithoutClientLookup`. Cites R17.
57. **No client breach lookup.** The client never calls a breach-range service. Verifies `noClientSideBreachLookup`. Cites R17.

## Phase 1 — Auth & account: enumeration safety

58. **Identical across account states.** Unknown, missing, locked, suspended, and deleted accounts render identical error copy and treatment. Verifies `identityErrorsIndistinguishableAcrossAccountStates`. Cites R30, L4.
59. **Identical across token states.** Bad, expired, and used tokens render identical error copy and treatment. Verifies `identityErrorsIdenticalAcrossTokenStates`. Cites R30, L4.

## Phase 1 — Auth & account: sessions

60. **Device label shown.** Each session shows its device label. Verifies `sessionListShowsDeviceLabel`. Cites R18. Blocked on Q5.
61. **Fallback label when null.** A null device label shows the neutral fallback. Verifies `sessionListShowsFallbackLabel`. Cites R18. Blocked on Q5.
62. **Rotation time shown.** Each session shows `rotatedAt`. Verifies `sessionListShowsRotatedAt`. Cites R18. Blocked on Q5.
63. **Revoke one.** Revoking one session removes it from the list. Verifies `revokeOneSessionRemovesIt`. Cites R18. Blocked on Q5.
64. **Revoke all.** Revoking all sessions removes every session. Verifies `revokeAllSessionsRemovesAll`. Cites R18. Blocked on Q5, Q15.
65. **Silent re-auth after revoke-all fails.** After revoke-all, a silent re-auth does not restore the session. Verifies `silentReauthAfterRevokeAllFails`. Cites R15, R18. Blocked on Q15.

## Phase 1 — Auth & account: MFA (voluntary)

66. **Enrollment shows URI and QR.** Beginning enrollment shows the `otpauth://` URI and a QR code. Verifies `enrollmentBeginShowsProvisioningUriAndQr`. Cites R11.
67. **Confirm needs a live code.** Confirmation succeeds only with a valid live code. Verifies `enrollmentConfirmRequiresLiveCode`. Cites R11.
68. **Recovery codes shown once.** The 10 recovery codes appear exactly once. Verifies `voluntaryEnrollmentShowsRecoveryCodesExactlyOnce`. Cites R11.
69. **Recovery codes not persisted.** Recovery codes are never written to any storage. Verifies `recoveryCodesNeverPersistedClientSide`. Cites R11, L3.
70. **Acknowledgement required.** The user must acknowledge the recovery codes before continuing. Verifies `recoveryCodesRequireAcknowledgement`. Cites R11.
71. **Disable needs password and code.** Disabling MFA requires the current password and a valid TOTP. Verifies `disableMfaRequiresPasswordAndCode`. Cites R12.
72. **Regenerate shows once.** Regenerated recovery codes show exactly once. Verifies `regenerateRecoveryCodesShowsOnce`. Cites R13.
73. **Privileged bootstrap path.** The chosen path for unenrolled MERCHANT and ADMIN accounts works and shows no enrollment hint. Verifies `privilegedBootstrapPathEnrollsWithoutEnrollmentHint`. Cites R10, L5. Blocked on Q13.

## Phase 1 — Auth & account: API keys

74. **Create blocked without MFA.** Without confirmed MFA, creation shows fixed copy. Verifies `apiKeyCreationRejectedWithoutMfaShowsFixedCopy`. Cites R24. Blocked on Q12.
75. **Plaintext shown once.** The `ck_live_` key is shown exactly once with an acknowledgement. Verifies `apiKeyIsShownExactlyOnceWithAcknowledgement`. Cites R24.
76. **Key not redisplayed.** After the user dismisses the key, it cannot be displayed again. Verifies `apiKeyNotRedisplayedAfterDismiss`. Cites R24.
77. **List shows contract fields.** The list shows `name`, `scopes`, `createdAt`, `lastUsedAt`, `expiresAt`, and `revokedAt`. Verifies `apiKeyListShowsOnlyContractFieldsAndNeverSecrets`. Cites R25.
78. **List never shows a secret.** No secret field is rendered anywhere in the list. Verifies `apiKeyListShowsOnlyContractFieldsAndNeverSecrets`. Cites R25.
79. **Revoke needs confirmation.** Revoke requires an explicit confirmation step. Verifies `apiKeyRevokeRequiresConfirmation`. Cites R26.
80. **Revoke calls DELETE.** Confirmed revoke calls `DELETE /api-keys/{keyUuid}`. Verifies `apiKeyRevokeCallsDeleteAfterConfirm`. Cites R26.

## Phase 1 — Auth & account: admin (role matrix)

81. **Admin area hidden from other roles.** Users without ADMIN or COMPLIANCE do not see the admin area. Verifies `adminAreaHiddenFromOtherRoles`. Cites R27.
82. **adminGetAccount.** Verifies `adminOperation_adminGetAccount_matchesRoleMatrix`. Cites R27. Status view blocked on Q14.
83. **adminDeleteAccount.** Verifies `adminOperation_adminDeleteAccount_matchesRoleMatrix`. Cites R27.
84. **adminActivateAccount.** Verifies `adminOperation_adminActivateAccount_matchesRoleMatrix`. Cites R27.
85. **adminSuspendAccount.** Verifies `adminOperation_adminSuspendAccount_matchesRoleMatrix`. Cites R27.
86. **adminReinstateAccount.** Verifies `adminOperation_adminReinstateAccount_matchesRoleMatrix`. Cites R27.
87. **adminUnlockAccount.** Verifies `adminOperation_adminUnlockAccount_matchesRoleMatrix`. Cites R27.
88. **getEffectiveRoles.** Verifies `adminOperation_getEffectiveRoles_matchesRoleMatrix`. Cites R27.
89. **assignRole.** Verifies `adminOperation_assignRole_matchesRoleMatrix`. Cites R27.
90. **removeRole.** Verifies `adminOperation_removeRole_matchesRoleMatrix`. Cites R27.
91. **assignRoleTemplate.** Verifies `adminOperation_assignRoleTemplate_matchesRoleMatrix`. Cites R27.
92. **removeRoleTemplate.** Verifies `adminOperation_removeRoleTemplate_matchesRoleMatrix`. Cites R27.
93. **createRole.** Verifies `adminOperation_createRole_matchesRoleMatrix`. Cites R27.
94. **listRoles.** Verifies `adminOperation_listRoles_matchesRoleMatrix`. Cites R27.
95. **createRoleTemplate.** Verifies `adminOperation_createRoleTemplate_matchesRoleMatrix`. Cites R27.
96. **listRoleTemplates.** Verifies `adminOperation_listRoleTemplates_matchesRoleMatrix`. Cites R27.
97. **listAuditEvents.** Verifies `adminOperation_listAuditEvents_matchesRoleMatrix`. Cites R27.

## Phase 1 — Payment verification & invoicing: invoices

98. **Invoice list states.** The list shows each state from OPEN to HELD by name. Verifies `invoiceListShowsStatesAndDecimalStringAmounts`. Cites R31. Blocked on Q2.
99. **Invoice amount is a decimal string.** The amount renders from the string as received and never as a JS number. Verifies `invoiceAmountRenderedAsDecimalStringOnly`. Cites R31, L7. Blocked on Q2.
100. **Underpayment discrepancy.** An underpaid invoice shows the shortfall. Verifies `underpaymentDiscrepancyShown`. Cites R31. Blocked on Q2.
101. **Overpayment discrepancy.** An overpaid invoice shows the excess. Verifies `overpaymentDiscrepancyShown`. Cites R31. Blocked on Q2.
102. **Expired invoice.** An EXPIRED invoice shows as expired, not as open. Verifies `expiredInvoiceShownAsExpired`. Cites R31. Blocked on Q2.
103. **Invoice detail from list.** Selecting a list row opens its detail view. Verifies `invoiceDetailOpensFromList`. Cites R31. Blocked on Q2.
104. **Invoice create via client.** The create form submits through the generated client only. Verifies `invoiceCreateSubmitsThroughGeneratedClient`. Cites R31, L6. Blocked on Q2.

## Phase 1 — Payment verification & invoicing: payment state

105. **Forward path order.** The payment view shows CREATED, WATCHING, SEEN, CONFIRMING, FINALIZED, ATTESTED in that order. Verifies `paymentForwardPathRendersInOrder`. Cites R32, L8. Blocked on Q2.
106. **Reorg CONFIRMING to SEEN.** The backward move is shown as it happened. Verifies `stateMachineShowsReorgReversalsTruthfully`. Cites R32, L8. Blocked on Q2.
107. **Reorg SEEN to WATCHING.** The second backward move is shown as it happened. Verifies `reorgSeenToWatchingShownTruthfully`. Cites R32, L8. Blocked on Q2.
108. **HELD from any state.** HELD appears as the current state whatever state preceded it. Verifies `heldFromAnyStateShown`. Cites R32, L8. Blocked on Q2.
109. **HELD never called pending.** HELD is never labelled pending. Verifies `heldNeverLabelledPending`. Cites R32, L8. Blocked on Q2.
110. **Live state update.** A state change appears without a page reload. Verifies `paymentStateUpdatesWithoutReload`. Cites R32. Blocked on Q2.
111. **HELD before attestation is neutral.** A compliance hold before attestation shows neutral copy that reveals no reason. Verifies `heldWithoutReceiptShowsNeutralCopyRevealingNoReason`. Cites R56, L8. Blocked on Q2.

## Phase 1 — Payment verification & invoicing: receipts

112. **No receipt before attestation.** No receipt renders at CREATED through FINALIZED. Verifies `receiptNotShownBeforeAttested`. Cites R33, L8. Blocked on Q2.
113. **Finalized copy.** FINALIZED shows "finalized, receipt pending". Verifies `finalizedShowsReceiptPendingCopy`. Cites R33. Blocked on Q2.
114. **Receipt at attestation.** At ATTESTED the receipt shows its verifiable fields. Verifies `receiptShowsVerifiableFieldsAtAttested`. Cites R34, L8. Blocked on Q2.
115. **Hold after attested reorg.** A HELD state after attestation shows alongside the receipt. Verifies `receiptShowsHoldAfterAttestedReorg`. Cites R34, L8. Blocked on Q2.
116. **Receipt not cached.** A receipt is never stored by the service worker or client storage. Verifies `receiptNeverCachedClientSide`. Cites R34, R57. Blocked on Q2.

## Phase 1 — Payment verification & invoicing: wallets and address safety

117. **Wallet monitoring setup.** The wallet registration form submits through the client. Verifies `walletMonitoringSetupShowsState`. Cites R38. Blocked on Q2.
118. **Monitoring state shown.** The wallet shows its current monitoring state. Verifies `walletMonitoringStateShown`. Cites R38. Blocked on Q2.
119. **Unknown token condition.** An unknown-token condition shows truthfully. Verifies `unknownTokenAndConfirmationCountShownTruthfully`. Cites R39. Blocked on Q2.
120. **Confirmation count.** The confirmation count shows against the required count. Verifies `confirmationCountShownAgainstRequired`. Cites R39. Blocked on Q2.
121. **Address-poisoning warning on payment.** A flagged payment shows the warning. Verifies `addressPoisoningFlagShowsWarningOnPaymentAndReceipt`. Cites R55. Blocked on Q2.
122. **Address-poisoning warning on receipt.** A flagged receipt shows the same warning. Verifies `addressPoisoningWarningOnReceipt`. Cites R55. Blocked on Q2.

## Phase 1 — Payment verification & invoicing: notifications

123. **Notification arrives in list.** An arriving notification appears in the list. Verifies `notificationArrivesInList`. Cites R35.
124. **Reconnecting state.** A dropped stream shows a reconnecting state. Verifies `streamDropShowsReconnectingState`. Cites R35.
125. **No duplicates on resume.** Resuming the stream shows no duplicate items. Verifies `notificationsReconnectWithoutDuplicates`. Cites R35.
126. **Stream auth without token in URL.** The stream authenticates per the chosen option and never places a token in a URL. Verifies `streamNeverPlacesTokenInUrlAndReopensAfterRenewal`. Cites R59, L6. Blocked on Q3 and O7.
127. **Stream reopens after renewal.** The stream reopens after a successful renewal. Verifies `streamReopensAfterRenewal`. Cites R59, L18. Blocked on Q3 and O7.

## Phase 1 — Payment verification & invoicing: history, exports, and amounts

128. **History is merchant-scoped.** History shows only the signed-in merchant's data. Verifies `merchantSeesOnlyOwnHistory`. Cites R36. Blocked on Q2.
129. **Export is merchant-scoped.** An export request carries only the merchant's scope. Verifies `exportRequestScopedToMerchant`. Cites R36. Blocked on Q2.
130. **History amounts are strings.** Every history amount renders as a decimal string. Verifies `historyAmountsAreDecimalStrings`. Cites R36, L7. Blocked on Q2.
131. **Base-unit scaling.** A base unit scales by its token decimals before display. Verifies `baseUnitAmountsScaledByTokenDecimalsWithoutNumber`. Cites R37, L7. Blocked on Q2.
132. **Scaling uses no Number.** The scaling path uses string or bigint arithmetic only. Verifies `amountScalingUsesNoNumberType`. Cites R37, L7. Blocked on Q2.
133. **Zero-decimal token.** A token with zero decimals shows a whole amount. Verifies `zeroDecimalTokenShowsWholeAmount`. Cites R37. Blocked on Q2.

## Phase 1 — Release gate

134. **OIDC round trip.** The full sign-in round trip passes end to end against a faithful identity provider. Verifies `oidcRoundTripEndToEnd`. Cites L1.
135. **Accessibility gate.** Every Phase 1 screen passes the accessibility checks. Verifies `phase1ScreensPassAccessibilityChecks`. Cites L11.
136. **Enumeration gate.** Identity errors stay indistinguishable across account states. Verifies `identityErrorsIndistinguishableAcrossAccountStates`. Cites L4.
137. **Bundle gate.** No secret or non-public key is in the production bundle. Verifies `bundleContainsNoSecretEnvKeys`. Cites L10.

## Phase 2 — Intelligence engine (DRAFT, blocked on Q4)

138. **Upload progress.** An evidence upload shows progress. Verifies `evidenceUploadShowsProgress`. Cites R40. Blocked on Q4.
139. **Upload failure reason.** A failed upload shows a safe reason. Verifies `evidenceUploadShowsSafeFailures`. Cites R40. Blocked on Q4.
140. **Upload failure hides internals.** A failed upload shows no server internals. Verifies `evidenceFailureHidesServerInternals`. Cites R40. Blocked on Q4.
141. **Fields beside integrity.** Extracted fields show beside the integrity assessment. Verifies `analysisShowsFieldsWithIntegrity`. Cites R41. Blocked on Q4.
142. **Low confidence labelled.** A result below the contract threshold is labelled. Verifies `analysisLabelsLowConfidenceAgainstContractThreshold`. Cites R41. Blocked on Q4.
143. **Transaction-hash collection.** Entering a transaction hash starts collection. Verifies `txHashCollectsEvidenceAndMarksUploadNeeded`. Cites R42. Blocked on Q4.
144. **Collected evidence labelled.** Auto-collected evidence is labelled as collected. Verifies `collectedEvidenceLabelledAsCollected`. Cites R42. Blocked on Q4.
145. **Still-needed evidence.** Items still needed are marked as upload-needed. Verifies `stillNeededEvidenceMarkedAsUploadNeeded`. Cites R42. Blocked on Q4.
146. **Graph nodes and edges.** The evidence graph renders its nodes and edges. Verifies `evidenceGraphRendersNodesAndEdges`. Cites R43. Blocked on Q4.
147. **Partial analysis marked.** Partial analysis is marked partial. Verifies `evidenceGraphMarksPartialAnalysis`. Cites R43. Blocked on Q4.
148. **Narrative beside graph.** The narrative shows alongside the graph. Verifies `narrativeShownWithGraph`. Cites R43. Blocked on Q4.

## Phase 3 — Dispute resolution (DRAFT, blocked on Q4)

149. **Claim status.** Each claim shows supported, contradicted, or incomplete. Verifies `claimStatusShownForEachClaim`. Cites R44. Blocked on Q4.
150. **Claim evidence.** Each claim shows its evidence. Verifies `claimShowsItsEvidence`. Cites R44. Blocked on Q4.
151. **Statement apart from facts.** The claimant statement is separated from blockchain facts. Verifies `claimantStatementSeparatedFromFacts`. Cites R44. Blocked on Q4.
152. **Narrative sections.** The narrative sections render in their declared order. Verifies `narrativeKeepsAiTextDistinct`. Cites R45. Blocked on Q4.
153. **AI text distinct.** AI-generated text is visually distinct from verified facts. Verifies `aiTextVisuallyDistinctFromFacts`. Cites R45. Blocked on Q4.
154. **Confidence and recommendation.** The narrative shows confidence and the recommended resolution. Verifies `narrativeShowsConfidenceAndRecommendation`. Cites R45. Blocked on Q4.
155. **Timeline order.** Timeline events render in the declared order. Verifies `timelineShowsEventsInDeclaredOrder`. Cites R46. Blocked on Q4.
156. **Missing event not inferred.** A missing timeline event is shown absent, never inferred. Verifies `timelineMissingEventNotInferred`. Cites R46. Blocked on Q4.
157. **Smart-contract failure observed.** A contract failure is labelled observed, not inferred. Verifies `smartContractFailureMarkedObservedNotInferred`. Cites R47. Blocked on Q4.

## Phase 4 — Reputation & trust (DRAFT, blocked on Q4)

158. **Signals not only a score.** The wallet profile shows contributing signals. Verifies `walletProfileShowsSignalsNotOnlyScore`. Cites R48. Blocked on Q4.
159. **Signal source.** Each signal shows its source. Verifies `walletSignalShowsSource`. Cites R48. Blocked on Q4.
160. **Passport metric window.** Each passport metric shows its source window. Verifies `trustPassportShowsSourceWindows`. Cites R49. Blocked on Q4.
161. **Metric value with window.** A metric shows its value and window together. Verifies `passportMetricShowsValueWithWindow`. Cites R49. Blocked on Q4.
162. **Counterparty relationships.** The behaviour graph renders its relationships. Verifies `counterpartyGraphRendersRelationships`. Cites R50. Blocked on Q4.
163. **Cluster evidence.** Each cluster shows its evidence. Verifies `counterpartyGraphShowsClusterEvidence`. Cites R50. Blocked on Q4.

## Phase 5 — Fraud intelligence & institutional API (DRAFT, blocked on Q4)

164. **Alert classification.** Each alert shows its classification. Verifies `fraudAlertDistinguishesConfirmedFromSuspected`. Cites R51. Blocked on Q4.
165. **Alert evidence.** Each alert shows its evidence. Verifies `fraudAlertShowsEvidence`. Cites R51. Blocked on Q4.
166. **Confirmed and suspected distinct.** Confirmed and suspected alerts are visually distinct. Verifies `confirmedAndSuspectedVisuallyDistinct`. Cites R51. Blocked on Q4.
167. **Cross-chain chains.** A cross-chain relationship shows the chains involved. Verifies `crossChainRelationshipShowsLinkingEvidence`. Cites R52. Blocked on Q4.
168. **Portal key shown once.** A portal key is shown exactly once. Verifies `portalKeyShownExactlyOnce`. Cites R53. Blocked on Q4.
169. **Portal plaintext-once rule.** The portal applies the plaintext-once rule to every key. Verifies `apiPortalKeysFollowPlaintextOnceRule`. Cites R53. Blocked on Q4.

## Cross-phase — Capability links (DRAFT, blocked on Q11)

170. **Unguessable identifier.** A capability link uses an unguessable identifier. Verifies `capabilityLinkIdIsUnguessable`. Cites R54. Blocked on Q11.
171. **No PII on link.** A capability link exposes no PII. Verifies `capabilityLinkExposesNoPii`. Cites R54. Blocked on Q11.
172. **Link not cached.** A capability link is never cached. Verifies `capabilityLinkNotCached`. Cites R54, R57. Blocked on Q11.
