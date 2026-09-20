# crypto · T05 · Phase 9 — Review Resolution

**Human Approval gate. Approved 2026-09-03.** Combines Phase 7 (self-review) and Phase 8 (Kimi
independent review) findings into one resolution log. Only accepted comments were applied — all
documentation-only (Javadoc), no structural code changes, no public-API changes beyond added prose.

## Resolution log

| # | Comment (source) | Decision | Reason | Change made |
|---|---|---|---|---|
| 1 | `TxResult.exists=false`/`getTx` contract undocumented (self-review Finding 1 / Kimi Finding 1) | **ACCEPTED** | A real gap T06/T07 would otherwise have to guess at, with a wrong guess breaking quorum's "not yet observed" state | Added a failure-vs-negative-answer contract paragraph to `ChainAdapter`'s class Javadoc; cross-referenced from `TxResult` |
| 2 | Required tests missing (Kimi Finding 2) | **ACKNOWLEDGED, not a Phase 9 action** | Test authorship is Phase 10 by pipeline design | No change |
| 3 | `finalizedBlockNumber` (primitive `long`) can't represent "unknown" (Kimi Finding 3) | **ACCEPTED, resolved differently** | A nullable `Long` (or sentinel) is unnecessary once failure is uniformly represented by an exception across all three query methods — cleaner and consistent with #1's own resolution, rather than adding a second, inconsistent "unknown" mechanism | Folded into the same `ChainAdapter` Javadoc paragraph (item 1): provider/transport failure throws; no method represents "unknown" via `null`/sentinel |
| 4 | `getTokenInfo` contract for unknown/non-allowlisted tokens unspecified (Kimi Finding 4) | **ACCEPTED, resolved differently** | Rejected the suggested `UNKNOWN_TOKEN` sentinel — allowlist classification (R14) is entirely `TokenValidator`'s job (task 11); `ChainAdapter` has no allowlist concept at all | Added a paragraph to `ChainAdapter`'s Javadoc stating `getTokenInfo` returns raw provider-reported metadata only |
| 5 | `getFinalityStatus` contract for nonexistent tx unspecified (Kimi Finding 5) | **ACCEPTED** | Symmetric to #1/#3 — finality has no meaning for a transaction never observed; calling it for one is a caller error | Added a paragraph to `ChainAdapter`'s Javadoc: callers must confirm existence via `getTx` first |
| 6 | `ObservationSink` has no error channel (Kimi Finding 6) | **ACCEPTED documentation only** | Rejected adding `onError(...)` — provider-health signaling is task 10's dedicated mechanism (R5); adding a parallel channel now risks conflicting with that task's own design | Added a Javadoc paragraph to `ObservationSink` stating this explicitly |
| 7 | `Subscription.cancel()` idempotency/thread-safety unspecified (Kimi Finding 7) | **ACCEPTED** | Cheap, sensible contract to fix before real implementations diverge | Added Javadoc: idempotent, any-thread-safe, no in-flight-delivery guarantee |
| 8 | `TokenInfo` equality-warning called a "design smell," should override `equals`/enforce structurally (Kimi Finding 8) | **REJECTED** | Already decided at the Phase 4 gate (amendment #1): overriding a record's generated equality is the anti-pattern this codebase avoids; real consumers are DB-keyed, not object-keyed. The frozen brief may not be renegotiated downstream | No change |
| 9 | `FinalityStatus` won't generalize to BASE/ARB/Solana (Kimi Finding 9) | **ACCEPTED documentation only** | Explicitly out of scope per `package.md` §2 ("must not preclude... not built here"); rejected any structural (sealed-hierarchy) change as premature | Added a launch-scope-shape paragraph to `FinalityStatus`'s Javadoc |

**6 accepted (3 resolved via one unified fix), 1 acknowledged as already-tracked, 1 rejected as
already-decided.**

## Files changed this phase

- `services/crypto/src/main/java/com/themistra/crypto/adapter/ChainAdapter.java` — class Javadoc
  gained the failure-vs-negative-answer contract (items 1, 3, 4, 5).
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/TxResult.java` — cross-reference
  to `ChainAdapter`'s contract (item 1).
- `services/crypto/src/main/java/com/themistra/crypto/adapter/ObservationSink.java` — no-error-channel
  rationale (item 6).
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/Subscription.java` —
  `cancel()` contract (item 7).
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/FinalityStatus.java` —
  launch-scope note (item 9).

All five files were already on the frozen brief's Files-to-Create list — no file outside that list
was touched. `mvn -pl services/crypto -am compile` — `BUILD SUCCESS` after all changes. No public
API/class was renamed or restructured; every change is additive Javadoc.
