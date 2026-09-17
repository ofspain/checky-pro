# crypto · T23 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

```
Author crypto-service's internal API and event contracts (T23)
```

## Commit message

```
Author crypto-service's internal API and event contracts (T23)

Closes the "contract does not exist yet" gap every prior task in this
package has disclosed since T11. Purely retrospective, documentation-
only work: no production code changes anywhere in this task.

contracts/api/crypto-internal.yaml documents the 4 real internal/
public routes (POST/DELETE /internal/v1/watches, POST /internal/v1/
attest, GET /.well-known/themistra-verification-keys) and their 6
component schemas, mirroring auth.yaml's own structural conventions
and its identical R47 precedent of scoping to success responses only
(the 409 problem+json refusal shape is cross-cutting, not endpoint-
specific, and out of scope here).

The 5 event schemas under contracts/events/chain/ document chain.tx.
seen/confirmed/finalized/reorged and chain.provider.degraded.
tx-finalized is copied verbatim from design.md itself (AC6); the
other three share its envelope minus the fields each event doesn't
carry. provider-degraded is a genuinely different envelope - no
idempotencyKey/watchId/txHash - its description now explicitly
documents the real, different {chain}:{provider}:degraded:
{occurredAt}:{UUID} idempotency-key format (verified against
ProviderDegradedPublisher.java) and why L5's chain:txHash:eventtype
format doesn't apply to it, closing a real risk of consumers wrongly
reading this as an L5 violation.

CryptoInternalOpenApiContractTest, 5 event-payload contract tests, and
a new centralized money-discipline test (walking all 6 contract files
for any monetary field that isn't type: string) prove the real code
conforms to what's now documented - the same plain-Jackson structural
technique already established for auth's own contracts, deliberately
not a JSON-Schema-validation library for one contract's sake.

Kimi's independent review (Phase 8) caught a real process gap: the
review-prep commit never staged the 6 contract files themselves (they
existed on disk throughout, verified by direct re-reading, but were
never `git add`ed) - fixed by committing them alongside two low-
severity test fixes his review also raised. Kimi's test review
(Phase 11) then closed two real, AC-mapped enforcement gaps: AC3's
idempotencyKey presence/absence was implied but never directly
asserted, and AC6's verbatim-fidelity requirement was checked once by
hand rather than continuously - both now have dedicated assertions.
Five smaller regression-lock additions (OpenAPI security checks, a
Chain enum-drift guard, additionalProperties enforcement, a status
type check) were accepted alongside them; one suggestion (exhaustive
per-field type/format checking across every event schema) was
rejected as a deliberate scope boundary this technique already
established in auth's own precedent test.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created:**
- `contracts/api/crypto-internal.yaml` (183 lines)
- `contracts/events/chain/tx-seen.v1.schema.json` (19 lines)
- `contracts/events/chain/tx-confirmed.v1.schema.json` (19 lines)
- `contracts/events/chain/tx-finalized.v1.schema.json` (23 lines)
- `contracts/events/chain/tx-reorged.v1.schema.json` (18 lines)
- `contracts/events/chain/provider-degraded.v1.schema.json` (15 lines)
- `services/crypto/src/test/java/com/themistra/crypto/common/CryptoInternalOpenApiContractTest.java` (442 lines)
- `services/crypto/src/test/java/com/themistra/crypto/watch/SeenPayloadContractTest.java` (59 lines)
- `services/crypto/src/test/java/com/themistra/crypto/watch/ConfirmedPayloadContractTest.java` (58 lines)
- `services/crypto/src/test/java/com/themistra/crypto/watch/FinalizedPayloadContractTest.java` (86 lines)
- `services/crypto/src/test/java/com/themistra/crypto/reorg/ReorgedPayloadContractTest.java` (58 lines)
- `services/crypto/src/test/java/com/themistra/crypto/provider/ProviderDegradedPayloadContractTest.java` (87 lines)
- `services/crypto/src/test/java/com/themistra/crypto/MoneyFieldsAreDecimalStringsContractTest.java` (74 lines)

**Modified:**
- `services/crypto/pom.xml` — added `jackson-dataformat-yaml` (test-scope).

**14 files changed (13 created, 1 modified), +1145 lines.** No production code, no migration, no schema
change, no persistence — this task documents and tests, it does not build.

## Summary

Closes a gap disclosed by every task in this package since T11: `crypto-service` shipped its internal
API and its 5 outbox-published events without ever having a written contract for either. This task is
purely retrospective — every schema and route documents already-shipped, already-tested behavior; no
controller, DTO, payload record, or any other production file was touched.

The review pipeline caught two real, distinct classes of gap. Kimi's independent review (Phase 8) found
a process defect, not a design one: the review-prep commit omitted the 6 contract files from `git add`
(they existed on disk and were the actual basis of Phase 6/7's work — verified directly, not assumed —
but the commit hadn't caught up), fixed at Phase 9 alongside two low-severity test fixes the same review
raised (an unused import, a missing path-parameter format assertion). Kimi's test review (Phase 11) then
found the more interesting class of gap: two of the six acceptance criteria (AC3, AC6) had real,
AC-specific enforcement holes that no earlier phase had caught — `idempotencyKey`'s required-vs-absent
status was asserted by implication, never directly, and AC6's "byte-for-byte" fidelity requirement had
only ever been checked once by hand. Both are now closed by dedicated, automated tests. One further
suggestion (exhaustive type/format checking for every event-schema field) was deliberately rejected as
scope creep beyond what this task's own ACs require and beyond what the identical, established technique
in `services/auth` already does.

## Testing performed

- `mvn -pl services/crypto -am test-compile` — clean.
- `mvn -pl services/crypto test -Dtest=CryptoInternalOpenApiContractTest,SeenPayloadContractTest,ConfirmedPayloadContractTest,FinalizedPayloadContractTest,ReorgedPayloadContractTest,ProviderDegradedPayloadContractTest,MoneyFieldsAreDecimalStringsContractTest`
  — 23/23 pass.
- `mvn -pl services/crypto -am test` (full module regression) — 743 tests, 6 failures, all pre-existing
  and unrelated to this task (`ProviderHealthRepositoryIntegrationTest`, `ObservationRepositoryIntegrationTest`,
  `QuorumDecisionRepositoryIntegrationTest`, `TokenAllowlistRepositoryIntegrationTest` — DB-permission
  issues in modules this task never touches, disclosed since T18-T22). Zero regressions.
- Full traceability matrix against `requirements.md`/`design.md`/`tasks.md`:
  `artifacts/12-specification-verification.md` — verdict **PASS**.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 23 ("Contracts").
- **Requirements:** R28 (internal responses and emitted events conform to their documented contracts).
- **LOCKED decisions:** L5 (`chain:txhash:eventtype` idempotency key — documented, with `provider-degraded`'s
  deliberate, explained exception).
- **Flagged, not fixed, for a future task:** `tx-finalized`'s optional `confirmations`/`addressPoisoningFlag`
  fields are documented per `design.md`'s own verbatim schema text but not yet emitted by the real
  `TxLifecyclePublisher.FinalizedPayload` — worth revisiting only if/when that logic is actually built.
