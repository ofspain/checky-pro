# crypto · T16 · Phase 3 — Design Challenge Findings

Reviewed: `artifacts/02-task-implementation-brief.md`, `spec/crypto-service/agents.md`,
`spec/crypto-service/design.md` (§4a L1-L3, L6, L14, L15, §4c), `spec/crypto-service/requirements.md`
(R1, R4, R5, R8-R12, R25), `spec/crypto-service/package.md` §8, and the existing T06-T10 code:
`ChainAdapter.java`, `ObservationSink.java`, `Subscription.java`, `TxResult.java`, `ObservationLog.java`,
`QuorumDecisionService.java`, `ProviderHealthTracker.java`, `ProviderProperties.java`,
`ProviderModuleBoundaryTest.java`, `EthereumAdapterConfig.java`, `TronAdapterConfig.java`, and the
baseline DDL (`V1__chain_baseline.sql` + `V2`-`V6` grants).

---

### 1. `ObservationSink` contract cannot deliver what the brief requires

**Issue:** The TIB says the watcher must (a) know which provider fired a callback and (b) log every
provider's *raw* response via `ObservationLog.record`. But `ObservationSink` is defined as
`void onObservation(TxResult result)` — it carries neither the provider name nor the raw JSON. The
`TxResult` record is a normalized value object, not a verbatim provider payload, and its Javadoc explicitly
states it is "never itself JSON-serialized" and that "the observation log persists each provider's *raw*
response verbatim." `ObservationLog.record` expects a `rawResponseJson` string. The brief therefore
assumes data that the current `ChainAdapter`/`ObservationSink` contract does not provide.

**Severity:** High

**Evidence:** `adapter/ObservationSink.java:21`; `adapter/model/TxResult.java:15-22`;
`observation/ObservationLog.java:50-61`; TIB Scope (`:18-29`).

**Recommended brief amendment:** Either (a) change `ObservationSink` to deliver a richer payload such as
`(String provider, String rawResponseJson, TxResult result)` — this is a VERBATIM contract change that
must be folded into `design.md` §4c and breaks no existing caller because there are none yet — or (b)
explicitly state that adapters must embed the raw JSON inside `TxResult` (a schema change to a frozen
record) and the provider identity inside the `Subscription` handle, with the watcher wrapping each
provider's sink to capture it. Amend AC2 to require logging the callback's raw response, not just the
fan-out responses.

---

### 2. `ProviderSet` placement in `provider/` violates the existing module-boundary test

**Issue:** The TIB proposes `provider/ProviderSet.java`, but the existing
`ProviderModuleBoundaryTest` forbids **any** `com.themistra.crypto.adapter` import inside `provider/`.
`ProviderSet` by definition must import `Chain`, `ChainAdapter`, and possibly `TxResult` from `adapter/`.

**Severity:** High

**Evidence:** TIB Files to Create (`:117`); `provider/ProviderModuleBoundaryTest.java:19-22`;
`design.md` §4a L15.

**Recommended brief amendment:** Move `ProviderSet` out of `provider/` to a package that is allowed to
import adapters. The natural homes are `adapter/` itself (it groups adapter instances by chain) or
`common/` (shared plumbing, the same role `ProviderProperties` already plays). Update the Files to Create
list accordingly. If it must stay in `provider/`, then the frozen `ProviderModuleBoundaryTest` must be
amended with an explicit allow-list for `ProviderSet`, which is a larger change than relocation.

---

### 3. No database grant for `crypto_app` on `chain.shedlock`

**Issue:** The TIB relies on `shedlock-provider-jdbc-template` to acquire, update, and release rows in
`chain.shedlock`. The current migrations create the table in `V1` but never grant `crypto_app` any DML
on it. `V2` grants `USAGE, SELECT` on sequences and `INSERT, SELECT` on `observations`/`attestations`/
`quorum_decisions`; `V3`-`V6` add grants for other tables but none for `shedlock`.

**Severity:** High

**Evidence:** `V1__chain_baseline.sql:126-131`; `V2__crypto_app_role_and_grants.sql`;
TIB State Changes (`:112-113`) and Files to Create (`:121`).

**Recommended brief amendment:** Expand `V7__crypto_app_chain_cursors_update_grant.sql` (or add a
separate `V8`) to grant `INSERT, UPDATE, DELETE` on `chain.shedlock` to `crypto_app`. Add a required test
(e.g., in `WatcherRegistryTest` or a dedicated grant test) that exercises the ShedLock table through the
JDBC template provider as the `crypto_app` role.

---

### 4. AMOUNT/TOKEN/CONFIRMATIONS facts must exclude providers that returned `exists=false`

**Issue:** The TIB says the watcher "decomposes the gathered `TxResult`s into `EXISTENCE` (`Boolean`),
`AMOUNT` (`BigDecimal`), `TOKEN` (`String`, contract address), and `CONFIRMATIONS` (`Integer`) facts."
But `TxResult`'s own Javadoc states: "When `exists=false`, the remaining fields carry no meaningful data
(implementers return zero/null for them, never fabricated values)." Feeding zero/null values from
non-observing providers into `QuorumDecisionService.evaluate` for `AMOUNT`/`TOKEN`/`CONFIRMATIONS` would
corrupt the quorum and likely produce `HELD` or false agreement. `ProviderAnswer.value` also rejects
`null`.

**Severity:** Medium

**Evidence:** `TxResult.java:11-14`; `quorum/ProviderAnswer.java:14-16`; TIB Scope (`:24-26`).

**Recommended brief amendment:** Explicitly state that `AMOUNT`, `TOKEN`, and `CONFIRMATIONS` are
quorum-evaluated only from the subset of providers whose `getTx` response has `exists=true`; providers
with `exists=false` contribute only to the `EXISTENCE` fact (and are still logged/health-tracked). Add this
as an acceptance criterion and a required test.

---

### 5. No strategy for the process-local "already processed" set being lost on restart/rebalance

**Issue:** The TIB proposes a `ConcurrentHashMap`-backed, process-local set to ensure each transaction's
facts are evaluated exactly once (AC4). This set is lost on every rolling deploy, crash, or eviction. After
a restart, a re-delivered subscription observation for an already-decided transaction would cause the
watcher to re-invoke `QuorumDecisionService.evaluate`, which rejects the duplicate with an
`IllegalStateException`. The brief says the watcher itself must guard against re-invocation, but if the
guard is lost, the resulting exception is not handled.

**Severity:** Medium

**Evidence:** TIB Constraints (`:186-189`); `quorum/QuorumDecisionService.java:82-88`.

**Recommended brief amendment:** Add a required behavior/test: the watcher catches the
`IllegalStateException` thrown by `QuorumDecisionService.evaluate` when a duplicate decision exists and
treats it as a benign no-op (do not propagate the exception, do not emit health degradation, do not retry).
This makes the schema's unique constraint a safe backstop rather than a latent crash vector.

---

### 6. No transactional strategy or ordering for `ChainCursor.lastBlock` advancement

**Issue:** The TIB explicitly avoids wrapping `ObservationLog.record`, `QuorumDecisionService.evaluate`,
and `ProviderHealthTracker.*` in an outer transaction. But advancing `ChainCursor.lastBlock` is itself a
database write. The brief does not state whether the cursor update happens inside the same unit of work as
the observation/quorum writes, nor what order it takes. If the cursor advances before the observation is
durably logged, a crash could leave a future reorg walk-back unable to find the observation that justified
the cursor position (L6). If the cursor lags behind, the watcher may re-process blocks on restart.

**Severity:** Medium

**Evidence:** TIB State Changes (`:106-110`); Constraints (`:189-193`); `ObservationLog.java:24-32`.

**Recommended brief amendment:** Specify the ordering: observation + quorum + health updates complete
first; the cursor is advanced afterward as a separate, best-effort write. Because the cursor is a
recoverable progress marker (not a source of truth), lag is acceptable but leading ahead of the log is not.
Add a required test confirming that a failure in the cursor-update path does not roll back or prevent the
preceding observation/quorum writes.

---

### 7. No watcher-lag metrics despite `agents.md` making them paged

**Issue:** `agents.md` Observability rule states: "Per-chain watcher lag and provider-disagreement rate
are **paged** metrics." The TIB does not require adding any Micrometer metrics for watcher lag or
provider disagreement, even though this task is the first place that data becomes available.

**Severity:** Medium

**Evidence:** `agents.md:53-54`; TIB Scope/Outputs.

**Recommended brief amendment:** Add a constraint or acceptance criterion requiring at least one
`MeterRegistry`-based lag metric (e.g., `crypto.watcher.lag.blocks` tagged by `chain`) and one
provider-disagreement counter (`crypto.provider.disagreements` tagged by `chain` and `provider`). These
should be wired in `Watcher` and `ProviderHealthTracker` respectively, with required tests verifying the
metrics are emitted under the expected conditions.

---

### 8. Shard-assignment algorithm for `WatcherRegistry` is unspecified

**Issue:** AC8 requires that ShedLock-sharded assignment guarantees no address is double-driven and that
a lost lease is picked up within one reconciliation interval. The TIB says watches are partitioned into
shards but does not specify the assignment function (e.g., `shard = watchId.hashCode() % shardCount`).
Without a deterministic, replica-independent algorithm, two replicas could compute different shard
ownership and double-drive a watch.

**Severity:** Medium

**Evidence:** TIB Scope (`:32-35`); AC8 (`:155-160`); Constraints (`:181-183`).

**Recommended brief amendment:** Specify the assignment function explicitly, e.g., "a watch's shard is
determined by `Math.floorMod(watchId.hashCode(), watcherProperties.shardCount())`, so every replica
computes the same mapping independently." Add a required test that two registry instances with the same
`shardCount` never claim responsibility for the same `watchId`, and that changing `shardCount` causes a
graceful handoff at the next reconciliation.

---

### 9. No required test for a partial provider fan-out that still reaches quorum

**Issue:** AC1 says the watcher gathers every configured provider's answer before evaluating; AC3/R5 say
a failed provider is recorded unhealthy and excluded if quorum is still achievable. The Required Tests
list includes tests for each health signal individually but does not explicitly require a scenario where
one provider in the fan-out times out/throws while the other two agree, producing an `AGREED` decision
and a `recordUnhealthy` call.

**Severity:** Low

**Evidence:** TIB Required Tests (`:164-177`).

**Recommended brief amendment:** Add a required test: "given three configured providers for a chain, when
one `getTx` throws a transport exception and the other two return matching answers, the watcher records
`recordUnhealthy` for the failed provider, evaluates `AGREED` for each fact, and does not throw."

---

### 10. No lifecycle rule for in-flight callbacks after `Subscription.cancel`

**Issue:** `Subscription.cancel()` is documented as idempotent and does **not** guarantee suppression of
observations already in flight. The TIB says `WatcherRegistry` starts/stops `Watcher` instances as shard
ownership changes, which entails cancelling subscriptions. An in-flight callback that arrives after
cancellation could still be processed by a `Watcher` that no longer owns the watch.

**Severity:** Low

**Evidence:** `adapter/model/Subscription.java:10-14`; TIB Scope (`:32-35`).

**Recommended brief amendment:** Specify that a `Watcher` must ignore any callback received after its
own `stop()`/`cancel()` has been called (e.g., by checking a volatile `running` flag or by cancelling the
underlying futures). Add a required test that an observation delivered after `Watcher.stop()` does not
trigger `getTx` fan-out or repository writes.
