# crypto · T15 · Phase 9 — Review Resolution

**Human Approval gate.** Approved 2026-09-07. Findings from Phase 7 (self-review) and Phase 8 (Kimi
independent review) are consolidated below. Kimi Findings 2, 5, 6 independently confirmed self-review
Findings 1, 4, 3 respectively.

## Resolution log

| # | Comment | Disposition | Change made |
|---|---|---|---|
| 1 | Kimi Finding 1 — no global RFC 9457 handler for framework-level errors (`@Valid` failures, malformed JSON, bad path-variable types, unexpected exceptions); this service has zero controller-advice infrastructure before this task | **ACCEPTED — new file, outside the original frozen brief's Files-to-Create list** | Added `common/ApiExceptionHandler.java` (`@RestControllerAdvice`, `@Order(LOWEST_PRECEDENCE)`), mirroring `services/auth`'s own `ApiExceptionHandler` shape, plus one addition auth's own doesn't have: a `MethodArgumentTypeMismatchException` handler (folds in Finding 8 below). Verified end-to-end via a throwaway `@WebMvcTest` (deleted after running, not a Phase 10 deliverable): missing-field → 400 "Validation failed", malformed JSON → 400 "Malformed request body", invalid path UUID → 400 "Malformed request parameter", and (via `WatchExceptionHandler`, confirmed to still correctly outrank this catch-all) an unknown `watchId` → 404 "Watch not found" — all four as real `application/problem+json` responses through actual Spring MVC dispatch. |
| 2 | Self-review Finding 1 / Kimi Finding 2 — `expectedAmount` regex has no upper bound; a value exceeding `NUMERIC(78,0)`'s precision would 500 instead of 400 | **ACCEPTED** | `EXPECTED_AMOUNT_PATTERN` changed to `^[1-9][0-9]{0,77}$` (`WatchService.java`). |
| 3 | Kimi Finding 3 — `ChainCursor.watchId` should be `nullable = false` in the JPA mapping even though the DDL itself omits `NOT NULL` | **ACCEPTED — verified safe before applying, not assumed** | Ran a real experiment against Postgres (temporarily add the annotation, boot a full Hibernate context with `ddl-auto=validate` against the actual frozen DDL) to confirm Hibernate does not reject a JPA constraint stricter than the physical column — it does not; context started cleanly. Applied `@Column(name = "watch_id", nullable = false)` to `ChainCursor.java`. |
| 4 | Kimi Finding 4 — no DB-level unique constraint enforces the 1:1 `Watch`↔`ChainCursor` relationship; `V1` is frozen so it cannot be added at the schema level | **ACCEPTED (already substantially documented); noted for Phase 10** | `ChainCursor`'s own Javadoc already states the schema enforces neither the `watch_id` match nor a 1:1 cardinality — this class is the sole guarantee. Additional required test for Phase 10: assert `WatchService.register` calls `chainCursorRepository.save` exactly once per registration. |
| 5 | Self-review Finding 4 / Kimi Finding 5 — `@Modifying` query does not clear the persistence context, a latent stale-read trap for a future caller (no current defect) | **ACCEPTED** | Added `clearAutomatically = true` to `@Modifying` on `WatchRepository.markUnregisteredIfRegistered`. |
| 6 | Self-review Finding 3 / Kimi Finding 6 — `WatchExceptionHandler` is package-private, deviating from the one established precedent (`ApiKeyExceptionHandler`, `public`) | **ACCEPTED** | `WatchExceptionHandler` made `public`. Also now empirically confirmed (see #1's verification) that Spring correctly registers and dispatches to it regardless — the visibility change removes the lingering question at zero cost, not because a real defect was found. |
| 7 | Kimi Finding 7 — mixed-case EVM addresses are not normalized before storage, and `TokenAllowlist` (T11) stores/matches lowercase, case-sensitive — a future consumer could see a false `UNKNOWN_TOKEN` | **ACCEPTED (documentation only, correctly out of scope for T15)** | Added a Javadoc note to `Watch.java` warning a future allowlist consumer must normalize case itself before lookup. |
| 8 | Kimi Finding 8 — invalid `DELETE` path UUID is not mapped to `400` | **ACCEPTED — folded into Finding 1** | Covered by `ApiExceptionHandler.onTypeMismatch`; verified in the same throwaway `@WebMvcTest` run. |

## Summary

1 accepted with a new file addition, empirically verified end-to-end (1, folding in 8); 4 accepted with
a direct code change, one of which was verified safe via a real-Postgres experiment before applying
rather than assumed (2, 3, 5, 6); 1 accepted as already-substantially-documented with an additional
required test noted for Phase 10 (4); 1 accepted as a documentation-only addition, correctly scoped out
of this task's actual code (7). Zero findings rejected — an unusually clean, fully-accepted review
across both Phase 7 and Phase 8.

`mvn -pl services/crypto compile` and `test-compile` both succeed cleanly after all changes.
`mvn -pl services/crypto -am test` (full module regression): 453 tests, 6 failures — the same
pre-existing, disclosed-in-Phase-6 set in `ObservationRepositoryIntegrationTest`,
`ProviderHealthRepositoryIntegrationTest`, `QuorumDecisionRepositoryIntegrationTest`, and
`TokenAllowlistRepositoryIntegrationTest` (T08-T11's own work, unrelated to `watch/` or
`common/ApiExceptionHandler.java`), zero regressions.

Files changed in this phase: `common/ApiExceptionHandler.java` (new), `watch/WatchService.java`,
`watch/WatchRepository.java`, `watch/WatchExceptionHandler.java`, `watch/ChainCursor.java`,
`watch/Watch.java` (Javadoc only). No public method signature changed in a way that breaks any prior
contract; `WatchExceptionHandler`'s visibility change (package-private → public) is additive.
