# crypto · T26 · Phase 9 — Review Resolution

Human-approved dispositions for the Phase 7 self-review (5 findings) and Phase 8 (Kimi) independent
review (10 findings). One Phase 8 finding required correcting my own earlier verification: an
incomplete grep pattern during Phase 5 caused me to miss that `Watch`'s real `@Id` is a separate
surrogate `Long`, not the `watchId` UUID column — Kimi's Finding #3 was right, my prior dismissal of the
analogous claim would have been wrong had I not re-checked it byte-for-byte this phase.

## Self-review findings (Phase 7)

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | Unused imports `Map`, `Function` | **ACCEPTED** | Removed. |
| 2 | `registerWatch`'s `txHashHint` parameter declared but never used | **ACCEPTED** | Parameter removed; every call site updated. |
| 3 | `WatchAccessor` inner class is unnecessary indirection | **ACCEPTED, superseded** | Replaced entirely per independent-review Finding #3's fix (see below) — the class no longer exists; `loadWatch(UUID)` is a single private method. |
| 4 | Repeated 3-provider setup across all 4 tests | **ACKNOWLEDGED, not implemented** | A real simplification opportunity, but left as-is given the volume of correctness fixes this phase already required — recorded here as a candidate for a future pass, not lost. |
| 5 | Confusing test-data naming (identical recipient/token addresses; `fromAddress` with contradictory per-flow meaning) | **ACCEPTED** | `VALID_TOKEN_CONTRACT` is now a distinct address from `VALID_RECIPIENT`; the shared `fromAddress` is now a clearly-named `COUNTERPARTY_ADDRESS` constant used consistently (its sanctioned/not-sanctioned status is determined solely by each flow's own mocked `ScreeningClient` configuration, not by the string itself). |

## Independent review findings (Phase 8)

Every finding was independently re-verified against the real, restored codebase (not accepted or
rejected on the reviewer's word alone) — including one direct execution (a standalone Mockito probe)
and one correction of my own prior verification gap.

| # | Finding | Disposition | Resolution |
|---|---|---|---|
| 1 | Unstubbed `ObservationSnapshotStore` causes NPE via `.orElse(null)` | **REJECTED — verified factually wrong by direct execution** | Wrote and ran a standalone probe: Mockito's default answer for an unstubbed `Optional`-returning method is `Optional.empty()`, not `null` (confirmed test passed). `.orElse(null)` on `Optional.empty()` correctly yields `null`, which `Observation.create`'s `s3SnapshotKey` parameter accepts (the column is nullable per the real DDL). No NPE occurs anywhere in this path. |
| 2 | No `ChainCursor` placeholder created after registration | **NOT APPLICABLE — re-verified true (again)** | `WatchService.register` (line 55) already calls `chainCursorRepository.save(ChainCursor.placeholder(...))` — re-confirmed directly against the restored branch's real source. |
| 3 | `WatchAccessor.load()` queries by the wrong primary-key type | **ACCEPTED — confirmed real bug, corrects my own earlier verification gap** | Re-checked `Watch.java` line-by-line (my Phase 5 grep pattern had skipped over the intervening field): `@Id` applies to a separate, auto-generated `Long id`; `watchId` is a distinct, non-`@Id` column. `entityManager.find(Watch.class, watchId)` was silently looking up by the wrong key. Replaced with a JPQL query on the real `watchId` column (`loadWatch(UUID)`), also resolving Self-Review Finding #3. |
| 4 | Hardcoded `txHash` in `tx(...)` causes cross-test contamination | **ACCEPTED — confirmed real bug** | `tx(...)` now takes an explicit `txHash` parameter, threaded consistently through every flow's `simulateReorg`/`scriptTx`/`scriptFinalityStatus`/assertions. Each flow uses its own distinct hash. |
| 5 | Flow 2 will also emit `chain.tx.confirmed`, contradicting its own assertion | **ACCEPTED — confirmed real bug** | Flow 2's three providers now report distinct `confirmations` (1/2/3), not just distinct `AMOUNT` — both facts genuinely disagree, so neither `confirmed` nor `finalized` is ever emitted. Added explicit `isAgreed(..., CONFIRMATIONS)` and `heldFactAlerter` assertions for the new disagreement too. |
| 6 | Flow 2's `heldFactAlerter` verification uses the wrong `txHash` | **ACCEPTED — resolved automatically by fixing #4** | Once `txHash` threads through correctly, the `verify(...)` argument is the real, matching hash. |
| 7 | Watch-registration DTO/controller mismatch | **NOT APPLICABLE — re-verified true (again)** | `RegisterWatchRequest`'s real fields and `WatchController`'s real `/internal/v1/watches` mapping, re-confirmed directly, exactly match what this test already sends. |
| 8 | Flow 4's mocked `ScreeningClient` persists outside a known transaction | **ACCEPTED — real, standard JPA/Spring risk** | The mocked answer's `entityManager.persist(...)` call is now wrapped in an autowired `TransactionTemplate.executeWithoutResult(...)`, giving it its own real, committing transaction regardless of `AttestationService`'s own (deliberately non-`@Transactional`) scope. |
| 9 | `Watcher.stop()` not invoked if a flow fails mid-test | **ACCEPTED** | A tracked `activeWatcher` field is now stopped unconditionally in `@AfterEach`, alongside the existing Kafka consumer cleanup — unlike `WatcherTest`'s fully-isolated-mock convention, this `Watcher` runs against real, class-shared infrastructure, so a leaked one is a genuine cross-test risk here. |
| 10 | `noRecordAppearsOnTopic` can report a stale buffered record as "found" | **ACCEPTED** | `awaitRecordOnTopic` now removes a matched record from the buffer once returned, so it can never be reported again by a later, unrelated absence check. |

## Files changed in this phase

- `services/crypto/src/test/java/com/themistra/crypto/watch/EndToEndIntegrationTest.java` — all fixes
  above applied. No production code changed.

## Verification

`mvn -pl services/crypto -am test-compile` — clean, confirmed directly after every fix.
A standalone Mockito probe (`MockitoOptionalDefaultProbeTest`, created and run to verify Finding #1,
then deleted — not part of this task's own deliverable) confirmed Mockito's real default-answer
behavior for `Optional`-returning methods.

Actual execution of the 4 flows remains deferred — Docker is still unavailable in this environment,
restated here rather than silently assumed resolved.
