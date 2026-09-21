# crypto · T29 · Phase 12 — Specification Verification

## Acceptance Criteria (Phase 1)

### AC1. §11 Q1, Q2, Q3, Q7 each explicitly addressed

**PASS.** All four now carry `**Resolved (2026-09-21, ...):**` notes citing real, verified code
(`ProviderProperties`, `FailClosedScreeningClient`, `AttestationService`, `KmsSigner`), each with an
explicit, honest `**Open follow-up:**` where a genuine vendor/procurement/config decision remains —
never marked resolved for a business decision this repository's own code cannot make. Q4, Q5, Q6
correctly left untouched (confirmed by regression test); Q8 unchanged.

### AC2. §9's 14 items checked against real, current evidence

**PASS, with one item honestly, correctly left open.** 13 of 14 items ticked `[x]`, each with a typed
evidence citation (unit test / ArchUnit / contract test / code review / CI process). Item 13 (`mvn
verify` / Docker image builds) is `[ ]`, disclosing two real facts discovered only because Docker became
available mid-pipeline: several pre-existing, unrelated integration-test failures (T28 Phase 12), and a
genuine, newly-discovered Maven reactor defect that makes `docker build` fail for both this service's
and auth-service's Dockerfiles. Both are cited with exact test names, a commit hash, and the literal
build error — not vaguely asserted.

### AC3. Header bump conditioned on AC1/AC2 genuinely holding

**PASS.** `Version: 0.2`, `Status: READY FOR IMPL`. This was not automatic — the tension between a
genuinely-failing checklist item and bumping the header was surfaced directly to the user (not resolved
unilaterally), who explicitly chose to bump with full disclosure rather than block on a cross-service
infrastructure defect unrelated to this spec's own content.

## Verdict

**PASS.** All three Phase 1 acceptance criteria satisfied. The spec-completeness gate (Q1/Q2/Q3/Q7's
engineering half, §3/§8's own named-test coverage) genuinely holds; the one real gap (item 13's
Docker-build/integration-test portion) is honestly disclosed, regression-guarded against being silently
hidden later, and explicitly out of this task's own scope to fix.

## Candidates for future tasks (not fixed here, out of T29's own scope)

- **Highest priority:** fix `services/crypto/Dockerfile` and `services/auth/Dockerfile`'s build context
  to include the sibling module's `pom.xml` (Maven reactor validation currently fails for both), then
  re-run `mvn -pl services/crypto verify` + `docker build` in a Docker-available CI environment to
  finally close §9 item 13.
- Carried forward from T28: `EndToEndIntegrationTest`'s Spring-context/Flyway defect (`chain.attestations`
  missing table), plus the 6 further, unrelated repository-integration-test failures — all still
  present, unchanged, not investigated this task either.
- Q1's own remaining half: select the 3 actual commercial RPC providers per launch chain (procurement).
- Q2's own remaining half: select the actual screening vendor (Chainalysis / TRM Labs / Elliptic).
- Q7's own remaining half (new, T29): make `KmsSigner.SIGNING_ALGORITHM` configurable via
  `KmsProperties` if the platform ever provisions a KMS key with a different algorithm.
- Q4, Q5, Q6 — untouched by this task, remain exactly as originally written.
