# crypto · T29 · Phase 0 — Repository Understanding

## 1. Architecture summary

Unchanged from T28's own Phase 0 (still accurate, re-confirmed): `services/crypto` is Themistra's
attestation engine — quorum arbitration across ≥2-of-3 independent RPC providers (L1), per-chain
finality (L4), reorg-aware state (L6), KMS-only signing confined to the `attest` module (L11), all
emitted via an outbox → Kafka pipeline with deterministic idempotency keys (L5). 11 feature modules +
`common` + `adapter`, JPA/Flyway persistence, OAuth2 resource-server security.

## 2. Existing code this task touches

T29 is a spec-document task, not a code task. Its one deliverable is `spec/crypto-service/package.md`'s
own header block (`Version`, `Status`) and its own §11 open-questions section. No `services/crypto`
source is expected to change. The guardrail text every phase template repeats — "Never modify the
specification files under `spec/`" — is the standing rule for every other task in this package; T29 is
the deliberate, single exception, exactly as T28's `SECURITY-THREAT-MODEL.md` update was the exception
for that file (which, unlike `package.md`, sits outside `spec/` entirely). This tension is not a
contradiction to route around silently — Phase 2 will need to state explicitly that this task's own
in-scope write target is `spec/crypto-service/package.md`'s header and §11, nothing else under `spec/`.

## 3. Established patterns to follow

Same as T28's own Phase 0: JPA/Flyway, outbox/idempotency, resource-server security, ArchUnit-as-canary
(the `@ArchTest` field is documentation-only in this repo; a plain `@Test` invoking `.check(...)`
directly is what gates the build).

## 4. Testing conventions

Unchanged from T28. Docker became available in this environment during T28 Phase 11 (first time since
T23) — full-suite verification is now meaningfully more complete than at any earlier point in this
session, though several pre-existing, unrelated integration-test failures remain open (T28 Phase 12,
flagged as follow-up candidates, not this task's own concern).

## 5. Known gaps / unknowns

**The central question this task must resolve, not assume, in Phase 1/2:** what does "closed" mean for
`package.md` §11's Q1, Q2, Q3, Q7 — the task statement's own named gate? Investigated each directly
against real, current implementation rather than speculating:

- **Q3 (fail-closed vs fail-open on screening outage).** `design.md` §4a-L12 already LOCKS
  "fail-closed (no signature)" as the decision. `FailClosedScreeningClient` exists, implements exactly
  this, and is tested (`FailClosedScreeningClientTest`). The engineering-facing half of Q3 is fully
  implemented; §11's own text just hasn't been updated to say so.
- **Q7 (KMS key type/algorithm).** `KmsSigner`'s own Javadoc reads "Algorithm pending Q7... `SIGNING_ALGORITHM`
  is a single named constant... so the one open question in this design... has exactly one place to
  change once Q7 is answered" — `ECDSA_SHA_256`, reasoned directly from `design.md` §4c's own fixed
  SHA-256 digest contract. This is a sound, documented, single-point-of-change default, not a final,
  platform-confirmed answer — the actual KMS key the platform provisions in a real AWS account could
  still differ, which is a deployment/ops decision, not something this repository's own code can settle.
- **Q2 (screening vendor).** "Until chosen, screening is behind an interface with a fail-closed stub" —
  exactly the current state (`ScreeningClient` interface, `FailClosedScreeningClient` stub). R21
  (`shouldReturnBlockedFromAttestOnSanctionedCounterparty`) is implemented and passing against the stub,
  closing Q2's own stated blocker ("Blocker for R21") without a real vendor being chosen.
- **Q1 (provider set & quorum N).** The "is N fixed at 3" half is already settled by L1 itself (2-of-3,
  not a tunable). The "which 3 commercial vendors" half remains genuinely open — `application.properties`
  itself says so directly: "real provider set is unresolved (package.md §11 Q1)", with vendor-agnostic
  fake placeholders standing in. Q1's own text already anticipates this exact distinction: "Blocker for
  real deployment (**not for fake-provider tests**)."

**I do not know** whether T29's "closed" is meant to require an actual, final, vendor-confirmed answer
for Q1/Q2/Q7 (which no amount of code-reading can produce — these are business/procurement decisions
outside this repository's own reach), or whether it means what the evidence above consistently suggests:
each question has a complete, tested, deployment-swappable engineering answer, with the pure
vendor-selection portion explicitly and deliberately deferred to deployment time by the spec's own
original wording. This is the single most consequential scoping decision for Phase 1/2 to make
explicitly, not silently assume either way.
