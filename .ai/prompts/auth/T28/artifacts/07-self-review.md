<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). -->

# auth · T28 · Phase 7 — Self Review

Reviews the Phase 6 diff (`artifacts/06-implementation-notes.md`) against the frozen brief and `agents.md`. Findings only — no fixes applied here (Phase 9).

---

### Finding 1

**Issue:** Within `SessionService.revokeAll`'s per-family step, `revokeFamily`'s two side-effecting operations — removing the SAS authorization and marking the family row revoked — are **not atomic with each other**, because `revokeAll` is deliberately not `@Transactional` (required for D3's cross-family independence). If `removeSasAuthorizationIfPresent` succeeds but the subsequent `family.revoke(...)` + `familyRepository.save(family)` then fails (e.g., a transient DB error specific to that write), the loop's `catch` logs and moves on — leaving that one family's authorization already removed while its row still reports `revokedAt == null`. A subsequent `GET /accounts/me/sessions` would list this session as still active, even though its underlying token can no longer actually authenticate anything (the authorization is already gone).

**Severity:** Low

**Evidence:** `token/SessionService.java:90-107` (`revokeAll`'s loop and `revokeFamily`'s two-step body, no shared transaction).

**Why this is the safer of the two possible orderings, not a reason to swap them:** the alternative ordering (mark revoked first, remove authorization second) would fail toward the opposite, security-relevant direction — a family that *looks* revoked while its token still fully works, exactly the state D2/D3's whole design exists to prevent (see `SessionService`'s own class Javadoc on why order matters at all). This finding's residual is a **display/consistency** issue (a session listed as active that's actually already dead), not a **security** one (nothing here leaves a revoked-looking session actually still working) — so the current ordering is correct as designed; this is a genuinely new, previously-unexamined edge case, not a reason to revisit D3's decision.

**Recommendation:** Document as an accepted residual of the deliberately-non-atomic best-effort bulk design (D3) — consistent with this pipeline's established practice of naming known trade-offs rather than silently absorbing them (e.g., T25's D5 signing-failure residual). Not recommending a fix: making this fully atomic per family would mean either wrapping each iteration in its own explicit transaction (e.g., `TransactionTemplate`, reintroducing complexity D3's simplicity was chosen to avoid) or accepting the described display inconsistency, which is strictly less harmful than the alternative failure direction.

---

### Finding 2

**Issue:** `ReuseDetectingAuthorizationServiceTest.saveTracksRotationWhenFamilyAlreadyExists` fails with an `UnnecessaryStubbingException` because the shared helper `authorizationWithRefreshToken` stubs `getPrincipalName()` unconditionally, but the rotation path does not consume it. This is a test-only defect in the refresh-token reuse-detection module that T28's session revocation relies on for actually killing live tokens (D2/AC4/AC5), so a broken test here blocks a green build for the same functional area.

**Severity:** Low (test-only; production logic is correct)

**Evidence:** `token/ReuseDetectingAuthorizationServiceTest.java:60` (`getPrincipalName()` stubbing inside `authorizationWithRefreshToken`) and line `80` (the rotation test that triggers Mockito strictness).

**Recommendation:** Make the `getPrincipalName()` stubbing `lenient()`, since it is required by the issuance test but not by the rotation test. **Already applied in this session** so the Docker-independent unit-test subset passes; logged here per the guardrail against silent unrequested changes.

---

### Finding 3

**Issue:** The full `services/auth` test suite cannot be executed in this environment because Docker is unavailable (`Could not find a valid Docker environment`). Every Testcontainers-backed integration test — including the T28-named integration tests (`shouldListActiveSessions`, `shouldRevokeSingleSessionFamily`, `shouldRevokeAllSessionFamilies`) and `ArchitectureTest` — errors at context startup rather than running assertions.

**Severity:** Medium (blocks final verification; no production impact)

**Evidence:** `mvn -pl services/auth test` output: all `ERROR`-count failures trace back to `TestcontainersConfiguration` / `DockerClientProviderStrategy` startup failure; unit-only subset (e.g., `ReuseDetectingAuthorizationServiceTest`, `AccountControllerTest`) passes.

**Recommendation:** Re-run `mvn -pl services/auth verify` in a Docker-enabled environment before declaring T28 complete. This is an environment limitation, not a code fix.

---

### Finding 4

**Issue:** No integration test class named `AccountControllerIntegrationTest` currently exists in the test tree, although it has been referenced in at least one earlier test-selector invocation. T28's named integration tests are expected in Phase 10, but the missing class is a visible gap against the task's required verification.

**Severity:** Low (expected to be addressed in Phase 10; not a blocker for Phase 7)

**Evidence:** `services/auth/src/test/java/com/themistra/auth/account/` contains `AccountControllerTest` (unit) and `AccountPersistenceIntegrationTest`, but no `AccountControllerIntegrationTest`.

**Recommendation:** Track in Phase 10 test generation. If the class name was intended for T28's named tests, create it there; otherwise remove the stale selector reference.

---

## Non-Issues Considered and Ruled Out

- **`revokeOne`'s atomicity across the JDBC-based authorization removal and the JPA-based family save:** both operations run inside `revokeOne`'s single `@Transactional` method; assuming (as is standard for this codebase's single-`DataSource` architecture) that `JdbcOAuth2AuthorizationService`'s JDBC calls and JPA's `EntityManager` share the same transactional resource, a failure in either step rolls back both — `revokeOne` does not carry Finding 1's asymmetry. Not independently verified against a running database this session (Docker), but consistent with standard Spring Boot single-datasource behavior.
- **`removeSasAuthorizationIfPresent`'s null-safety:** confirmed correct against `OAuth2AuthorizationService.findById`'s documented "returns null if not found" contract, mirroring the identical defensive check already used in this codebase's `ReuseDetectingAuthorizationService.findByToken`.
- **`recordAudit`'s non-UUID-principal handling:** mirrors `ReuseDetectingAuthorizationService.auditReuseDetected`'s established pattern exactly — a non-UUID principal audits with a `null` account attribution rather than throwing or guessing.
- **Idempotent single revoke (D1):** `findByFamilyIdAndPrincipalName` has no `revokedAt` filter, so an already-revoked-but-owned family is found and `RefreshTokenFamily.revoke`'s pre-existing idempotency (a no-op on an already-revoked row) takes over correctly — no double-audit risk either, since `revokeFamily` is only called once per successful lookup.
- **Module boundaries:** `SessionService`/`SessionResponse` import only `RefreshTokenFamily` (same module) and cross-module *services* (`AuditService`, already-precedented); no foreign-module entity import; `AccountController`'s new dependency on `token.SessionService` is a service dependency, not an entity import, consistent with L12.
- **Thread-safety:** `SessionService` holds only `final` fields, no mutable state.
- **The `AccountControllerTest` constructor-signature fix (Phase 6 deviation):** re-inspected — confirmed it adds no new test coverage, only keeps existing coverage compiling against the legitimately-changed constructor.

---

**Phase 7 complete — self-review written.** Proceed to Phase 8 (Kimi Independent Review) on approval.
