# payment · T25 · Phase 11 — Test Review

Gaps found by reviewing the Phase 10 test manifest and the actual `ArchitectureTest` against the frozen brief's acceptance criteria.

---

## Gap 1 — No committed test proves the rule fails when the `ResourceServerConfig` exclusion is removed

- **Why it matters:** `shouldEnforcePublicEndpointAllowlistExclusionIsLoadBearing` proves that `ResourceServerConfig` contains a real `permitAll()` call, but it does not run the rule against `ResourceServerConfig` *without* the exclusion. The non-vacuity claim relies on the Phase 6 manual mutation check, which is not part of the committed test suite.
- **Suggested test:** Add a test that imports only `ResourceServerConfig` (and its dependencies) and asserts that `shouldEnforcePublicEndpointAllowlist` fails when checked against it. This encodes the mutation result permanently and guards against a future refactor that accidentally makes the rule too narrow.

---

## Gap 2 — No test asserts exactly one production `permitAll()` call

- **Why it matters:** The load-bearing test uses `anyMatch`, so it only proves at least one `permitAll()` call exists. `PublicEndpointsTest` guards the source-text count, but an ArchUnit-level assertion that there is exactly one distinct `permitAll()` call in production code would close the loop between the bytecode rule and the allowlist test.
- **Suggested test:** In `shouldEnforcePublicEndpointAllowlistExclusionIsLoadBearing`, collect the distinct `permitAll()` method calls from `ResourceServerConfig` and assert the count is one (filtering out synthetic bridge methods if necessary).

---

## Gap 3 — No violation canary covers `permitAll()` inside a lambda

- **Why it matters:** `ResourceServerConfig`'s real call sits inside a lambda, and the rule works because the class is excluded. A stray `permitAll()` call inside a lambda in another production class could potentially evade `callMethod` detection depending on how ArchUnit resolves lambda bodies. The current fixture (`RoguePermitAllUser`) calls `permitAll()` directly.
- **Suggested test:** Add a second fixture method or a second fixture class that calls `auth.requestMatchers(...).permitAll()` inside a lambda, and verify the rule still fails. If it does not, the rule needs to be broadened.

---

## Gap 4 — No test verifies `anonymous()` or `web.ignoring()` are absent

- **Why it matters:** The rule's Javadoc documents that it only covers `permitAll()`. A future developer could introduce `anonymous()` or a `WebSecurityCustomizer` with `web.ignoring()` and believe L10 is still enforced. There is no test or rule that would fail.
- **Suggested test:** Either extend `shouldEnforcePublicEndpointAllowlist` to ban these methods too (with canaries), or add a separate `shouldNotDisableAuthenticationViaAnonymousOrWebIgnoring` rule with its own fixture.

---

## Gap 5 — No test verifies the fixture is not component-scanned

- **Why it matters:** `RoguePermitAllUser` is intentionally not a Spring bean, but a regression in component-scan configuration could pick it up in a slice test or full context test. The current tests are pure ArchUnit, so they never load the class as a bean.
- **Suggested test:** Add a cheap ArchUnit assertion that `RoguePermitAllUser` (and the other fixtures) carry no Spring stereotype annotations (`@Component`, `@Service`, `@Configuration`, etc.).

---

## Summary

T25's test suite is solid: every rule has a real-code canary and a violation canary, the Surefire workaround is preserved, and the load-bearing exclusion test addresses the vacuity risk. The highest-value additions are Gap 1 (encode the no-exclusion failure) and Gap 3 (lambda violation canary). Gaps 2, 4, and 5 are lower-priority completeness notes.
