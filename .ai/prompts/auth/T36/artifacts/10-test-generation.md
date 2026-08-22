<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T36 · Phase 10 — Test Generation

Test-only task convention (same as T27/T32/T33/T35): the task's entire deliverable is test code,
already written across Phases 6 and 9 in `EndToEndLifecycleIntegrationTest.java`. No production code
exists to test separately. This phase is purely the manifest.

## `EndToEndLifecycleIntegrationTest` (1 test method, composed flow — 11 steps after Phase 11)

| Flow step | Acceptance criterion | Requirement(s) | Assertions |
|---|---|---|---|
| Register | AC1 | R1 | `202 Accepted` via real HTTP |
| Verify email | AC1 | R4 | `204 No Content` via real HTTP; raw token obtained only via the real `auth.email.requested` outbox event (enumeration-safety), full schema-shape asserted (Phase 11, Kimi Gap 8); `auth.user.lifecycle(ACTIVE)` event observed (Kafka, JSON-parsed); account's persisted `status()` directly asserted `ACTIVE` (Phase 9, Kimi Finding 6) |
| Login (password) | — (task-statement step, no dedicated AC) | — | Full authorize flow succeeds (`code` present) before the account holds any mandatory-MFA role — added Phase 11, Kimi Gap 7: the task statement lists this as its own step and the test previously skipped it entirely |
| Admin assigns MERCHANT | — (authz plumbing) | — | `204 No Content` via real HTTP, admin-Bearer-authenticated; grant's persistence directly asserted via `RoleService.resolveEffectiveRoles` (Phase 11, Kimi Gap 4) |
| Next login blocked pre-enrollment | AC2 | R24, L10 | `/oauth2/authorize` layer: no `code`, `FOUND` status (Phase 9, Kimi Finding 8), `Location` contains `/login?error` |
| Enroll TOTP | — (no HTTP surface, Phase 4 gate) | R24 (indirectly) | Direct `MfaService.beginEnroll`/`.confirm` |
| Login with TOTP | AC3 | R24, L10, L6 | Full authorize flow succeeds, `code` present, `state` round-trips, issued JWT's `amr` contains `otp` |
| Create API key | AC4 | R30 | `201 Created`; `plaintextKey` matches `ck_live_` shape; response body contains no 64-hex hash (Phase 9, Kimi Finding 5) |
| Exchange key for JWT | AC5 | R31, L8, L9 | `200 OK`; `token_type=Bearer`, `expires_in=600` (Phase 9, Kimi Finding 4); JWT `sub`/`scope`/`amr`/`roles` (roles added Phase 11, Kimi Gap 2 — the only test proving a real, persisted role resolves through the full exchange path, not a mocked one) |
| Call session list | AC6 | R36 | `200 OK`; exactly one family; `createdAt`/`rotatedAt` non-blank, `deviceLabel` field present (Phase 9, Kimi Finding 3) |
| Revoke session | AC7 | R37 | `204 No Content`; follow-up list empty; family row `revokedAt`/`revokedReason=USER_REVOKED`; live SAS `OAuth2Authorization` removed (Phase 9, Kimi Finding 7) |

**Named test**: per Phase 1/2's finding, `package.md` §8 has no valid entry for this task — the
header's `shouldConformToAuthOpenApiContract` belongs to T33's unrelated contract test. This test's
method name, `shouldCompleteFullMerchantIdentityLifecycle`, was chosen in Phase 5 to match this
module's `*IntegrationTest` naming convention; it does not claim to satisfy any package.md-named test.

## Boundary/negative coverage folded into the one composed flow (per the frozen brief's Scope)

- **AC2 is the task's one genuine negative case**: a `MERCHANT`-assigned account with no confirmed
  TOTP enrollment must be blocked at the `/oauth2/authorize` layer — asserted inline, not as a
  separate test method, matching the frozen brief's "one composed end-to-end test method" decision.
- Every other boundary this task's own six requirement IDs could raise (locked accounts, wrong
  passwords, expired/reused verification tokens, revoked/malformed API keys, bulk session revoke,
  token reuse detection) is out of this task's scope (frozen brief) and already covered by the
  dedicated test files that own those requirements (`SasLoginIntegrationTest`,
  `AccountPersistenceIntegrationTest`, `ApiKeyLifecycleIntegrationTest`, `SessionIntegrationTest`,
  `RefreshTokenFamilyIntegrationTest`) — T36 is deliberately the *composition* proof, not a
  re-verification of ground those files already cover.

## Kimi Phase 11 test review — gaps closed

All 8 findings verified against source before disposition (per this session's standing practice).

| Gap | Disposition |
|---|---|
| Gap 1 — TOTP code may age across the multi-hop authorize flow | **Rejected, restated from Phase 8 Finding 9** — no new evidence. `SasLoginIntegrationTest`'s already-passing `confirmedMfaRequiresCodeToFinishAuthorizeFlow`/`issuedTokenHasOtpAmrAndAcrAfterMfa` use the identical generate-then-multi-hop pattern; Kimi's own earlier-cited 90s tolerance window covers it. |
| Gap 2 — AC5 doesn't assert full L9 claims | **Narrowly accepted.** Verified `ApiKeyTokenIssuerTest` uses `@Mock RoleService` (Mockito, not a real DB) — meaning this task's own test is genuinely the only place proving a *real, persisted* role resolves correctly through the full exchange path. Added a `roles` claim assertion. The remaining static/mechanical L9 claims (`iss`, `aud`, `client_id`, `acr`, timestamps) add no equivalent real-data value and stay exclusively in `ApiKeyTokenIssuerTest` — not duplicated. |
| Gap 3 — AC4 create-response field set only partially asserted | **Rejected, restated from Phase 8 Finding 5** — no new evidence. `ApiKeyLifecycleIntegrationTest` already owns exhaustive field-set coverage; AC4 as the frozen brief states it is fully covered by the existing regex plus the Phase 9 hash-leak guard. |
| Gap 4 — MERCHANT role assignment not asserted before the blocked-login check | **Accepted.** A genuinely new point: without this, a silently-no-op role assignment would make AC2's "blocked" assertion pass for the wrong reason (no role at all, not a genuine enrollment gate). Added `assertThat(roleService.resolveEffectiveRoles(merchantUuid)).contains("MERCHANT")` immediately after the HTTP assignment. |
| Gap 5 — `earliest`-offset Kafka consumer may be slow in a larger future suite | **Rejected, restated from Phase 8 Finding 11** — no new evidence. Matches `AccountPersistenceIntegrationTest`'s own already-accepted pattern; speculative future-scale concern, not a current defect. |
| Gap 6 — no assertion that the exchanged JWT authorizes session endpoints | **Rejected — already satisfied.** Verified: `assertThat(exchangedClaims.getStringListClaim("amr")).contains("api_key")` already exists in the code (added before Phase 9), immediately after the exchange step. Kimi's suggested line is a near-exact duplicate of an assertion already present. |
| Gap 7 — no login-with-password step before MERCHANT assignment | **Accepted, and more significant than framed.** Re-reading the task statement ("register → verify email → **login (password)** → admin assigns MERCHANT → ...") against the actual test flow found this step was not merely under-asserted — it was **entirely missing**: the test jumped straight from email verification to admin role assignment. Added a real, successful password-only full-authorize-flow login (no MFA role held yet, so no gate applies) as its own step, between verify-email and admin-assigns-MERCHANT, matching the task statement's literal ordering. |
| Gap 8 — `auth.email.requested` payload shape under-verified | **Accepted.** Added `accountUuid`/`occurredAt` assertions in `awaitRawVerificationToken`, completing verification of every field `email-requested.v1.schema.json` requires (previously only `purpose`/`token` were checked). |

`EndToEndLifecycleIntegrationTest` grew from 10 to 11 flow steps (the missing login-password step),
plus 4 additional assertions folded into existing steps (Gaps 2, 4, 8, and the already-satisfied
Gap 6 needing no change).

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors, after every Phase 9 and Phase 11
  change.
- `mvn -pl services/auth test -Dtest=EndToEndLifecycleIntegrationTest` — re-run against real Docker
  Testcontainers (Postgres + Kafka) after the Phase 11 fixes. Registration (real HTTP + the CSRF fix)
  continues to pass; the run remains blocked at the same already-logged, pre-existing Kafka
  producer→broker environment issue (unrelated to this task's code, independently reproduced on an
  unrelated already-merged test — see Phase 6/9 notes). No regression introduced by any Phase 9 or
  Phase 11 change — the failure point and error signature are unchanged.

---

**Phase 10 complete — test manifest written and updated post-Phase-11.** Proceed to Phase 12
(Specification Verification) on approval.
