# open-issues sweep · Phase 11 — Test Review Resolution

Consumes:
- `artifacts/11-test-review.md` (Kimi, `c0a7a6f2`)
- `artifacts/10-test-generation.md`

I checked every gap against the source at `c0a7a6f2` before deciding.

The review says "there is no Phase 10 manifest for the sweep", but `10-test-generation.md` was committed in `2369197d`, before the review ran. The review therefore checks the self-review rather than the tests, and gives no file:line evidence. Seven of its 12 gaps are already covered by a committed test or were already decided in Phase 9. Four lead to changes, and one is deferred.

## Decisions

| Gap | Decision | Evidence or change |
|---|---|---|
| 1 — no test boots crypto and payment together | **Defer (known)** | Already disclosed in `09-review-resolution.md` F1 and in the manifest's "Not covered". Crypto's half is `EndToEndIntegrationTest.anUnregisteredWatchStillEmitsFinalizedForItsSeenTransactionButMatchesNothingNew`; payment's half runs against a crypto stand-in. A test that boots both needs a cross-service harness, which is an infrastructure decision. |
| 2 — no startup smoke test | **Reject: covered** | Crypto: `ApplicationStartupIntegrationTest` (F4) requires one real bean each of the six it lists. Auth: `AuthServiceApplicationTests`. Notification: 14 full-application tests with no mocked beans. Payment: its end-to-end test mocks only `JwtDecoder`. A context needs its database, so "no container dependencies" is not possible. |
| 3 — no end-to-end receipt ownership test | **Reject: covered, and the expectation is wrong** | F10 added it to `PaymentEndToEndIntegrationTest.shouldDriveInvoiceToAttestedReceiptEndToEnd`. Another merchant gets **404**, not 403 (R28/L10): a 403 would confirm that the receipt exists. |
| 4 — new email links not checked through Kafka | **Accept, invoice link only** | Verify and reset links are already checked from consumed `auth.email.requested` events (`SecretLeakEndToEndIntegrationTest:169,203`), and the receipt link from consumed payment events (`PaymentEventConsumerIntegrationTest:261`). The invoice link was checked only in `TemplateRendererTest`. `paymentLifecycleIsConsumedRoutedAndDispatchedToTheMerchantExactlyOnce` now requires the `invoice.created` email to end with `/app/invoices/<invoice>`, and every other message to carry no invoice link. |
| 5 — built images never started | **Accept: runtime smoke run** | Process evidence, as in payment T27 (details below). It found one deployment defect, which is not a test gap and needs an author decision. |
| 6 — FU-1/FU-2 rules miss `libs/` | **Reject: already decided** | `09-review-resolution.md` F9: `libs/java` contains no Java source, so there is nothing to scan. |
| 7 — expiry metric not checked | **Reject: covered** | `ParkedEventExpiryJobTest:82` requires the counter at 2.0 after two expiries. Lines 91 and 106 require zero when nothing expires and when the run rolls back. |
| 8 — G2 event-key note not pinned | **Reject: the code side is pinned** | `PaymentEndToEndIntegrationTest` finds each outbound record by its key: the invoice UUID (`:396`), the payment UUID (`:415`, `:513`) and the receipt UUID (`:524`). A record keyed any other way times out. The note itself is a dated spec note; changing it is a deliberate edit. |
| 9 — JWKS fix might regress elsewhere | **Accept, broadened** | The old tests pinned only `jwk-set-uri`. A new test, `everyLoopbackValueIsOverridableByEnvironment`, fails on any `localhost`/`127.0.0.1`/`[::1]` value that is not a `${VAR:default}` placeholder. It runs in all four services (auth: new `ApplicationPropertiesLoopbackTest`). Each test first requires a known loopback value to be seen, so it cannot pass by seeing nothing. One exemption, in crypto: the chain provider URLs are local fake placeholders (package.md Q1). The exemption holds only while the provider's name starts with `fake-`. |
| 10 — notification test controller not gated | **Reject: done** | Gated by profile in F5 (`8d5f86bc`). It is under `src/test/java` and never ships. |
| 11 — flaky auth audit test | **Reject: root cause fixed** | F6 (`8d5f86bc`): records are buffered across polls, so a batch holding both events can no longer be lost. |
| 12 — `QuorumEvaluator` not checked as one bean | **Reject: covered** | `ApplicationStartupIntegrationTest` requires exactly one real `QuorumEvaluator`. `Watcher` is not a bean; `WatcherRegistry` builds each one with the evaluator. |

## Deliberate breaks (6, all caught)

| Break | Caught by |
|---|---|
| L1 payment Kafka bootstrap hard-coded to `localhost:9094` | payment `everyLoopbackValueIsOverridableByEnvironment` |
| L2 notification `issuer-uri` hard-coded to `http://localhost:8080` | notification `everyLoopbackValueIsOverridableByEnvironment` |
| L3 crypto datasource URL hard-coded | crypto `everyLoopbackValueIsOverridableByEnvironment` |
| L4 a crypto provider on loopback renamed so it is no longer `fake-` | crypto `everyLoopbackValueIsOverridableByEnvironment` |
| L5 auth SPA redirect URI hard-coded | auth `ApplicationPropertiesLoopbackTest` |
| L6 `TemplateRenderer.INVOICE_PATH` changed to `/app/invoice/` | `PaymentEventConsumerIntegrationTest.paymentLifecycleIsConsumedRoutedAndDispatchedToTheMerchantExactlyOnce` |

Each break changed one line, ran the named test and restored the file from a backup. All restores were verified.

## Gap 5 — the runtime smoke run

The three images were rebuilt from the current code and started read-only:
- **Setup:** a throwaway network with `postgres:16-alpine` and `apache/kafka:3.8.0` (KRaft); crypto and notification migrated with `flyway:migrate`; their app roles given passwords; notification's six topics created.
- **Run:** each image with `--read-only --tmpfs /tmp`, and the database, Kafka, JWKS and issuer addresses supplied by environment variable. Crypto also got `AWS_REGION`.

| Image | Result |
|---|---|
| auth | `running=true readonly=true user=nonroot`. `Started AuthServiceApplication`, `/actuator/health` → `UP`. No ERROR lines. Expected local-dev WARNs only: no MFA KEK ARN, an ephemeral JWT signing key. |
| crypto | `running=true readonly=true user=nonroot`. `Started CryptoServiceApplication`, health `UP`. No ERROR or WARN lines. |
| notification | `running=true readonly=true user=nonroot`. `Started NotificationServiceApplication`, partitions assigned for all six topics, health `UP`. No ERROR or WARN lines. |

**Teardown:** every `sw-*` container, the network and the three smoke images were removed. `auth-postgres-1` and `auth-kafka-1` were untouched.

### Found by the run: notification cannot migrate a fresh shared database — needs an author decision

On the first attempt notification's V1 failed with `type "citext" does not exist`:
1. Auth's V1 runs `CREATE EXTENSION IF NOT EXISTS citext` with Flyway's default schema `auth`, so the extension lands in `auth`.
2. Notification's V1 sets `search_path TO notifications`, so it cannot see the type.
3. Postgres allows one copy of an extension per database, so notification cannot install its own.

The run went ahead with the manual step notification T02 used on the local database: `ALTER EXTENSION citext SET SCHEMA notifications`. Auth's citext column still resolves afterwards.

This is not a sweep regression. It was found during notification T02 and fixed by hand on the local database (`.ai/prompts/notification/T02/artifacts/06-implementation-notes.md` step 5). It was never fixed for deployment, so every fresh shared database needs that manual step, and auth's own V1 would most likely fail the same way if notification migrated first (not tried).

Both V1 files are immutable, so a fix means one of:
- a database per service;
- a pre-migration step owned by the deploy;
- a new migration on one side that changes how the type is referenced.

That is an author decision, so it is not fixed here.

**Author decision (2026-10-20): run each service in its own database, and add a notification-side callback (part 1, built).**
- **The callback:** `services/notification/src/main/resources/db/migration/beforeMigrate.sql` installs `citext` into `notifications` before V1. If `citext` already lives in another schema, it stops with a message saying notification needs its own database. V1 is unchanged.
- **Tests:** the 14 integration tests that installed `citext` by hand no longer do, so every one of them now depends on the callback. `NotificationBaselineMigrationIntegrationTest` adds two tests: the callback installs `citext` into `notifications`; and a database where `citext` lives in `auth` is refused before any versioned migration runs.
- **Plugin check:** `mvn flyway:migrate` against a fresh database migrated to V19, with `citext` in `notifications`. This is the path deployments use.
- **Deliberate breaks (3, all caught):**
  - the install removed
  - the schema check disabled
  - `citext` installed into `public`
- **Spec:** a dated note in `spec/notification-service/design.md` after the V1 block. The §4c block is unchanged.
- **Part 2,** a database per service in the deployment, is infrastructure work.

## Suite state after resolution

`mvn -o -pl services/<service> verify`, every one BUILD SUCCESS. Counts are test cases in the Surefire reports. They run a little above the earlier "Tests run" totals, because those undercount nested test classes.

| Service | Test cases | Failures |
|---|---|---|
| auth | 782 | 0 |
| crypto | 794 | 0 |
| notification | 458 | 0 |
| payment | 571 | 0 |
