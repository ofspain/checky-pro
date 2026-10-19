# payment · T28 · Phase 3 — Design Challenge

Findings from an adversarial review of `artifacts/02-task-implementation-brief.md` (TIB) against `spec/payment-service/agents.md`, `SECURITY-THREAT-MODEL.md` threat #5, and the existing repository/S3 code.

---

## Finding 1 — D1 does not structurally ban raw SQL/JDBC or `EntityManager` mutations

- **Issue:** The proposed ArchUnit rule only forbids calls to methods named `delete*` on `LedgerEntryRepository`/`ReceiptRepository` and the declaration of `@Modifying` methods on those interfaces. A malicious or buggy code path that uses `JdbcTemplate.update("DELETE ...")`, `EntityManager.createNativeQuery("UPDATE ...")`, or raw `Statement.executeUpdate` would evade the rule. The doc would then over-state the "no code path can UPDATE/DELETE" guarantee.
- **Severity:** Medium — the threat model row should not claim a guarantee the rule cannot enforce.
- **Evidence:** TIB D1, D3; `services/payment/src/main/java/com/themistra/payment/receipt/ledger/LedgerEntryRepository.java`; `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptRepository.java`.
- **Recommended brief amendment:** Either add a second ArchUnit rule that bans DML calls via `JdbcTemplate`, `Statement`, `PreparedStatement`, and `EntityManager` create/update/remove methods in `src/main`, or explicitly disclose in row #5 that the structural guard covers repository-mediated mutation only and that raw-SQL paths are prevented by schema grants (AC1) and code review.

---

## Finding 2 — D2 only restricts `S3Client`; other AWS SDK entry points are unrestricted

- **Issue:** The rule limits `S3Client` method calls to `putObject` from `ReceiptStore`. A future developer could introduce `S3AsyncClient`, `S3Presigner`, `S3TransferManager`, or a higher-level client and bypass the guard while still mutating S3 objects.
- **Severity:** Medium — the guard should cover the SDK surface the service is allowed to use.
- **Evidence:** TIB D2; AWS SDK v2 class hierarchy.
- **Recommended brief amendment:** Expand the rule to ban calls on `S3AsyncClient`, `S3Presigner`, and `S3TransferManager` outside `ReceiptStore`, or disclose that the guard is scoped to the synchronous `S3Client` currently in use.

---

## Finding 3 — Same-key S3 re-put is not structurally prevented

- **Issue:** D2 allows `putObject` from `ReceiptStore`. On a versioned Object Lock bucket, a second `putObject` to the same key creates a new version; the original locked version survives, but the latest version would change. The brief discloses this under D4 and OQ2, but it is a real gap in the "no overwrite" claim because the code path exists and is allowed.
- **Severity:** Medium — the current design relies on a fresh `receiptUuid` per receipt, which is good, but a bug or malicious edit in `ReceiptService` could reuse one.
- **Evidence:** TIB D2, D4, OQ2; `services/payment/src/main/java/com/themistra/payment/receipt/ReceiptStore.java`.
- **Recommended brief amendment:** Make the "same-key re-put creates a new version" disclosure prominent in row #5, and consider adding a unit/integration test that proves `ReceiptService` never issues a `putObject` for an existing `receiptUuid` key. Do not add the `If-None-Match` production change here (it is out of scope), but log it as a follow-up.

---

## Finding 4 — D1 rule name over-promises what it enforces

- **Issue:** The rule is named `shouldForbidLedgerAndReceiptMutation`, but it only bans `delete*` calls and `@Modifying` methods. It does not ban `save` (updates are blocked by immutability/no-setters, not by the rule), and it does not ban raw SQL. The name could be read as a blanket guarantee.
- **Severity:** Low — naming clarity.
- **Evidence:** TIB D1, D3.
- **Recommended brief amendment:** Rename the rule to `shouldForbidRepositoryDeleteAndModifyingMethods` or add Javadoc that lists exactly what is and isn't covered.

---

## Finding 5 — Lambda and method-reference delete paths may not be caught by D1

- **Issue:** ArchUnit's `callMethod` detection works well for direct invocations, but `repository::delete` method references and lambdas such as `list.forEach(repository::delete)` compile to `invokedynamic` and may not be reliably matched by a simple method-name rule. T25 Gap 3 raised the same concern for `permitAll`.
- **Severity:** Low — the fixture currently uses a direct call, so the rule is not proven against these forms.
- **Evidence:** TIB D1; T25 Phase 11 Gap 3.
- **Recommended brief amendment:** Add a second violation fixture (or extend the existing one) that exercises a lambda and a method-reference delete, and confirm the rule fails on both. If it does not, broaden the rule or disclose the limitation.

---

## Finding 6 — D2 does not prove Object Lock COMPLIANCE is used on every put

- **Issue:** The ArchUnit rule only checks which class calls which `S3Client` method. It cannot assert that every `putObject` carries `ObjectLockMode.COMPLIANCE` and a `RetainUntilDate`. That property is verified by `ReceiptStoreTest` and the T26 E2E, not by the structural guard.
- **Severity:** Low — the property is tested elsewhere, but the doc should not imply the ArchUnit rule covers it.
- **Evidence:** TIB D2, AC3; `services/payment/src/test/java/com/themistra/payment/receipt/ReceiptStoreTest.java`.
- **Recommended brief amendment:** In row #5, separate the "no delete/overwrite API call" claim (D2 rule) from the "every put is COMPLIANCE-locked" claim (`ReceiptStoreTest` / T26).

---

## Finding 7 — Row #5 should clearly distinguish "prevented" from "detected" from "disclosed"

- **Issue:** The threat model row needs to credit the right control for each risk. An insider altering a historical verification is: prevented at the DB layer by grants (AC1) and the no-delete repository rule (D1); prevented at the S3 layer by no delete/overwrite API calls (D2) plus Object Lock (tested elsewhere); detected by `LedgerVerifier` (AC4); but the verifier has no scheduled/endpoint caller, and the on-chain anchor is deferred. If the row mingles these, a reader may believe tampering is automatically detected in production.
- **Severity:** Medium — threat-model accuracy matters for audits and the Phase 4 gate.
- **Evidence:** TIB D4; `SECURITY-THREAT-MODEL.md` row #5.
- **Recommended brief amendment:** Structure row #5 with sub-bullets under Prevention, Detection, and Residual Risk, mirroring the crypto-service rows. Explicitly state that detection is manual/on-demand until a scheduler/endpoint and the anchor are built.

---

## Summary

T28 is a critical security close-out. The highest-priority amendments are Finding 1 (raw SQL/JDBC disclosure or guard), Finding 2 (other S3 SDK entry points), and Finding 7 (clear threat-model wording). Findings 3–6 are scope/clarity notes that should be dispositioned at Phase 4. The two new ArchUnit rules are a good idea and should be added, provided their limitations are honestly recorded.
