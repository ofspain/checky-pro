# open-issues sweep · Phase 7 — Self-Review

Consumes: the diff `8d8236da..57060d2f` and each commit's message. This artifact contains no code; it is documentation only. It stands in for Phases 6 and 7, because the sweep ran as direct fixes outside the pipeline.

## Author decisions the sweep follows

- **Process:** direct grouped fixes. Each fix has its own tests and deliberate breaks (a real line changed, the targeted tests run, the file restored from a backup), then its own commit and push.
- **Email links:** `/app/verify?token=`, `/app/reset?token=`, `/app/invoices/<uuid>`, `/app/receipts/<uuid>`.
- **Receipts:** merchant-only read, authorized by JWT and scoped like the invoice reads.
- **Spec edits:** dated notes only; the §4c verbatim blocks are unchanged. The payment, frontend and notification spec notes were approved by the author. The `agents.md` notes record the new FU-1/FU-2 rules.
- **Out of scope:** payer notification (FU-T07-1), the R26 on-chain anchor, infrastructure alerting and IAM.

## Fixes

| # | Commit | Service | Issue closed | Guarding tests | Deliberate breaks |
|---|---|---|---|---|---|
| 1 | `1b64b6d7` | crypto | Unregistering at SEEN stopped finality/reorg tracking; a restart dropped seen-but-not-final transactions | `WatcherTest`, `WatcherRegistryTest`, `WatchRepositoryIntegrationTest` (9 new) | 12, all caught |
| 2 | `af3c1132` | crypto | 11 long-failing tests; they hid that `QuorumEvaluator` was not a bean, so the service could not start | the 11 tests, now passing | 5, all caught |
| 3 | `1796f7e6`, `ee06a3e8` | crypto | Inert `@ArchTest`/`@AnalyzeClasses` annotations removed. `ee06a3e8` corrects `1796f7e6`'s message: the plain `@Test` canaries had always run | `KmsSignerArchitectureTest`, `CrossModuleEntityArchitectureTest` | each allowlist entry removed, all caught |
| 4 | `238614f1` | auth | 8 of 11 architecture rules had no canary, so they never ran | `ArchitectureTest` (14) | admin handler without `@PreAuthorize`, public repository |
| 5 | `3f05acb2` | auth | A role-denied admin call returned 500 | `AdminAccountAuthorizationIntegrationTest` (real HTTP; failed with 500 first) | handler removed, caught |
| 6 | `a4e3f1c0` | auth | 7 failing integration tests: the Kafka producer bypassed `@ServiceConnection`, and a shared `TestRestTemplate` leaked a request factory | the 7 tests | producer address, D-031 gate |
| 7 | `bb8fe26f` | notification | Every verify/reset email link was dead | `TemplateRendererTest`, `TemplateRendererIntegrationTest`, a frontend-spec path check | 3, all caught |
| 8 | `07f4347e` | payment | FU-T29-1: no merchant receipt read | `ReceiptControllerTest`, `ReceiptReadServiceTest`, `ReceiptRepositoryIntegrationTest`, `PaymentsOpenApiContractTest` | 11, all caught |
| 9 | `3ef39a09` | notification | FU-T07-2: the receipt email had no link (V15) | consumer unit + Kafka integration, migration tests | 5, all caught |
| 10 | `db341066` | notification | FU-T20-1: the in-app verify message stored the raw token (V16) | `SecretLeakEndToEndIntegrationTest`, `TemplateRendererIntegrationTest`, delivery log test | 3, all caught |
| 11 | `3a5d4007` | notification | S2: decimals learned late were never stored (V17, grant on one column) | `PaymentEventConsumerIntegrationTest`, grant tests | 6, incl. a broader grant and a merchant overwrite |
| 12 | `3c8666e3` | notification | FU-T07-4: a payment event that arrived before its route was dropped (V18 park and replay) | `PaymentEventConsumerTest`, `PaymentEventConsumerIntegrationTest`, grant tests | 8, all caught |
| 13 | `bfc1796e` | auth, crypto, notification | FU-T27-1: three Docker images failed to build | `DockerfileModulePomsTest` in each; all three images built from the repo root | 6, all caught |
| 14 | `61e20d5b` | payment, crypto, notification | FU-T27-2: hard-coded JWKS URI was the container's own loopback | properties test in each | 3, all caught |
| 15 | `30fcfe3f` | payment | A missing `tokenDecimals` bound silently to 0 | `InvoiceControllerTest`, `PaymentsOpenApiContractTest` | 2, both caught |
| 16 | `8639c223` | payment | FU-1 layering and FU-2 `@PreAuthorize` rules were not enforced | `ArchitectureTest` (27) with fixtures and a handler count | 2 in real code, both caught |
| 17 | `99c34a1f` | payment | Hibernate JSON audit (9 files): `VerificationRecord` could issue a spurious UPDATE | `VerificationRecordRepositoryIntegrationTest` (failed with "permission denied" first) | n/a, reproduced first |
| 18 | `2afdb1da` | payment spec | G2: the spec said every event is keyed by `invoiceUuid`; the code keys each by its aggregate | spec note only | n/a |
| 19 | `92a4df54` | notification spec | Stale notes after fixes 9–12 | `T01SkeletonRegressionTest` spec guards | 3, all caught |
| 20 | `ef3edda7` | notification | A parked event whose route never arrives stayed parked forever | `ParkedEventExpiryJobTest`, `PaymentEventConsumerIntegrationTest.expiryRemovesOnlyEventsParkedBeforeTheCutoff` | 5, all caught |
| 21 | `8e9150c7`, `f009df6c`, `57060d2f` | auth, crypto, notification | FU-1/FU-2 ported (details below) | `ControllerRulesArchitectureTest` in each; `MethodSecurityTest` in crypto and notification | 13, all caught |

### Fix 21 in detail (the FU-1/FU-2 port)

- **Every handler carries `@PreAuthorize`:** 34 in auth, 4 in crypto, 2 in notification.
  - Public handlers say `permitAll()`.
  - Self-service handlers say `isAuthenticated()`.
  - Crypto's `/internal/v1` handlers require `ResourceServerConfig.INTERNAL_SCOPE_AUTHORITY`, now public.
- **The public set is tied to `PublicEndpoints`:**
  - Auth: the `permitAll()` set must equal `METHOD_SCOPED` exactly.
  - Crypto: it must equal the non-actuator `PATTERNS`, which is only `/.well-known/themistra-verification-keys`.
  - Notification: it must be empty.
- **Method security in crypto and notification:** `@EnableMethodSecurity` is now on in both. An `AuthorizationDeniedException` maps to a detail-free 403 problem. Before, the catch-all handlers would have made it a 500.
- **The layering rule found one violation:** notification's `InappReadController` used the repository directly. It now goes through `InappNotificationReader`.
- **One existing test changed:** `InAppChannelIntegrationTest` calls the controllers directly. Method security refused those calls, so the test now signs the caller in with an authenticated `JwtAuthenticationToken`, as the filter does.
- **Fixtures** live in a top-level `archtestfixtures` test package, outside component scan.
- **The 13 deliberate breaks were:**
  - one handler unguarded in each service
  - an authenticated handler switched to `permitAll()`
  - a public handler that lost its `permitAll()`
  - method security turned off
  - the 403 mapping removed
  - a crypto handler weakened to `isAuthenticated()`
  - a controller given a repository field

## Suite state at `57060d2f`

| Service | Result |
|---|---|
| auth | 778/779. The one error was `AuditTrailIntegrationTest` timing out on its Kafka wait. It is unrelated to the port and passed when re-run alone (see S8). |
| crypto | 788/788 |
| notification | 451/451 |
| payment | 564/564 |

## Self-found weaknesses

### S1 — Low. The public-handler check matches the text `permitAll`

`ControllerRulesArchitectureTest.permitAllHandlers` treats a handler as public only if its expression contains `permitAll`. An always-true expression such as `@PreAuthorize("true")` would open an endpoint without being counted. The URL rules still require authentication for anything outside `PublicEndpoints`, so a real opening also needs a security-config change, and that change is guarded by `PublicEndpointsTest`.

### S2 — Info. In crypto and notification the method rules repeat the URL rules

Over HTTP the URL rule always refuses first, so a real handler's method-level denial cannot be reached that way. `MethodSecurityTest` proves it two ways instead:
- a direct call on the secured bean is refused
- a test-only `denyAll()` endpoint returns the 403 problem

### S3 — Low. Crypto's allowlist check assumes GET

`allowlistedPublicHandlers` maps each non-actuator `PATTERNS` entry to `GET <path>`. A future public POST would fail the check and need the test changed. That is safe, but awkward.

### S4 — Info. FU-1 is a proxy for the layering rule

No module has `api`/`application`/`domain` packages. The rule enforces the same direction in the shape the code has: controller → service → persistence, and nothing depends upward on a controller. The `agents.md` notes say exactly this.

### S5 — Low. Fixture placement differs between services

Payment's fixtures sit in `com.themistra.payment.archtestfixtures` behind a never-active `@Profile`. The other three services use a top-level `archtestfixtures` package. Both keep the fixtures out of Spring contexts.

### S6 — Low, pre-existing. One notification test controller is not profile-gated

`ResourceServerTestController` sits in `com.themistra.notification.common` with no profile, so it is scanned into full-application test contexts as `GET /v1/notifications/unread`. The sweep did not change it. Crypto's equivalent was gated in fix 2.

### S7 — Info. Parked-event expiry discards the event

An expired event is gone for good. It is visible only as a WARN log line (event key only) and the counter `themistra.notification.parked_events.expired`. Alerting on that counter is infrastructure-owned and out of scope.

### S8 — Low, pre-existing. One auth audit test is flaky

`AuditTrailIntegrationTest.recordMirrorsToKafkaWithoutLeakingIpOrUserAgent` timed out once on its 15-second Kafka wait in the full run, and passed alone.

### S9 — Cosmetic. Auth's `ArchitectureTest` still has `@AnalyzeClasses`

Surefire therefore prints a second "Tests run: 0" line for it. The 14 canaries do run.

### S10 — Info. One commit message is wrong

`1796f7e6` says the crypto rules never ran. `ee06a3e8` corrects this, and the history is not rewritten.

### S11 — Info. No startup smoke test outside the integration tests

Fix 2 shows that crypto could not start anywhere before the sweep. The only full-context start checks are the `@SpringBootTest` integration tests. No container-level smoke run is in the build.

## Scope check

- **Production code:** only what each fix's commit lists.
- **Specs:** dated notes only.
- **Secrets:** none added.
- **Logs:** the new log lines carry event keys and counts only, never payloads, tokens or emails.
