<!-- MODEL: Claude Sonnet — Phase 6 (Implementation). -->

# auth · T37 · Phase 6 — Implementation Notes

Test-file-only change, exactly matching the Phase 5 plan. No production code touched.

## What changed

- `services/auth/src/test/java/com/themistra/auth/audit/AuditTrailIntegrationTest.java` — added
  `AccountService` autowiring, a `registerAndActivate(String email)` helper, and replaced the
  fabricated `UUID.randomUUID()` account UUID with a real, registered/activated account's UUID.
- `services/auth/src/test/java/com/themistra/auth/authz/RoleAssignmentIntegrationTest.java` — same
  pattern: added `AccountService` autowiring, a `registerAndActivate(String email)` helper, applied
  to the two test methods that call `assignRole`/`assignRoleTemplate`
  (`directAndTemplateExpandedRolesAreUnionedCorrectly`,
  `removingATemplateRemovesItsContributionButNotDirectRoles`). The third method
  (`accountWithNothingAssignedHasNoEffectiveRoles`) never writes to `auth_audit` and was already
  passing — left untouched.

## Mapping to the plan / acceptance criteria

- **AC1a** (Group C fixed, zero regressions): **Met**, confirmed by direct test runs (see
  Verification below).
- **AC1b** (Groups A/B documented as deferred, each with independent evidence): unaffected by this
  phase's changes — both groups' evidence was already established at Phase 0/4; this phase's job was
  only to confirm Group C's fix didn't accidentally touch either.

## Deviations forced by reality (flagged, not hidden)

**None beyond what the Phase 5 plan already predicted.** The plan explicitly forecast that fixing
`AuditTrailIntegrationTest`'s FK violation would unmask Group A's Kafka timeout underneath it, since
the test also waits on the real `auth.security.audit` topic. This is exactly what happened — verified
by direct observation, not assumed:

- **Before the fix**: `AuditTrailIntegrationTest.recordMirrorsToKafkaWithoutLeakingIpOrUserAgent`
  failed synchronously with `DataIntegrityViolation ... auth_audit_account_uuid_fkey`.
- **After the fix**: the same test now fails with `ConditionTimeout ... audit mirror for <uuid>
  observed on the real topic` — the FK error is gone; what's left is Group A's already-logged Kafka
  producer→broker connectivity issue, reached for the first time now that the earlier failure no
  longer pre-empts it. This is not a new defect; it's the frozen brief's own AC1b category, now
  correctly categorized for this specific test rather than left under the wrong (Group C) label.

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest=RoleAssignmentIntegrationTest` — **all 3 tests pass** (exit 0),
  confirming both previously-FK-violating tests are now genuinely fixed, not just changed.
- `mvn -pl services/auth test -Dtest=AuditTrailIntegrationTest` — FK violation confirmed gone;
  replaced by the predicted, already-logged Group A Kafka timeout (not a pass, matching the Phase 5
  plan's explicit forecast).
- `mvn -pl services/auth verify` (full suite): **702 tests, 1 failure, 6 errors** — down from the
  Phase 0 baseline's 1 failure/8 errors. The exact 2-error reduction matches Group C's 2 now-fixed
  tests precisely; every other previously-failing test still fails for the same, already-diagnosed
  reason (confirmed by comparing error messages/stack traces line-by-line against the Phase 0
  baseline — no new failure signature appeared, no previously-passing test regressed).

**Final failure inventory (7 remaining, all pre-existing/already-diagnosed, matching AC1b):**
- Group A (3): `EndToEndLifecycleIntegrationTest`, `AccountPersistenceIntegrationTest`,
  `AuditTrailIntegrationTest` — Kafka producer→broker connectivity, environment-level.
- Group B (4): `ApiKeyLifecycleIntegrationTest` (1 failure) + `ApiKeyExchangeIntegrationTest` (3
  errors) — null-response flakiness under full-suite load, unconfirmed root cause, scoped out at
  Phase 4.

---

**Phase 6 complete — implementation written, verified.** Proceed to Phase 7 (Self Review) on
approval.
