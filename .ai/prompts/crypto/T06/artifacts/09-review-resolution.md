# crypto · T06 · Phase 9 — Review Resolution

**Human Approval gate. Approved 2026-09-03.** Combines Phase 7 (self-review) and Phase 8 (Kimi
independent review) findings into one resolution log. Only accepted comments were applied.

## Resolution log

| # | Comment (source) | Decision | Reason | Change made |
|---|---|---|---|---|
| 1 | `getTx`'s Transfer-log lookup can disagree with what `subscribeAddress` detected, for multi-Transfer transactions (self-review Finding 1 / Kimi Finding 1) | **ACCEPTED** | Real correctness bug — `pollOnce`'s recipient-filtered query could report a different Transfer than a generic `getTx` re-lookup would find | `pollOnce` now builds each `TxResult` directly from the specific matched `Log` via a new `buildTxResultFromLog` helper — never round-trips through `getTx` again. Also eliminates a redundant `eth_getTransactionByHash`/`eth_getTransactionReceipt` pair per observation |
| 2 | `ethCall` never checks for a JSON-RPC error before decoding (self-review Finding 2 / Kimi Finding 2) | **ACCEPTED** | A reverting/non-ERC20 contract would produce an opaque `NullPointerException` instead of a clear, named exception | `ethCall` now checks `EthCall.hasError()` and throws a named `IllegalStateException` (contract address, function name, provider error message) before ever returning a value to decode |
| 3 | `Web3j`/scheduler never shut down — resource leak (self-review Finding 3 / Kimi Finding 3) | **ACCEPTED** | Real leak on every context restart | `EthereumAdapter` now implements `AutoCloseable`, closing `Web3j` and shutting down its scheduler. `EthereumAdapterConfig` retains every adapter it builds and closes them all from a `@PreDestroy` hook |
| 4 | Required tests missing (Kimi Finding 4) | **ACKNOWLEDGED, not a Phase 9 action** | Phase 10 by pipeline design | No change |
| 5 | Missing `{apiKey}` env var leaves the literal placeholder in the URL, no startup failure (Kimi Finding 5) | **ACCEPTED, refined** | A genuine gap in the original design: it conflated "no placeholder" (must stay silent for `local`) with "placeholder present but unresolved" (should fail per L13). Narrows, not reverses, the Phase 5 decision | `resolveUrl` now throws `IllegalStateException` (naming the provider and the unresolved `apiKeySecretName`) specifically when a placeholder is present but the environment value is absent; the no-placeholder case is unchanged and still silent |
| 6 | API key embedded in the URL can leak through exception/log messages (Kimi Finding 6) | **ACCEPTED, documentation only** | Rejected switching to header-based credential injection — would break compatibility with the real providers (Alchemy/Infura/QuickNode) this design was chosen at Phase 4 specifically to support | Added a Javadoc paragraph to `EthereumAdapter`'s class-level docs naming the risk and recommending structured-logging configuration treat this class's exceptions as potentially sensitive |
| 7 | `getTx` picks the first Transfer log even for direct (non-watcher) calls (Kimi Finding 7) | **ACCEPTED, documentation only** | `ChainAdapter.getTx(String txHash)` has no parameter to disambiguate by — that interface is frozen (T05); not a defect this task can structurally fix | Added a Javadoc paragraph to `EthereumAdapter`'s class-level docs stating the limitation explicitly and noting `subscribeAddress` doesn't share it (per item 1's fix) |
| 8 | Confirmation count can go negative or overflow `int` (Kimi Finding 8) | **ACCEPTED** | Cheap, real robustness gap | New shared `computeConfirmations` helper (used by both `getTx` and `buildTxResultFromLog`) throws a named `IllegalStateException` on a negative result and uses `Math.toIntExact`/`longValueExact` to fail loudly rather than silently wrap on overflow |
| 9 | `OkHttpClient` missing write/call timeouts (Kimi Finding 9) | **ACCEPTED** | Cheap, closes a real timeout-coverage gap | Added `.writeTimeout(...)` and `.callTimeout(...)` to the builder, same `timeoutSeconds` value |
| 10 | `fetchBlockNumber`/`fetchLogs` don't null-check successful-but-empty payloads (Kimi Finding 10) | **ACCEPTED** | Matches the class's own established named-exception style | Both now null-check their response payload and throw a named `IllegalStateException` before returning |

**9 accepted (2 as documentation-only), 1 acknowledged as already-tracked Phase 10 work, 0 rejected.**

## Files changed this phase

- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapter.java` — items 1, 2,
  3, 6, 7, 8, 10.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/eth/EthereumAdapterConfig.java` —
  items 3, 5, 9.

Both files were already on the frozen brief's Files-to-Create list — no file outside that list was
touched. `mvn -pl services/crypto -am compile` / `test-compile` — `BUILD SUCCESS` after all ten items.
No public API was removed; `EthereumAdapter` gained `implements AutoCloseable` (additive) and one new
public method (`close()`), both direct, necessary consequences of item 3 — not an unrelated refactor.
