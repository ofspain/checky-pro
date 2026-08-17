<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). -->

# auth · T32 · Phase 10 — Test Generation

Test-only task convention (same as T27): the task's entire deliverable is test code, already
written and resolved across Phases 6/9 by extending `ArchitectureTest.java`. No production code
exists to test separately. This phase is purely the manifest mapping each test to what it verifies.
No new test was written in this phase.

## `ArchitectureTest.java` (extended — 3 new members)

| Test | Type | Verifies |
|---|---|---|
| `apiKeysTokenExchangeIsInThePublicAllowlist` | Plain `@Test` (JUnit Jupiter) | AC1 — `PublicEndpoints.METHOD_SCOPED` contains `POST /api-keys/token`. Fails if that entry is ever removed. |
| `shouldEnforcePublicEndpointAllowlist` (**named test, `package.md` §8**) | `@ArchTest static final ArchRule` (ArchUnit) | AC2 — no class other than `SecurityChainsConfig` calls `AuthorizedUrl.permitAll()`. Empirically proven to fire correctly on a deliberately-introduced violation and pass correctly without one (Phase 6 negative-proof). |
| `shouldEnforcePublicEndpointAllowlistActuallyRunsUnderThisBuild` | Plain `@Test` (JUnit Jupiter) | Kimi Phase 8 Finding 2's closure — directly invokes `shouldEnforcePublicEndpointAllowlist.check(...)` against a freshly-imported class set, so AC2 is enforced by `mvn test` today regardless of the separate, still-open Surefire/ArchUnit engine-integration gap. Re-verified via a second negative-proof run in Phase 9 (reintroduce violation → confirm real `BUILD FAILURE` → revert). |

## Boundary/negative-proof coverage (performed, not separately shipped as tests)

- Phase 6: introduced a scratch `.permitAll()` call outside `SecurityChainsConfig`, confirmed (via
  direct JUnit Platform Launcher invocation, since Surefire alone doesn't run the ArchUnit engine)
  that `shouldEnforcePublicEndpointAllowlist` correctly fails; reverted.
- Phase 9: reintroduced the same scratch violation, confirmed the new canary test
  (`shouldEnforcePublicEndpointAllowlistActuallyRunsUnderThisBuild`) makes `mvn test` itself report
  `BUILD FAILURE` with the exact violation message; reverted.
- AC1's negative case (removing `/api-keys/token` from `PublicEndpoints.METHOD_SCOPED`) was not
  separately re-verified this phase — it follows the same, already-established pattern as T07's
  `PublicEndpointsTest`, whose own equivalent assertions are proven to correctly fail on removal by
  construction (a plain `contains(...)` on an immutable list either holds or doesn't; no dynamic
  behavior to prove beyond the assertion itself compiling and running, both confirmed).

## Verification performed

- `mvn -pl services/auth clean test-compile` — clean, no errors.
- `mvn -pl services/auth test -Dtest='ArchitectureTest'` — **3/3 pass** (the two content/canary
  tests via the JUnit Jupiter engine, which directly exercises the named ArchUnit rule's own
  `.check()` logic; the ArchUnit engine's own separate invocation still reports 0 tests, the known,
  separately-tracked Surefire/ArchUnit gap — no longer a blind spot for this task's own rule
  specifically, since the canary test closes it).

The named `package.md` §8 test (`shouldEnforcePublicEndpointAllowlist`) is fully written, verified
correct via two independent negative-proof runs, and — as of the Phase 9 resolution — actually
enforced by this project's standard `mvn test` command.

---

**Phase 10 complete — test manifest written.** Proceed to Phase 11 (Kimi test review) on approval.
