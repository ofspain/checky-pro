# payment · T28 · Phase 7 — Self-Review

Consumes:
- `artifacts/06-implementation-notes.md`
- the Phase 6 diff (`e014befb`)

Reviewed against `artifacts/04-frozen-task-brief.md` (FROZEN) and `spec/payment-service/agents.md`.
This phase lists findings only. Fixes are decided at Phase 9.

## Scope check

**Files changed:**
- `ArchitectureTest.java`
- four new test fixtures
- `SECURITY-THREAT-MODEL.md`, row #5, the header and the footnote only

**Untouched:** production code, migrations, poms, `spec/` and `contracts/`.

**Flagged in `06`:** three deviations, each making the rules stronger, that need a Phase 9
decision:
- method references to Spring Data types are banned
- guarded repositories are found by domain type
- the D1 fixtures are placed in the `receipt.ledger` test package

## Dimensions with no finding

- **Business logic:** money types, transactions, idempotency and module boundaries. No runtime
  code was added.
- **L12:** the fixtures in `receipt.ledger` reference only that module's own `LedgerEntry`. The
  `archtestfixtures` classes use no feature-module entity.
- **Thread-safety.** The rules are static and immutable, and ArchUnit's import is per test.
- **Secret handling:** not applicable.

## Findings

| # | Issue | Severity | Evidence (file:line) | Recommendation |
|---|---|---|---|---|
| S1 | **Spring Data derived `remove…` methods evade D1.** Spring Data derives delete queries from `deleteBy…` **and** `removeBy…` method names. A guarded repository declaring `long removeByReceiptId(Long id)` needs no `@Modifying` and doesn't start with `delete`, so D1 would not flag it. The DB grant would still deny it at runtime. | Medium | `ArchitectureTest.java:312` (`startsWith("delete")`), `:322` (`@Modifying` only) | Treat names starting with `delete` **or** `remove` as deletes. Also have D1(b) flag any method a guarded repository *declares* with a `delete`/`remove` prefix. |
| S2 | **Hibernate- and JPA-native write APIs are outside D5.** The rule doesn't cover `EntityManager.unwrap(Session.class)` followed by `Session.createMutationQuery` / `createNativeMutationQuery`, nor `StatelessSession`. `createNamedQuery`, which can run an `@NamedQuery` UPDATE, and `createStoredProcedureQuery` aren't covered either. Nothing uses any of them today. | Low | `ArchitectureTest.java:424` (`Set.of("remove", "merge", "createQuery")`) | Add `createNamedQuery` and `createStoredProcedureQuery` to the banned `EntityManager` set. Ban any access to `org.hibernate.Session` and `org.hibernate.StatelessSession`. Alternatively, document them as a limit. |
| S3 | **Spring's `JdbcClient` (6.1+) is not covered.** It isn't a `JdbcOperations` subtype. Its terminal `update()` would bypass D5. `spring-jdbc` 6.2 is on the classpath. | Low | `ArchitectureTest.java:418-420` | Add `org.springframework.jdbc.core.simple.JdbcClient` (any access) to D5. |
| S4 | **The method-reference ban in D1 applies to every repository, so it can be over-broad.** A future legitimate `processedEventRepository::deleteById` would fail the build. That is a deliberate trade-off (deviation 1), and the Javadoc explains it. | Info | `ArchitectureTest.java:312-316` | Keep it, and accept deviation 1 at Phase 9. |
| S5 | **The threat-model footnote's opening sentence is now partly stale.** It still says "Threats #1–#6 are owned by `crypto-service`", but row #5 is now shared with payment. The later sentence added in Phase 6 explains the split, but the first one reads as if crypto owns #5 alone. | Low | `SECURITY-THREAT-MODEL.md:19` | Reword it to "#1–#4 and #6 are owned by crypto-service; #5 is shared with payment-service (ledger, receipts)". |
| S6 | **Javadoc refers to follow-up `FU-T28-1`,** an ID that exists only in `.ai/` pipeline artifacts. A reader of the code has no way to resolve it. | Info | `ArchitectureTest.java` (D2 Javadoc) | Spell out the follow-up ("a conditional `If-None-Match` put") instead of using the bare ID. Optional. |
| S7 | **D2's `putObject` allowance matches the exact name, which is correct.** `putObjectRetention`, `putObjectLegalHold`, `putObjectAcl` and `putObjectTagging` are all banned, because the check is `equals`, not `startsWith`. The Javadoc doesn't say so explicitly. | Info | `ArchitectureTest.java:377` | Optional: one Javadoc line. M4 already proves both real calls are seen. |

## Summary

- **No Critical or High findings.**
- **S1 (Medium)** is a real, demonstrable bypass of the code-path claim. It is still backed by the
  DB grant (AC1), so it is not exploitable at runtime, but row #5's claim "no repository delete"
  is incomplete without it.
- **S2, S3 and S5 (Low):** completeness and accuracy.
- **S4, S6 and S7 (Info).**

All AC evidence in `06` stands: 504/504 and mutation proofs M1–M5.

## Open Questions

- **Deviations 1–3 (`06`)** need Phase 9 acceptance.
- **S1–S3** would widen the frozen D1 and D5 sets. That also needs a Phase 9 decision.
