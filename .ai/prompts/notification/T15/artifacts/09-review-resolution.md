# notification · T15 · Phase 9 — Review Resolution

**Human Approval gate.** Resolution log for the Phase 7 self-review (2 findings) and Phase 8
independent review (4 findings — 2 concur with self-review, 2 new but assessed as "no action
required"). Both self-review findings were marked "optional for Phase 9" by both reviews, but both
are cheap, low-risk, clearly-correct improvements — applied here rather than left as
documentation-only, consistent with how this pipeline has treated similarly low-cost, unambiguous
fixes before.

## Finding 1 (self-review + Kimi concur) · `matchesJsonSchemaType` assumes a single-string `type`, not the legal array form

**ACCEPTED, documented, not structurally fixed.** Both reviews agreed documenting the limitation is
sufficient — neither of this service's own two real consumed schemas (confirmed again directly, all
three schemas under `contracts/events/auth/`, including the unconsumed `security-audit`) ever uses
the array `type` form, so extending the helper to handle it now would be speculative for a case
nothing in this repository needs today.

**Change:** A Javadoc note added directly on `matchesJsonSchemaType` explaining the limitation, why
it's safe today, and what would happen if it were ever hit (a confusing `IllegalArgumentException`
rather than correct validation) — so a future maintainer who does need the array form knows exactly
where to look and why it wasn't handled.

## Finding 2 (self-review + Kimi concur) · A failure in the first DTO's check can mask a simultaneous drift in the second

**ACCEPTED and fixed** — the one finding both reviews explicitly offered as the "compatible fix" for
(`SoftAssertions`, since `@ParameterizedTest` was already rejected in Phase 3 to preserve the
literal method name).

**Change:** `shouldConformToConsumedEventSchemas` now reads both real schema files up front, then
runs both DTOs' own conformance checks inside one `SoftAssertions.assertSoftly` block, so every
assertion for both DTOs is evaluated and every failure reported, regardless of how many others also
fail. **Verified empirically, not assumed**: temporarily swapped which schema was paired with which
DTO (forcing 8 genuine, simultaneous failures across both), confirmed the real failure report
listed all 8 — 4 per schema, from both DTOs — in one single run, then reverted the injection and
confirmed the real test passes cleanly again.

## Finding 3 (Kimi, new) · The type check silently skips an optional schema field the DTO doesn't serialize

**ACCEPTED — correctly assessed as intentional, no action needed.** Verified directly: this is
exactly AC1's own stated scope (required-present, no-undeclared, now also type-match for whatever
*is* present) — a consumer is never obligated to serialize every optional field a schema declares,
only forbidden from inventing undeclared ones. No change.

## Finding 4 (Kimi, new) · The new test doesn't literally assert `additionalProperties: false` from the schema

**ACCEPTED — correctly assessed as already equivalent in practice, no action needed.** Verified
directly against both real schema files: the existing "every serialized field must be declared in
`properties`" check already enforces the exact same practical guarantee `additionalProperties: false`
does for these two schemas. No change.

## Summary

Two real changes applied: a documentation comment on `matchesJsonSchemaType`'s own known,
disclosed, currently-unreachable limitation (Finding #1), and a real, empirically-verified
correctness fix using `SoftAssertions` so a simultaneous drift in both DTOs is never masked
(Finding #2). Findings #3 and #4 required no change, both correctly assessed by Kimi as intentional,
already-correct behavior. Full suite: `mvn -pl services/notification clean verify` — 358 tests,
0 failures, 0 errors (unchanged count — this phase only strengthened existing test logic, added no
new test method).
