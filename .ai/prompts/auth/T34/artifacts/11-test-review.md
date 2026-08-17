<!-- MODEL: Kimi 2.7 — Phase 11 (Test Review). -->

# auth · T34 · Phase 11 — Test Review

Consumed `artifacts/10-test-generation.md` and the final `contracts/api/token-claims.md`. No tests
exist for this task by design, so this review focuses on the verification substitutes and the
drift risks that result.

---

## Executive Summary

T34 is a genuinely doc-only task: the deliverable is `contracts/api/token-claims.md` and no test
file was added. The Phase 8 findings were resolved in Phase 9: the `email_verified` inaccuracy is
corrected, `aud`'s bare-string wire shape is documented, and the top-level table now shows claim
presence per path. The only remaining concern is the lack of any automated, CI-reproducible check
that the doc stays accurate as the code evolves.

---

## Findings

### Gap 1 — No automated test enforces the contract against future code drift

**Why it matters:** `agents.md` states "Contract tests validate against `contracts/`." T33 added
contract tests for its contract files; T34 has none. The doc itself acknowledges this in its
Verification section: "there is no automated test enforcing this today." A future change to
`TokenClaimsCustomizer` or `ApiKeyTokenIssuer` could silently make the doc stale, and the build
would still pass.

**Suggested test:** Add a lightweight plain-JUnit test (no Spring context) that reflects on
`TokenClaimsCustomizer` and `ApiKeyTokenIssuer` and asserts the claim names they set match the
claim names declared in `token-claims.md`. This is not required to complete T34, but the doc's own
Verification section already suggests it; the test review recommends scheduling it as a follow-up
and linking it in the task's memory.

**Evidence:** `contracts/api/token-claims.md:115-118`.

---

### Gap 2 — Verification evidence is not reproducible in CI

**Why it matters:** The Phase 10 manifest states that claim presence, `scope`/`aud` wire shape, and
the Path 1 real token were verified via temporary probes that were run once and reverted. This is
valuable evidence at authoring time, but a reviewer six months from now cannot re-run those probes
to confirm the doc still matches reality.

**Suggested test:** Preserve at least one minimal, permanent test that asserts the current claim-set
description. For example, a test that parses `token-claims.md` and verifies that every claim name
mentioned in the path tables appears in the code's claim-set strings would be cheap and would fail
if a claim is renamed or removed without updating the doc.

**Evidence:** `artifacts/10-test-generation.md:20-33`.

---

## Non-Issues Confirmed

- **Phase 8 findings resolved:** `email_verified` no longer claims it is "in practice always true";
  `aud` is documented as a bare string; the top-level table shows presence per path.
- **Doc accuracy:** The three issuance paths, claim counts (12/9/13), and per-claim semantics align
  with `TokenClaimsCustomizer.java` and `ApiKeyTokenIssuer.java`.
- **No leftover probe files:** `git status` confirms only `token-claims.md` is in the diff.
- **Scope discipline:** No production code was modified, matching the frozen brief.

---

## Traceability Summary

| AC | Covered By | Gap |
|---|---|---|
| AC1 — doc exists, lists L9's 13 claims, states no-PII rule | `contracts/api/token-claims.md` | None |
| AC2 — doc accurately describes every issuance path | `contracts/api/token-claims.md` (verified by temporary probes) | Gap 1 (no automated enforcement), Gap 2 (probes not reproducible) |

---

**Phase 11 complete — test review written.** Proceed to Phase 12 (Specification Verification) on approval.
