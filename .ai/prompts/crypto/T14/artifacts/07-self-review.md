# crypto · T14 · Phase 7 — Self-Review

Reviewed the diff (Phase 6) against the frozen brief and `agents.md`: correctness, boundary conditions,
null-safety, thread-safety, transaction boundaries, module boundaries, idempotency, money types,
enumeration-safety/secret-handling, readability, complexity. Findings only — no fixes applied here.

---

### 1. `FinalityPolicy`'s interface methods carry no method-level Javadoc — a caller hovering on the
method itself (not the class) may miss the caller-routing/trust-boundary/null-fail-fast contracts

- **Severity:** Low
- **Evidence:** `finality/FinalityPolicy.java:34,36` (`Chain chain();` / `boolean isFinal(FinalityStatus
  status);`) — all three contracts (caller-routing responsibility, trust boundary, null fail-fast) are
  documented only at class level (`finality/FinalityPolicy.java:8-31`), not per-method.
- **Recommendation:** This is the identical class of gap T13 self-review Finding 2 caught and fixed for
  `AddressPoisoningDetector.detectPoisoning` — condense the class-level contracts into a method-level
  Javadoc on `isFinal` (an IDE hover on a method call site typically surfaces the method's own Javadoc,
  not necessarily the declaring interface's class Javadoc). `chain()` is simple enough that a one-line
  method comment likely suffices without a full block.

---

### 2. `TronFinalityPolicy`'s Q4 documentation references `TronAdapter.computeConfirmations` /
`EthereumAdapter.computeConfirmations` as plain `{@code}` text, not a resolvable `{@link}` — if either
private method is ever renamed, this cross-class claim can drift stale with no build-time signal

- **Severity:** Low
- **Evidence:** `finality/TronFinalityPolicy.java:17-20` — `{@code TronAdapter.computeConfirmations}`
  and `{@code EthereumAdapter.computeConfirmations}` are formatting-only tags; unlike `{@link}`, they
  are never resolved or checked against the referenced class's actual method names, so nothing catches
  a rename in either adapter class silently invalidating this Javadoc's claim.
- **Recommendation:** Low-value to fix given both referenced methods are `private` (a `{@link}` from
  another package cannot resolve to a private member either, so switching tags would not add real
  safety) — flagging as informational only; no action likely warranted, but included per this phase's
  own "findings only, don't hide anything" directive.

---

No correctness, boundary-condition, null-safety, thread-safety, module-boundary, idempotency, money-
type, or secret-handling defects found. `isFinal`'s `txBlockNumber <= finalizedBlockNumber` comparison
correctly implements both R6 and R7 with no confirmation-count arithmetic (L4); both implementations'
bodies are intentionally identical per the frozen brief (Phase 3 Finding 3); imports in all three files
are limited to `Chain`, `FinalityStatus`, `Objects`, and Spring's `@Component` (AC6, module boundary);
`Objects.requireNonNull(status, "status")` matches this codebase's own established fail-fast message
style (`TokenAllowlistSeeder`).
