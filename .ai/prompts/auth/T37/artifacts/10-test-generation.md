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

## Kimi Phase 11 test review — gaps closed

All 7 findings verified against source before disposition.

| Gap | Disposition |
|---|---|
| Gap 1 — AC1 still not fully satisfied, needs prominent documentation | Accepted in substance, no immediate artifact change — the AC1a/AC1b split is already documented across Phases 4/6/9/10; will be restated in the Phase 12 verdict and Phase 13 PR summary, the natural place for a final-task-level summary. |
| Gap 2 — extend `registerAndActivate` to the third, read-only test | **Rejected, restated from Phase 8 Finding 3** — no new evidence. Same reasoning: speculative future-proofing against a hypothetical write side-effect `resolveEffectiveRoles` does not have today. |
| Gap 3 — no test for the non-existent-account-UUID failure path | **Rejected.** Same underlying question as Phase 4/8's already-logged, human-gated-out follow-up (should `AuditService`/`RoleService` validate account existence). Adding a test that pins down the *current* raw-FK-exception behavior would codify a state already flagged as a candidate for a future fix, not strengthen this task's own scope. |
| Gap 4 — Docker build not CI-automated | Rejected as out of scope for T37 (Kimi's own framing: "may be out of scope for the Java test suite"). The task statement requires the image to build, not that building it be wired into this Maven module's own test suite — already directly verified at Phase 0. Noted as a possible separate infrastructure follow-up. |
| Gap 5 — add a comment explaining the FK precondition | **Rejected — already satisfied.** Verified: the exact comment already exists at both call sites (`AuditTrailIntegrationTest.java:78`, `RoleAssignmentIntegrationTest.java:49`), added at Phase 6. |
| Gap 6 — `AuditTrailIntegrationTest` doesn't assert the `auth_audit` row itself | **Accepted.** Added `assertThat(auditService.list(accountUuid, Pageable.unpaged()).getContent()).anySatisfy(...)` immediately after the `record(...)` call, before the Kafka wait. Re-run confirms it passes (the DB write succeeds even though Group A blocks the Kafka relay afterward) — genuinely new, working coverage. |
| Gap 7 — Group A/B deferral evidence is narrative, not reproducible | Rejected as already-substantially-addressed — every phase's artifacts already quote exact error messages/stack-trace excerpts verbatim (e.g. `"Bootstrap broker localhost:9094 ... could not be established"`, the exact `ConditionTimeout` text), not paraphrased prose; a reader has the literal signatures without needing a live re-run. |

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors, after Phase 6, 9, and 11 changes.
- `mvn -pl services/auth test -Dtest=RoleAssignmentIntegrationTest` — 3/3 pass.
- `mvn -pl services/auth test -Dtest=AuditTrailIntegrationTest` — FK violation confirmed gone; the
  new `auth_audit` row assertion (Gap 6) passes; Group A's already-logged Kafka timeout remains the
  only failure, unchanged in signature.
- `mvn -pl services/auth verify` (full suite, post-Phase-6): 702 tests, 1 failure, 6 errors — down
  from the Phase 0 baseline's 1 failure/8 errors, exactly the 2-test Group C reduction. Unchanged by
  Phase 9/11's assertion-only changes (same failure count, same signatures).

---

**Phase 10 complete — test manifest written and updated post-Phase-11.** Proceed to Phase 12
(Specification Verification) on approval.
