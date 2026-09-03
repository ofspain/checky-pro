# crypto · T05 · Phase 7 — Self Review

Reviewed the Phase 6 diff (`adapter/Chain.java`, `adapter/ChainAdapter.java`,
`adapter/ObservationSink.java`, `adapter/model/*.java`) against the frozen brief and `agents.md`. No
rewrites performed — findings only, fixes are Phase 9.

---

## Finding 1 — `TxResult`'s field semantics when `exists=false` are undocumented, and `ChainAdapter.getTx`'s contract doesn't state that a genuinely nonexistent/unobserved transaction is a normal return value, not an exception

**Issue:** Neither `TxResult`'s nor `ChainAdapter`'s Javadoc says what the non-`exists` fields
(`amount`, `confirmations`, `blockNumber`, `fromAddress`, `toAddress`, `tokenContractAddress`) mean
when `exists=false` — are they required to be null/zero, meaningless, or should an implementer still
populate whatever partial data is known? More importantly, nothing states that `getTx` returning
`TxResult(exists=false, ...)` — as opposed to throwing — is the **expected, normal** way to represent
"this provider hasn't observed the transaction," which is itself one of the quorum-checked facts
(`requirements.md` R1 lists "tx existence" alongside amount/token/confirmations/finality as a fact
requiring 2-of-3 agreement, meaning "false" is a legitimate, common answer, not an error case).

**Severity:** Medium — not a defect in this task's own code (no consumer exists yet to get this
wrong), but a real specification gap that the next two tasks to implement this interface for real
(T06 `EthereumAdapter`, T07 `TronAdapter`) will hit directly. If either implementation mistakenly
throws on a simply-unobserved transaction instead of returning `exists=false`, quorum evaluation
(task 9) would never be able to reach a stable "not yet observed" agreement state — every provider
lagging behind head would look like an error instead of a normal, poll-again-later condition.

**Evidence:**
- `services/crypto/src/main/java/com/themistra/crypto/adapter/model/TxResult.java:18-27` — no
  Javadoc on the `exists` field or the record as a whole addresses this.
- `services/crypto/src/main/java/com/themistra/crypto/adapter/ChainAdapter.java:18` — `getTx`'s
  inline comment ("provider-scoped; quorum compares across adapters") doesn't address the
  not-yet-observed case either.
- `spec/crypto-service/requirements.md` R1: "tx existence, amount, token contract, confirmations,
  finality status" are each independently quorum-checked facts — "existence" being false is exactly
  as legitimate an answer as it being true.

**Recommendation:** Add a Javadoc note on `TxResult` (or its `exists` field) stating: (a) a
not-yet-observed/nonexistent transaction is represented by `getTx` returning `TxResult(exists=false,
...)`, never by throwing; (b) when `exists=false`, the remaining fields carry no meaningful data (a
reasonable convention: implementers populate them with the type's zero-value/null rather than
fabricating data). This closes the ambiguity before T06/T07 have to guess.

---

## Not flagged (checked and found correct)

- The "VERBATIM" `ChainAdapter` interface text matches `design.md` §4c's own code block exactly
  (method names, parameter types, return types, and inline comments all identical); the added blank
  lines between methods are a necessary consequence of embedding the fragment in a real compilable
  file with package/imports/Javadoc, not a substantive deviation — and the frozen brief's own
  amendment #9 already anticipated this by scoping the structural test to name/return-type/
  parameter-types, not a byte-for-byte diff (unlike `V1__chain_baseline.sql`'s SQL, which genuinely
  is pasted as a standalone script).
- `FinalityStatus.finalizedBlockNumber`'s claim that Ethereum's beacon-finalized checkpoint and
  Tron's solidified block are the same underlying concept (a consensus-declared irreversible block
  number) was checked directly against `design.md` §4c's own finality-policy table wording
  ("ETHEREUM: block is at or below the beacon-chain `finalized` checkpoint... TRON: block is
  solidified") — both are phrased as block-number thresholds, confirming the unification is accurate,
  not an oversimplification.
- Module boundaries (L15) — every new file is under `adapter/` or `adapter/model/`, matching the
  frozen brief exactly.
- `mvn -pl services/crypto -am compile` clean, no warnings; this task has no Docker/Spring/persistence
  dependency to caveat.
