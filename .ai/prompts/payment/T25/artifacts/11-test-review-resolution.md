# payment · T25 · Phase 11 — Test Review Resolution

Consumes: `artifacts/11-test-review.md` (Kimi).

**Human sign-off: 2026-10-19.** Every gap was checked against source before disposition.

| Gap | Decision | Reason | Exact change |
|---|---|---|---|
| **3**: no violation case calls `permitAll()` inside a lambda | **ACCEPTED** | This is the realistic shape of a rogue `SecurityFilterChain`. The fixture only had a direct call. | `RoguePermitAllUser` gains `openUpInsideALambda(HttpSecurity)`, which calls `http.authorizeHttpRequests(auth -> auth.requestMatchers("/rogue-lambda/**").permitAll())`. In the violation canary, the assertion is now `hasMessageContaining("RoguePermitAllUser.openUp(")` (with the opening paren, so it doesn't also match the lambda method's name), plus `hasMessageContaining("RoguePermitAllUser.openUpInsideALambda(")`. |
| **2**: the load-bearing test proves "at least one" `permitAll()`, not exactly one | **ACCEPTED** | It is cheap to add. It is the bytecode-level counterpart to `PublicEndpointsTest`'s source-line count. | In `shouldEnforcePublicEndpointAllowlistExclusionIsLoadBearing`, `anyMatch(...)` became `filteredOn(...).hasSize(1)`. |
| **1**: a committed no-exclusion run of the rule against `ResourceServerConfig` | **REJECTED** | It is already proven in combination by two tests. The violation canary proves the rule's predicate matches `AuthorizedUrl.permitAll()` calls, including in lambdas (Gap 3). The load-bearing test proves `ResourceServerConfig` contains exactly one such call. A copy of the rule without the exclusion would only duplicate rule logic. | None |
| **4**: `anonymous()` / `web.ignoring()` | **REJECTED** | This was already decided twice (Phase 4 F2, Phase 9 F3): document the limit only, as the rule's Javadoc does. | None |
| **5**: ban Spring stereotypes on fixtures | **REJECTED** | No fixture carries any annotation besides `RogueUnmappedEntity`'s deliberate `@Entity`, which is pre-existing. The frozen brief already requires the T25 fixture to be unannotated. The added value is low, and it would be scope beyond the frozen brief. | None |

## Verification

- **`ArchitectureTest`: 12/12 passed.** There is no new test method: Gaps 2 and 3 extend
  existing tests.
- **Mutation check (temporary, both files restored from scratchpad copies).**
  - I made two edits:
    - changed the lambda call to `.denyAll()`
    - changed `hasSize(1)` to `hasSize(2)`
  - Exactly the two affected tests failed: the violation canary and the load-bearing test.
- **Full `mvn -o test`: 493/493, BUILD SUCCESS.**
