# payment · T15 · Phase 6 — Implementation Notes

Consumes: `artifacts/05-implementation-plan.md`

## What changed

**New files:**
- `common/crypto/AttestationResult.java` — sealed interface (`Signed`/`Blocked`), Phase 3
  Finding 1.
- `common/crypto/AttestRequest.java` / `AttestResponse.java` — wire DTOs, mirror the real
  crypto-service's own shapes exactly.
- `common/AttestationRefusedException.java` — carries the 409 body text best-effort (Phase 3
  Finding 3), with a Javadoc correction disclosing that the real crypto-service's own message is
  always a fixed, generic string (verified against source, not Kimi's stated "quorum/finality/
  screening" rationale — see Phase 4's own disposition).
- `test/java/org/web3j/StubWeb3jClass.java`, `test/java/org/tron/StubTronClass.java`,
  `test/java/software/amazon/awssdk/services/kms/StubKmsClass.java` — empty marker classes so a
  real `import` statement against each banned package compiles without adding the real SDKs
  (Phase 3 Finding 2, verified `pom.xml` has none of them).
- `test/java/.../archtestfixtures/RogueKmsAndChainClientUser.java` — negative-proof fixture,
  mirrors `RogueFloatingPointMoneyUser`'s own placement/visibility exactly.

**Modified:**
- `common/CryptoClient.java` — new `attest(String receiptDigestSha256, String chain, String
  txHash)` method; new `attestPath` field read from the already-existing
  `CryptoClientProperties.attestPath()`; new class-level Javadoc paragraph recording the
  module-placement decision (Phase 3 Finding 5 — applied as a code comment only, NOT a
  `package.md` edit, since every phase's own guardrail forbids modifying files under `spec/`).
- `ArchitectureTest.java` — new `shouldBanKmsAndChainClientImports` rule (L3), using the built-in
  `noClasses().should().dependOnClassesThat().resideInAnyPackage(...)` DSL (no custom
  `ArchCondition` needed, unlike the two existing rules) + its paired canary/negative-proof
  tests.

**Deferred to Phase 10 (test-only task exception does not apply here — T15 is a real
implementation task):** the 9 new `CryptoClientTest` methods covering `attest`'s own business
behavior (signed/blocked/refused/bad-request/server-error/connection-failure/timeout/malformed-
response), per the standard "write tests in Phase 10" rule. The ArchUnit rule's own canary +
negative-proof tests were written now, in Phase 6, not deferred — they are the L3 enforcement
mechanism itself (the actual substance of this task's own "add the ArchUnit ban here" text), the
same precedent T05/T09 established for their own L6/L1-L12 rules.

## Mapping to the frozen brief / acceptance criteria

- **AC1/AC2** — `attest`'s `200` branch maps `outcome` (`"SIGNED"`/`"BLOCKED"`, case-insensitive,
  mirroring `registerWatch`'s own `equalsIgnoreCase` precedent for `REGISTERED_STATUS`) to
  `AttestationResult.Signed`/`Blocked`. Neither is thrown as an exception.
- **AC3** — `HttpClientErrorException.Conflict` (409) is caught specifically, before the generic
  `RestClientException` catch, and mapped to `AttestationRefusedException` carrying
  `e.getResponseBodyAsString()`.
- **AC4** — every other `RestClientException` (which covers 400, every 5xx, connection failures,
  and read timeouts — Spring's `RestClient` throws `HttpClientErrorException`/
  `HttpServerErrorException`/`ResourceAccessException`, all `RestClientException` subtypes) maps
  to the existing `CryptoServiceUnavailableException`, unchanged. A successfully-parsed `200`
  response with a `null` or unrecognized `outcome` value also maps there (a new edge case this
  method introduces that `registerWatch` has no direct equivalent for, since `registerWatch`'s
  own "unexpected status" check uses a fixed single expected value, not a 3-way outcome switch).
- **AC5** — `shouldBanKmsAndChainClientImports` + its two tests, confirmed below.
- **AC6** — `attest` reuses the same `restClient` instance built once in the constructor, with
  the same `connectTimeoutMs`/`readTimeoutMs` already configured there — no new timeout code.

## Deviations forced by reality

None beyond what Phase 3/4 already anticipated and resolved. One small implementation-level
choice not explicitly pinned by the frozen brief: `attest`'s malformed/unrecognized-`outcome`
handling throws `CryptoServiceUnavailableException` with a `null` cause (mirrors `registerWatch`'s
own identical `response == null || response.watchId() == null` branch's own `null`-cause style)
rather than inventing a new exception shape for it — consistent with the frozen brief's own AC4
disposition ("any... malformed/unparseable response" collapses into the existing exception), not
a new deviation.

## Verification performed this phase

- `mvn -o -pl services/payment -am compile`: clean (60 source files, up from 59 — `attest`
  added, no new main-source file count increase beyond the three new `common.crypto`/`common`
  classes, since `AttestationResult` counted as one file despite two records).
- `mvn -o -pl services/payment -am test-compile`: clean (36 test source files, up from 32 — the
  3 stub classes + 1 rogue fixture).
- `mvn -o -pl services/payment -am test -Dtest=ArchitectureTest`: **7/7 passing** (5 existing + 2
  new) — confirms the new L3 rule passes on current code AND genuinely fails against
  `RogueKmsAndChainClientUser` (the negative-proof test's `assertThatThrownBy` succeeded, proving
  the rule is not vacuously always-passing).
- `mvn -o -pl services/payment -am test` (full suite, Docker up —
  `services/auth/compose.local.yaml`): **299/299 passing** (297 pre-task baseline + 2 new
  `ArchitectureTest` methods). No regression.
