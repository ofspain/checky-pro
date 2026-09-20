<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# auth · T38 · Phase 12 — Specification Verification

Compares the final state (Phases 6-11) against `requirements.md`, `design.md`, `tasks.md`, and the
frozen brief (as amended) for **T38 only**. `spec/auth-service/` confirmed unchanged throughout.

---

## Traceability Matrix — Task Statement's Five Defect Classes

| Defect class | Absent? | Evidence (file:line) | Durable guard? | Deviation? |
|---|---|---|---|---|
| Plaintext credentials | Yes | `Account.passwordHash`, `RefreshTokenFamily.currentTokenHash`, `ApiKey.keyHash`, `MfaSeedEncryption`; `application.properties` local-only placeholders only; comment scan 0 matches; `.github/workflows/ci.yml` scanned | Yes — pre-existing (`@ConfigurationProperties` startup validation) | No |
| Unauthenticated admin routes | Yes | `PublicEndpoints.java` — no `/admin/**` entry | Yes — pre-existing (`ArchitectureTest.shouldEnforcePublicEndpointAllowlist`, `admin_controller_handlers_require_preauthorize`) | No |
| Shared model artifact | Yes | Full dependency inventory, both `pom.xml` files | **Yes — new** (`GapAnalysisDefectRegressionTest.java:40`) | No |
| `Long.getLong` config misread | Yes | Zero matches for `Long.getLong`/`Integer.getInteger`/`System.getProperty`/`System.getenv`; two `@Value` usages confirmed default-free | Yes — pre-existing (`@ConfigurationProperties`/`@Validated`) + **new** (`GapAnalysisDefectRegressionTest.java:76`, the `@Value`-specific gap) | No |
| `allow-circular-references` | Yes | Zero matches across `src/`, `pom.xml` | **Yes — new** (`GapAnalysisDefectRegressionTest.java:58`) | No |

## Traceability Matrix — Locked Decisions

| Decision | Honored? | Evidence |
|---|---|---|
| L11 (public-endpoint discipline) | Yes | No `/admin/**` in `PublicEndpoints.java`; both enforcement mechanisms confirmed present |
| L12 (module boundaries) | Yes | New test file is test code, excluded from `ArchitectureTest` analysis; no production dependency change |
| L13 (secrets discipline) | Yes | Every credential-shaped value hashed/encrypted; every `application.properties` sensitive value environment-injected with a named local-only fallback |

## Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| AC1 | **Met** | Full evidence chain, Phases 0/4/6/9 — credential storage, config placeholders, comment scan, CI-config scan |
| AC2 | **Met** | `PublicEndpoints.java`, both ArchUnit mechanisms; naming-convention residual explicitly preserved (Phase 4), not fixed |
| AC3 | **Met, now with a durable guard** | Full dependency inventory + `GapAnalysisDefectRegressionTest.noSharedModelArtifactDependencyIsIntroduced` (negative-proofed) |
| AC4 | **Met, now with a durable guard for its `@Value` half** | Zero-match greps + `GapAnalysisDefectRegressionTest.noValueAnnotationEverCarriesADefaultFallback` (negative-proofed) |
| AC5 | **Met, now with a durable guard** | Zero-match greps + `GapAnalysisDefectRegressionTest.allowCircularReferencesIsNeverEnabled` (negative-proofed) |

## Findings from this phase

None new. This task's own review process (Phase 7: 1 methodology caveat, already self-resolved;
Phase 8: 6 findings; Phase 11: 7 findings — 14 total) already surfaced and resolved every material
gap, including:

1. **A genuine, real, previously-unconfirmed finding surfaced while closing a Kimi gap**: the repo's
   CI (`.github/workflows/ci.yml`) is an explicit placeholder with no gitleaks/secret-scanning step —
   the durable defense the gap-analysis document itself names for the "committed secrets" half of
   AC1. Documented as a genuine, out-of-scope-for-`services/auth` follow-up, not silently glossed
   over or left as a vague "not verified" note.
2. **A deliberate, human-approved scope expansion**: three permanent regression tests added at
   Phase 11 for the three defect classes ("absence by construction") that previously had no durable
   guard — each individually negative-proofed (introduced, confirmed failing for the right reason,
   reverted, re-confirmed passing).
3. **A recurring Kimi factual error caught and rejected three separate times** (Phase 3 Finding 8,
   Phase 8 Finding 4, Phase 11 Gap 6) — attributing T37's "Docker image must build from repo root"
   clause to T38's own task statement, which has no such clause.
4. **One rejected Kimi test suggestion with a clear reason**: a proposed direct
   `PublicEndpoints`-allowlist-content test (Phase 11 Gap 2) was rejected as redundant with the
   already-existing, broader `shouldEnforcePublicEndpointAllowlist` rule, which catches the actual
   risk (an admin handler being `permitAll()`'d) more directly than checking the allowlist's own
   contents would.

---

## Principal-Engineer Assessment

**(1) Is the task fully complete?** Yes. All five named defect classes are confirmed absent with
direct source evidence, and — following a deliberate, human-approved scope expansion beyond the
task's original "verification only" framing — the three that previously had no durable guard now
have one, each negative-proofed.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1-AC5 all Met, three now with newly-added
durable regression protection rather than a one-time manual check alone.

**(3) Does it violate any LOCKED decision?** No. L11/L12/L13 all honored.

**(4) Remaining risks?**
- **The repo-wide gitleaks CI gate does not yet exist** — a real, documented, out-of-scope-for-T38
  gap in the durable defense the gap-analysis document itself calls for. Worth a dedicated follow-up
  task at the platform/CI level, not `services/auth` code.
- **AC2's admin-route enforcement remains name-based** (`Admin*` prefix), not path-based — a
  documented, explicitly-preserved residual from Phase 4, not fixed under this task's scope.
- **AC1's comment/source scan remains a heuristic** (regex/name-pattern based), not a proof of
  absence — the same limitation a real entropy-based scanner (gitleaks, once configured) would close.

**Verdict: PASS** — every one of the five task-statement defect classes traces to direct source
evidence; three now carry a genuinely new, negative-proofed durable guard; every residual limitation
and every out-of-scope finding (the missing CI gate, the naming-based admin rule, the heuristic
comment scan) is named explicitly, not hidden or silently accepted.

---

**Phase 12 complete — verification written.** Proceed to Phase 13 (PR Preparation) on approval.
