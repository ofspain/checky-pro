# crypto · T08 · Phase 9 — Review Resolution

**Human Approval gate. Approved 2026-09-03.** Combines Phase 7 (self-review, 5 findings) and Phase 8
(Kimi independent review, 8 findings) into one resolution log. Substantial overlap (5 of Kimi's
findings independently confirmed all 5 of the self-review's) increased confidence these were real
issues. Two contested points were resolved by reading the relevant framework's own source rather than
by argument. Only accepted comments were applied.

## Resolution log

| # | Comment (source) | Decision | Reason | Change made |
|---|---|---|---|---|
| 1 | The computed S3 key (`prefix+chain+txHash+factType+provider+timestamp+UUID`) can exceed `observations.s3_snapshot_key VARCHAR(256)` for realistic input lengths, which would fail the Postgres insert (self-review Finding 1 / Kimi Issue 1) | **ACCEPTED** | Real, well-grounded — Postgres raises a hard error on an over-length `VARCHAR`, not a silent truncation; a successful S3 write followed by a failed DB insert on its own snapshot-key column is exactly backwards from the frozen brief's intent | `buildKey` shortened to `{prefix}{chain}/{txHash}/{UUID}.json` — bounded independently of any input's length (the UUID alone already guarantees uniqueness). `provider`/`factType`/`observedAt` moved to the object's metadata (already present for `provider`/`factType`; `observedAt` added there too, so no audit information is lost) |
| 2 | `@Transactional` on `ObservationLog.record` holds a DB connection/transaction open for the full method, including the S3 network call (up to 5s) *before* the Postgres write (self-review Finding 2 / Kimi Issue 2) | **ACCEPTED** | Real connection-pool-exhaustion risk; contradicts the frozen brief's own transaction constraint in spirit | `@Transactional` removed from `ObservationLog.record` entirely — verified by reading `SimpleJpaRepository`'s own source that `save(...)` is already individually `@Transactional` (class-level `@Transactional(readOnly = true)`, method-level `@Transactional` on `save`), so the single DB write already gets its own, correctly-scoped transaction with no explicit annotation needed here. A self-invocation workaround (extracting a private `@Transactional` helper in the same class) was considered and rejected — Spring AOP proxies don't intercept internal self-calls, so it would have silently done nothing |
| 3 | `S3Client` bean is never explicitly closed on Spring context shutdown (Kimi Issue 3) | **REJECTED, verified false** | Read `DisposableBeanAdapter`'s own source (`spring-beans:6.2.9`): Spring's default inferred-destroy-method logic checks `instanceof AutoCloseable` and calls `close()` automatically for any such `@Bean` singleton, with no explicit `@PreDestroy`/`destroyMethod` needed. `S3Client extends SdkAutoCloseable extends AutoCloseable`. This differs from T06/T07's `List<Adapter>` case, which genuinely needed manual `@PreDestroy` tracking because Spring's inference doesn't reach into collection elements — `s3Client` here is a direct singleton bean, not wrapped in a collection | No change |
| 4 | Only `SdkException` is caught in `ObservationSnapshotStore.store`; any other `RuntimeException` (e.g. an unexpected NPE) would propagate out of `ObservationLog.record` before the Postgres insert, contradicting "an S3 failure must not block the DB write" (Kimi Issue 4) | **PARTIALLY ACCEPTED** | Catching a broad `RuntimeException` would also swallow genuine bugs in this class's own code (e.g. a null-input NPE), conflicting with this codebase's established fail-loudly convention. The specific NPE risk Kimi's evidence names is better closed by validating inputs up front than by broadening the catch clause | Addressed via item 5's `Objects.requireNonNull` guards instead — a null argument now fails immediately and clearly, before `buildKey`/`buildRequest`/the S3 call ever run, rather than either propagating an obscure NPE (the pre-fix state) or being silently swallowed as if it were a normal S3 outage (the rejected broad-catch approach) |
| 5 | `ObservationSnapshotStore.buildKey` uses locale-dependent `toLowerCase()` with no `Locale` argument, inconsistent with `FactType.DbConverter`'s correct `Locale.ROOT` usage (self-review Finding 3 / Kimi Issue 5) | **RESOLVED AS A SIDE EFFECT of item 1**, no separate change needed | Item 1's shortened key scheme removes `factType.name().toLowerCase()` from the key entirely (moved to metadata as `factType.name()`, uppercase, no casing conversion at all) | None beyond item 1 |
| 6 | No automated (ArchUnit or similar) enforcement that `Observation`/`ObservationRepository` never produce an `UPDATE`/`DELETE` (Kimi Issue 6) | **ACKNOWLEDGED, not a Phase 9 action** | The Phase 5 plan already scopes a unit-level test for this (`hasNoPublicMutatorBeyondConstruction`, Phase 10) — a full ArchUnit rule is heavier machinery than one class's own shape warrants, and the `crypto_app` DB grant is itself the actual, load-bearing enforcement (T02) | No change |
| 7 | `Instant.toString()` embeds colons in the S3 key, which can require special handling in URLs (self-review Finding 4 / Kimi Issue 7) | **RESOLVED AS A SIDE EFFECT of item 1**, no separate change needed | Item 1's shortened key scheme removes `observedAt` from the key entirely (moved to metadata, where colons in a value are harmless — no URL/console-navigation concern applies there) | None beyond item 1 |
| 8 | `ObservationSnapshotStore.store` has no documented non-null contract; `Map.of(...)` would throw an unhelpful `NullPointerException` deep inside `buildRequest` for a null argument (self-review Finding 5 / Kimi Issue 8) | **ACCEPTED** | Cheap, real robustness gap; also the correct fix for item 4's underlying concern (see above) | Added `Objects.requireNonNull` guards, one per parameter with a named message, at the top of `store(...)`, before any key/request building begins |

**5 accepted (2 of which were resolved as a side effect of item 1's fix, needing no separate change),
1 rejected with verified reasoning, 1 partially accepted via a more precise fix than proposed, 1
acknowledged with no action.**

## Files changed this phase

- `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationSnapshotStore.java` —
  items 1, 4/8, 5, 7.
- `services/crypto/src/main/java/com/themistra/crypto/observation/ObservationLog.java` — item 2.

Both files were already on the frozen brief's Files-to-Create list — no file outside that list was
touched. `mvn -pl services/crypto -am compile` — `BUILD SUCCESS`, zero warnings.
`mvn -pl services/crypto -am test` — 208 tests, 0 failures, same 3 pre-existing Docker-unavailable
errors as before this phase; no regression. No public API was changed in a breaking way — `buildKey`'s
signature narrowed (private method, not part of any public contract); `store(...)`'s public signature
is unchanged, now with documented, enforced non-null parameters.
