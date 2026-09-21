# crypto · T27 · Phase 1 — Specification Extraction

## Business Rules

No individual `R`-numbered requirement targets this task specifically — it is a process/verification
gate, not a functional behavior. The governing text is `package.md` §9's own verification checklist,
whose final bullet is this task's literal restatement: "`mvn -pl services/crypto verify` must pass
(unit + integration with fake providers); Docker image builds."

## Locked Decisions

- **L13 (relevant to the new Dockerfile, not modified by this task).** Secrets discipline — no
  provider API key, DB credential, or KMS key ARN is committed; External Secrets Operator injects them
  at runtime. Directly constrains the new Dockerfile: it must contain no secret, credential, or
  environment-specific value, matching auth's own precedent exactly (no `ENV` lines carrying secrets,
  runtime config supplied externally).

## Files involved

**Existing (read-only, this task verifies but does not change):**
- The entire `services/crypto` module — every main/test source file, every Flyway migration, every
  ArchUnit rule, `pom.xml`.

**Precedent to mirror exactly:**
- `services/auth/Dockerfile` — the direct, working precedent for the one new file this task authors.

**New, this task's own deliverable (per Phase 0's finding):**
- `services/crypto/Dockerfile` — does not exist anywhere in the repository today; auth's own identical
  task (its task 37, verbatim-matching wording) has a working precedent, and no other task in either
  service's own task list claims this scope.

## Dependencies

None new. No production or test dependency changes — this task packages what already exists.

## Acceptance Criteria

1. **AC1.** `mvn -pl services/crypto verify` succeeds. Split honestly per Phase 0's own finding: the
   compile/package/unit-test portion is achievable and verifiable in this environment now; the
   Testcontainers-based integration-test portion cannot be executed here (Docker unavailable,
   unresolved since T23) and must be disclosed as deferred, not silently assumed passing.
2. **AC2.** `docker build -f services/crypto/Dockerfile -t crypto-service .` (from the repo root)
   succeeds. Requires both the new Dockerfile (AC3) and a running Docker daemon — the latter is
   unavailable in this environment and this task cannot itself resolve that; the Dockerfile's own
   correctness is verified by direct construction/review (mirroring auth's exact, working structure)
   rather than by an actual local build.
3. **AC3 (the one genuinely new deliverable).** `services/crypto/Dockerfile` exists, is a correct
   multi-stage build (a Maven build stage producing the executable jar, a distroless non-root runtime
   stage), matches `agents.md`'s Deployment rule, and contains no secret or credential (L13).

## Tests required

None — this task authors no test. Its own "test" is the full-suite run itself (AC1) and the image build
(AC2), both process-level checks, not new JUnit tests.

## Open Questions

No blockers requiring the author's decision before proceeding — Phase 0's own finding (mirror auth's
Dockerfile, since no other task claims this scope and auth's identical task did exactly this) is
adopted as the working assumption for Phase 2, with the Docker-unavailability limitation on AC1/AC2's
actual execution disclosed plainly rather than treated as blocking this task's own preparatory work.
