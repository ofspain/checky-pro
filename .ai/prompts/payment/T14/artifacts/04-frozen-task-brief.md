# payment · T14 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN** (approved by the user, 2026-10-15 — "Approve as written")

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T14 — Idempotency & ordering tests |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |

## Disposition of each Phase 3 (Kimi) finding

### Finding 1 (Low) — ACCEPTED

Decision 1's own wording overstated the precedent. Reworded below: "adapts the single-class/
shared-container/`Awaitility` shape of `AuthEventConsumerIntegrationTest`, with a real local
Kafka broker (not a Testcontainers Kafka image — neither this nor that precedent uses one),
`@MockBean CryptoClient`, and raw-JDBC domain-table verification in place of its own projection-
table checks."

### Finding 2 (High) — ACCEPTED outright

`spring.kafka.consumer.auto-offset-reset=earliest` added to the test's own `@SpringBootTest`
properties — a real, well-known Spring Kafka testing race (the listener container's own initial
partition assignment is asynchronous and not guaranteed complete before the test method's first
`send()`; without `earliest`, a message published in that narrow window is silently never read,
not retried, causing an `Awaitility` timeout rather than a clear failure).

### Finding 3 (High) — ACCEPTED outright

`spring.task.scheduling.enabled=false` added. Confirmed this governs `@Scheduled` methods
(`OutboxRelay`, `InvoiceExpirySweepJob`) specifically, via a mechanism entirely separate from
`@KafkaListener` container lifecycle — does not affect the real listener wiring under test.
Cheap, zero-risk, removes incidental noise even though this task's own invoices (expiring far in
the future, under this task's own control) were never actually at risk from the sweep job
specifically.

### Finding 4 (Medium) — ACCEPTED outright

`cleanUp()` widened to also truncate `payments.processed_events`, `payments.outbox`,
`payments.verification_records` — hygiene, not a correctness fix (unique per-test UUIDs already
prevent collision), but cheap and removes cross-test-method accumulation within the one shared
static container.

### Finding 5 (Medium) — ACCEPTED outright

Every event JSON payload is a raw string literal (mirrors `ChainEventConsumerTest`'s own
existing unit-test convention exactly, e.g. its own `FINALIZED_JSON` already writes
`"amount":"1000"` as a quoted string) — never a Jackson-serialized DTO, which would risk writing
`amount` as a JSON number and testing the wrong producer contract.

### Finding 6 (Medium) — ACCEPTED, reclassified from an O6 concern to an R14 strengthening

Confirmed real: AC3 as originally scoped only proves the happy-path forward sequence, not an
actual cross-topic race. Kimi's own option (a) — publish genuinely out of order — is adopted,
but as a **third R14 scenario** (cross-topic staleness), not a separate O6 fix: `chain.tx.confirmed`
delivered for a payment that has *already* reached `FINALIZED` (realistic — `chain.tx.confirmed`
and `chain.tx.finalized` are separate topics with no cross-topic ordering guarantee, the exact
race `ReorgOutcome`'s own Javadoc already names) must leave the payment's state unchanged. AC3
itself is reworded per Kimi's own option (b): proves the real listener wiring correctly reaches
`FINALIZED` via the full forward sequence, not a claim about proving Kafka's own cross-topic
ordering guarantee (which is the broker's property, not this consumer's).

### Finding 7 (Medium) — REJECTED, with a correction to Kimi's own premise

Kimi's own concern assumes a Testcontainers Kafka image. This design uses the **real, already-
running local broker** (`services/auth/compose.local.yaml`), whose own config was directly
confirmed (not assumed) to already set `KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"`. The established
precedent this task adapts (`AuthEventConsumerIntegrationTest`) relies on the identical real
broker and does **not** pre-create topics via `AdminClient` either. Adding that machinery here
would diverge from the proven pattern for no real benefit against this specific, already-
confirmed environment.

### Finding 8 (Low) — ACCEPTED, documentation only

Decision 2 gains a note: the identical fixed-idempotency-key limitation (one key per
`{chain}:{txHash}:reorged`) already applies to `chain.tx.reorged` — a second genuine reorg of
the same transaction is contractually indistinguishable from a duplicate delivery, confirmed
already documented in `ChainTxReorged.java`'s own Phase 9 Finding 9. Not a new test gap.

### Finding 9 (Low) — ACCEPTED, documentation only

A note added: `CryptoClient` is mocked because T14's own scope is dedupe/ordering/staleness, not
crypto-service availability; `onChainTxSeen`'s own held-open-connection behavior across the
`unregisterWatch` call remains covered by existing unit tests and is out of scope here.

### Finding 10 (Low) — ACCEPTED outright

`webEnvironment = SpringBootTest.WebEnvironment.NONE` added explicitly, matching
`InvoiceRepositoryIntegrationTest`'s own existing convention.

### Finding 11 (Low) — ACCEPTED outright

Dedicated group id: `payment-chain-idempotency-it`.

## Final Scope

**In:** one new test class, `ChainEventConsumerIdempotencyAndOrderingIntegrationTest`, with the
amendments above folded in. Zero production code (unchanged from Phase 2).

**Out (unchanged):** any production class, migration, contract file, Kafka partition-assignment
testing itself, `AdminClient`-based topic pre-creation (Finding 7, rejected).

## Business Rules / Locked Decisions

R13, R14, O6, L5 — unchanged, now with Finding 6's own third R14 scenario folded in as the real
proof that cross-topic staleness (not just the happy path) is handled correctly.

## Files to Create (final)

- `services/payment/src/test/java/com/themistra/payment/consumer/ChainEventConsumerIdempotencyAndOrderingIntegrationTest.java`

## Acceptance Criteria (final)

- **AC1 (R13, named `shouldDedupeDuplicateChainEventsByEventKey`)** — unchanged from Phase 2.
- **AC2 (R14, named `shouldIgnoreOutOfOrderOrStaleChainEvents`)** — now **three** scenarios: (a)
  `chain.tx.finalized` for an unknown payment; (b) `chain.tx.reorged` for an already-`FINALIZED`
  payment; (c) `chain.tx.confirmed` for an already-`FINALIZED` payment (Finding 6's own addition
  — the realistic cross-topic race).
- **AC3 (O6, reworded per Finding 6)** — a real `chain.tx.seen` → `chain.tx.confirmed` →
  `chain.tx.finalized` sequence, same `watchId`, correctly reaches `FINALIZED` with the expected
  field values — proving the real listener wiring handles the full forward sequence end-to-end,
  not a claim about Kafka's own cross-topic ordering guarantee.
- **AC4 (test-infrastructure correctness, new)** — `auto-offset-reset=earliest`, scheduling
  disabled, full cleanup, raw-string JSON payloads, explicit `WebEnvironment.NONE`, dedicated
  group id — all confirmed present in the implementation (Phase 6/7's own job to verify).

## Open Questions

None remaining. Finding 7's rejection is a resolved correction, not a deferred question.

---

**This is the decision packet for the human-approval gate. Approval needed on:**
1. Finding 6's reclassification — folding the cross-topic staleness scenario into AC2 (R14)
   rather than treating it as a separate O6 fix, and rewording AC3 accordingly.
2. Finding 7's rejection — no `AdminClient` topic pre-creation, relying on the real broker's own
   already-confirmed auto-create config instead.
