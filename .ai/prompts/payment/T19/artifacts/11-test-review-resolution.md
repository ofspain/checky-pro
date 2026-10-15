# payment · T19 · Phase 11 — Test Review Resolution

Consumes: `artifacts/11-test-review.md` (Kimi)

This companion note records the disposition, mirroring T18's own identical precedent (the test-
review phase has no separate numbered resolution artifact in this task's own prompt set).

## A real, severe production bug found while fixing Gap 7 (disclosed prominently, not buried)

While building the real-17-field-payload test Kimi's own Gap 7 asked for, the test failed with
`permission denied for table ledger_entries` — **not a test artifact, a genuine, confirmed defect
that would have broken every single real production ledger append.**

**Root cause (confirmed empirically, isolated by direct experiment, not guessed):** Hibernate's
own dirty-checking for a `@JdbcTypeCode(SqlTypes.JSON)` `Map<String,Object>` field takes its
"last known state" snapshot by round-tripping the value through JSON. A boxed `Short` value
survives JSON *serialization* but comes back as a different boxed numeric type (`Integer`) on
Hibernate's own internal *deserialization* side of that round-trip — and `Short(6).equals
(Integer(6))` is `false` in Java, so Hibernate concludes the field "changed" since the last
snapshot and issues a spurious `UPDATE` immediately after the correct `INSERT`, inside the same
transaction. `payment_app` has no `UPDATE` grant on `ledger_entries` (L7, by design) — every
real `LedgerService.append(...)` call uses `ReceiptStore.toCanonicalMap(...)`'s own output as the
base of its 17-field payload, and that map's `tokenDecimals` field is always a bare `short`
(confirmed, `ReceiptStore.java`) — so this was not a hypothetical edge case, it was the
*guaranteed* shape of every real production payload.

Confirmed via a direct, controlled experiment: changing `tokenDecimals` from `(short) 6` to the
string `"6"` in the test made it pass; changing it back reproduced the crash deterministically.
`VerificationRecord.detail` (T10) uses the identical `@JdbcTypeCode(SqlTypes.JSON)` mapping but
was checked and confirmed *not* currently exposed — every existing caller only ever puts `int`/
`Integer` values into it (which round-trip through JSON as `Integer` correctly), never a `Short`
or other type Jackson's generic deserialization would "upgrade." Disclosed as a related, latent,
not-yet-triggered risk for `VerificationRecord` — not fixed there, out of this task's own scope,
since no actual bug manifests today.

**Fix applied:** `@Immutable` (Hibernate's own annotation) added to `LedgerEntry`. This tells
Hibernate the entity is never updated after insert, skipping dirty-checking entirely — fixing the
bug as a direct consequence, and also the semantically correct annotation regardless: L7 itself
says "no `UPDATE`/`DELETE` ever." Confirmed via the same test, now green with the real `Short`
value restored.

This also means the earlier, unroot-caused "bare `ExecutorService` thread" anomaly from Phase 6/7
(self-review Finding 2, Kimi Phase 8 Finding 3) is now **explained, not merely worked around**:
that scratch test's own payload also contained a boxed `Long` (`receiptId`), the identical class
of bug — thread context was a red herring; the real trigger was always the non-String-surviving
value type, present in that scratch test's own payload independent of which thread ran it.

## Disposition of Kimi's 10 gaps

| # | Gap | Disposition | Reason |
|---|---|---|---|
| 1 | `ledgerService.append` called with a null/unverified receipt id in mocks | **ACCEPTED** | Set a known id via `ReflectionTestUtils` on the stubbed `Receipt`; `shouldTransitionToAttestedAfterReceiptStoredAndLedgerAppended` now asserts `ledgerService.append(eq(SAVED_RECEIPT_ID), ...)` exactly. |
| 2 | `shouldEmitReceiptIssuedAfterAttestation` asserts only 5 of 11 fields | **ACCEPTED** | All 11 fields now asserted, against fixed (not re-randomized) UUID constants. |
| 3 | `shouldTransitionToAttestedAfterReceiptStoredAndLedgerAppended` uses loose matchers | **ACCEPTED** | Captures and asserts every `Receipt` field and the full ledger payload map's key entries. |
| 4 | No real-DB test for `markAttestedIfFinalized`'s own JPQL | **ACCEPTED** | New `PaymentRepositoryIntegrationTest`, mirroring `InvoiceRepositoryIntegrationTest`'s established shape (including its own identical `TransactionRequiredException`-avoidance fix) — proves `state`/`attested_at` really update; mutation-verified. The identical gap for `markHeldIfFinalized` (T17) is disclosed, not fixed — out of scope. |
| 5 | Golden-hash tie test only covers genesis | **ACCEPTED** | New test ties the SECOND entry's hash to an independent recomputation using the first entry's real, non-genesis `entryHash`. |
| 6 | Abort test doesn't prove the real DB rollback | **REJECTED** | Mirrors T17's own Phase 11 Gap 2 precedent exactly: asserting `TransactionTemplate`'s own rollback mechanism mostly tests Spring's framework, not application logic. The genuinely representative proof (two real Kafka listener threads, a real end-to-end flow) belongs with task 26's own end-to-end test, not a unit-level addition here — the same scope boundary already drawn for AC4's concurrency guarantee at Phase 9/10. |
| 7 | No test uses the real 17-field payload shape | **ACCEPTED — and this is exactly what surfaced the severe bug above.** | New `shouldAppendTheRealSeventeenFieldLedgerPayloadShape` test. |
| 8 | No test pins the advisory-lock SQL expression | **ACCEPTED** | A reflection-based test asserts the `@Query` annotation's literal value exactly. |
| 9 | No test verifies `occurredAt` serializes as a date-time string | **ACCEPTED** | New `ReceiptIssuedPayloadSerializationTest`, mirroring `InvoiceCreatedPayloadSerializationTest`'s own established pattern (T07). |
| 10 | `LedgerServiceTest` never exercises the real `Receipt` entity/repository | **ACCEPTED** | New `ReceiptRepositoryIntegrationTest`, a real JPA round-trip proof. |

No finding was rejected without a documented reason.

## Verification performed this phase

- The `@Immutable` fix was verified by direct, controlled before/after comparison (the exact same
  test, same payload, failing with the real `Short` value and passing with a `String` substitute
  before the fix; passing with the real `Short` value restored after the fix) — not merely
  inferred.
- `mvn -o -pl services/payment -am test` (full suite, Docker up): **405/405 passing** (398
  Phase-10 baseline + 7 new: `shouldComputeSecondEntryHashUsingTheRealPredecessorHashNotGenesis`,
  `shouldAppendTheRealSeventeenFieldLedgerPayloadShape`, `shouldAcquireTheAdvisoryLockUsingTheNamespacedKeyExpression`,
  `PaymentRepositoryIntegrationTest` ×2, `ReceiptIssuedPayloadSerializationTest`,
  `ReceiptRepositoryIntegrationTest`).

## Carried-forward disclosed gaps (not fixed, by design)

- `markHeldIfFinalized`'s own JPQL (T17) has never been exercised against a real database either
  — the identical class of gap `markAttestedIfFinalized` just closed, left open for `markHeldIfFinalized`
  specifically since retrofitting it is out of this task's own scope.
- `VerificationRecord.detail`'s identical, currently-latent `@JdbcTypeCode(SqlTypes.JSON)` risk —
  no current caller triggers it (every value is `Integer`, which round-trips correctly), but a
  future caller putting a `Short`/similar boxed type into it would hit the identical crash. Not
  fixed (no demonstrated need, out of scope), but now a named, searchable risk for whoever next
  touches that class.
- AC4's full concurrency guarantee under genuine Spring-managed concurrent load (Gap 6) — deferred
  to task 26's own end-to-end test, consistent with the Phase 9/10 scope boundary already drawn.
