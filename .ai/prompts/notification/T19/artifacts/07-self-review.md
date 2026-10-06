# notification · T19 · Phase 7 — Self Review

Self-review of the Phase 6 test against the frozen brief and Phase 1's acceptance criteria. Findings
only; fixes are Phase 9's job.

## Finding 1 · The template-version assertion covers only EMAIL rows, not the rendered IN_APP rows

**Severity:** Low

**Evidence:** `DeliveryLogDisputeGradeIntegrationTest.java:268-271`. The `allSatisfy` block asserting
`templateVersion()` is non-null runs over `emailRows` only (the list filtered to channel `EMAIL`).
The IN_APP row for the same event is also a rendered row (`user.verify` renders a message), so
AC3's own scope ("every rendered row records its template version") is not fully tested.

**Issue:** The AC3 claim is wider than the assertion. A regression that dropped the template version
on IN_APP rows only would pass the test, while the claim in the Phase 1 extraction would be false.

**Recommendation:** Extend the non-null template-version check to every row the chain renders, which
includes IN_APP. Fix in Phase 9.

## Finding 2 · The AC5 scan is textual and only catches literal SQL and repository delete calls

**Severity:** Low

**Evidence:** `noApplicationCodeUpdatesOrDeletesDeliveryLogRows` matches two patterns in `src/main/java`:
raw `UPDATE`/`DELETE ... delivery_log` text, and `deliveryLogRepository.delete*(`. It does not catch
mutations issued through `EntityManager` (for example, `createNativeQuery` with a dynamically built
string) or through another repository reference.

**Issue:** The static half of AC5 is partial by construction. The database grant (AC1) is the real,
complete backstop, because it rejects any such statement regardless of how it was built. So the
property still holds; only the static evidence is narrower than "no code path mutates the log."

**Recommendation:** Disclose in the Phase 12 verification that AC5's static scan is a textual guard,
and that AC1 carries the complete guarantee. No change to the test needed.

## Finding 3 · The scan's relative path depends on the working directory

**Severity:** Informational

**Evidence:** `Path.of("src/main/java")`. Surefire runs tests with the module as the working
directory, so the scan works as written.

**Assessment:** If the test were run from a different directory, `Files.walk` would throw
`NoSuchFileException` and the test would error. It would not pass silently on an empty scan. Behavior
is acceptable; no change.

## Checked and found correct

- The AC1 proof uses `notification_app` credentials, so it exercises the real grant (Phase 2
  constraint).
- The assertions check SQLState `42501`, not merely that some exception was thrown.
- Each method uses a fresh random account and source key, so methods do not depend on each other.
- AC4 asserts that no email was actually sent for the suppressed channel, not only the log row.

## Open Questions

No blockers. Finding 1 is a real, cheap assertion gap and will be fixed in Phase 9. Findings 2 and 3
are disclosures, not defects.
