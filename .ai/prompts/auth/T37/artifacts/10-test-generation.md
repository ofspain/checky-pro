<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T37 · Phase 10 — Test Generation

No new tests. This task's deliverable is fixing two existing tests, not authoring new ones — the
manifest below maps the fix to acceptance criteria.

## Fixed tests

| Test | Acceptance criterion | Requirement | Fix |
|---|---|---|---|
| `RoleAssignmentIntegrationTest.directAndTemplateExpandedRolesAreUnionedCorrectly` | AC1a | R43 (indirectly, via the audit write `assignRole` triggers) | Real account via `registerAndActivate`, replacing `UUID.randomUUID()` |
| `RoleAssignmentIntegrationTest.removingATemplateRemovesItsContributionButNotDirectRoles` | AC1a | R43 (indirectly) | Same |
| `AuditTrailIntegrationTest.recordMirrorsToKafkaWithoutLeakingIpOrUserAgent` | AC1a (FK half); AC1b (Kafka half, unmasked, deferred) | R43 (directly) | Real account via `registerAndActivate`; Kafka payload assertion changed from string-contains to JSON-parsed (Phase 9, Kimi Finding 4) |

## Unaffected (already passing, no change)

`RoleAssignmentIntegrationTest.accountWithNothingAssignedHasNoEffectiveRoles` — read-only, never
touches the FK-constrained write path.

## Boundary/AC1b coverage — deferred, not silently dropped

Groups A (`EndToEndLifecycleIntegrationTest`, `AccountPersistenceIntegrationTest`,
`AuditTrailIntegrationTest`'s Kafka-wait half) and B (`ApiKeyLifecycleIntegrationTest`,
`ApiKeyExchangeIntegrationTest` ×3) remain red, exactly as scoped out at the Phase 4 human gate.
AC1b's own definition (Phase 4) requires each to carry independent corroborating evidence, not just
this task's own observation:
- Group A: independently reproduced on `AccountPersistenceIntegrationTest` before T37 ever touched
  anything (first logged during T36), and again on `EndToEndLifecycleIntegrationTest` — two
  unrelated files, same signature, both predating this task.
- Group B: observed since the T31 era (2026-08-17), unrelated to any code T31-T37 touched, still
  reproducing with the identical `expiredKeyRejectedUniformlyThroughTheEndpoint`/etc. null-response
  signature.

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors, after both Phase 6 and Phase 9
  changes.
- `mvn -pl services/auth test -Dtest=RoleAssignmentIntegrationTest` — 3/3 pass.
- `mvn -pl services/auth test -Dtest=AuditTrailIntegrationTest` — FK violation confirmed gone;
  Group A's already-logged Kafka timeout is the only remaining failure, unchanged in signature after
  Phase 9's assertion rewrite.
- `mvn -pl services/auth verify` (full suite, post-Phase-6): 702 tests, 1 failure, 6 errors — down
  from the Phase 0 baseline's 1 failure/8 errors, exactly the 2-test Group C reduction.

---

**Phase 10 complete — test manifest written (no new tests, fix-only task).** Proceed to Phase 11
(Kimi Test Review) on approval.
