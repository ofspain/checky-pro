# payment · T29 · Phase 11 — Test Review

Gaps found by reviewing the Phase 10 test manifest and the final `T01SkeletonRegressionTest` against the frozen acceptance criteria.

---

## Gap 1 — The citation guard does not verify that cited methods are `@Test` methods

- **Why it matters:** `everyTestNamedInTheVerificationChecklistExists` checks for `void <name>(` anywhere in the cited class. A package-private helper method with the same name would satisfy the guard even though it is not a JUnit test.
- **Suggested test:** Change the source search to require `void ` + name + `(` to appear after `@Test` (with possible whitespace/annotations in between) or to appear inside a class that has at least one `@Test` annotation.

---

## Gap 2 — The partial/deferred guard does not verify that items 2–12 remain ticked

- **Why it matters:** `verificationChecklistKeepsItsPartialAndDeferredItemsUnticked` asserts only that items 1, 13, and 14 are not ticked. A future edit could silently untick an item that is supposed to be complete, and no test would fail.
- **Suggested test:** Add assertions that exactly 11 items start with `- [x] `, that items 2–12 are among them, and that no item uses a malformed marker such as `- [~]`.

---

## Gap 3 — The citation guard does not verify that cited classes are actual test classes

- **Why it matters:** A bare `…Test` class citation passes if the file exists, even if the file contains no `@Test` methods or is not compiled as a test. The guard only checks file existence.
- **Suggested test:** Assert that each cited class file contains at least one `@Test` annotation.

---

## Gap 4 — No guard ties the deferred-test exemption to the spec text

- **Why it matters:** `DEFERRED_UNWRITTEN_TESTS` is hard-coded to `shouldAnchorLedgerHeadOnChainDaily`. The test does not prove that this is the same name used in §9 item 14. If item 14's wording changed, the exemption could become stale or over-broad.
- **Suggested test:** Parse the deferred method name(s) from item 14 itself and assert they match `DEFERRED_UNWRITTEN_TESTS`.

---

## Gap 5 — No guard verifies the R28 follow-up is recorded in §9 item 1

- **Why it matters:** Item 1 is partial because the merchant-scoped receipt read endpoint (R28) is unbuilt. The test only checks that item 1 starts with `- [ ] ` and contains `**Partial —**`; it does not verify that the follow-up language is present.
- **Suggested test:** Assert that item 1 contains a marker such as `FU-T29-1`, `receipt read`, or `unbuilt` so the partial reason cannot be accidentally dropped.

---

## Gap 6 — No guard verifies that the header table has exactly one Version and one Status row

- **Why it matters:** The header guard restricts its search to the header table and checks the values, but it does not assert uniqueness. A malformed header with two Version rows (one `0.1` and one `0.2`) could pass.
- **Suggested test:** Assert that `Pattern.compile("^\\| Version \\|").matcher(header).results().count()` is exactly 1, and similarly for Status.

---

## Gap 7 — The cited tests are not proven to be passing or even runnable

- **Why it matters:** The guards prove that cited test names exist, not that the tests pass, are enabled, or are part of the Maven Surefire run. A cited test that is `@Disabled`, excluded by a profile, or in a non-standard directory would still satisfy the guard.
- **Suggested test:** This is intentionally out of scope for a fast unit test, but the manifest should record that the passing claim relies on the full `mvn verify` run, not on these guards alone.

---

## Summary

T29's guard tests are very strong after Phase 9: they verify the header values, the existence and class-location of cited tests, and the visibility of partial/deferred items. The highest-value additions are Gap 2 (ensure completed items stay ticked) and Gap 4 (tie the deferred exemption to the spec text). Gaps 1, 3, 5–7 are smaller completeness or documentation notes.
