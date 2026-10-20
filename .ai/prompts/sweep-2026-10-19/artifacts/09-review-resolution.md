# open-issues sweep · Phase 9 — Review Resolution

Consumes:
- `artifacts/08-independent-review.md` (Kimi, `f7228e88`)
- `artifacts/07-self-review.md`

I checked every finding against the source at `57060d2f` before deciding. Kimi's evidence column cites the self-review rather than file:line, and 7 of the 10 findings restate self-review items (S1, S3, S4, S6, S7, S8, S11). Checking the source turned up two things the review did not:
- **Finding 6:** the flaky test has a real bug that explains the flake.
- **Finding 4:** the gap is narrower than reported and confined to one service.

## Decisions

| Finding | Decision | Change |
|---|---|---|
| F1 — no end-to-end proof that the unregister fix works with payment | **Accept, crypto's half** | New `EndToEndIntegrationTest.anUnregisteredWatchStillEmitsFinalizedForItsSeenTransactionButMatchesNothingNew` (details below) |
| F2 — public detection trusts the substring `permitAll` (= S1) | **Accept, all four services** | Every handler's expression must be in a per-service `KNOWN_ACCESS_EXPRESSIONS` set, and `permitAll()` is now matched exactly. Fixture `RogueAlwaysTrueController` (`@PreAuthorize("true")`) in each service. |
| F3 — crypto's allowlist assumes GET (= S3) | **Accept** | `PATTERNS` are public for any method, so crypto and notification now compare public *paths*. Auth keeps method plus path, because `METHOD_SCOPED` is method-specific. |
| F4 — no startup smoke test (= S11) | **Accept for crypto only** | New `ApplicationStartupIntegrationTest` in crypto (details below) |
| F5 — notification test controller not profile-gated (= S6) | **Accept** | `ResourceServerTestController` now loads only under `resource-server-test-controller`; its two `@WebMvcTest` users activate that profile. |
| F6 — auth `AuditTrailIntegrationTest` is flaky (= S8) | **Accept: root cause fixed** | Details below |
| F7 — expired parked events leave no durable record (= S7) | **Deferred to the author** | No change; see below |
| F8 — FU-1 is a proxy (= S4) | **Accept: comment** | Each `ControllerRulesArchitectureTest` Javadoc says why the rule keys on `@RestController` and persistence types, not package names, and to add, not replace, a package rule if layered packages appear. (The review says the rule uses "package depth"; it does not.) |
| F9 — shared library controllers might be missed | **Reject: verified** | `grep -rln "@RestController\|@Controller\|@RequestMapping" libs` finds nothing, so `libs/` has no controllers. |
| F10 — receipt read not proven beyond unit tests | **Accept, corrected expectation** | Details below |

### F1 — crypto's half of the unregister flow, end to end

The new test drives a real `WatcherRegistry` over the real watch and cursor tables, with real Postgres and Kafka. It registers over HTTP, sees a transaction, then unregisters over HTTP exactly as payment does at SEEN. It then requires two things:
- `chain.tx.finalized` still arrives on the real topic;
- a second transaction to the same address is *not* seen.

Two parts of the setup are not real:
- **Chain providers** are fakes, as in every crypto end-to-end test.
- **Registry inputs:** the registry's results are narrowed to this test's own watch. The earlier flows in the class leave registered watches on the same address, and their watchers raced this one. Quorum decisions are made once per transaction, so the test failed in the full class run.

**What remains:** payment's half is already proven against a crypto stand-in that sends `chain.tx.finalized` after the unregister (`PaymentEndToEndIntegrationTest`). A test that boots both services together is not built. It needs a cross-service harness, which is an infrastructure decision.

### F4 — a full-context startup test for crypto

Checking the source:
- **Auth:** has `AuthServiceApplicationTests`.
- **Notification:** has 14 full-application tests with no mocked beans.
- **Payment:** its end-to-end test mocks only Spring Boot's own `JwtDecoder`.
- **Crypto:** its only full-application test, `EndToEndIntegrationTest`, mocks five of its own beans (`WatcherRegistry`, `ObservationSnapshotStore`, `KmsSigner`, `ScreeningClient`, `HeldFactAlerter`). A wiring fault in any of them would pass the suite.

The new `ApplicationStartupIntegrationTest` starts crypto with no beans replaced and requires each of those five, plus `QuorumEvaluator`, to be one real bean. Its first run failed: `KmsSigner` refuses to start without an AWS region. That refusal is deliberate and pinned by `KmsSignerSpringWiringTest`, so the test sets `aws.region` and clears it afterwards, as the wiring test does.

### F6 — the flaky audit test's root cause

`registerAndActivate` writes an `account.activated` audit event with the same Kafka key as the `account.suspended` event under test. The old wait loop asserted the event type of *every* record with that key. When both records arrived in one poll batch, the assertion threw, Awaitility retried, the batch had already been consumed, and the suspended record was never seen again. The result was a timeout that depended on batching.

The loop now keeps every record across polls, picks out the suspended one by key and event type, and checks that none of the account's records carries the IP or user agent.

### F7 — left to the author

The spec already records the 168-hour expiry, the WARN log per event key, and the metric (`package.md` Q7, dated 2026-10-20).

A durable record would mean a schema change to a dispute-grade table. `delivery_log` allows a null account and recipient, but its outcome check (`SENT`/`FAILED`/`SUPPRESSED`/`DEAD_LETTERED`) has no expiry value, and every row names a channel. An event whose route never arrived reached no channel and no recipient, so a row would need a new outcome value and a placeholder channel.

That is a design decision about the dispute-grade log, not a review fix, so it goes to the author.

### F10 — the receipt read over real HTTP, Postgres and S3

The review expected a 403 for another merchant. The spec requires the opposite: another merchant's receipt gets the same detail-free 404 as an unknown one (R28/L10), so a 403 would confirm the receipt exists.

`PaymentEndToEndIntegrationTest.shouldDriveInvoiceToAttestedReceiptEndToEnd` now ends by reading the issued receipt three times:

| Caller | Expected result |
|---|---|
| The owner | 200, with the receipt's UUID, merchant, transaction hash and a signature |
| Another merchant | 404 |
| Anyone, for an unknown UUID | 404 |

The two 404 problem bodies must be identical, except for `instance`, which echoes the request path.

## Deliberate breaks (8, all caught)

| Break | Caught by |
|---|---|
| Registry stops an unregistered watch (`keepTrackingOnly` removed) | new crypto end-to-end test |
| `stopMatching` keeps matching | new crypto end-to-end test (the second transaction is seen) |
| `HeldFactAlerter` not a bean | `ApplicationStartupIntegrationTest` |
| `@PreAuthorize("true")` on a real handler, in each of crypto, notification, auth and payment | `everyHandlerUsesAKnownAccessExpression` in each |
| Receipt ownership check skipped | `PaymentEndToEndIntegrationTest` (another merchant gets 200) |

**Not mutation-checked:**
- **F5's profile gate:** it is test hygiene, and no test fails without it.
- **F6:** it removes a timing-dependent failure, which a single deliberate break cannot reproduce on demand.

## Suite state after resolution

| Service | Result |
|---|---|
| auth | 781/781, including `AuditTrailIntegrationTest` |
| crypto | 792/792 |
| notification | 453/453 |
| payment | 566/566 |
