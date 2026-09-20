<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# auth · T32 · Phase 1 — Specification Extraction

## Business Rules

This task carries no direct requirement ID (`package.md` §7 lists it as a process/verification
step, not tied to an R-numbered EARS requirement). It exists to permanently enforce two already-
LOCKED decisions rather than to implement new user-facing behavior:

- No R-ID applies directly. The task's own effect is to make **L11** and **L12** (below)
  CI-enforced rather than convention-only.

## Locked Decisions

- **L11 — Public endpoint discipline.** The only unauthenticated API paths are: actuator
  health/info/prometheus, `POST /accounts`, SAS protocol endpoints (`/oauth2/**`, `/.well-known/**`,
  `/userinfo`, `/login`), and the API-key exchange `POST /api-keys/token`. Any new public path must
  be added to `PublicEndpoints.java`. **This task's core subject.**
- **L12 — Module boundaries.** No feature module may import an entity class from another feature
  module; shared plumbing lives in `common`; enforced by `ArchitectureTest`. Relevant here only
  insofar as any new rule this task adds must itself respect module boundaries (e.g. not requiring
  a new cross-module dependency) — this task adds no new module, so L12 is a constraint on *how*
  to implement, not a subject to newly enforce.

(Per Phase 0's finding: `package.md`'s own named-test table cites L9 instead of L11 for this task's
named test — treated as a stale cross-reference in `package.md`, not followed; L11/L12 per this
task's own Phase 0-3 header is authoritative.)

## Files involved

**Existing files to read/extend:**
- `services/auth/src/main/java/com/themistra/auth/common/PublicEndpoints.java` — the allowlist
  itself. Already contains `POST /api-keys/token` (added at T25); no production change expected
  unless Phase 2/5 finds a gap.
- `services/auth/src/main/java/com/themistra/auth/token/SecurityChainsConfig.java` — the only file
  that calls `.permitAll()` (2 call sites, both sourced from `PublicEndpoints`); read-only for this
  task unless a structural ArchUnit rule needs a specific shape here to be checkable.
- `services/auth/src/test/java/com/themistra/auth/ArchitectureTest.java` — target file per the
  task's own literal wording ("Update `ArchitectureTest`"); already has
  `only_token_module_references_public_endpoints` (class-dependency rule) as a neighboring
  precedent, but no rule yet constrains `.permitAll()` call sites or asserts allowlist contents.
- `services/auth/src/test/java/com/themistra/auth/common/PublicEndpointsTest.java` — established
  precedent (T07) for "assert entry X is registered in `PublicEndpoints.METHOD_SCOPED`," via a
  plain JUnit content assertion, not ArchUnit. Currently asserts the two password-reset entries
  only.

**New files the spec expects:** none named explicitly. `design.md`'s own file tree only lists
`PublicEndpoints.java` under this task's line item, annotated "(add /api-keys/token if public)" —
already satisfied since T25. No new production class is implied by the spec; whether a new test
file is needed (vs. extending the two existing ones) is a Phase 2/5 decision.

## Dependencies

- `com.themistra.auth.common.PublicEndpoints` (class + its `PATTERNS`/`METHOD_SCOPED` fields).
- `com.themistra.auth.token.SecurityChainsConfig` (the sole caller of `.permitAll()`).
- ArchUnit (`com.tngtech.archunit.*`) — already a test dependency, used by every existing
  `ArchitectureTest` rule; whatever construct this task needs (class-dependency rule vs. a
  method-call-site rule, per Phase 0's open question) must exist in the already-resolved ArchUnit
  version already on the classpath — no new dependency expected.
- No new config keys, no new contracts, no new entity/repository/service dependency.

## Acceptance Criteria

`package.md` does not enumerate task-specific AC-numbered criteria for this task (its AC-numbered
lists in `package.md`/frozen-brief-style documents are per-feature-task; T32 is a verification
sweep). Deriving directly from the task statement's own two clauses:

- **AC1.** `/api-keys/token` is asserted to be present in the public/unauthenticated allowlist —
  a regression fails CI if this entry is ever removed or altered.
- **AC2.** No handler anywhere in the codebase is `permitAll`-reachable outside
  `PublicEndpoints`'s declared set — a regression (a future stray `.permitAll()` call, or a new
  `requestMatchers(...)` added outside `SecurityChainsConfig`) fails CI.

## Tests required

- **`shouldEnforcePublicEndpointAllowlist`** (named, `package.md` §8) — does not exist yet anywhere
  in the codebase (confirmed via `grep -rn` in Phase 0). Its exact shape/location (single test vs.
  a pairing of an ArchUnit rule + a content-assertion test, and which file(s) it lives in) is a
  Phase 2/5 design decision, not decided here.
- Implied boundary tests (not separately named, but covered by AC1/AC2's own scope):
  - A negative case proving the rule/test would actually fail if `/api-keys/token` were removed
    from `PublicEndpoints` (or if a stray `permitAll()` were added elsewhere) — i.e. the test must
    be demonstrably not vacuously true. How to prove this without literally breaking production
    code temporarily is a Phase 5 implementation-plan concern.

## Open Questions

No genuine blockers. Two non-blocking observations carried forward from Phase 0 for Phase 2 to
resolve (not spec-author questions — no `package.md` §11 item covers this task, confirmed by
reading the full §11 list: none of Q1-Q6 concern public-endpoint enforcement):

1. The task statement names `ArchitectureTest` specifically, but this codebase's own established
   precedent for "assert an allowlist entry is present" (T07's `PublicEndpointsTest`) is a
   different, non-ArchUnit file. Phase 2 must decide whether to follow the task's literal wording,
   the codebase's own precedent, or split the two ACs across both (structural rule in
   `ArchitectureTest`, content assertion in `PublicEndpointsTest`).
2. Whether ArchUnit (the version already on this classpath) can express "no class outside
   `SecurityChainsConfig` may call `.permitAll()`" as a method-call-site rule (distinct from every
   existing rule in this file, which operates on class-level dependencies, not individual method
   calls) needs verification before Phase 5 commits to that approach.

---

**Phase 1 complete — specification extraction written.** Proceed to Phase 2 (Task Implementation
Brief) on approval.
