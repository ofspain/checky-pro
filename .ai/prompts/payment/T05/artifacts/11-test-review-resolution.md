# payment · T05 · Phase 11 — Test Review Resolution

Consumes `artifacts/11-test-review.md` (Kimi 2.7). Records disposition of each gap and the
fixes actually applied, mirroring the established `payment` T02/T04 Phase 11 precedent (gaps
verified against source, then fixed in the same phase rather than deferred).

## Gap 1 — Happy path does not verify `repository.save(...)` is invoked

**ACCEPTED and FIXED.** Added `verify(repository).save(invoice)` to
`shouldCreateInvoiceWithOpenStateAndComputedExpiry`.

## Gap 2 — No boundary test at exactly 78 digits

**ACCEPTED and FIXED.** Added `shouldAcceptAmountAtExactly78DigitsPrecisionBoundary`.

## Gap 3 — `@Transactional` is not tested

**ACCEPTED and FIXED.** Added `createMethodIsAnnotatedTransactional`, a plain source-text guard
reading `InvoiceService.java` directly — mirrors `T01SkeletonRegressionTest`'s own identical
style, avoiding the need for a Spring context boot.

## Gap 4 — Constructors not scanned for forbidden parameter types

**ACCEPTED and FIXED.** Extended `ArchitectureTest`'s custom condition to also iterate
`javaClass.getConstructors()`. `RogueFloatingPointMoneyUser` now has a `(float seedAmount)`
constructor; the "actually fails" test confirms it is caught (`<init>(float)` in the violation
message).

## Gap 5 — Array types evade the forbidden-type set

**ACCEPTED and FIXED.** Expanded `FORBIDDEN_FLOATING_POINT_TYPES` with the JVM binary array
names (`[F`, `[D`, `[Ljava.lang.Float;`, `[Ljava.lang.Double;`). `RogueFloatingPointMoneyUser`
now has a `double[] amountHistory` field; confirmed caught (`forbidden floating-point type [D`).

## Gap 6 — `Invoice` JPA mapping still not empirically verified against a real database

**ACCEPTED as written; NO action taken.** Same carried-forward, disclosed, environment-caused
gap as Phase 7 finding 1 / Phase 8 finding 6 — Docker is not running in this session. Not
actioned again here for the same reason; task 6's own first DB-backed invoice test remains the
natural place this closes.

## Gap 7 — `Invoice`'s lifecycle fallback (`@PrePersist`/`@PreUpdate`) is not exercised

**ACCEPTED as written; NO test added.** Same disposition as `OutboxEvent`'s identical T04 Phase
11 Gap 7 — `crypto-service` has no equivalent fallback test for its own analogous entities
either, and the alternative suggestion (an ArchUnit/source-text rule forbidding direct `Invoice`
construction outside its package) is module-boundary enforcement (L12), explicitly out of
scope for this task per the frozen brief ("L1/L12 — out of scope, L6 only").

## Verification performed this phase

- `mvn -pl services/payment -am test -Dtest='ArchitectureTest,InvoiceServiceTest'` — 10/10
  passing (up from 8).
- Mutation checks (introduce defect → confirm red → revert → confirm green) on: `InvoiceService.
  create` returning the unsaved `invoice` directly instead of `repository.save(invoice)`
  (caught by Gap 1's new `verify`); an off-by-one in the 78-digit precision comparison (caught by
  Gap 2's new boundary test); removing `@Transactional` (caught by Gap 3's new source-text
  guard). All three production files confirmed reverted to a clean `git status`.
- Full non-Docker-dependent suite: **95/95 passing** (up from 93 before this phase).

## Net result

5 of 7 gaps accepted and fixed in this phase. Gap 6 (DB-mapping verification) and Gap 7
(`Invoice` lifecycle fallback) accepted as disclosed, precedent-consistent non-actions. No
production code changed. Proceeding to Phase 12.
