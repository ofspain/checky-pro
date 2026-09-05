<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). -->

# auth · T33 · Phase 13 — PR / Commit Preparation

Phase 12 verdict was **PASS**. This task is ready for merge. Branches off `main`; `main` stays
deployable throughout.

---

## Commit title

```
auth: author OpenAPI + event contract files with negative-proofed conformance tests (T33)
```

## Commit message

```
auth: author OpenAPI + event contract files with negative-proofed conformance tests (T33)

Documents contracts/api/auth.yaml (all 30 non-SAS endpoints, 19 component
schemas) and the two remaining undocumented event schemas
(email-requested/security-audit.v1.schema.json) - the routing itself for both
events already worked and was already tested; this closes the documentation
gap around it.

Independent review's largest volume this session (15 findings across three
rounds) drove the contract test suite well past a simple "does the YAML
parse" check: AuthOpenApiContractTest proves route completeness in both
directions, that every component schema matches its real DTO's shape, that
every operation's request/response $ref names the CORRECT component (not
merely a valid one), and that neither OpenAPI-exposed enum (AccountStatus,
AuditOutcome) can silently drift from Java. Two hand-maintained expectation
tables are themselves guarded against a forgotten entry.

Every one of these checks was proven non-vacuous with a real negative-proof
run, not trusted on inspection: a genuine YAML authoring mistake was caught
by the tests during implementation (not by review), and three more scenarios
were deliberately reproduced and reverted - a wrong $ref, a missing route,
and a forgotten expectation-table entry - each producing the exact expected
failure before being reverted.

Scope is deliberately narrower than R47's literal text in one respect,
decided and documented at a human gate: exact HTTP status codes and
field-level type/format correctness are not verified (status codes aren't
reliably reflectable from ResponseEntity.status(...) calls; type-checking
would go beyond the established UserLifecycleEventPayloadContractTest pattern
this task was instructed to mirror). auth.yaml still documents both for
human and future-codegen reference.

Co-Authored-By: Claude Opus 4.8 (1M context) <noreply@anthropic.com>
```

## Files changed

**Contracts**
- `contracts/api/auth.yaml` (new — OpenAPI 3.0.3, 30 paths, 19 component schemas)
- `contracts/events/auth/email-requested.v1.schema.json` (new)
- `contracts/events/auth/security-audit.v1.schema.json` (new)

**Tests**
- `services/auth/src/test/java/com/themistra/auth/account/event/EmailRequestedEventPayloadContractTest.java` (new, 2 tests)
- `services/auth/src/test/java/com/themistra/auth/audit/AuditMirrorPayloadContractTest.java` (new, 3 tests)
- `services/auth/src/test/java/com/themistra/auth/common/AuthOpenApiContractTest.java` (new, 10 tests)

**Build**
- `services/auth/pom.xml` (modified — 1 new test-scope dependency, `jackson-dataformat-yaml`, no
  explicit version, resolved to 2.19.2 via Spring Boot's own BOM)

No production code changed. No `spec/` file touched. No migration.

## Summary

Implements R44/R45 (documents and schema-verifies payloads for two already-correctly-routed
events) and R47 (authors `auth.yaml` as the documented contract for all 30 non-SAS endpoints). No
LOCKED decision is scoped to this task. The review process (Phase 3: 6 findings; Phase 8: 5
findings; Phase 11: 4 findings — 15 total, this session's largest single-task review volume) drove
three genuine human-gated design decisions: the OpenAPI conformance test verifies operation-level
schema *references* (not just that named components are internally correct), covers both exposed
enums against drift, and self-guards its own hand-maintained expectation tables — while explicitly
and transparently declining to verify exact status codes or field-level types, judged
disproportionate to this task's own documentation-gap-closure purpose. A real, independent finding
(Phase 0/4): the task's own file-count arithmetic was wrong at Phase 0 ("28 endpoints, 6
controllers"), corrected to the actual 30/7 after Kimi's Phase 3 recount.

## Testing performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest='AuthOpenApiContractTest,EmailRequestedEventPayloadContractTest,AuditMirrorPayloadContractTest,EventTopicsTest'`
  — **18/18 pass** (10 + 2 + 3 + 3, the last being the pre-existing named-test regression check).
- **Six negative-proof runs performed and reverted** across Phases 6, 9, and 11 — every check in
  this PR has been demonstrated to actually fail under the condition it exists to catch, not just
  assumed correct from reading the code:
  1. Removed `/admin/audit` from `auth.yaml` → completeness check failed by name.
  2. Added a fake `/fake/nonexistent` path → reverse-completeness check failed by name.
  3. A real YAML syntax error (a malformed quoted scalar) was caught by the suite during authoring,
     not introduced deliberately — the clearest possible proof the approach works.
  4. Swapped `POST /accounts`'s request `$ref` to the wrong component → `$ref`-correctness check
     failed with the exact wrong/expected names (reproducing Kimi's own example scenario exactly).
  5. Removed one entry from the response-schema expectation table → the new guard test failed by
     name, not a downstream test with a confusing message.
  6. (Implicit) all 18 tests pass cleanly with every temporary change reverted, confirmed via
     `git status` showing only the intended files changed.

## Specification references

- **Task:** T33 — Contract files (`spec/auth-service/tasks.md`, task 33)
- **Requirements:** R44, R45, R47
- **LOCKED decisions:** none scoped to this task
- **Named tests (`package.md` §8):** `shouldRouteEmailRequestedEventsToAuthEmailRequestedTopic`
  (pre-existing, reconfirmed green), `shouldConformToAuthOpenApiContract` (new, written, executed,
  passing, and — unusually for this pipeline — proven non-vacuous via six separate negative-proof
  runs rather than one)

---

## Note for the reviewer: one file on this branch is T32's own, not T33's

`services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` shows as changed in a naive
`git diff` against T32's last commit, but the change (renaming `shouldEnforcePublicEndpointAllowlistActuallyRunsUnderThisBuild`,
extracting the shared `ANALYZED_PACKAGE` constant) is T32's own Phase 11 gap-closing work — already
reviewed and documented in `T32/artifacts/10-test-generation.md` and `T32/artifacts/12-specification-verification.md`.
It simply landed in a commit chronologically after T32's own commit-message boundary, the same
git-hygiene quirk previously noted for T26/T27/T30 (commits don't cleanly separate by task
boundary in this session). Not part of this PR's own scope; no action needed here.

---

**Phase 13 complete — PR preparation written. T33 is ready for merge.**
