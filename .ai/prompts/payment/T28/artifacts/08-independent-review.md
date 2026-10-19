# payment · T28 · Phase 8 — Independent Code Review

Fresh findings from reviewing the Phase 6 implementation and Phase 7 self-review against the frozen brief, `spec/payment-service/agents.md`, and `SECURITY-THREAT-MODEL.md` threat #5.

---

## Finding 1 — `remove*` derived queries evade D1

- **Issue:** Spring Data derives delete semantics from both `deleteBy…` and `removeBy…` method names. The current D1 rule only checks `access.getName().startsWith("delete")`, so a guarded repository declaring `long removeByReceiptUuid(UUID uuid)` would not be flagged. The DB grant still denies it at runtime, but the code-path claim is incomplete.
- **Evidence:** `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` lines 311-312; Spring Data documentation.
- **Recommendation:** Extend the delete-name check to `startsWith("delete") || startsWith("remove")`, and add a fixture method that uses `removeBy…` on a guarded repository to prove the rule fails.
- **Confidence:** Medium — this is a real, demonstrable bypass of the structural guard.

---

## Finding 2 — `JdbcClient` is not covered by the raw-SQL guard

- **Issue:** Spring 6.1+ `JdbcClient` is not a `JdbcOperations` subtype. Its `update()`/`sql()` methods offer the same raw-SQL escape hatch as `JdbcTemplate`, but the D5 rule does not restrict it. `spring-jdbc` 6.2 is on the classpath.
- **Evidence:** `ArchitectureTest.java` lines 417-420.
- **Recommendation:** Add `org.springframework.jdbc.core.simple.JdbcClient` to the banned set in D5, and add a canary fixture.
- **Confidence:** Low — no code uses `JdbcClient` today.

---

## Finding 3 — Hibernate `Session` and mutation queries are outside D5

- **Issue:** A determined bypass could use `EntityManager.unwrap(Session.class)` and then `Session.createMutationQuery`, `createNativeMutationQuery`, or a `StatelessSession`. The current D5 rule bans `EntityManager.remove`/`merge`/`createQuery` and `createNativeQuery` (except in the export read), but not these Hibernate-specific APIs.
- **Evidence:** `ArchitectureTest.java` lines 421-426.
- **Recommendation:** Either ban any access to `org.hibernate.Session` and `org.hibernate.StatelessSession`, or explicitly disclose this limit in the threat-model row.
- **Confidence:** Low — no Hibernate-native code exists today.

---

## Finding 4 — `jakarta.persistence.Query.executeUpdate()` is not banned directly

- **Issue:** D5 blocks the creation of JPQL/native queries outside the export read, but it does not block `Query.executeUpdate()` on a query that was created elsewhere and passed in. That path is unlikely today because no production code creates such queries, but a structural guard should close the loop.
- **Evidence:** `ArchitectureTest.java` lines 421-427.
- **Recommendation:** Add a check for `owner.isAssignableTo("jakarta.persistence.Query") && method.equals("executeUpdate")` to D5.
- **Confidence:** Low — defensive completeness.

---

## Finding 5 — D2 allows non-`S3Client` S3 operations inside `ReceiptStore`/`ReceiptStoreConfig`

- **Issue:** Part (a) of the S3 rule permits any dependency on the `software.amazon.awssdk.services.s3` package from `ReceiptStore` or `ReceiptStoreConfig`. Part (b) only restricts calls whose target owner is `S3Client` or `S3AsyncClient` to `putObject`/`builder`. A future developer could add `S3Presigner.presignXxx(...)` or `S3Utilities` usage inside `ReceiptStore`, and it would pass both parts.
- **Evidence:** `ArchitectureTest.java` lines 350-385.
- **Recommendation:** Tighten part (b) so that the only allowed S3 SDK target classes inside the permitted classes are `S3Client` and `S3AsyncClient`, and the only allowed method names are `putObject` and `builder`. Any other S3 SDK class (presigner, utilities, model builders are fine as parameter types) used via method call should be a violation.
- **Confidence:** Low — the current code only uses `S3Client.putObject`.

---

## Finding 6 — D1 repository detection may miss indirectly-typed repositories

- **Issue:** `isGuardedRepository` looks only at the direct interfaces of a repository for a parameterized type argument of `LedgerEntry` or `Receipt`. If a future repository extends an intermediate interface (e.g., `LedgerEntryRepositoryBase extends JpaRepository<LedgerEntry, Long>` and `MyRepo extends LedgerEntryRepositoryBase`), the actual type argument may not appear in `MyRepo.getInterfaces()`, and the rule would not treat `MyRepo` as guarded.
- **Evidence:** `ArchitectureTest.java` lines 270-278.
- **Recommendation:** Either walk the entire interface hierarchy to resolve generic type arguments, or disclose that repositories for ledger/receipt entities must directly extend a `JpaRepository<GuardedEntity, ?>`. A simpler alternative is to guard by simple name substring (`*LedgerEntryRepository`, `*ReceiptRepository`), but that is less robust.
- **Confidence:** Low — no indirect repositories exist today.

---

## Finding 7 — The threat-model footnote still attributes rows #1–#6 to crypto-service

- **Issue:** Line 19 of `SECURITY-THREAT-MODEL.md` says "Threats #1–#6 are owned by `crypto-service`", but row #5 now has a verified payment-service portion. The later text explains the split, but the opening sentence is stale and could mislead a reader.
- **Evidence:** `SECURITY-THREAT-MODEL.md` line 19.
- **Recommendation:** Reword to "Threats #1–#4 and #6 are owned by crypto-service; #5 is shared with payment-service (ledger, receipts)."
- **Confidence:** Low — documentation accuracy.

---

## Finding 8 — D5 does not prove the export's native query is read-only

- **Issue:** The D5 rule exempts `TransactionExportRepository` from the `createNativeQuery` ban, but it does not inspect the SQL text to confirm it is a `SELECT`. The DB grant for `payment_app` is INSERT+SELECT only on ledger/receipt tables, and the export query likely reads from other tables, but the rule itself allows any native query from that class.
- **Evidence:** `ArchitectureTest.java` lines 425-426.
- **Recommendation:** Add an ArchUnit or source-text assertion that `TransactionExportRepository` contains no `UPDATE`, `DELETE`, or `INSERT` keywords in its native-query strings. Alternatively, rely on the existing grant tests and disclose this limitation.
- **Confidence:** Low — the export read is out of T28 scope and its SQL is not a ledger/receipt mutation path.

---

## Summary

T28 is a strong security close-out. The structural guards are comprehensive, and the threat-model update captures the residual risks. Findings 1–5 are the most useful Phase 9 additions because they close demonstrable gaps in the code-path guards. Findings 6–8 are smaller completeness and documentation notes. No production code changes are needed.
