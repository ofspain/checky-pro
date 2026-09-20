<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). -->

# auth · T36 · Phase 7 — Self Review

## Findings

### Finding 1 — "Empirically unaffected by CSRF" claim for `/admin/accounts/.../roles/{roleName}` is not actually verified

**Issue.** The class Javadoc (`EndToEndLifecycleIntegrationTest.java:280-287`) and the Phase 6
implementation notes both state that every Bearer/API-key-authenticated call in this test —
including `assignRoleViaHttp`'s `POST /admin/accounts/{accountUuid}/roles/{roleName}` — is
"empirically unaffected" by CSRF, "confirmed by `ApiKeyLifecycleIntegrationTest`'s already-passing
precedent." This overstates what was actually checked. The only live run performed timed out inside
`awaitRawVerificationToken` (flow step 2), which executes *before* `assignRoleViaHttp` (step 3) —
so that line was never reached, let alone verified. The precedent it leans on
(`ApiKeyLifecycleIntegrationTest`) only exercises `/api-keys` and `/api-keys/token`; a repo-wide
search (`grep -rln "/admin/accounts/" src/test/java`) confirms **no existing test in this codebase
calls `/admin/accounts/.../roles/...` over real HTTP at all** — there is no genuine precedent for
this specific endpoint, CSRF-exempt or not. Given `POST /accounts` failed exactly this way on the
first run before being fixed, there is a real, concrete chance `assignRoleViaHttp` fails identically
once the logged Kafka environment blocker clears and the test reaches that line.

**Severity.** Medium — a plausible, previously-demonstrated failure mode on an untested code path;
not yet disproven, and the artifact prose currently overstates confidence.

**Evidence.** `EndToEndLifecycleIntegrationTest.java:194-195` (the register call that *did* fail this
way on the first run) vs. line 215 (`assignRoleViaHttp`, never reached); class Javadoc lines 280-287;
Phase 6 notes' "Deviations" §1, last sentence.

**Recommendation.** Correct the overstated claim in both the class Javadoc and the Phase 6 notes to
say "not yet exercised by a live run" rather than "confirmed." Once the logged Kafka environment
issue clears, re-run and either drop the caveat (if it passes) or apply the same CSRF-context fix
used for `/accounts`/`/accounts/verify-email` (if it fails the same way).

### Finding 2 — Kafka payload field access has no null-safety for an unexpected message shape

**Issue.** `awaitRawVerificationToken` calls `payload.get("purpose").asText()` and
`payload.get("token").asText()` (`EndToEndLifecycleIntegrationTest.java:373-375`) without checking
either `JsonNode` for null first. Both fields are `required` in
`contracts/events/auth/email-requested.v1.schema.json` for every message this codebase currently
produces on `auth.email.requested`, so this cannot fail against production code as it stands today —
but the consumer subscribes to the topic generically (no schema/type filter), so a differently-shaped
message on the same topic (a future schema evolution, or a stray message from unrelated test
tooling) would throw an unhandled `NullPointerException` from inside the `Awaitility.untilAsserted`
lambda, surfacing as an opaque condition-evaluation error rather than a clear assertion failure.

**Severity.** Low — no known way to trigger this against current production code; a pure
defensive-coding gap on a boundary condition the review checklist asks about.

**Evidence.** `EndToEndLifecycleIntegrationTest.java:373-375`.

**Recommendation.** Guard with a null check (skip the record if `payload.get("purpose")` or
`payload.get("token")` is null) before calling `.asText()`, matching the review checklist's
null-safety criterion. Optional — low real-world likelihood — leave to Phase 9's judgment.

## Checked and cleared (no finding)

- **Module boundaries** — `EndToEndLifecycleIntegrationTest` imports `RefreshTokenFamily` (a
  `token`-module `@Entity`) from the top-level `com.themistra.auth` package, but
  `ArchitectureTest`'s `@AnalyzeClasses(..., importOptions = ImportOption.DoNotIncludeTests.class)`
  (verified at `ArchitectureTest.java:42`) excludes all test code from analysis — this file cannot
  trip `shouldPreventCrossModuleEntityImports` regardless of what it imports.
- **Enumeration-safety / secret-handling** — `AccountService.findLoginView`'s `passwordHash` field
  is never read or logged; only `.accountUuid()` is used, and only for test correlation. The raw
  TOTP secret and verification token are never logged or asserted via string containment beyond
  what each flow step requires.
- **Thread-safety** — `AtomicReference<String>` correctly used for cross-lambda-invocation state in
  `awaitRawVerificationToken`; Awaitility's default `untilAsserted` polling is single-threaded.
- **Transaction boundaries** — every direct service call (`bootstrapAdminBearerToken`, `enrollTotp`,
  `ensureRoleExists`) matches the exact sequence and transaction shape already established and
  accepted in `SasLoginIntegrationTest`/`ApiKeyLifecycleIntegrationTest`.
- **Ordering/race conditions** — `awaitRawVerificationToken` (step 2) always completes before
  `verifyEmailViaHttp` is called, so the merchant account's own `auth.user.lifecycle` event cannot
  exist yet during that poll loop; no risk of the shared consumer silently discarding it before
  `awaitUserRegisteredLifecycleEvent` (step 2, later) gets a chance to see it.
- **Money types / idempotency** — not applicable to this task.

---

**Phase 7 complete — self review written.** Proceed to Phase 8 (Independent Review) on approval.
