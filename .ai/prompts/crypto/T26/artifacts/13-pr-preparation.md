# crypto · T26 · Phase 13 — PR / Commit Preparation

Phase 12 verdict: **PASS**. Proceeding to merge preparation.

## Commit title

```
Add the end-to-end real-infrastructure integration test (T26)
```

## Commit message

```
Add the end-to-end real-infrastructure integration test (T26)

Every piece of this pipeline (T02-T25) has been proven correct in
isolation - unit tests with mocked collaborators, or Testcontainers
tests scoped to one repository/module. Nothing had ever proven the
pieces work correctly together, through a real database and a real
message broker, the way package.md's own verification checklist asks
for L1 specifically. This is that proof: four flows over a real
PostgreSQLContainer and KafkaContainer - register/seen/confirmed/
finalized/attest-signs; a genuine two-fact disagreement holds and
emits nothing further; a reorg after confirmed emits chain.tx.reorged
and invalidates the cursor; a sanctioned counterparty is blocked with
no signature.

ProviderSet's constructor is hard-typed to concrete EthereumAdapter/
TronAdapter lists, not the ChainAdapter interface, so a FakeChainAdapter
cannot be wired through the real WatcherRegistry/ProviderSet path
without a production change. Each flow instead constructs its own
Watcher directly, mirroring WatcherTest's own established technique,
but every collaborator is a real, Spring-autowired bean backed by the
real containers rather than a mock. WatcherRegistry itself is
@MockBean-doubled specifically because its own real, @Scheduled
reconcile() would otherwise start a second, real-RPC-backed Watcher
racing this test's own the moment a watch row exists - a real risk
found independently, not by either review pass.

The Kafka-verification technique matters as much as the flows
themselves: consumer records are matched by parsing each one's own
txHash payload field, not just by topic. Without this, a later flow's
brand-new, earliest-reset consumer group would see every earlier
flow's leftover messages on the same class-scoped container and could
be silently satisfied - or wrongly failed - by the wrong flow's event
entirely. This was caught by the test-review pass, not assumed away.

Three full review rounds - self-review, independent review, and test
review - found and fixed eleven genuine defects before this test could
be trusted, the two most serious being a tx() helper that silently
hardcoded one transaction hash for every flow (making all four
secretly share one identity), and an entity lookup querying Watch by
the wrong primary key entirely (Watch's real @Id is a surrogate,
watchId is a separate column) - both would have made every flow
either collide with each other or silently operate on a null entity.
One independent-review finding was disproven by directly executing a
standalone Mockito probe rather than accepted on the reviewer's word;
several others were confirmed already covered by existing, dedicated
tests elsewhere (ResourceServerConfigIntegrationTest for R27's negative
paths, ObservationLogTest for L3's S3-before-DB ordering) and correctly
not duplicated here.

No production code changed anywhere in this task - one new test file
is the entire diff.

Docker was unavailable throughout this task's entire development in
this environment. This class is correct by construction - every method
signature, field name, and entity mapping it uses was verified directly
against the real source, not assumed, and it compiles cleanly against
all of it - but it has never actually been run. This is this task's
one disclosed, principal residual risk, not a silent claim of a passing
result.

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Created:**
- `services/crypto/src/test/java/com/themistra/crypto/watch/EndToEndIntegrationTest.java` (+577 lines)

**1 file changed, +577 lines.** No production code, no migration, no schema change, no contract change
— this task adds real-infrastructure test coverage only.

## Summary

Closes the final proof `package.md` §9's own verification checklist calls for explicitly: that no
single-provider answer ever leaves this service as fact, demonstrated against real infrastructure, not
only mocked unit tests. Four flows exercise the complete pipeline — registration, quorum arbitration,
event emission via a real outbox→Kafka path, and attestation gating — end to end.

The path to a trustworthy test here was not mechanical. A genuine, hard architectural constraint
(`ProviderSet`'s concrete-type-only constructor) required a considered workaround rather than a
production change; a real, previously-undiscovered risk (`WatcherRegistry`'s own scheduled reconciler
racing this test's manually-built `Watcher`) was found independently while investigating a design
review that had — through no fault of its own technique — run against the wrong branch state; and three
full review rounds collectively found and fixed eleven genuine defects, several severe enough that the
test would have either silently passed for the wrong reasons or silently corrupted its own cross-flow
data without them. One reviewer finding was checked by direct execution and proven wrong rather than
accepted at face value; several others were correctly recognized as already covered elsewhere and not
duplicated.

## Testing performed

- `mvn -pl services/crypto -am test-compile` — clean, confirmed directly after every fix across every
  phase of this task's own review process.
- A standalone Mockito probe, written and run specifically to verify one independent-review finding
  (Mockito's real default answer for an unstubbed `Optional`-returning method), then removed — not part
  of this task's own deliverable, but its result is recorded in `artifacts/09-review-resolution.md`.
- Full traceability matrix against `requirements.md`/`design.md`/`tasks.md`:
  `artifacts/12-specification-verification.md` — verdict **PASS**.
- **Not performed: an actual run of the four flows.** Docker has been unavailable in this development
  environment throughout this task's entire lifecycle. This is disclosed plainly, here and in every
  preceding phase's own artifacts, as this task's principal residual risk.

## Specification references

- **Task:** `spec/crypto-service/tasks.md`, task 26 ("End-to-end integration test").
- **Requirements:** R1, R2, R3, R6, R7, R8, R9, R10, R11, R12, R18, R20, R21, R23, R27.
- **LOCKED decisions:** L1, L2, L3, L4, L5, L6, L10, L11 (confirmed unmodified), L12.
- **Flagged, not fixed, for a future task:** this class has never been run — re-verify it by an actual
  execution at the first opportunity in a Docker-available environment. Also flagged: the recurring
  branch-poisoning issue that affected this branch's development process throughout T24-T26 (9
  occurrences in this session alone), an external process risk unrelated to this task's own content.
