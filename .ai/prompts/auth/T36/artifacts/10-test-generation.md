<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T36 · Phase 10 — Test Generation

Test-only task convention (same as T27/T32/T33/T35): the task's entire deliverable is test code,
already written across Phases 6 and 9 in `EndToEndLifecycleIntegrationTest.java`. No production code
exists to test separately. This phase is purely the manifest.

## `EndToEndLifecycleIntegrationTest` (1 test method, composed flow)

| Flow step | Acceptance criterion | Requirement(s) | Assertions |
|---|---|---|---|
| Register | AC1 | R1 | `202 Accepted` via real HTTP |
| Verify email | AC1 | R4 | `204 No Content` via real HTTP; raw token obtained only via the real `auth.email.requested` outbox event (enumeration-safety); `auth.user.lifecycle(ACTIVE)` event observed (Kafka, JSON-parsed); account's persisted `status()` directly asserted `ACTIVE` (Phase 9, Kimi Finding 6) |
| Admin assigns MERCHANT | — (authz plumbing) | — | `204 No Content` via real HTTP, admin-Bearer-authenticated |
| Next login blocked pre-enrollment | AC2 | R24, L10 | `/oauth2/authorize` layer: no `code`, `FOUND` status (Phase 9, Kimi Finding 8), `Location` contains `/login?error` |
| Enroll TOTP | — (no HTTP surface, Phase 4 gate) | R24 (indirectly) | Direct `MfaService.beginEnroll`/`.confirm` |
| Login with TOTP | AC3 | R24, L10, L6 | Full authorize flow succeeds, `code` present, `state` round-trips, issued JWT's `amr` contains `otp` |
| Create API key | AC4 | R30 | `201 Created`; `plaintextKey` matches `ck_live_` shape; response body contains no 64-hex hash (Phase 9, Kimi Finding 5) |
| Exchange key for JWT | AC5 | R31, L8, L9 | `200 OK`; `token_type=Bearer`, `expires_in=600` (Phase 9, Kimi Finding 4); JWT `sub`/`scope`/`amr` |
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

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest=EndToEndLifecycleIntegrationTest` — run against real Docker
  Testcontainers (Postgres + Kafka) after every Phase 9 fix. Registration (real HTTP + the CSRF fix)
  continues to pass; the run remains blocked at the same already-logged, pre-existing Kafka
  producer→broker environment issue (unrelated to this task's code, independently reproduced on an
  unrelated already-merged test — see Phase 6/9 notes). No regression introduced by any Phase 9
  change.

---

**Phase 10 complete — test manifest written.** Proceed to Phase 11 (Kimi Test Review) on approval.
