# payment · T15 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**)

No code in this artifact — documentation only, per this phase's own instructions.

**Branching note:** same established practice as every prior task this session — executed
directly on the long-running `spec/service-specs-and-ai-framework` branch, not a fresh per-task
branch off `main`. `main` itself was never touched and stays deployable. The attribution trailer
below uses this session's own current identity (Claude Sonnet 5), not the generic placeholder
text baked into this prompt template's own header.

## Commit title

`payment T15: Crypto client attest + L3 ArchUnit ban`

## Commit message

```
payment T15: Crypto client attest + L3 ArchUnit ban (R19 partial, L3)

Adds CryptoClient.attest(...) for POST /internal/v1/attest, the one external call this
service makes to obtain a receipt signature (R19's own mechanism; the full digest/decide/
record flow is T16/T17's scope). registerWatch/unregisterWatch already existed since T06/T08
- this task's real net-new work was the attest method itself and formalizing L3 as an
enforced ArchUnit rule, previously an unwritten pom.xml-level convention since T01.

Returns a sealed AttestationResult (Signed/Blocked) rather than a nullable-field record, so a
caller cannot read signature() on a Blocked outcome and silently get null. A new
AttestationRefusedException covers the real crypto-service's 409 REFUSED outcome (the signing
gate not satisfied - quorum/finality unmet, or a screening error), distinct from the existing
CryptoServiceUnavailableException (which also now covers 400, by explicit disposition, since
this client trusts its own caller and performs no local request validation).

CryptoClient stays in common rather than relocating to the attestation package design.md and
package.md's own module diagrams both describe - it already has real callers in invoice
(InvoiceController, T06) and consumer (the expiry sweep, T08), both predating any reason for an
attestation module to exist. Documented as a Javadoc note on CryptoClient itself, not a spec
edit - every phase's own guardrail forbids modifying files under spec/.

Three independent review rounds (self-review + two Kimi rounds) found and fixed a real
correctness gap: a SIGNED response with a missing signature/kmsKeyId/signedAt was silently
accepted as a usable outcome. Fixed with an explicit guard plus a compact-constructor
null-check on AttestationResult.Signed itself (defense-in-depth). One Kimi Phase 8 finding
was tried and reverted after its own premise proved factually wrong (CryptoClient and
AttestResponse are NOT in the same Java package, contrary to the finding's claim - verified by
attempting the suggested visibility change, which broke compilation immediately). A second
Kimi finding (parse the 409 body as structured ProblemDetail) was rejected as a re-proposal of
what Phase 4's own frozen brief had already explicitly decided against.

ArchitectureTest: 7/7 passing (5 existing + 2 new L3 canary/negative-proof). CryptoClientTest:
32/32 passing, confirmed stable across 3 consecutive runs (25 after Phase 10, +7 after Phase
11's own strengthening round). Full suite: 316/316 passing (299 pre-task baseline + 17 new).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Production code:**
- `services/payment/src/main/java/com/themistra/payment/common/CryptoClient.java` — new
  `attest(...)` method; new `attestPath` field; new class-level Javadoc paragraph documenting
  the module-placement decision.
- `services/payment/src/main/java/com/themistra/payment/common/crypto/AttestationResult.java`
  (new) — sealed interface (`Signed`/`Blocked`), `Signed` gains a compact-constructor null-check
  (Phase 9).
- `services/payment/src/main/java/com/themistra/payment/common/crypto/AttestRequest.java` (new).
- `services/payment/src/main/java/com/themistra/payment/common/crypto/AttestResponse.java`
  (new) — the internal wire-deserialization DTO.
- `services/payment/src/main/java/com/themistra/payment/common/AttestationRefusedException.java`
  (new).

**Tests:**
- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` — new
  `shouldBanKmsAndChainClientImports` rule (L3) + its 2 paired tests.
- `services/payment/src/test/java/com/themistra/payment/archtestfixtures/RogueKmsAndChainClientUser.java`
  (new) — negative-proof fixture.
- `services/payment/src/test/java/org/web3j/StubWeb3jClass.java`,
  `services/payment/src/test/java/org/tron/StubTronClass.java`,
  `services/payment/src/test/java/software/amazon/awssdk/services/kms/StubKmsClass.java` (new)
  — classpath stand-ins so the negative proof compiles without adding real SDK dependencies.
- `services/payment/src/test/java/com/themistra/payment/common/CryptoClientTest.java` — 17 new
  `attest`-related test methods across Phases 6/10/11, plus a new `startAttestServer(...)` test
  helper.

No migration file — this task adds no entity, no persistence, no config field (`attestPath`
already existed in `CryptoClientProperties` since T03, unused until now).

## Summary

T15 gives this service its first real `POST /attest` call and, in doing so, formalizes a
LOCKED decision (L3) that had only ever existed as an unenforced build-level convention since
T01. The hardest design question was representing a three-way business outcome
(`SIGNED`/`BLOCKED`/`409 REFUSED`) with no existing two-way success/failure precedent on this
client to extend — resolved with a sealed-interface return type and a dedicated exception,
both stress-tested across self-review and two independent Kimi rounds, one of which correctly
caught a real null-handling gap and one of which (Kimi's own, not mine) turned out to rest on a
factually wrong premise about Java package structure, caught before it could break the build.

## Testing performed

- `mvn -o -pl services/payment -am compile`, `mvn -o -pl services/payment -am test-compile`:
  clean throughout every phase.
- `mvn -o -pl services/payment -am test -Dtest=ArchitectureTest`: **7/7 passing**.
- `mvn -o -pl services/payment -am test -Dtest=CryptoClientTest`: **32/32 passing**, confirmed
  stable across 3 consecutive runs after the Phase 11 addendum.
- `mvn -o -pl services/payment -am test` (full suite, Docker up —
  `services/auth/compose.local.yaml`): **316/316 passing** (299 pre-task baseline + 17 new).
- Mutation-tested the one guard this task's own review cycle added (`SIGNED` with a missing
  field): removed the check, confirmed the regression test failed red, restored, confirmed
  green.

## Specification references

- **Task:** T15 — Crypto client (`spec/payment-service/tasks.md`).
- **Requirement IDs:** R19 (partial — the client mechanism only; the full receipt-issuance flow
  is T16/T17's own scope).
- **LOCKED decisions:** L3 (scoped, now enforced for the first time); L1/L12 (continuity,
  unaffected, reconfirmed in Phase 12's own traceability matrix).
- **Disclosed deviation (not a numbered requirement):** `CryptoClient` stays in `common`, not
  the `attestation` module `design.md`/`package.md` both diagram — recorded in `CryptoClient`'s
  own Javadoc, flagged in Phase 12 as a precedent for T16-T19 to follow.
