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

66. **Enrollment shows URI and QR.** Beginning enrollment shows the `otpauth://` URI and a QR code. Verifies `enrollmentBeginShowsProvisioningUriAndQr`. Cites R11. Blocked on Q1.
67. **Confirm needs a live code.** Confirmation succeeds only with a valid live code. Verifies `enrollmentConfirmRequiresLiveCode`. Cites R11. Blocked on Q1.
68. **Recovery codes shown once.** The 10 recovery codes appear exactly once. Verifies `voluntaryEnrollmentShowsRecoveryCodesExactlyOnce`. Cites R11. Blocked on Q1.
69. **Recovery codes not persisted.** Recovery codes are never written to any storage. Verifies `recoveryCodesNeverPersistedClientSide`. Cites R11, L3. Blocked on Q1.
70. **Acknowledgement required.** The user must acknowledge the recovery codes before continuing. Verifies `recoveryCodesRequireAcknowledgement`. Cites R11. Blocked on Q1.
71. **Disable needs password and code.** Disabling MFA requires the current password and a valid TOTP. Verifies `disableMfaRequiresPasswordAndCode`. Cites R12. Blocked on Q1.
72. **Regenerate shows once.** Regenerated recovery codes show exactly once. Verifies `regenerateRecoveryCodesShowsOnce`. Cites R13. Blocked on Q1.
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

## Phase 1 — Payment verification & invoicing (coarse, to be decomposed)

98. **Invoices.** Build the invoice list and detail with the states from R31 and decimal-string amounts. Cites R31, L7. Blocked on Q2.
99. **Payment state machine view.** Show the current state, reorg reversals, `HELD` from any state, and the neutral hold copy. Cites R32, R56, L8. Blocked on Q2.
100. **Receipts.** Show no receipt before `ATTESTED`, and show the hold state after an attested reorg. Cites R33, R34, L8. Blocked on Q2.
101. **Wallet monitoring and unknown-token state.** Cites R38, R39. Blocked on Q2.
102. **Address-poisoning warnings.** Cites R55. Blocked on Q2.
103. **In-app notification list.** Cites R35. Blocked on Q3.
104. **Notification stream authentication.** Cites R59. Blocked on Q3 and O7.
105. **History, exports, and base-unit scaling.** Cites R36, R37. Blocked on Q2.
106. **Phase 1 release gate.** Cites L1, L4, L10, L11.

## Phase 2 — Intelligence engine (coarse, DRAFT, blocked on Q4)

107. **Evidence upload.** Cites R40. Blocked on Q4.
108. **Evidence analysis view.** Cites R41. Blocked on Q4.
109. **Transaction-hash collection.** Cites R42. Blocked on Q4.
110. **Evidence graph.** Cites R43. Blocked on Q4.

## Phase 3 — Dispute resolution (coarse, DRAFT, blocked on Q4)

111. **Dispute case view.** Cites R44. Blocked on Q4.
112. **Dispute narrative and timeline.** Cites R45, R46. Blocked on Q4.
113. **Smart-contract evidence.** Cites R47. Blocked on Q4.

## Phase 4 — Reputation & trust (coarse, DRAFT, blocked on Q4)

114. **Wallet reputation.** Cites R48. Blocked on Q4.
115. **Trust passport and counterparty graph.** Cites R49, R50. Blocked on Q4.

## Phase 5 — Fraud intelligence & institutional API (coarse, DRAFT, blocked on Q4)

116. **Fraud alerts.** Cites R51. Blocked on Q4.
117. **Cross-chain relationships.** Cites R52. Blocked on Q4.
118. **Institutional API portal.** Cites R53. Blocked on Q4.

## Cross-phase — Capability links (DRAFT, blocked on Q11)

119. **Capability links.** Cites R54. Blocked on Q11.
