<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# auth · T32 · Phase 12 — Specification Verification

Compares the final implementation and tests (Phases 6–11) against `requirements.md`, `design.md`,
`tasks.md`, and the frozen brief (`artifacts/04-frozen-task-brief.md`) for **T32 only**.
`spec/auth-service/` confirmed unchanged during this task (`git diff HEAD -- spec/auth-service/` —
empty).

---

## Traceability Matrix — Task Statement

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| `/api-keys/token` is in the public allowlist | Yes | `PublicEndpoints.java:34` (unchanged, present since T25) | `ArchitectureTest.java:129-133` (`apiKeysTokenExchangeIsInThePublicAllowlist`) | No | No |
| No handler is `permitAll` outside the list | Yes, scoped to `.permitAll()` DSL calls (Phase 4 D-scope decision) | `ArchitectureTest.java:120-127` (`shouldEnforcePublicEndpointAllowlist`) | Same rule, invoked both as an `@ArchTest` and directly via the Phase 9 canary (`ArchitectureTest.java:148-150`) | No, within the frozen scope | Scope intentionally narrowed to `.permitAll()` only — `WebSecurityCustomizer`/`@PermitAll` explicitly out of scope (Phase 4, reaffirmed Phase 9); path-level verification (which paths are passed to `.permitAll()`, not just who calls it) explicitly accepted as a documented residual (Phase 9) |

## Traceability Matrix — Locked Decisions

| Decision | Honored? | Evidence |
|---|---|---|
| **L11** — public endpoint discipline, exhaustive allowlist, new public paths must be added to `PublicEndpoints.java` | Yes | Both new checks directly enforce this; no new public path was added (none needed) |
| **L12** — module boundaries, enforced by `ArchitectureTest` | Yes | No new package/module introduced; new imports (`SecurityChainsConfig`, `PublicEndpoints`) don't trip `only_token_module_references_public_endpoints` since `ArchitectureTest.java` is itself excluded from the analyzed class set (`ImportOption.DoNotIncludeTests`) — verified, not assumed (Phase 7) |

## Acceptance Criteria

| AC | Status | Evidence |
|---|---|---|
| AC1 — `/api-keys/token` presence regression-locked | **Met** | `apiKeysTokenExchangeIsInThePublicAllowlist`; negative-proof performed (Phase 11 Gap 3 closure): removed the entry, confirmed real `BUILD FAILURE`, restored |
| AC2 — no class outside `SecurityChainsConfig` calls `AuthorizedUrl.permitAll()` | **Met** | `shouldEnforcePublicEndpointAllowlist`; negative-proof performed twice (Phase 6 via direct Launcher invocation, Phase 9 via the canary under real `mvn test`) |

## This Task's Own Design Decisions — honored?

| Decision | Honored? | Evidence |
|---|---|---|
| AC2 mechanism: `callMethod(AuthorizedUrl.class, "permitAll")`, not a class-dependency rule (Phase 4, resolving Kimi Phase 3 Findings 1/2) | Yes | `ArchitectureTest.java:122-123`; verified via `javap` against the resolved Spring Security 7.1.0 jar that this precisely targets the method, not `.authenticated()`/`.hasRole()` |
| AC2 scope narrowed to `.permitAll()` only, not `WebSecurityCustomizer`/`@PermitAll` (Phase 4, reaffirmed Phase 9 against Kimi Phase 8 Finding 3) | Yes | Neither mechanism exists anywhere in this codebase (confirmed via `grep`) |
| Path-level verification explicitly out of scope (Phase 9, resolving Kimi Phase 8 Finding 1) | Yes | Documented residual, no code claims otherwise |
| Canary test to close the CI-enforcement gap, scoped to only this task's rule (Phase 9, resolving Kimi Phase 8 Finding 2; refined Phase 11 Gaps 1/2/4) | Yes | `ArchitectureTest.java:148-156` (`shouldEnforcePublicEndpointAllowlistIsCheckedDuringStandardBuild` + shared `analyzedClasses()` helper); explicitly documented as not covering the other 9 pre-existing rules |

## Findings from this phase

None new. All gaps found during T32's own pipeline (the AC1 negative-proof, the canary's scan-config
duplication, its name, its scope) were closed within Phases 9–11, not carried forward.

**Two findings surfaced during this task remain open as separate, out-of-scope follow-ups** (already
logged in `artifacts/06-implementation-notes.md` and project memory, not re-litigated here):
1. ArchUnit's JUnit 5 engine does not execute under this project's `mvn test`/Surefire setup at
   all — affects every `@ArchTest` rule in this file except `shouldEnforcePublicEndpointAllowlist`
   (which the new canary now covers independently). Root cause not fully identified.
2. A genuine pre-existing bug in `only_the_account_module_may_touch_the_Account_entity` (missing
   `..` wildcard) is currently hidden by finding #1 and will fail the build the moment it's fixed.

Neither is a T32 defect — both predate this task and are outside its frozen Files to Modify.

---

## Principal-Engineer Assessment

**(1) Is the task fully complete?** Yes, within its frozen scope. Both halves of the task
statement are implemented, tested, and each independently negative-proofed — a higher bar than
most tasks in this pipeline, driven by the discovery that a naive implementation would have
silently enforced nothing under the project's actual build command.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 and AC2 both Met, with real
negative-proof evidence for each, not just a passing assertion taken at face value.

**(3) Does it violate any LOCKED decision?** No. L11 is the task's own subject and is now
CI-enforced (for this rule specifically); L12 is respected — no new module, no verified boundary
violation.

**(4) Remaining risks?**
- The path-level residual (Phase 9 D-decision): a future in-`SecurityChainsConfig` change could
  still call `.permitAll()` on an undeclared path without failing this rule. Accepted, documented,
  and still requires a human-reviewed change to `SecurityChainsConfig` to trigger — low residual
  risk given that gate.
- The broader Surefire/ArchUnit non-execution issue (this task's own rule is now immune to it via
  the canary, but the other 9 pre-existing rules, including a real bug in one of them, are not) —
  a real, session-significant risk for the codebase as a whole, explicitly out of this task's scope
  and tracked separately.
- `WebSecurityCustomizer`/`@PermitAll` remain unguarded, accepted per the task's literal wording.

**Verdict: PASS** — both halves of the task statement are implemented, tested, and verified correct
via real negative-proof runs (not merely "the assertion is written and green"); no LOCKED decision
is violated; the two known open risks are pre-existing, out of scope, and already tracked
separately, not silently absorbed into this task's own claim of completeness.

---

**Phase 12 complete — verification written.** Proceed to Phase 13 (PR Preparation) on approval.
