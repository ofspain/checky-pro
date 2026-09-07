# crypto · T15 · Phase 12 — Specification Verification

## Traceability matrix

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| R18 — `POST /internal/v1/watches` registers a watch, begins watching the address, returns `watchId` + `REGISTERED` | Yes | `watch/WatchController.java:29-32` (`@PostMapping`/`register`); `watch/WatchService.java:42-56` (`register`, VERBATIM `200 { watchId, status }` shape) | Yes — `shouldRegisterWatchAndReturnWatchId` (`WatchServiceTest`, `WatchControllerTest`) | No | "Begins watching the address" is satisfied by persisting a placeholder `ChainCursor` for the future watcher (task 16) to act on — no real subscription is established by this task; documented, not a gap (task statement's own scope: "Implement `WatchService` + `WatchController`... Persist `Watch` and its `ChainCursor`," not "start watching") |
| R19 — `DELETE /internal/v1/watches/{watchId}` stops watching, returns `204` | Yes | `watch/WatchController.java:35-38` (`@DeleteMapping`/`unregister`); `watch/WatchService.java:61-67` (`unregister`); `watch/WatchRepository.java:24-28` (atomic conditional `UPDATE`) | Yes — `shouldUnregisterWatchOnDelete` (`WatchServiceTest`, `WatchControllerTest`, `WatchRepositoryIntegrationTest`) | No | `404` for an unknown `watchId` and idempotent `204` for an already-non-`REGISTERED` one are both implementer-resolved extensions of R19's literal text (Phase 2/4 design decision) — not stated in R19 itself, but not a deviation from it either |
| Task statement — "Persist `Watch` and its `ChainCursor`" | Yes | `watch/WatchService.java:49-54` (both `save` calls, same `@Transactional` method) | Yes — `registerPersistsAChainCursorWithTheMatchingWatchIdAndChain`, `registerCallsChainCursorRepositorySaveExactlyOnce`, `WatchRepositoryIntegrationTest.fullRegisterThenUnregisterFlowPersistsCorrectlyAndIsIdempotent` | No | `ChainCursor.lastBlock` is seeded with a documented `-1` sentinel, not a real chain position (Phase 2/4 design decision — `ChainAdapter` has no current-block method independent of an existing tx hash) |
| L6 — reorg is a first-class transition; cursor/checkpoint exists to support walk-back | Yes (scope-appropriate) | `watch/ChainCursor.java` (entity exists, placeholder-only) | N/A — no walk-back logic exists yet, correctly out of this task's scope | No | None — `ChainCursor`'s own Javadoc (`ChainCursor.java:19-26`) explicitly defers real cursor advancement to task 16 |
| L15 — module boundaries; no feature module imports another's entity | Yes | `watch/WatchModuleBoundaryTest.java` (source-scan; allow-lists exactly `token.AddressValidator`, a stateless predicate, not an entity) | Yes — `noMainSourceFileInWatchImportsBeyondItsAllowedTokenTypeOrAnyForbiddenPackage` | No | None |
| L8 — address validation is mandatory; invalid addresses rejected at the boundary | Yes | `watch/WatchService.java:87-94` (`validateAddress`, dispatches to `AddressValidator.isValidEvmAddress`/`isValidTronAddress`) | Yes — 6 tests in `WatchServiceTest` covering valid/invalid EVM and Tron, both `address` and `tokenContractAddress`, plus `postWithAnInvalidTokenContractAddressReturnsProblemJsonBadRequest` at the controller level | No | This is a Phase 3 Kimi-caught correction to the Phase 2 TIB's own original (incorrect) scoping — now resolved and verified, not a residual gap |
| agents.md — errors are RFC 9457 `application/problem+json`, no stack traces/internal detail | Yes | `common/ApiExceptionHandler.java` (new, Phase 8/9 addition) + `watch/WatchExceptionHandler.java:18,25` | Yes — 6 tests in `WatchControllerTest` covering validation failure, malformed JSON, unparseable field, invalid path type, domain 400, domain 404 — all asserting `application/problem+json` and title | No | This is the service's first controller-advice infrastructure, added specifically because this is the service's first controller at all (Phase 8 Finding 1) |
| agents.md — least-privilege DB grants | Yes | `db/migration/V6__crypto_app_watches_grant.sql:8-9` (`INSERT, SELECT, UPDATE` on `watches`; `INSERT, SELECT` on `chain_cursors`) | Yes — `cryptoAppCanInsertSelectAndUpdateButNotDeleteOnWatches`, `cryptoAppCanInsertAndSelectButNotUpdateOrDeleteOnChainCursors` (both now also assert an explicit raw `SELECT`, Phase 11 Finding 2) | No | None |

## Assessment

**(1) Is the task fully complete?** Yes. `WatchService`, `WatchController`, `Watch`, `ChainCursor`, both
repositories, both exceptions, both DTOs, the grant migration, and the global/domain error handlers are
all implemented and tested. All three of Phase 1's flagged open questions (`ChainCursor` initial value,
`DELETE` semantics, validation scope) were resolved as explicit design decisions at Phase 2/4, challenged
at Phase 3/8, and are now implemented and verified.

**(2) Does it satisfy every acceptance criterion?** Yes — AC1 through AC9 (frozen brief, as amended by
Phase 9's resolution) are each traced to a concrete implementation and at least one passing test above.

**(3) Does it violate any LOCKED decision?** No. L6 is satisfied by scope (a placeholder `ChainCursor`
only, walk-back correctly deferred). L15 is satisfied and enforced by an automated boundary test. L8 —
the one LOCKED decision this task's own Phase 2 TIB initially mis-scoped — was caught at Phase 3, fixed,
and is now fully implemented and tested; the final state violates nothing.

**(4) Remaining risks?**
- `FinalityStatus`-style caller-routing risk: `Watch`'s stored `address`/`tokenContractAddress` are not
  case-normalized (mixed-case EIP-55 preserved), while `TokenAllowlist` (T11) stores/matches lowercase —
  documented in `Watch.java`'s own Javadoc as a future consumer's responsibility (Phase 8 Finding 7,
  correctly out of this task's own scope).
- No dispatcher/registry yet wires a real watcher to the `ChainCursor` placeholder rows this task
  creates — expected and documented; task 16's own job.
- `POST` is not idempotent (a retried request creates a second, independent watch) — a documented,
  accepted risk locked in by an executable test (`twoSequentialRegisterCallsProduceTwoDistinctWatchIds`),
  not a defect of this task; the schema provides no deduplication key and R18 does not require one.
- The 6 pre-existing, unrelated test failures surfaced when Docker became available mid-pipeline
  (`ObservationRepositoryIntegrationTest`, `ProviderHealthRepositoryIntegrationTest`,
  `QuorumDecisionRepositoryIntegrationTest`, `TokenAllowlistRepositoryIntegrationTest`) remain
  unaddressed — explicitly out of scope for T15 (T08-T11's own work), disclosed in Phase 6/9/10, not
  hidden.

## Verdict

**PASS.** R18 and R19 are fully implemented as a persisted `Watch`/`ChainCursor` pair behind a
correctly-secured, RFC 9457-compliant REST API; every LOCKED decision in scope (L6, L8, L15) is
satisfied and tested, including a genuine mid-pipeline correction (L8) that was caught, fixed, and
verified rather than shipped wrong; the full module suite (505 tests) shows zero regressions.
