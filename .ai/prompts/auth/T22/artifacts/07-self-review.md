# auth · T22 · Phase 7 — Self Review

Findings only, ranked by severity.

---

1. **Issue:** `attemptFullAuthorizeFlow` generates a fresh `state` per attempt and threads it through both `/oauth2/authorize` calls, but never verifies the `state` value actually returned in the final redirect matches what was sent.
   **Severity:** Medium.
   **Evidence:** `authn/SasLoginIntegrationTest.java:581-607` (`attemptFullAuthorizeFlow`) calls `authorizeUrl(pkce.challenge(), state)` twice with the same `state`, and `extractQueryParam(finalLocation, "code")` at `:605` reads only `code` from the final `Location`, never checking `extractQueryParam(finalLocation, "state")` against the `state` argument. `state`-echo verification is part of what a real OAuth2 client must do to prevent the authorization-response CSRF the parameter exists to defend against — a regression in SAS that dropped or corrupted `state` on the way back would go completely undetected by this suite.
   **Recommendation:** Add an assertion (in at least `issuedTokenHasOtpAmrAndAcrAfterMfa`, the test that already has everything needed) that `extractQueryParam(finalLocation, "state")` equals the `state` passed in — cheap to add, and closes a real coverage gap rather than just a style nit.

2. **Issue:** `V6__oauth2_authorization_device_and_user_code_columns.sql` uses plain `ADD COLUMN`, not `ADD COLUMN IF NOT EXISTS`, unlike `V5__lockout_cleanup_and_shedlock.sql`'s established `CREATE TABLE IF NOT EXISTS`/`CREATE INDEX IF NOT EXISTS` defensive style.
   **Severity:** Low-Medium.
   **Evidence:** `db/migration/V6__oauth2_authorization_device_and_user_code_columns.sql` vs. `db/migration/V5__lockout_cleanup_and_shedlock.sql`'s two `IF NOT EXISTS` clauses. Flyway's own tracking (`flyway_schema_history`) makes this safe under normal single-application operation — the gap only matters for the same class of local-dev/partial-failure re-run resilience V5 was written to tolerate.
   **Recommendation:** Postgres supports `ADD COLUMN IF NOT EXISTS` since 9.6 (this stack's Postgres is newer) — change all 8 `ADD COLUMN` clauses to `ADD COLUMN IF NOT EXISTS` for consistency with the established convention, at no cost.

3. **Non-issue, confirmed correct on review:** `SasLoginIntegrationTest` now imports `com.themistra.auth.token.AuthClientsProperties` — a new cross-module reference from `authn` to `token`. Checked whether this trips any module-boundary `@ArchTest`: `ArchitectureTest` is annotated `@AnalyzeClasses(..., importOptions = ImportOption.DoNotIncludeTests.class)`, so test classes aren't analyzed by any of its rules at all, and none of the existing rules restrict `authn`↔`token` imports for production code either (the reverse direction — `token` importing `authn` — is already established, e.g. `SecurityChainsConfig` importing `LoginFailureHandler`). Confirmed via the full suite's `ArchitectureTest` run (0 failures) rather than assumed.

4. **Non-issue, confirmed correct on review:** `exchangeCodeForToken` never checks the HTTP status code before parsing the response body, only checks for a present `access_token` field. Considered flagging this, but the failure mode is already handled correctly in substance: a non-200 error response (RFC 6749 shape, e.g. `{"error":"invalid_grant"}`) still parses as valid JSON, has no `access_token` key, and the existing `IllegalStateException` message includes the full response body — so a failure here is already loud and diagnosable, just via a slightly different code path than an explicit status check would give. Not worth a separate fix.

## Open Questions
None. Both findings above are additive fixes, not blockers — the four tests pass correctly as written; the review-flagged coverage gap (finding 1) and style inconsistency (finding 2) don't invalidate what's already been verified against a real server.
