# payment · T04 · Phase 10 — Test Generation

| | |
|---|---|
| **Service** | `payment-service` |
| **Task** | T04 — Outbox & EventTopics |
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | this artifact |

No production code changed in this phase.

## Test manifest

| Test | File | Verifies |
|---|---|---|
| `shouldRouteEachPublishedEventToItsPaymentsTopic` (parameterized, 4 cases) | `events/EventTopicsTest.java` | R30/AC3 — all four aggregate→topic mappings (`invoice`, `payment-seen`, `payment-finalized`, `receipt`), exactly as named in `package.md` §8 |
| `unmappedAggregateTypeFailsLoudRatherThanGuessing` | `events/EventTopicsTest.java` | AC3 negative case — unmapped type throws `IllegalStateException`, not a silent drop/guess |
| `blankAggregateTypeIsTreatedAsUnmapped` | `events/EventTopicsTest.java` | Boundary case: empty string is absent from the map, same `IllegalStateException` path |
| `nullAggregateTypeThrowsNullPointerException` | `events/EventTopicsTest.java` | Boundary case: `Map.of(...)` rejects a null key at lookup; documented as a caller bug, not a config error (matches `crypto-service`'s identical `EventTopics`) |
| `outboxRelayIntervalIsConfigured` | `common/ApplicationPropertiesContentTest.java` | AC6 / Phase 8 finding 2 — `themistra.payment.outbox.relay-interval-ms` stays present as a source-text guard, closing the gap Kimi's independent review found |

## Scope note

Both files above are the only two touched in this phase, per Phase 9's resolution (findings 1
and 2 routed here). `OutboxPublisher`, `OutboxRelay`, `OutboxEvent`, and `KafkaProducerConfig`
have no dedicated new tests in this task — their behavior is exercised indirectly (compile-time
wiring, Spring context boot) and the task's own named test scope (R30) is EventTopics routing
only. No integration/Testcontainers test was added for the outbox table itself — T02's own
`SchemaBaselineMigrationIntegrationTest` already covers the `outbox` table's schema shape, and
`OutboxEvent`'s mapping was verified by direct comparison against that migration in Phase 6/7/8,
not by a new Testcontainers round-trip test (not named in `package.md` §8 for this task).

## Verification performed this phase

- `mvn -pl services/payment -am test -Dtest=EventTopicsTest,ApplicationPropertiesContentTest` —
  15/15 passing (7 + 8).
- Mutation check 1: corrupted the `"receipt"` topic mapping in `EventTopics.java`, confirmed
  `EventTopicsTest` fails on exactly that case, reverted, confirmed clean.
- Mutation check 2: deleted the `relay-interval-ms` line from `application.properties`, confirmed
  `outboxRelayIntervalIsConfigured` fails, reverted, confirmed clean.
- Full non-Docker-dependent suite: `mvn -pl services/payment -am test
  -Dtest='!SchemaBaselineMigrationIntegrationTest'` — 59/59 passing.
  `SchemaBaselineMigrationIntegrationTest` (T02, Testcontainers) could not be run this phase —
  Docker is not currently running in this environment; this test is unrelated to T04's own
  changes and was last confirmed green in Phase 6 (68/68 full suite, Docker up).
