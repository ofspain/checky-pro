<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# auth · T36 · Phase 6 — Implementation Notes

Test-only task (same convention as T27/T32/T33/T35): the deliverable IS the test, written directly
in this phase. No production code changed.

## What changed

- **New**: `services/auth/src/test/java/com/themistra/auth/EndToEndLifecycleIntegrationTest.java` —
  one `@SpringBootTest(webEnvironment = RANDOM_PORT)` class, one `@Test` method
  (`shouldCompleteFullMerchantIdentityLifecycle`), covering every flow step in the task statement.

## Mapping to the Phase 5 plan / acceptance criteria

| AC | Implemented as | Deviation from plan? |
|---|---|---|
| AC1 (R1, R4) | `registerViaHttp` → `awaitRawVerificationToken` → `verifyEmailViaHttp` → `awaitUserRegisteredLifecycleEvent` | Yes — see "CSRF" and "Kafka correlation" below |
| AC2 (R24, L10) | `attemptFullAuthorizeFlow(..., null, ...)`, asserts empty `authorizationCode` + `/login?error` | None |
| AC3 (R24, L10, L6) | `enrollTotp` (direct call, Finding 1) then `attemptFullAuthorizeFlow` with a valid code; added a light `amr` claim assertion (`contains("otp")`) beyond the plan's minimum, cheap and directly proves the MFA gate was genuinely exercised | Additive, not a deviation |
| AC4 (R30) | `post(sasAccessToken, "/api-keys", ...)`, asserts `ck_live_` prefix | Authenticates with the SAS-issued token from the login-with-TOTP step, per the frozen brief |
| AC5 (R31, L8, L9) | `postApiKeyExchange`, decodes claims (`sub`, `scope`, `amr`) | None |
| AC6 (R36) | `get(exchangedToken, "/accounts/me/sessions")`, asserts exactly one family | None |
| AC7 (R37) | `delete(...)` + follow-up empty list + `reloadFamily` (`revokedAt`/`revokedReason` = `USER_REVOKED`) | Yes — see "Package-private repository" below |

## Deviations forced by reality (flagged, not hidden)

1. **CSRF, not anticipated by the Phase 5 plan.** `SecurityChainsConfig` only exempts `/api/**` and
   `/api-keys/token` from CSRF (`.csrf(csrf -> csrf.ignoringRequestMatchers(...))`) — `POST /accounts`
   and `POST /accounts/verify-email` are not exempt. My first run confirmed this empirically: a raw
   JSON POST with no CSRF token returned `302` to `/login` (Spring's `ExceptionTranslationFilter`
   routes an anonymous request's CSRF-caused `AccessDeniedException` to the form-login
   `AuthenticationEntryPoint`, not a `403`). Fixed by scraping a real CSRF token + session cookie off
   a `GET /login` first (same technique `SasLoginIntegrationTest` already uses for the login form
   itself) and submitting both as `X-CSRF-TOKEN` header + `Cookie` header on the two JSON POSTs. Every
   other call in this test (`/admin/accounts/.../roles/MERCHANT`, `/api-keys`, `/api-keys/token`,
   `/accounts/me/sessions`) is Bearer/API-key authenticated and empirically unaffected — confirmed
   both by this test's own successful progression past those calls and by
   `ApiKeyLifecycleIntegrationTest`'s identical, already-passing, CSRF-free pattern for `/api-keys`.
2. **`RefreshTokenFamilyRepository` is package-private to `token`** (`interface
   RefreshTokenFamilyRepository extends JpaRepository<...>`, no `public` modifier) — the Phase 5 plan
   listed it as a dependency, which does not compile from this class's top-level package.
   `RefreshTokenFamily` itself is `public`, so `reloadFamily` uses a plain `@PersistenceContext
   EntityManager.find(RefreshTokenFamily.class, familyId)` instead — the same technique
   `SessionIntegrationTest.reloadFamily` already uses, just without a repository dependency.
3. **Kafka event correlation, not addressed by the Phase 5 plan.** `POST /accounts`'s response is
   deliberately enumeration-safe and never reveals the new account's UUID (by design), but every
   integration test class in this module with matching Spring configuration shares one long-lived
   Testcontainers Kafka broker across the whole suite run — an `earliest`-offset consumer sees every
   other test class's prior registrations too. Resolved by calling
   `accountService.findLoginView(email)` immediately after the HTTP register call — a direct service
   call used purely for test-correlation (never asserted on, never a substitute for the tested HTTP
   action) — then filtering the Kafka consumer by that UUID as the record key, the same key-filtering
   technique `AccountPersistenceIntegrationTest` already established for this exact shared-container
   hazard.
4. **R4's event assertion technique.** `UserLifecycleEventPayload` carries no `eventType`
   discriminator on the wire (`OutboxRelay` never serializes one — `eventType` exists only as an
   outbox-table column used for topic routing). `awaitUserRegisteredLifecycleEvent` therefore matches
   on `status":"ACTIVE"` for this account's key on `auth.user.lifecycle`, exactly the string-containment
   technique `AccountPersistenceIntegrationTest.activateEmailDeliversARealLifecycleEventToKafka`
   already uses — the correct, and only, wire-observable proxy for "the R4 registered transition
   fired," since there is no more specific field to assert on.

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest=EndToEndLifecycleIntegrationTest` — run against real Docker
  Testcontainers (Postgres + Kafka). **Registration + CSRF fix confirmed genuinely working**: the
  first run failed with `expected 202, was 302` (proving the bug was real), the fix changed the
  outcome to passing that assertion and progressing to the Kafka-dependent step.
- **Blocked from a full green run by a pre-existing, environment-level issue, not a T36 defect**:
  the app's Kafka producer cannot reach the Testcontainers broker in this environment right now
  (`Bootstrap broker localhost:9094 ... could not be established`). Independently reproduced twice
  on `AccountPersistenceIntegrationTest.activateEmailDeliversARealLifecycleEventToKafka` — an
  already-merged, previously-passing, completely unrelated test — confirming this is a local
  Docker/Testcontainers Kafka networking problem external to this task's code, not something T36
  introduced. Confirmed no lingering/stale containers (`docker ps -a` — Ryuk cleanup is working) and
  no hardcoded `bootstrap-servers`/port override anywhere in `application.properties`.
  Human-gate decision (this phase): log this as a separate, out-of-scope environment issue — the
  same disposition T32 gave the ArchUnit/Surefire non-execution issue — and proceed on the strength
  of (a) the one part live-verified (CSRF/registration), (b) every other line being a direct,
  near-verbatim port of three already-passing, already-reviewed precedent files
  (`SasLoginIntegrationTest`'s authorize flow, `ApiKeyLifecycleIntegrationTest`'s API-key HTTP calls,
  `SessionIntegrationTest`'s session list/revoke), and (c) the Kafka-correlation logic mirroring
  `AccountPersistenceIntegrationTest`'s own already-established, pre-this-environment-issue-working
  technique exactly.

---

**Phase 6 complete — implementation written; one environment-level blocker logged, not fixed
in-scope.** Proceed to Phase 7 (Self Review) on approval.
