<!-- MODEL: Claude Sonnet — Phase 1 (Specification Extraction). -->

# auth · T38 · Phase 1 — Specification Extraction

## Business Rules

No requirement ID is scoped to this task (process/verification step, confirmed at the header).
The task's own five defect classes function as its business rules, each already directly checked
against source at Phase 0 (see Files involved below).

## Locked Decisions

- **L11** — Public endpoint discipline: the only unauthenticated API paths are the ones named in
  `PublicEndpoints.java`; any new public path must be added there. Governs "unauthenticated admin
  routes" — confirmed honored (Phase 0): no `/admin/**` entry exists in that allowlist.
- **L12** — Module boundaries: no feature module may import an entity class from another feature
  module; shared plumbing lives in `common`; enforced by `ArchitectureTest`. Governs "shared model
  artifact" at the cross-service level (this task's actual scope) and, incidentally, is the same
  discipline applied intra-service.
- **L13** — Secrets discipline: no secret, credential, or signing key material is committed to the
  repo; External Secrets Operator injects real values; hardcoded defaults exist only for local
  development and are refused in non-local profiles by validated `@ConfigurationProperties` or
  startup guards. Governs "plaintext credentials" — confirmed honored (Phase 0): every sensitive
  `application.properties` value is `${ENV_VAR:local-only-placeholder}`, and every credential-shaped
  domain value (passwords, refresh tokens, API keys, TOTP seeds) is hashed or envelope-encrypted at
  rest.

No LOCKED decision directly names `Long.getLong`-style config misreads or
`allow-circular-references` — both are covered by this service's uniform, pre-existing
`@ConfigurationProperties`/`@Validated` convention (a design pattern, not a numbered decision) and by
simply never having set that Spring Boot property, respectively.

## Files involved

**Read/checked (no new files expected):**
- `services/auth/docs/architecture/gap-analysis.md` — the source of all five defect-class
  definitions (§2 Violations Register, §3 Defect → Test-Case Mapping).
- `services/auth/src/main/java/com/themistra/auth/common/PublicEndpoints.java` — admin-route check.
- `services/auth/pom.xml` + root `pom.xml` — shared-model-artifact check.
- `services/auth/src/main/resources/application.properties` — plaintext-credential check (config
  values) and `allow-circular-references` check.
- `Account.java` (`passwordHash`), `RefreshTokenFamily.java` (`currentTokenHash`), `ApiKey.java`
  (`keyHash`), `MfaSeedEncryption.java` — plaintext-credential check (domain values).
- Every `src/main/java/com/themistra/auth/**` source file — `Long.getLong`/raw-config-read check
  (full-tree grep, already performed).

**New files expected:** none, unless Phase 0's provisional "all absent" finding is overturned by
Phase 3/8's adversarial review, in which case a fix (not a new file, most likely) would follow the
same pattern as whichever existing mechanism already handles that defect class elsewhere in the
service.

## Dependencies

None beyond what's already listed under Files involved — this is a read-only verification task, not
a task with new runtime dependencies.

## Acceptance Criteria

| AC | Statement | Status at Phase 0 |
|---|---|---|
| AC1 | Plaintext credentials are absent | **Met** — every credential-shaped value hashed/encrypted; no committed secret in `application.properties` |
| AC2 | Unauthenticated admin routes are absent | **Met** — no `/admin/**` in `PublicEndpoints.java`, CI-enforced by `ArchitectureTest` |
| AC3 | Shared model artifact is absent | **Met** — no cross-service entity-sharing dependency in either `pom.xml` |
| AC4 | `Long.getLong` config misread is absent | **Met** — zero matches across the full source tree; uniform validated `@ConfigurationProperties` convention |
| AC5 | `allow-circular-references=true` is absent | **Met** — zero matches |

## Tests required

`package.md` §8 has no named test for this task. No new test is expected — this task's own
verification IS the deliverable, and the mechanisms that already enforce AC1-AC5 (validated
`@ConfigurationProperties`, `ArchitectureTest.shouldEnforcePublicEndpointAllowlist`, the absence of
any shared-model dependency) are pre-existing, already-tested elsewhere in this codebase's history
(T30/T32/T35 respectively) — this task's job is confirming they hold, not authoring new coverage for
them.

## Open Questions

**Not a blocker, carried forward for awareness.** Whether the repo-wide gitleaks CI gate (gap-analysis
§2/§3's stated defense for the "committed secrets" half of AC1) is actually configured is outside
`services/auth`'s own source tree to confirm from within this task's scope. Not blocking — AC1's
in-scope claim (no plaintext credential currently exists in this service) is independently verified
regardless of whether the CI gate that would catch a *future* one is present.

---

**Phase 1 complete — specification extraction written.** Proceed to Phase 2 (Task Implementation
Brief) on approval.
