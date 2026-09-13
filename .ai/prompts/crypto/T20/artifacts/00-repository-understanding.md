# crypto · T20 · Phase 0 — Repository Understanding

## 1. Architecture summary

`services/crypto` is the Spring Boot 3.5.4 / Java 21 module implementing Themistra's non-custodial
crypto-payment verification and attestation platform, package-by-feature under `com.themistra.crypto`
(`adapter`, `provider`, `quorum`, `finality`, `observation`, `watch`, `reorg`, `token`, `screening`,
`events`, `common`). It owns the `chain` Postgres schema, migrated via immutable, additive-only Flyway
migrations (`V1`…`V9`). Every emitted domain fact goes through the transactional outbox
(`events.OutboxPublisher` → `chain.outbox` → `OutboxRelay` → Kafka). Internal endpoints require a
service-to-service JWT with `internal.crypto:write` scope (`common.ResourceServerConfig`); the
public-endpoint allowlist is `common.PublicEndpoints`. `common.ClockConfig` supplies the single
injectable `Clock` bean used everywhere.

This task's own concern — attestation signing — is the platform's single most security-critical path:
`package.md` frames the core threat as "an attacker makes Themistra attest to something false," and
`agents.md`'s service-specific rules state plainly that `kms:Sign` must be reachable **only** from the
`attest` module, enforced by both ArchUnit (application layer) and IAM (infrastructure layer, out of
this codebase's scope). The only key material in the entire platform is the attestation key, and it
never leaves AWS KMS.

## 2. Existing code this task touches

- **Already exists, pre-built ahead of this task (T03's own scope, not this task's to create):**
  `common.config.KmsProperties` — a validated `@ConfigurationProperties` record
  (`prefix = "themistra.crypto.kms"`) with exactly one field, `keyId` (`@NotBlank String`). Its own
  Javadoc states deliberately: "exactly one identifying field... KMS resolves region/account from it, so
  a separate region field would be redundant." `KmsPropertiesTest` (4 tests) already exists and passes,
  proving the binding/validation behavior. Neither file is this task's to modify — `KmsSigner` consumes
  `KmsProperties.keyId()`, nothing more.
- **Already exists, direct pattern precedent for this task's own AWS-SDK client wiring:**
  `observation.ObservationSnapshotStoreConfig` — builds a real `S3Client` bean
  (`Region.of(properties.region())`, a fixed `ClientOverrideConfiguration` API-call timeout, no
  credential set so the SDK's default credential chain applies). Its own Javadoc says explicitly: **"No
  AWS-SDK-client-wiring precedent exists anywhere else in this codebase yet (KMS's own real usage is
  still unbuilt, confined to the future attest module) — this class sets that precedent from a blank
  slate."** This is this task's direct, named forward-reference — a `KmsClient` bean should mirror this
  shape.
- **Already declared in `pom.xml`, never yet used:** `software.amazon.awssdk:kms` (comment: "ADR-0004...
  kms:Sign only, confined to the future attest module's KmsSigner") and
  `com.tngtech.archunit:archunit-junit5:1.3.0` (test scope) — confirmed via direct search that **zero**
  files in `services/crypto/src/test` import any `com.tngtech.archunit` class today. Every module-
  boundary test built so far (`WatchModuleBoundaryTest`, `ReorgModuleBoundaryTest`,
  `ScreeningModuleBoundaryTest`, ...) is a plain source-scan test, not ArchUnit. This task is the first
  to actually exercise the already-declared ArchUnit dependency.
- **Already exists (V2, T02), never yet consumed:** `chain.attestations` table, with `crypto_app`
  already granted `INSERT, SELECT` on it. Not this task's concern — `Attestation`/
  `AttestationRepository` (the entity mapping this table) is design.md's own `attest/` package-map entry
  listed *after* `KmsSigner.java`, i.e. task 21's job, not this one. `KmsSigner` itself persists nothing.
- **Does not exist yet, this task's own primary deliverable:** the `attest/` package itself —
  `KmsSigner.java` is the first file in it. No `AttestController`, `AttestationService`,
  `VerificationKeysController`, or `Attestation` entity exists yet (all task 21/22).
- **Precedent for a LocalStack-backed real-API integration test (not a fake/mocked one):**
  `observation.ObservationSnapshotStoreLocalStackIntegrationTest` — constructs a real AWS SDK client
  against a `LocalStackContainer` (Testcontainers), no Spring context, proving "S3 is core persistence
  infrastructure here... not an external RPC provider to fake." The same framing plausibly applies to
  KMS (also core infrastructure, not a fake-able external chain RPC provider) — to be confirmed at
  Phase 2/5, not assumed here.

## 3. Established patterns to follow

- **AWS SDK client bean wiring** (`ObservationSnapshotStoreConfig`): a small `@Configuration` class,
  one `@Bean` method building the SDK client from `Region.of(properties.region())` plus a fixed
  `ClientOverrideConfiguration` timeout, no explicit credentials (default chain), and a second `@Bean`
  wiring the actual component that uses the client. `KmsProperties` has no `region()` field of its
  own (only `keyId`) — how a `KmsClient`'s region is determined is therefore an open design question for
  Phase 2 (the SDK's own default region provider chain, an env var, or a new config field — not yet
  decided, not to be assumed).
- **Config records**: validated, `@NotBlank`/`@Min` constrained, immutable Java records under
  `common.config`, one file per concern, already-shipped and frozen once a prior task built them (T03
  for `KmsProperties`) — this task reads `KmsProperties`, does not modify it.
- **Package layout & boundaries** (`agents.md`): package-by-feature; ArchUnit enforces `api → application
  → domain` within a module and forbids cross-module entity imports — this task is the very first to
  actually author an ArchUnit rule rather than only relying on the plain-source-scan-test convention
  every prior module used.
- **Security**: internal endpoints require `internal.crypto:write` (not directly relevant to this task —
  no endpoint exists yet); secrets (KMS key id itself is not secret — it identifies, doesn't contain,
  key material) are never committed, injected via External Secrets Operator in real environments.

## 4. Testing conventions

- Plain JUnit unit tests with a fixed `Clock` for anything timestamped; no real network calls in unit
  tests.
- Testcontainers for real infrastructure: Postgres (this task needs none — `KmsSigner` persists
  nothing), and — per the `ObservationSnapshotStoreLocalStackIntegrationTest` precedent — `LocalStackContainer`
  for a real AWS-API round-trip test where the SDK itself is core infrastructure, not a fake-able
  external RPC provider.
- ArchUnit (`archunit-junit5:1.3.0`, already a test-scope dependency, never yet used) for this task's own
  named rule: no package outside `attest` may reference `KmsSigner` or the KMS SDK signing API. This is
  structurally different from every prior module-boundary test in this codebase (which restrict a
  module's own *outbound* imports); this rule restricts every *other* module's *inbound* reference to
  `attest`/the KMS signing surface — the first "who is allowed to depend on me" rule in this codebase,
  as opposed to "what am I allowed to depend on."
- `agents.md`: "real RPC providers are never called in tests or CI" — the established interpretation for
  S3 (LocalStack, not a fake) suggests the same likely applies to KMS, but this is a Phase 2 design
  decision, not yet settled here.

## 5. Known gaps / unknowns

- **Q7 (`package.md` §11) — KMS signing key spec is explicitly unanswered**: "Confirm the KMS key
  type/algorithm for attestation (e.g. ECDSA P-256 / secp256k1 / RSA) and the digest/signature encoding...
  Drives R20/R24." I do not know which algorithm this task should use. `KmsProperties` itself carries no
  algorithm field, only `keyId`. This may be a genuine blocker for a real `kms:Sign` call (the AWS KMS
  `Sign` API requires a `SigningAlgorithmSpec`), or the algorithm may be resolvable from the key's own
  metadata via `DescribeKey`/`GetPublicKey` rather than hardcoded — undetermined, not to be assumed.
- **Whether LocalStack's KMS emulation supports asymmetric `SIGN_VERIFY` key creation and the `Sign`
  operation** at the pinned `localstack/localstack:3.8` image version already used elsewhere in this
  service (`ObservationSnapshotStoreLocalStackIntegrationTest`) — I do not know this; it needs direct
  verification (attempting the real Testcontainers call), not assumption, before committing to a
  LocalStack-based integration-test design in Phase 5.
- **How a `KmsClient` bean resolves its AWS region**, given `KmsProperties` has no `region()` field
  (unlike `SnapshotProperties`, which does) — I do not know whether this task should add a region field,
  rely on the SDK's default region provider chain, or reuse an existing env-derived region. A Phase 2
  design decision.
- **Digest vs. raw-message signing**: R20 says "the receipt digest is sent to KMS for signing." AWS
  KMS's `Sign` API takes a `MessageType` of either `RAW` or `DIGEST`. I do not know which the frozen
  brief will specify without first checking whether `MessageType.DIGEST` is even compatible with the
  eventual signing algorithm (ECDSA specs typically expect `DIGEST` with a specific hash width) — a
  Phase 2 design decision, not a Phase 0 one.
- **Whether `attest/` will need any `AttestModuleBoundaryTest`-style companion test** in this task, or
  only the cross-cutting ArchUnit rule the task statement explicitly names — the task statement names
  only the ArchUnit rule; whether a same-package boundary test is also warranted (mirroring
  `ScreeningModuleBoundaryTest`'s own convention for what a *new* module itself may import) is a Phase 2
  scoping decision.
