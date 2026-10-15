# payment · T15 · Phase 0 — Repository Understanding

Artifact: `.ai/prompts/payment/T15/artifacts/00-repository-understanding.md`

## 1. Architecture summary

`payment-service` (`services/payment`, package `com.themistra.payment`) is package-by-feature:
`invoice`, `payment`, `verification`, `consumer`, `events` (cross-cutting outbox infra), and
`common` (shared plumbing — config, security, the outbound Crypto Service client). Persistence
is Postgres via Flyway (`payments` schema, least-privilege `payment_app` role, incremental
per-table grants). Every externally-relevant state change is published through the `outbox`
table in the same transaction as the DB write, relayed to Kafka by `OutboxRelay`. Security is
zero-trust: every non-public endpoint validates a JWT as an OAuth2 resource server against
auth-service's JWKS; this service also makes its own outbound, authenticated service-to-service
calls to the Crypto Service using a `client_credentials` token it fetches itself
(`CryptoServiceTokenProvider`).

The full state machine (`CREATED → WATCHING → SEEN → CONFIRMING → FINALIZED → ATTESTED`, with
`CONFIRMING→SEEN`/`SEEN→WATCHING` reorg reversals) is implemented (T09/T11/T12) and consumed by
`ChainEventConsumer`. T13 added amount-discrepancy/poisoning-flag persistence and `Invoice.state`
outcome marking at `FINALIZED`. T14 added the first real Postgres+Kafka integration test. This
task (T15) is the first task in the "Attestation, receipts, and ledger" group (tasks 15–21) —
nothing in that group has been built yet.

## 2. Existing code this task touches

**Already exists (built at T06/T08, ahead of this task's own formal "add CryptoClient" text):**
- `common/CryptoClient.java` — `registerWatch(...)` (T06) and `unregisterWatch(UUID watchId)`
  (T08) are both already implemented, tested, and in production use (called from
  `InvoiceController` and `InvoiceExpirySweepJob`/`ChainEventConsumer` respectively). **T15 does
  NOT need to build watch registration from scratch** — it already exists.
- `common/CryptoServiceTokenProvider.java` — thread-safe single-flight `client_credentials` token
  cache, already used by every existing `CryptoClient` call.
- `common/CryptoServiceUnavailableException.java` — the existing catch-all for
  network/5xx/malformed-response failures on both existing methods.
- `common/config/CryptoClientProperties.java` — already has `attestPath` as a validated,
  `@NotBlank` field (added at T03, unused until now), alongside `baseUrl`/`watchPath`/timeouts/
  `auth`.
- `common/crypto/RegisterWatchRequest.java` / `RegisterWatchResponse.java` — the existing
  watch-registration DTOs.
- `services/payment/src/test/java/.../common/CryptoClientTest.java` — the established test
  pattern: a real local JDK `com.sun.net.httpserver.HttpServer` (127.0.0.1-only), **not**
  `MockRestServiceServer` (documented gotcha: `CryptoClient`'s constructor calls
  `.requestFactory(...)` on the builder after binding, which would silently defeat a
  `MockRestServiceServer`-bound factory and fall through to a real network call). Mirrors
  `auth-service`'s own `BreachCheckClientTest`.
- `ArchitectureTest.java` — this service's only existing architecture test file: a plain
  `private static final ArchRule` + `@Test` canary shape (no `@ArchTest` field — this repo's
  Surefire 3.5.3 silently reports 0 tests for any class containing one, confirmed empirically and
  already the subject of a separate, logged, cross-service issue). Currently has exactly two
  rules: `shouldRejectFloatingPointMoneyTypes` (L6) and `shouldPreventCrossModuleEntityImports`
  (L1/L12, entity-only — does **not** restrict plain service/component classes like `CryptoClient`
  at all). **No existing rule bans any import at the SDK/package level** — the L3 ArchUnit ban
  this task's own text calls for is wholly new.

**New in this task:**
- A new `attest(...)` method on `CryptoClient` (or a relocated client — see Open Question 1
  below), covering `POST /internal/v1/attest`.
- New request/response DTOs for the attest call.
- A new ArchUnit rule banning `org.web3j..`, `org.tron..`, and
  `software.amazon.awssdk.services.kms..` imports anywhere in `com.themistra.payment` (L3).
- Likely a new, narrower exception type to let a caller (T17's `ReceiptService`, not built yet)
  distinguish the `409` "gate not satisfied" business outcome from a true service-unavailable
  failure — see Open Question 2.

**Confirmed NOT present anywhere in `services/payment`'s own `pom.xml`:** no `web3j`, no `trident`
(Tron), no AWS KMS SDK dependency — L3 has been informally honored since T01's own pom (its
comments explicitly call this out). This task formalizes that as an enforced ArchUnit rule rather
than an unenforced convention.

## 3. Established patterns to follow

- **RestClient + request interceptor.** `CryptoClient`'s constructor builds one `RestClient`
  with a `SimpleClientHttpRequestFactory` (explicit connect/read timeouts from
  `CryptoClientProperties`) and a `requestInterceptor` that calls
  `tokenProvider.getAccessToken()` and sets the bearer header — shared by every method on the
  client, so a new `attest` method gets authentication for free.
- **Error mapping.** `RestClientException` → `CryptoServiceUnavailableException` (unchecked,
  wraps the cause). A response that parses successfully but carries an unexpected/invalid
  business shape (e.g. `registerWatch`'s own non-`REGISTERED` status, or a null `watchId`) is
  *also* mapped to `CryptoServiceUnavailableException` today — there is no existing precedent for
  a *second*, business-outcome-specific exception type, which is directly relevant to how
  `attest`'s own `BLOCKED`/`409 REFUSED` outcomes should be modeled (Open Question 2).
  `unregisterWatch` treats `404`/`410` as success (idempotent-retry safety), not as an error at
  all — a precedent for "a non-2xx HTTP status is not automatically an error," relevant to attest's
  `409`.
- **Config.** A single `@ConfigurationProperties(prefix = "themistra.payment.crypto")` validated
  record (`CryptoClientProperties`) backs the whole client; `attestPath` is already there.
- **Package placement.** `common` is this service's explicit "shared plumbing used by more than
  one feature module" package per `agents.md` (`"Shared plumbing lives only in common — no
  core/util dumping ground"`). `CryptoClient` is called today from `invoice` (`InvoiceController`)
  and `consumer`/sweep-job code — genuinely cross-feature already, independent of this task.

## 4. Testing conventions

- **Unit only, no Spring context**, for `CryptoClient` itself — `CryptoClientTest` constructs it
  directly with a mocked `CryptoServiceTokenProvider` and a real local `HttpServer`. No
  Testcontainers needed for this task (no DB/Kafka involvement at all).
- **ArchUnit**: plain `ArchRule` field (not `@ArchTest`) + a `@Test` canary that calls
  `rule.check(analyzedClasses)` directly, mirroring the two existing rules exactly. Every existing
  rule in this file also has a deliberate "rogue" fixture class proving the rule can actually fail
  (`RogueFloatingPointMoneyUser`, `RogueInvoiceEntityReferencer`, `RogueUnmappedEntity`, all under
  `archtestfixtures` or inside a real feature module) — the new L3 rule will need an equivalent
  rogue fixture that genuinely imports one of the banned SDK packages (none exists in this
  codebase's own real source to exercise against, since the ban has always held informally).
- Fixed `Clock` is not relevant to this task — `CryptoClient` has no time-dependent logic of its
  own (unlike `registerWatch`'s `expiresAt` parameter, which is caller-supplied, not
  self-generated).

## 5. Known gaps / unknowns

**Open Question 1 — module placement.** `design.md`'s own module-layout diagram (§4c) places
`CryptoClient.java` under a new top-level `attestation/` package, with its own
`AttestRequest`/`AttestResponse`/`WatchRequest`/`WatchResponse` DTOs — different names from the
already-shipped `common.crypto.RegisterWatchRequest`/`RegisterWatchResponse`. No earlier task's
own artifacts (checked T06's Phase 0–13 directly) flagged this placement as something to revisit
at T15; `CryptoClient` was simply built in `common` out of necessity at T06, before an
`attestation` module had any reason to exist yet. I do not know whether the team intends T15 to
relocate the existing, already-tested, multi-caller `CryptoClient` into a new `attestation`
package (which would require `invoice` and `consumer` to depend on that new module — not banned
by the existing entity-only cross-module ArchUnit rule, but in tension with `agents.md`'s own
"shared plumbing lives in `common`" framing for a class with callers in three different feature
modules) or to keep the established `common` placement and treat the design diagram as
describing the DTOs' logical ownership rather than a literal required package. Flagging for
Phase 1/2 recommendation, not deciding here.

**Open Question 2 — attest's three-way outcome shape has no existing client-side precedent.**
Confirmed directly against the real, built `crypto-service` (`AttestationService.java`,
`AttestExceptionHandler.java`, `AttestResponse.java`) and `contracts/api/crypto-internal.yaml`:
`POST /attest` has three outcomes, not two — `200 SIGNED` (success), `200 BLOCKED` (a legitimate,
expected business outcome: a sanctions/compliance hit — `requirements.md` R20, owned by T17, maps
this to `HELD`), and `409 problem+json REFUSED` (a *different* business outcome: the signing gate
itself isn't satisfied yet — quorum/finality unmet, or a screening `ERROR` — which is not named
anywhere in `requirements.md` at all). `registerWatch`/`unregisterWatch` only ever have a
clean success/failure shape (mapped entirely into one `CryptoServiceUnavailableException`); there
is no existing precedent for a client method returning one of several named business outcomes, or
for a dedicated exception distinct from "the service is unavailable." I do not know whether
`REFUSED` should be retried by a caller, surfaced to `HELD` like `BLOCKED` is, or treated as a
hard failure — `requirements.md` is silent on it. Flagging for Phase 1/2; this task's own scope
(`tasks.md` task 15) is only the client method itself, not the caller's handling (T17), but the
client's own method signature/exception design needs to anticipate this distinction now, since
retrofitting it later would be a breaking change to every caller.

**Known, not a gap:** `ReceiptDigest` (T16) does not exist yet. `attest`'s own client method
therefore takes raw `receiptDigestSha256`/`chain`/`txHash` primitives as parameters (mirroring
`registerWatch`'s own plain-primitives style), not an object from a not-yet-built class.
