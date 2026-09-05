# auth · T22 · Phase 6 — Implementation Notes

## Summary
Implemented all four planned tests in `authn/SasLoginIntegrationTest.java`, plus the supporting flow infrastructure the Phase 5 plan specified (PKCE generation, cookie-carrying HTTP helpers, JWT claim parsing). All four pass individually and as a class, run against real Postgres/Kafka/HTTP via Testcontainers — not written-but-unverified like every prior attempt at this category of test in this codebase.

**One deviation from the frozen brief's file scope, authorized mid-phase, not silently done:** a new Flyway migration, `V6__oauth2_authorization_device_and_user_code_columns.sql`. The frozen brief scoped this task as test-only with production code explicitly out of bounds. Running the tests surfaced a real, previously-invisible production bug that made R25/R26 impossible to complete no matter how the test code was written — flagged to the human, who authorized fixing it as part of this phase rather than deferring the whole task again.

## Mapping to the plan

All Phase 5–planned methods implemented as specified, with one structural correction (below):
- `PkcePair generatePkce()`, `authorizeUrl(...)`, `getWithCookies(...)`, `mergeCookies(...)` — implemented as planned.
- `attemptLogin`'s POST logic factored into `postLoginForm(...)` — implemented as planned, existing `attemptLogin` behavior unchanged (verified: all 10 of T20's tests in this class still pass unmodified).
- `exchangeCodeForToken(...)`, `parseClaims(...)` — implemented as planned, using the already-confirmed-real `nimbus-jose-jwt:9.47:compile` dependency.
- Four test methods — implemented as planned, using descriptive names (not the literal `package.md` §8 strings) since two of those strings are already claimed by T20's differently-shaped tests at other layers (`TokenClaimsCustomizerTest.shouldIssueTokenWithOtpAmrAndAcrAfterMfa` is a unit test of the customizer directly; this task's version is the full-flow proof) — each new test's comment states exactly which named test/requirement it satisfies, matching this codebase's established practice for multi-layer verification of one requirement.

## Deviation #1 (test code): the governing mechanism's "resume the saved request" step doesn't work as designed — confirmed empirically, not assumed

The frozen brief's primary mechanism assumed `LoginSuccessHandler`'s `SavedRequestAwareAuthenticationSuccessHandler` would redirect back to the original `/oauth2/authorize` request after a successful login (the standard Spring Security "protected resource" pattern). Running the R25 test showed this doesn't happen: the unauthenticated `/oauth2/authorize` response carries **no `Set-Cookie` at all** (confirmed via temporary diagnostic logging, removed before finalizing), meaning no session — and therefore no saved request — exists at that point. A successful login instead redirects to the default target (`/`), which then 500s when followed (see Deviation #2).

This is exactly the contingency the frozen brief already documented and pre-authorized: "if step 6's redirect doesn't land back on `/oauth2/authorize` as expected... the fallback is issuing `/oauth2/authorize` again directly with the post-login cookies." That fallback is what's actually implemented — it's not a fallback branch in the code, it *is* the mechanism now, since the primary path was confirmed not to apply. `attemptFullAuthorizeFlow`'s Javadoc documents this as what running it actually showed, not what was originally planned.

## Deviation #2 (production schema, authorized by the human): `oauth2_authorization` was missing 8 columns

Re-issuing `/oauth2/authorize` after login (Deviation #1's fix) surfaced a second, unrelated failure: `PSQLException: column "user_code_value" does not exist`. Spring Authorization Server's default `JdbcOAuth2AuthorizationService` (wired in `AuthorizationServiceConfig`, unchanged) generates SQL referencing all 32 columns of its reference schema unconditionally, including 8 Device-Authorization-Grant columns (`user_code_*`, `device_code_*`) that `V1__auth_baseline_schema.sql` never included — a reasonable-looking simplification at the time (this service doesn't support that grant type) that happens to break *every* query against the table, not just device-grant ones, since the library doesn't know to omit them from a query it isn't grant-type-aware about.

This has been broken since V1 was written; it was never exercised because nothing before this task actually drove `/oauth2/authorize` to a real database read/write. Confirmed by decompiling the library's own bundled `oauth2-authorization-schema.sql` and comparing column-for-column.

**Fix:** `V6__oauth2_authorization_device_and_user_code_columns.sql` — additive only, adds the 8 missing columns with the same `TEXT`/`TIMESTAMPTZ` type translation V1 already applied to every other `blob`/`timestamp` column in this table. No existing column touched, no application code changed.

## Files changed
- `authn/SasLoginIntegrationTest.java` — extended (imports, 2 new fields, ~10 new private helpers/records, 4 new test methods, 1 small refactor extracting `postLoginForm` from the existing `attemptLogin`).
- `db/migration/V6__oauth2_authorization_device_and_user_code_columns.sql` — new, additive.

## Verification
- `mvn -pl services/auth -am test-compile`: clean.
- All four new tests pass individually, then together, then as part of the full `SasLoginIntegrationTest` class (14/14, including T20's original 10, unmodified behavior confirmed).
- Full suite: **486 tests run, 480 passing, 6 errors** — identical set of 6 pre-existing, already-documented, unrelated failures as before this phase (Kafka timing, the breach-check/audit-FK ordering bug ×3, Mockito strict-stubbing ×2). The V6 migration introduced no new failures anywhere else in the suite.

## Acceptance criteria
| ID | Satisfied by |
|---|---|
| R24 | `merchantWithoutEnrollmentCannotFinishAuthorizeFlow` — the full authorize flow yields no code |
| R25 | `confirmedMfaRequiresCodeToFinishAuthorizeFlow` — password-only fails, valid TOTP succeeds |
| R26 | `issuedTokenHasOtpAmrAndAcrAfterMfa` — an actually-issued, actually-parsed JWT has `amr: ["pwd","otp"]`, `acr: urn:themistra:acr:otp` |
| (positive control) | `issuedTokenHasPwdOnlyAmrThroughFullFlowWhenMfaNotRequired` |
