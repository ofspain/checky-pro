<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# auth · T38 · Phase 9 — Review Resolution

**Human decision:** approve — accept 5, reject 1 (a repeated factual error).

## Resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| 1 | AC4 evidence doesn't mention `@Value` usages | **Accepted.** Verified: `ApiKeyTokenIssuer.java:47` and `KafkaProducerConfig.java:26` both use `@Value("${...}")` with no `:default` fallback — a missing property fails boot, consistent with AC4's principle. Added explicitly to AC4's evidence below. |
| 2 | AC1's "0 matches" scan is heuristic, not proof | **Accepted — no new action.** Duplicate of the self-review's own Finding 1; the existing open note already states this. Kimi's independent re-run of a broader pattern (`(password|secret|token|key|private)\s*=\s*["'][^"']{8,}["']`) found the same two known-benign false positives (`ACR_API_KEY`, a `toString()` field label) — corroborating, not new. |
| 3 | Test resources / build-CI files not explicitly checked | **Accepted.** Verified `src/test/resources` doesn't exist in this module (nothing to scan). Additionally scanned `.github/workflows/ci.yml` (repo-wide, technically outside this service's own scope, similar to the already-carved-out gitleaks-gate exclusion, but cheap to check) — zero credential-shaped literals beyond proper `secrets.*`/`${{ }}` GitHub Actions references. Folded into AC1 evidence below. |
| 4 | Docker image build not re-verified in Phase 6 | **Rejected — repeated factual error.** T38's task statement, quoted verbatim at the top of both this and Kimi's own Phase 3 artifact, has no Docker-build clause: "Review against gap analysis defect catalogue. Verify plaintext credentials, unauthenticated admin routes, shared model artifact, `Long.getLong` config misread, and `allow-circular-references` classes of error are absent." That clause belongs to T37 ("Docker image must build from repo root"). This is the second time this finding has attributed T37's requirement to T38 (previously Phase 3 Finding 8, already rejected on the same grounds). No action. |
| 5 | Preserve AC2's naming-convention residual in the final record | **Accepted — no new action.** Already present in Phase 6/frozen brief; will carry through to Phase 12/13 as planned. |
| 6 | Independent re-run of AC3/AC4/AC5 greps confirms the claims | **Accepted — no new action.** Positive corroboration, no gap. |

## Frozen AC1/AC4 evidence, as amended

- **AC1** additionally: `src/test/resources` does not exist in this module; `.github/workflows/ci.yml`
  (repo-wide, technically outside this service's own scope) scanned for completeness — zero
  credential-shaped literals beyond proper GitHub Actions `secrets.*` references.
- **AC4** additionally: two `@Value` usages exist (`ApiKeyTokenIssuer`, `KafkaProducerConfig`);
  neither provides a default value, so a missing property fails boot rather than silently
  defaulting — no `@Value` with a fallback default or raw `Environment` lookup exists anywhere in
  this service.

## Verification after applying fixes

No code changed (zero-code-change task, as throughout). The two accepted evidence additions (AC1,
AC4) are documentation-only, verified directly against source as recorded above.

---

**Phase 9 complete — review resolved, human-approved. No code changes; two evidence citations
added.** Proceed to Phase 10 (Test Generation) on approval.
