# payment · T25 · Phase 9 — Review Resolution

Consumes:
- `artifacts/07-self-review.md`
- `artifacts/08-independent-review.md` (Kimi)

**Human sign-off: 2026-10-19.** Every disposition below is the human's decision. Each Kimi
finding was checked against source and against the frozen brief before it went to the human.

## Resolution log

| Comment | Decision | Reason | Exact change |
|---|---|---|---|
| **Kimi F1** (= self-review F2): the `"permitAll"` assertion in the violation canary does not discriminate | **ACCEPTED** | Verified. The rule's own description always contains "permitAll", so the assertion holds for *any* failure of this rule. | In `ArchitectureTest`, `shouldEnforcePublicEndpointAllowlistActuallyFailsAgainstAGenuineViolation`: the two checks `hasMessageContaining("RoguePermitAllUser")` and `.hasMessageContaining("permitAll")` became one, `hasMessageContaining("RoguePermitAllUser.openUp")`. That pins the exact offending method. |
| **Kimi F5**: no committed test proves the real-code canary isn't vacuous | **ACCEPTED** | Verified. Only Phase 6's one-off mutation check showed it. | New `@Test shouldEnforcePublicEndpointAllowlistExclusionIsLoadBearing`. It asserts that `analyzedClasses().get(ResourceServerConfig.class).getMethodCallsFromSelf()` contains a call named `permitAll` whose owner is `AuthorizeHttpRequestsConfigurer.AuthorizedUrl`. It doesn't duplicate any rule logic. The change also adds an `assertThat` static import. |
| **Kimi F2**: rename the rule to `shouldBanStrayPermitAllCalls` | **REJECTED** | This duplicates Phase 3 F6, which the human already decided at the Phase 4 gate: keep the name for parity with auth and notification, and add Javadoc. Phase 9 rules also forbid renames. | None |
| **Kimi F3**: extend coverage to `anonymous()` / `web.ignoring()`, or edit `agents.md` | **REJECTED** | This duplicates Phase 3 F2, decided at Phase 4: document the limit only. `spec/` files may not be modified (guardrail). | None |
| **Kimi F4**: a Spring Security upgrade note in `pom.xml` | **REJECTED** | `pom.xml` is outside the frozen brief's Files section. A rename fails compilation loudly, and the rule's Javadoc already records the coupling (Phase 4 F4). | None |
| **Self-review F1**: `doNotBelongToAnyOf` also exempts nested classes | **No change** (informational) | A nested class still lives in `ResourceServerConfig.java`, so `PublicEndpointsTest`'s source sweep catches it. This also matches sibling parity. | None |
| **Self-review F3**: the Phase 5 per-class count was wrong | **No change** (informational) | This is a documentation error only, already disclosed in Phase 6. | None |

## Verification

- **`ArchitectureTest`: 12/12 passed.** That is 11 plus the F5 test.
- **Mutation check, temporary and restored from a scratchpad copy.**
  - I made two edits:
    - changed the F5 predicate to `"denyAll"`
    - changed the F1 assertion to `"RoguePermitAllUser.nope"`
  - Each edit made exactly its own test fail: 2 failures in total.
  - Restoring the file was confirmed.
- **Full `mvn -o test`: 493/493, BUILD SUCCESS.** That is 492 plus the F5 test.

The brief's AC5 figure of "492" is now 493. The extra test is a human-approved Phase 9
addition, not drift.
