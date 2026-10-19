# payment · T28 · Phase 11 — Test Review

Gaps found by reviewing the Phase 10 test manifest and the final `ArchitectureTest`/fixtures against the frozen acceptance criteria.

---

## Gap 1 — D2 does not prove that only `ReceiptStore` calls `putObject`

- **Why it matters:** Part (b) of the S3 rule allows `putObject` from any class that is permitted to depend on the S3 SDK. Today only `ReceiptStore` and `ReceiptStoreConfig` have that permission, so `ReceiptStoreConfig` could legally call `putObject`. The brief's claim is that only `ReceiptStore` writes objects.
- **Suggested test:** Tighten part (b) so that `putObject` is allowed only when the caller is `ReceiptStore` (or its test fixtures), and `S3Client.builder()` only when the caller is `ReceiptStoreConfig`. Add a fixture proving `ReceiptStoreConfig.putObject(...)` fails.

---

## Gap 2 — D2 allows `S3AsyncClient.putObject`, diverging from the brief

- **Why it matters:** The brief says "only `putObject`" and refers to `S3Client`. The current rule permits `S3AsyncClient.putObject` as well, because part (b) checks both `S3Client` and `S3AsyncClient`. If a future developer introduces async writes, the rule would not flag a deviation from the intended synchronous-only design.
- **Suggested test:** Either remove `S3AsyncClient` from the allowed owner set (and add a fixture showing `S3AsyncClient.putObject` fails), or explicitly disclose in the threat-model row that async S3 operations are also permitted.

---

## Gap 3 — No fixture exercises a `delete` on `ReceiptRepository`

- **Why it matters:** All D1 violation fixtures use `LedgerEntryRepository` or an indirect ledger repository. The rule is generic, but a reader of the test manifest has no concrete proof that a `ReceiptRepository.delete(...)` would be caught.
- **Suggested test:** Add a `RogueReceiptRepositoryUser` fixture in the `receipt` test package (where `ReceiptRepository` is package-private) with direct, lambda, and method-reference deletes, and assert each is reported.

---

## Gap 4 — No fixture exercises `@Modifying` or a derived delete on `ReceiptRepository`

- **Why it matters:** The existing `@Modifying` and `removeBy` fixtures target `LedgerEntry`. A future regression specific to `ReceiptRepository` (e.g., a copy-paste `@Modifying` query) would not be covered by a failing fixture.
- **Suggested test:** Add `RogueModifyingReceiptRepository` and a `removeBy...` method on a receipt repository, and assert both are reported.

---

## Gap 5 — No fixture proves `S3Client.putObject` outside `ReceiptStore` fails

- **Why it matters:** The existing S3 fixture only exercises non-`putObject` operations. A future class that calls `S3Client.putObject` from outside `ReceiptStore` would violate L9, but there is no canary proving the rule catches that specific case.
- **Suggested test:** Add a `RogueS3ObjectMutator.putObjectOutsideReceiptStore(S3Client, PutObjectRequest)` method and assert part (a) or part (b) reports it.

---

## Gap 6 — No fixture proves `EntityManager.createNativeQuery` in a lambda outside the export read fails

- **Why it matters:** The D5 fixture covers direct `createNativeQuery(...).executeUpdate()`, but not a lambda or method-reference form. If the rule's access detection behaves differently for invokedynamic, this gap leaves it unproven.
- **Suggested test:** Add `RogueRawSqlUser.nativeQueryInsideALambda` and assert it is reported.

---

## Gap 7 — No automated check that threat-model cited test names are real

- **Why it matters:** Row #5 cites many test names. A typo or a renamed test would make the threat model stale without failing any build.
- **Suggested test:** Add a lightweight test (or CI script) that parses the cited test/method names from `SECURITY-THREAT-MODEL.md` and asserts each class and method exists in the current source tree. This is optional but would close the loop between docs and code.

---

## Gap 8 — The "not tested here" disclosures are not encoded as tests or TODOs

- **Why it matters:** The manifest lists deferred items (anchor, production `LedgerVerifier` caller, same-key re-put, export SQL text, real AWS IAM). These are honest disclosures, but nothing prevents them from being forgotten in later planning.
- **Suggested test:** Not a test, but add them as explicit Open Questions in the Phase 10 manifest and consider creating placeholder tests marked `@Disabled("deferred until R26"` or similar so they appear in the backlog.

---

## Summary

T28's test suite is very strong after Phase 9: the rules cover repository deletes, raw SQL, EntityManager, Hibernate sessions, JdbcClient, and the S3 SDK, with comprehensive canaries. The highest-value additions are Gap 1 (tighten the `putObject` caller) and Gap 3/4 (Receipt repository fixtures). Gaps 5–8 are smaller completeness or documentation-tracking notes.
