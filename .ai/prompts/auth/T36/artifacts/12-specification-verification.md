<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# auth · T36 · Phase 12 — Specification Verification

Compares the final implementation and tests (Phases 6-11) against `requirements.md`, `design.md`,
`tasks.md`, and the frozen brief (as amended) for **T36 only**. `spec/auth-service/` confirmed
unchanged throughout this task.

---

## Traceability Matrix — Task Statement Flow Steps

| Flow step | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| Register (R1) | Yes | `EndToEndLifecycleIntegrationTest.java:198-200` | Same lines | No | No — real HTTP, per frozen brief |
| Verify email (R4) | Yes | Lines 207-219 | Same | No | No — token obtained via real Kafka event (only possible source, given enumeration-safety); R4's event half asserted both via Kafka and a direct status read |
| Login (password) | Yes | Lines 221-226 | Same | No | **Corrected during Phase 11** — originally missing entirely (Kimi Gap 7); now a real, successful password-only full-authorize-flow login, matching the task statement's literal step ordering |
| Admin assigns MERCHANT | Yes | Lines 228-238 | Same | No | No — real HTTP; grant persistence directly verified (Phase 11, Kimi Gap 4) |
| Next login requires MFA enrollment | Yes (AC2) | Lines 239-249 | Same | No | No |
| Enroll TOTP | Yes, by human-gate design | Lines 251-252 | Same | No | **Deliberate, Phase 4-gated**: no HTTP endpoint exists anywhere in this codebase (Phase 0 finding); direct `MfaService` calls, matching `SasLoginIntegrationTest`'s already-accepted precedent |
| Login with TOTP (AC3) | Yes | Lines 254-263 | Same | No | No |
| Create API key (AC4) | Yes | Lines 265-273 | Same | No | No |
| Exchange key for JWT (AC5) | Yes | Lines 275-294 | Same | No | No |
| Call session list (AC6) | Yes | Lines 296-309 | Same | No | No |
| Revoke session (AC7) | Yes | Lines 312-322 | Same | No | No |

## Traceability Matrix — Requirement IDs

| Requirement | Implemented? | Evidence | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R1 | Yes | Lines 198-200 | Same | No | No |
| R4 | Yes | Lines 207-219 | Same | No | No |
| R24 | Yes | Lines 239-263 (both the block and the successful-with-TOTP branches) | Same | No | No |
| R30 | Yes | Lines 265-273 | Same | No | No |
| R31 | Yes | Lines 275-294 | Same | No | No |
| R36 | Yes | Lines 296-309 | Same | No | No |
| R37 (widened, Phase 1) | Yes | Lines 312-322 | Same | No | No — task statement's own final step ("revoke session") requires this; widened from the original header's 6-ID list with Phase 1 justification |

## Traceability Matrix — Locked Decisions

| Decision | Honored? | Evidence |
|---|---|---|
| L6 (TOTP parameters) | Yes | `referenceGenerateCode` (RFC 6238, 30s/6-digit/HMAC-SHA1), lines 251-263 |
| L8 (API-key JWT contract) | Yes | Lines 275-294: `sub`, `scope`⊇`merchant.api`, `amr`⊇`api_key`, `expires_in=600` |
| L9 (access-token claim set) | Yes, by omission | No claim outside the L9 set is asserted or assumed present; `roles` (an L9 member) is directly verified |
| L10 (MFA mandatory for MERCHANT/ADMIN) | Yes | Lines 221-249: password-only login succeeds pre-MERCHANT-grant, is blocked post-grant pre-enrollment |
| L11 (public-endpoint list) | Yes, by omission | Test adds no new endpoint; confirms (via the CSRF investigation, Phase 6) that the enrollment gap is not a public-endpoint gap |
| L12 (module boundaries) | Yes | Test-only change; `ArchitectureTest`'s `DoNotIncludeTests` import option excludes it from analysis entirely (verified, Phase 7) |

## Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| AC1 | **Met** | Lines 198-219: real HTTP register+verify, Kafka event, direct status assertion |
| AC2 | **Met** | Lines 239-249: no `code`, `FOUND` status, `/login?error` |
| AC3 | **Met** | Lines 254-263: `code` present, state round-trips, `amr` contains `otp` |
| AC4 | **Met** | Lines 265-273: `ck_live_` shape, no hash leak |
| AC5 | **Met** | Lines 275-294: envelope + claims, including the real-data `roles` assertion |
| AC6 | **Met** | Lines 296-309: exactly one family, required fields present |
| AC7 | **Met** | Lines 312-322: family + live SAS authorization both confirmed removed |

## Findings from this phase

None new. This task's own review process (Phase 7: 2 findings; Phase 8: 11 findings; Phase 11: 8
findings — 21 total) already surfaced and resolved every material gap, including:

1. **A real, previously-undetected implementation bug** (CSRF rejection on `/accounts`/
   `/accounts/verify-email`), caught by this task's own first negative-proof run, not by review —
   fixed in Phase 6.
2. **A genuine, previously-invisible completeness gap**: the "login (password)" flow step was
   entirely missing from the test until Phase 11's review, despite being an explicit, named step in
   the task statement itself.
3. **Two overstated verification claims** (self-authored, caught by this task's own self-review
   before Kimi ever saw them) — corrected in Phase 9 rather than left standing.
4. **Several Kimi findings rejected with concrete, re-derivable evidence** rather than reflexively
   applied (Phase 8 Findings 9/11, restated without new evidence at Phase 11 Gaps 1/3/5, all
   rejected again for the same reasons; Phase 11 Gap 6 rejected as already-satisfied by an assertion
   already present in the code).

---

## Principal-Engineer Assessment

**(1) Is the task fully complete?** Yes. Every flow step in the task statement is implemented and
exercised, including "login (password)" — a step this task's own process initially missed and only
caught via Phase 11's adversarial review, not by construction. This is the one place in this task
where the process, not the first draft, is what makes the final result correct.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1-AC7 all Met, each with direct evidence,
not proxy assertions alone (AC1's Kafka-event proxy is backed by a direct status read; AC7's family
row is backed by a direct SAS-authorization-removal check).

**(3) Does it violate any LOCKED decision?** No. L6/L8/L9/L10/L11/L12 are all honored; none required
any deviation.

**(4) Remaining risks?**
- **The Kafka producer→broker environment issue is unresolved and blocks a full local green run.**
  Independently reproduced on an already-merged, unrelated test (`AccountPersistenceIntegrationTest`),
  confirming it predates and is external to this task. Logged, not fixed, per the human-gate decision
  at Phase 6. This means the test's Kafka-dependent steps (AC1's event half onward) have not been
  proven to pass end-to-end in this environment, though every individual mechanism they rely on
  (Kafka correlation technique, JWT claim decoding, HTTP call shapes) has been independently
  verified against already-passing precedent files.
- **`/admin/accounts/.../roles/{roleName}`'s CSRF-exemption is expected, not confirmed** — no
  precedent test in this codebase exercises it over real HTTP, and this test's own live run has not
  yet reached that line (blocked by the issue above). Explicitly flagged in-code (Phase 9) rather
  than silently assumed.
- **`FEATURE_MODULES`/ArchUnit exposure**: none — this test is test code, structurally outside
  ArchUnit's analyzed population, confirmed rather than assumed (Phase 7).

**Verdict: PASS** — every flow step, requirement, and acceptance criterion for T36 traces to
implemented, reviewed, and (where the environment allows) verified code; the one genuine
completeness gap found during this task's own process (the missing password-login step) was caught
and closed before this phase, not left as a known gap; the sole open risk (the environment blocker)
is external to this task's code and already logged with independent corroborating evidence, not
hidden or minimized.

---

**Phase 12 complete — verification written.** Proceed to Phase 13 (PR Preparation) on approval.
