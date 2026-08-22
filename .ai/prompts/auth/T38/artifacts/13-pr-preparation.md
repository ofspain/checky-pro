<!-- MODEL: Claude Sonnet — Phase 13 (PR / Commit Preparation). -->

# auth · T38 · Phase 13 — PR / Commit Preparation

Phase 12 verdict was **PASS**. This task is ready for merge. Branches off `main`; `main` stays
deployable throughout.

---

## Commit title

```
auth: verify + add regression guards for gap-analysis defect classes (T38)
```

## Commit message

```
auth: verify + add regression guards for gap-analysis defect classes (T38)

Verifies the five reference-project defect classes named in
docs/architecture/gap-analysis.md (plaintext credentials, unauthenticated
admin routes, shared model artifact, Long.getLong config misread,
allow-circular-references) are absent from this service. All five were
confirmed absent by direct source inspection, re-verified independently at
Phase 8, with zero code change required to satisfy the task's literal
"verify absent" wording.

Two of the five already had durable, pre-existing guards (hashed/encrypted
credential storage + validated @ConfigurationProperties for plaintext
credentials; ArchitectureTest's public-endpoint allowlist rule for admin
routes). The other three - shared model artifact, allow-circular-references,
and the @Value-specific half of the config-misread class - were previously
only confirmed by manual grep, with no regression protection. Kimi's Phase
11 review made a real case for closing that gap; three small, negative-
proofed permanent tests were added (GapAnalysisDefectRegressionTest) rather
than leaving the task as a one-time snapshot.

Closing a Kimi gap surfaced a genuine, previously-unconfirmed finding along
the way: the repo's CI (.github/workflows/ci.yml) is an explicit placeholder
with no gitleaks/secret-scanning step - the durable defense the
gap-analysis document itself names for future committed secrets. Logged as
an out-of-scope-for-services/auth follow-up, not silently glossed over.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
```

## Files changed

**Tests only**
- `services/auth/src/test/java/com/themistra/auth/GapAnalysisDefectRegressionTest.java` (new, 111
  lines, 3 tests)

No production code changed. No `spec/` file touched. No migration.

## Summary

Confirms the five gap-analysis defect classes are absent from `services/auth`, each with direct
source evidence. Beyond the task's original "verify only" scope, closes a real durability gap for
three of the five (shared model artifact, `allow-circular-references`, `@Value` defaults) with new,
negative-proofed regression tests — a deliberate, human-approved expansion, not scope creep. Also
surfaces one genuine, real finding outside this task's own fixable scope: the repo's CI has no
secret-scanning step configured yet.

## Testing performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest=GapAnalysisDefectRegressionTest` — 3/3 pass.
- **Three negative-proof runs**, each confirmed to fail for the right reason and reverted (`git diff`
  empty afterward on all three touched files):
  1. `noSharedModelArtifactDependencyIsIntroduced` — a scratch `commons-netra` reference appended to
     `pom.xml` correctly failed.
  2. `allowCircularReferencesIsNeverEnabled` — `spring.main.allow-circular-references=true` appended
     to `application.properties` correctly failed.
  3. `noValueAnnotationEverCarriesADefaultFallback` — `KafkaProducerConfig`'s `@Value` given a
     `:localhost:9092` default correctly failed and correctly identified the exact offending file.

## Specification references

- **Task:** T38 — Review against gap analysis defect catalogue (`spec/auth-service/tasks.md`, task 38)
- **Requirements:** none scoped (process/verification task)
- **LOCKED decisions:** L11, L12, L13 (all honored, none required deviation)
- **Named tests (`package.md` §8):** none scoped to this task

## Known, logged, out-of-scope follow-ups

1. **No gitleaks/secret-scanning step in CI** — `.github/workflows/ci.yml` is an explicit placeholder
   ("Path-filtered CI — placeholder until service skeletons land"), running only `mvn -B -pl
   services/auth verify`. The gap-analysis document's own stated durable defense against future
   committed secrets does not yet exist. Repo-wide CI infra, not `services/auth` code — genuinely
   out of scope for this task to fix.
2. **AC2's admin-route enforcement remains name-based** (`Admin*` prefix convention), not path-based
   — a documented, explicitly-preserved residual (Phase 4), not fixed here.

---

**Phase 13 complete — PR preparation written. T38 is ready for merge.**
