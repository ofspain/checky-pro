<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). -->

# auth · T27 · Phase 7 — Self Review

Reviews the Phase 6 diff (`artifacts/06-implementation-notes.md`) against the frozen brief and `agents.md`. Findings only — no fixes applied here (Phase 9).

---

## Findings

**None.** This test-only change was reviewed line-by-line against the frozen brief's eight-step sequence, correctness, boundary conditions, and readability; no defect was found. Recorded here honestly rather than manufacturing a finding for its own sake — this matches T26 Phase 6's own precedent ("clean, no surprises") for a task where the underlying operations were already fully built and correctly specified before any code (or, here, test code) was written.

---

## Non-Issues Considered and Ruled Out

- **Race between revoke (step 5) and the post-revocation exchange (step 7):** none possible — `DELETE /api-keys/{keyUuid}` only returns 204 after `ApiKeyService.revoke`'s `@Transactional` method (and its underlying conditional `UPDATE`) commits; `TestRestTemplate` calls are synchronous and sequential, so step 7 cannot fire before the revocation is durably committed.
- **Race between exchange (step 3) and the `lastUsedAt` observation (step 4):** same reasoning — `ApiKeyService.exchange`'s `updateLastUsedAt` commits before the controller returns 200, and the subsequent `GET` is a separate, later, synchronous call.
- **`findByKeyUuid` ambiguity:** each test run seeds a fresh, unique account that creates exactly one key, so the helper can never match more than one item — no risk of picking the wrong list entry.
- **Byte-for-byte body comparison (step 8) fragility:** comparing raw JSON strings (rather than parsed maps, as `ApiKeyExchangeIntegrationTest`'s equivalent test does) is deliberate here — it matches AC3's own "byte-for-byte identical" wording exactly, and `ProblemDetail`'s Jackson serialization order is deterministic for two objects built via the identical code path (`ApiKeyExceptionHandler.onExchangeRejected`), so this isn't a source of flakiness.
- **Malformed-key rejection (step 8) vs. revoked-key rejection (step 7) being audited against different targets internally** (one against `null`, one against the real account) — irrelevant to this test, which only asserts the HTTP response body; internal audit-row differences don't affect the client-observable uniformity this test proves.
- **Test method length (~40 lines, 8 sequential steps):** appropriate for a flow test whose entire purpose is sequential continuity — splitting it into per-step private methods that pass state between them would reduce readability, not improve it, for this specific kind of test (same judgment `SasLoginIntegrationTest`'s own multi-step flow tests already reflect).
- **Module boundaries / thread-safety:** no production code touched; standard `@SpringBootTest` field injection, no shared mutable state.
- **D3's documented dependency on T25's `ApiKeyTokenIssuer`:** correctly not treated as something to guard against in the test itself (e.g., no defensive try/catch around `bearerTokenFor`) — if that infrastructure is broken, the test should fail loudly and directly, which it will.

---

**Phase 7 complete — self-review written, no findings.** Proceed to Phase 8 (Kimi Independent Review) on approval.
