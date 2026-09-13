# crypto · T22 · Phase 0 — Repository Understanding

## 1. Architecture summary

`services/crypto` is the Spring Boot 3.5.4 / Java 21 module implementing Themistra's crypto-payment
verification and attestation platform. T20 built `attest.KmsSigner` (the sole `kms:Sign` caller,
structurally enforced by `KmsSignerArchitectureTest`'s ArchUnit rule) and T21 built the full
`POST /internal/v1/attest` orchestration on top of it. This task is the third and final piece of the
`attest` package per `design.md`'s own package map: a public, unauthenticated endpoint publishing the
attestation key's public component so any third party can independently verify a Themistra receipt's
signature, without ever needing to call KMS themselves.

Internal endpoints require `internal.crypto:write` scope via `common.ResourceServerConfig`'s
`/internal/v1/**` path rule — **this endpoint is the opposite**: it must be public, unauthenticated, and
is already pre-provisioned in `common.PublicEndpoints.PATTERNS`
(`"/.well-known/themistra-verification-keys"`, with a comment explicitly naming this task, T22, as its
future implementer). No security configuration change is needed for this task.

## 2. Existing code this task touches

- **`common.PublicEndpoints`** — already lists `/.well-known/themistra-verification-keys` (confirmed
  read directly); no change needed.
- **`common.config.KmsProperties`** — the only existing config, `keyId` (a key id or alias/ARN). No
  `region` field (T20's own disposition: SDK default region provider chain). This task will read
  `keyId()` to know which key to query, mirroring `KmsSigner`'s own usage.
- **`attest.KmsSigner`** — the sole class in this codebase permitted to touch the KMS SDK at all,
  enforced by `KmsSignerArchitectureTest`'s two ArchUnit rules: (1) no class outside `attest` may
  reference `KmsSigner`; (2) no class anywhere in `com.themistra.crypto`, **except one named
  `KmsSigner*` and residing in `attest`**, may depend on any type under
  `software.amazon.awssdk.services.kms..` at all. **This second rule is broader than `kms:Sign`
  specifically** — it bans the whole KMS SDK package, including `GetPublicKey`, from every class not
  matching that name-plus-package exception. This is the single most important constraint this task
  must design around: a naive `VerificationKeysController`/`VerificationKeyService` calling
  `KmsClient.getPublicKey(...)` directly would fail this already-existing, negative-proof-tested rule.
  The two realistic resolutions (a Phase 2 design decision, not resolved here): (a) add a
  `getPublicKey()`-shaped method to `KmsSigner` itself, so all KMS SDK usage stays concentrated in that
  one class exactly as the rule already enforces; or (b) widen the rule's exception. Given L11's own
  actual wording is scoped to `kms:Sign` specifically ("`kms:Sign` on the attestation key is reachable
  only from the attest module"), and this codebase's whole design philosophy for `attest/` is
  concentration of KMS access, option (a) is the more consistent fit with existing precedent — but this
  is a design choice, not decided here.
- **`attest.SignatureResult`** — package-private record; not directly relevant to this task (it's
  `KmsSigner.sign`'s own return type), but its Javadoc's "only legitimate consumer" framing is the same
  pattern this task's own new response types will likely follow.
- **No existing entity/repository this task needs** — publishing public keys requires no persistence;
  `chain.attestations` (T21) is unrelated to this task's own scope.
- **New file, per `design.md`'s own package map**: `attest/VerificationKeysController.java` — the only
  file this task statement names explicitly. Whether a separate service class is warranted (mirroring
  `AttestationService`'s own separation from `AttestController`) or the controller calls `KmsSigner`
  directly is a Phase 2 design decision.

## 3. Established patterns to follow

- **Public endpoint precedent**: none exists yet in this codebase — every other endpoint
  (`/internal/v1/watches`, `/internal/v1/attest`) requires the internal scope. This is the first genuinely
  public, unauthenticated endpoint in `services/crypto`. `common.PublicEndpoints`'s own sweep test
  (referenced in its Javadoc: "A sweep test asserts no `permitAll` exists outside this list") is the
  existing guard this task's new endpoint must not violate — no new `permitAll()` call should be needed
  since the path is already listed.
- **Controller shape**: `WatchController`/`AttestController`'s established convention — a thin
  `@RestController`, no security annotations of its own, delegates to a service class. This task's own
  controller similarly needs no security annotation (the path is already public via the centralized
  config), but unlike the other two controllers, there is no mutation and likely no request body at all
  (`GET`, no path variable, no query parameter per `design.md`'s own wire shape).
- **DTO conventions**: hand-written records (no `contracts/api/crypto-internal.yaml` exists anywhere in
  this repo, confirmed absent by every prior task in this package). This task's response record(s) will
  follow the same convention.
- **KMS SDK usage precedent**: `KmsSigner`'s own `resolveKmsClient()` pattern (a `KmsClient` built with a
  fixed `ClientOverrideConfiguration` timeout, no explicit region/credentials, folded into the same class
  that uses it rather than a separate `@Configuration` — discovered necessary in T20 specifically because
  a separate config class would itself trip the ArchUnit rule). If this task extends `KmsSigner` itself,
  no new client-construction pattern is needed at all.

## 4. Testing conventions

- Plain JUnit unit tests, fixed `Clock` where timestamps matter (not obviously needed here — a public-key
  listing has no timestamp in `design.md`'s own wire shape).
- `KmsSignerLocalStackIntegrationTest` (T20) is the direct precedent for a real, LocalStack-backed
  integration test proving a real `GetPublicKey` round-trip — it already creates a real asymmetric
  `SIGN_VERIFY`/`ECC_NIST_P256` key and calls `GetPublicKey` on it to verify a signature, so the exact
  API call this task needs has already been exercised successfully once in this codebase, though not
  through production code.
- `KmsSignerArchitectureTest`'s existing rules must still pass with this task's new files present —
  regression check, not a new test, exactly as T21 handled it.
- Real KMS/AWS infrastructure is treated as real (LocalStack), never faked, per `ObservationSnapshotStore`/
  `KmsSigner`'s own established posture — consistent with `agents.md`'s "real RPC providers are never
  called in tests" applying to blockchain providers specifically, not core AWS infrastructure.

## 5. Known gaps / unknowns

- **How `kid` is derived is not specified anywhere in the spec.** `design.md`'s wire shape lists both
  `kid` and `kmsKeyId` as separate fields (`{ kid, kmsKeyId, alg, publicKeyPem }`), but no document
  defines what `kid` actually is or how it differs from `kmsKeyId` (a JWK "key ID" convention, an internal
  short alias, a key version string?). I do not know this; a Phase 2 design decision, not to be assumed.
- **Q7 (KMS key type/algorithm) is formally unanswered**, same as T20's own disclosed gap — but this
  task has a partial, verified answer available that T20 did not: `GetPublicKeyResponse` (confirmed via
  direct `javap` inspection of the actual SDK jar) carries the key's own `signingAlgorithms()` list
  directly from KMS — the real key's own metadata, not a value this service has to separately track or
  guess. The response's `alg` field can plausibly be populated from this authoritative source rather than
  from `KmsSigner`'s own `SIGNING_ALGORITHM` constant — a Phase 2 design decision.
- **PEM encoding of the public key is not directly supported by any existing code in this repository.**
  `GetPublicKeyResponse.publicKey()` returns the key's X.509 SubjectPublicKeyInfo DER bytes (confirmed:
  `KmsSignerLocalStackIntegrationTest` already successfully parses this exact format via
  `KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(...))`). Producing a PEM string
  (`-----BEGIN PUBLIC KEY-----` + base64 + `-----END PUBLIC KEY-----`) from those same DER bytes requires
  no new dependency (base64 + a fixed header/footer, no library not already in use), but no existing code
  in this service does this today — a small, new, self-contained piece of logic.
- **Whether this endpoint should cache the public key (fetched once, held for the app's lifetime) or
  call `GetPublicKey` on every request is not specified.** A public key never changes for a given
  `kmsKeyId` unless the key is rotated (an operational event, not a per-request concern), so caching is a
  plausible design choice, but not mandated by any requirement — a Phase 2 decision.
- **Multi-key support**: `design.md`'s wire shape names a `keys` array (plural), suggesting the response
  could in principle list more than one key (e.g., during a rotation window), but `KmsProperties` only
  ever configures one `keyId`. Whether this task should design for a single-element array or leave room
  for more is a Phase 2 scoping decision — the spec gives no signal either way beyond the plural wire
  shape itself.
