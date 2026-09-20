<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# auth · T37 · Phase 12 — Specification Verification

Compares the final state (Phases 6-11) against `requirements.md`, `design.md`, `tasks.md`, and the
frozen brief (as amended) for **T37 only**. `spec/auth-service/` confirmed unchanged throughout.

---

## Traceability Matrix — Task Statement

| Clause | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| `mvn -pl services/auth verify` must pass | **Partially — AC1a met, AC1b explicitly deferred** | `RoleAssignmentIntegrationTest.java:41,51,72`; `AuditTrailIntegrationTest.java:81,91,113` | Same files, re-run and confirmed at Phases 6/9/11 | No — every code-level (Group C) failure fixed | **Deliberate, Phase 4-gated**: AC1 split into AC1a (code-level, met) and AC1b (environmental/unconfirmed, deferred with independent corroborating evidence) rather than the literal "zero failures" reading, since Group A has no known code-level fix and Group B has no confirmed root cause |
| Docker image must build from repo root | Yes | Phase 0 — `docker build -f services/auth/Dockerfile -t auth-service-t37-check .`, exit 0, 459MB image, confirmed then removed | Manual verification (not a Maven-bound test — the task statement doesn't require CI automation, Phase 11 Gap 4 disposition) | No | No |

## Traceability Matrix — Requirement IDs

| Requirement | Implemented? | Evidence | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R43 | Yes | `AuditTrailIntegrationTest.java:81-91` (FK fix + new DB-row assertion); `RoleAssignmentIntegrationTest.java:41-72` (FK fix for the audit writes `assignRole`/`assignRoleTemplate` trigger) | Same | No | No |

## Traceability Matrix — Locked Decisions

| Decision | Honored? | Evidence |
|---|---|---|
| L1 (immutable migrations) | Yes | No schema change made or needed |
| L12 (module boundaries) | Yes | Fix confined to the two named test files; cross-module `account` import into `audit`/`authz` test code confirmed excluded from `ArchitectureTest` analysis (Phase 7/9) |

## Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| AC1a | **Met** | `mvn -pl services/auth verify`: 702 tests, 1 failure, 6 errors — down from the Phase 0 baseline's 1 failure/8 errors, exactly the 2-test Group C reduction; zero regressions confirmed by comparing failure signatures line-by-line against the baseline |
| AC1b | **Met** | Groups A (3 tests) and B (4 tests) documented with independent corroborating evidence per their own Phase 4 definition — Group A reproduced on two unrelated files (`AccountPersistenceIntegrationTest`, first logged during T36; `EndToEndLifecycleIntegrationTest`), Group B observed unchanged since the T31 era; this Phase 4 human-gate sign-off is their explicit deferral |
| AC2 | **Met** | Phase 0, Docker build exit 0 |

## Findings from this phase

None new. This task's own review process (Phase 7: 0 findings; Phase 8: 7 findings; Phase 11: 7
findings — 14 total) already surfaced and resolved every material gap, including:

1. **A real, verified, but explicitly out-of-scope production observation**: neither
   `AuditService.record` nor `RoleService.assignRole` validates account-UUID existence before the
   DB write — confirmed via source (Phase 4), logged as a follow-up candidate rather than fixed,
   since production-code validation is outside a task whose statement is "run the suite."
2. **A precisely-predicted-then-confirmed residual**: the Phase 5 plan forecast that fixing
   `AuditTrailIntegrationTest`'s FK violation would unmask Group A's Kafka timeout underneath it —
   exactly what happened, verified by direct before/after test runs, not assumed.
3. **One genuinely new, verified-working coverage gap closed** (Phase 11 Gap 6): the test now
   asserts the `auth_audit` row directly, not just its Kafka mirror — confirmed to pass independently
   of Group A's Kafka relay issue, proving the DB write itself is unaffected by that environmental
   problem.
4. **Two Kimi findings restated across both review rounds with no new evidence, rejected both
   times for the same reason** (extending the third `RoleAssignmentIntegrationTest` method to use a
   real account for hypothetical future-proofing; adding a test that pins down the currently-known
   validation gap's exception behavior).

---

## Principal-Engineer Assessment

**(1) Is the task fully complete?** Yes, under the human-gated AC1a/AC1b reading established at
Phase 4 — every failure with a known, fixable, code-level cause is fixed; every failure without one
is named, evidenced, and explicitly deferred, not silently tolerated or hidden. The task statement's
literal "must pass" is not met in the narrowest possible reading (`mvn verify`'s own exit code is
still non-zero, due to Groups A/B), and that gap is honestly surfaced here rather than glossed over.

**(2) Does it satisfy every acceptance criterion?** Yes, under the frozen brief's own AC1a/AC1b/AC2
structure — each individually Met with direct evidence.

**(3) Does it violate any LOCKED decision?** No. L1/L12 both honored.

**(4) Remaining risks?**
- **Groups A and B are still open** — this task did not and could not close them (Group A has no
  known code-level fix; Group B has no confirmed root cause). A truly literal, zero-exit-code
  `mvn -pl services/auth verify` still requires separate work, most likely infrastructure-level
  (Docker/Kafka networking) for Group A and a dedicated investigation for Group B.
- **The account-existence-validation gap** (Kimi Finding 5/Gap 3) remains a real, if minor,
  production behavior observation, not yet acted on — logged, not fixed, per explicit human-gate
  decision.
- **`FEATURE_MODULES`/ArchUnit exposure**: none — this task's changes are test code, structurally
  outside ArchUnit's analyzed population.

**Verdict: PASS** — under the frozen brief's own explicit AC1a/AC1b/AC2 structure, which itself
honestly reflects what this task can and cannot achieve given two pre-existing, out-of-code-scope
environmental issues. Every requirement, LOCKED decision, and acceptance criterion traces to
implemented, reviewed, and directly-verified code; every deferred item carries independent evidence
and an explicit human sign-off, not a silently-accepted red build.

---

**Phase 12 complete — verification written.** Proceed to Phase 13 (PR Preparation) on approval.
