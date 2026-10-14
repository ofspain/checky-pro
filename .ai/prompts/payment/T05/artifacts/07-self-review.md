# payment · T05 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T05 — Invoice domain |
| **Consumes** | `artifacts/06-implementation-notes.md` |

Findings only — no fixes applied here (Phase 9).

## Finding 1 — `Invoice`'s JPA mapping has not been empirically verified against a real database

- **Severity:** Medium (process gap, not a known defect)
- **Evidence:** This service's `ddl-auto=validate` (T04) means a wrong `precision`/`scale`/
  `length`/type on any column only surfaces at Spring context boot against a real Postgres
  instance — not at compile time, not in a plain-JUnit unit test. Docker is not running in this
  environment (confirmed earlier this session), so no context-loading test could be run this
  phase to empirically confirm the mapping is correct, only manually re-checked line-by-line
  against `V1__payments_baseline.sql` (done, Phase 6).
- **Recommendation:** No fix requested now — same situation `OutboxEvent` was in at T04 Phase 6/7
  (manually verified, empirically confirmed later once a context-loading test existed). Phase 10
  should include (or this task's `InvoiceServiceTest` should include, if it uses
  `@DataJpaTest`/Testcontainers rather than a pure-mock unit test) a real context boot to close
  this gap — flagged so it isn't silently skipped.

## Finding 2 — Amount-precision check logic reviewed for edge cases

- **Issue:** Not a defect — self-verification record. `validateAmount`'s ordering (null → `<=0`
  → `stripTrailingZeros().scale() > 0` → `toBigIntegerExact()` digit count) was traced through:
  `BigDecimal.ZERO` (caught by the `<=0` check before reaching `stripTrailingZeros`, avoiding a
  known JDK edge case where zero's own stripped scale is inconsistent across versions);
  trailing-zero whole numbers like `100.00` (scale 2, stripped scale -2, correctly accepted, not
  rejected as fractional); and a 81-digit whole number with trailing zeros (correctly rejected —
  `toBigIntegerExact()` is called on the *original*, non-stripped `amount`, so the true digit
  count is measured, not the reduced-precision count `stripTrailingZeros()` would report).
- **Recommendation:** None — traced correct by inspection; Phase 10's tests should include the
  81-digit and trailing-zero-whole-number cases explicitly to make this reasoning permanent, not
  just this artifact's prose.

## Boundary / null-safety / thread-safety / transaction-boundary review

- `merchantAccountUuid`/`payToAddress` throw `NullPointerException` (not the typed business
  exception) on null — matches the frozen brief's own explicit distinction (these two are
  defensively guarded, not spec-named validations).
- `InvoiceService` holds no mutable instance state; `@Transactional` wraps the single `save`
  call, forward-looking per Phase 3 finding 5.
- `Invoice`'s `@PrePersist`/`@PreUpdate` fallback (`Instant.now()`) is unreachable through
  `InvoiceService.create` (which always sets both timestamps explicitly) — same accepted,
  disclosed limitation as `OutboxEvent`'s identical T04 pattern.
- No money type anywhere is `float`/`double` — `amount` is `BigDecimal` throughout; the ArchUnit
  enforcement itself is Phase 10's job (test file), not verified by this review beyond the
  manual class-by-class read performed here.

## Verdict

No fixes required before Phase 8. Finding 1 is a disclosed, carried-forward verification gap
(environment-caused, not a code defect); Finding 2 is a positive self-check record.
