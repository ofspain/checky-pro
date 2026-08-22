<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# auth · T35 · Phase 12 — Specification Verification

Compares the final implementation and tests (Phases 6-11) against `requirements.md`, `design.md`,
`tasks.md`, and the frozen brief (as amended) for **T35 only**. `spec/auth-service/` confirmed
unchanged throughout this task.

---

## Traceability Matrix — Task Statement

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| "New modules do not import account entities" | Yes, generalized beyond account (D1b) | `ArchitectureTest.java`: `shouldPreventCrossModuleEntityImports` + `onlyBeAccessedFromTheSameFeatureModule()` | Named test, own canary, 2 regression guards (allowlist/common-module helper), 6 negative-proof runs across Phases 6/9/11 | No | Broadened from "account entities" to "every module's entities" — a human-gated, evidence-backed widening (verified zero current violations), not a narrowing |
| "Controllers depend only on their module services" | Yes, with 2 named exceptions + `common` allowed | `ArchitectureTest.java`: `controllersDependOnlyOnTheirOwnModuleServices` + `dependOnlyOnServicesFromTheSameFeatureModuleOrAnAllowedException()` | Same rule set, canary, 2 negative-proofed exceptions | No | Two pre-existing, real, intentional exceptions (`AccountController`→`SessionService`, `AdminAccountController`→`LockoutService`) explicitly allowlisted rather than broken — a documented, evidence-backed accommodation of reality, not a silent narrowing of the rule's intent |

## Traceability Matrix — Locked Decisions

| Decision | Honored? | Evidence |
|---|---|---|
| **L12** — module boundaries | Yes, more thoroughly than before this task | Both rules' `.because(...)` text cites L12 explicitly; the entity rule is now general-purpose (any entity, any module) rather than `Account`-specific; the controller rule adds a genuinely new boundary check L12's own general text implies but no prior rule enforced |

## Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| AC1 | **Met** | `shouldPreventCrossModuleEntityImports` negative-proofed for `Account` and `VerificationToken`; `account.dto`/`account.event` confirmed exempt under the new mechanism (re-verified, not assumed carried over from the old buggy rule); fails fast for an entity outside every known module |
| AC2 | **Met** | `controllersDependOnlyOnTheirOwnModuleServices` negative-proofed for an un-allowlisted dependency; both real exceptions confirmed to still pass; `common` services confirmed allowed; `@RestControllerAdvice` confirmed structurally excluded; fails fast for a controller outside every known module |
| AC3 | **Met** | Both rules have their own canary (T32's established pattern), confirmed actually executing under `mvn test` (the ArchUnit engine itself still doesn't, a separate, already-tracked issue neither rule is newly exposed to) |

## Findings from this phase

None new. This task's own review process (Phase 3: 9 findings; Phase 8: 10 findings; Phase 11: 6
findings — 25 total, the largest cumulative review volume of any task this session) already
surfaced and resolved every material gap, including:

1. **A genuine implementation bug self-caught during Phase 6**, not by review: the first attempt
   at the entity rule used ArchUnit's access-tracking API instead of its dependency-tracking API,
   silently missing a real violation (a declared-but-unused field of the wrong-module entity
   type). Caught by the task's own first negative-proof run, before any review phase saw it.
2. **Two design decisions requiring real trade-off judgment**, both resolved via human gate with
   verified-zero-risk evidence: broadening the entity rule beyond `Account` (Phase 4), and making
   both rules fail-fast for classes outside every known module plus treating `common` as
   universally available for services (Phase 9).
3. **Two Kimi Phase 11 suggestions explicitly rejected** with stated reasoning rather than
   reflexively implemented — a regression guard duplicating already-covered logic, and converting
   every manual negative-proof into a permanent test (which would require leaving broken code in
   `main`, a pattern this whole session has consistently declined).

---

## Principal-Engineer Assessment

**(1) Is the task fully complete?** Yes. Both halves of the task statement are implemented, tested,
and negative-proofed considerably more thoroughly than the task's own brief scope would have
strictly required, driven by how much real, concrete design tension the review process surfaced.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1-AC3 all Met, each with negative-proof
evidence across multiple review rounds, not a single pass taken at face value.

**(3) Does it violate any LOCKED decision?** No. L12 is honored more completely after this task
than before it — the original rule's real bug (the wildcard omission) is fixed, and a second,
previously-unenforced dimension of L12 (controller→service boundaries) is now covered too.

**(4) Remaining risks?**
- `FEATURE_MODULES` remains a hardcoded list — mitigated, not eliminated, by the new fail-fast
  behavior (a class outside the list is now a build failure, not a silent gap), but still requires
  a human to add a new module's name when one is created.
- Detection is deliberately narrow to `@Entity`/`@RestController`/`@Service` — a future class using
  only `@Component` or plain `@Controller` would not be seen by either rule. Explicitly documented
  in-code (Kimi Phase 8 Findings 6/7's disposition) as a named, accepted limitation matching this
  codebase's current, exclusive use of the narrower annotations.
- The pre-existing, separately-tracked Surefire/ArchUnit engine non-execution issue (found during
  T32) still affects the other 10 `@ArchTest` rules in this file — this task's own two rules are
  immune to it via their canaries, but the broader issue remains open for a future task.

**Verdict: PASS** — every requirement, design decision, and acceptance criterion for T35 traces to
implemented, tested, and genuinely-negative-proofed rules; every accepted residual and every
rejected review suggestion is named with its own reasoning, not silently absorbed or dropped.

---

**Phase 12 complete — verification written.** Proceed to Phase 13 (PR Preparation) on approval.
