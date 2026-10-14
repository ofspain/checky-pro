# payment · T10 · Phase 9 — Review Resolution

Human Approval gate decision not required for this phase's fixes — Finding 1's fix is a real,
confirmed defect with an obvious, uncontroversial fix (same established precedent as T06 Phase
7/T08 Phase 9); Finding 3 is a documentation clarification. Recorded here for the audit trail.

## Finding 1 — `detail` stored/exposed by reference, weakening immutability

**ACCEPTED and FIXED — the most significant finding this phase.** `VerificationRecord.create`
and `getDetail()` now both defensively copy the map via a shared private `copyOf` helper
(`Collections.unmodifiableMap(new LinkedHashMap<>(source))`, `null`-safe) — a caller mutating the
map after calling `record(...)`, or mutating what `getDetail()` returns, can no longer silently
change this entity's own persisted state. `Map.copyOf` was considered and rejected: it rejects
null keys/values, and a JSONB `detail` payload may legitimately contain a null value.

## Finding 2 — No test exercises the JSONB `detail` mapping

**ACKNOWLEDGED, no new action — already planned.** This is AC8 from the frozen brief
(`VerificationRecordRepositoryIntegrationTest`), already scheduled for Phase 10. Kimi's own
re-discovery of this gap independently confirms Phase 4's own disposition of Design-Challenge
Finding 3 was correct to prioritize.

## Finding 3 — "immutable" Javadoc wording was ambiguous

**ACCEPTED and FIXED.** The original phrase "(immutable, T02)" was describing
`V1__payments_baseline.sql` (the migration file itself, matching `Invoice`/`OutboxEvent`'s own
identical established convention for citing a frozen migration), not claiming
`VerificationRecord` the Java class is deeply immutable — but the placement was genuinely
ambiguous enough to misread, confirmed by Kimi's own citation spanning the Javadoc through the
`create` method body. Reworded for clarity, and added an explicit note on why fields are
non-final (a JPA requirement, not a sign of real mutability) plus a cross-reference to Finding
1's own fix for the one field (`detail`) that needed more than "non-final" to be safe.

## Finding 4 — `InvoiceRepositoryIntegrationTest` (T08) still broken

**ACKNOWLEDGED, no new action.** Already disclosed at T09 Phase 9/11 and logged in memory;
Kimi's own re-confirmation here adds no new information. Remains out of T10's own scope.

## Verification performed this phase

- `mvn -pl services/payment -am compile` — clean.
- Full non-Docker suite: **183/183 passing, unchanged** (the `detail`-copying fix is
  production-only; no new test yet — Phase 10's own job, which will also add a dedicated
  mutation-after-the-fact regression test for this exact fix).

## Net result

1 real defect fixed (the most significant finding), 1 documentation clarification applied, 2
findings acknowledged as already-planned/already-logged with no new action. Proceeding to Phase
10.
