<!-- MODEL: Claude Sonnet — Phase 2 (Task Implementation Brief). -->

# crypto · T01 · Phase 2 — Task Implementation Brief

## Task

Update `SECURITY-THREAT-MODEL.md` for threats #1–#6; register `services/crypto` in the root
`<modules>`; create `services/crypto/pom.xml` mirroring `services/auth`'s, plus web3j, a Tron gRPC
client, and the AWS KMS SDK (KMS scoped to the future `attest` module only); enable Java 21 virtual
threads.

## Purpose

First task of a new service. Establishes the buildable skeleton every later crypto-service task
builds on, and satisfies the platform rule that the threat model must be current before the first
line of crypto-service code exists.

## Scope

**In**: the root POM edit, the new module's own POM, the threat-model doc update, virtual-threads
config. **Out**: any Java source under `services/crypto/src` (no feature module exists yet — that is
every task from T02 onward); resolving `package.md` §11's Q1–Q7 (explicitly scoped to later tasks or
real deployment, not T01); populating `libs/java/outbox` or any other shared library.

## Business Rules

None (process/skeleton task, no requirement ID implemented).

## Locked Decisions

L11 (KMS reachable only from the future `attest` module — governs how the KMS dependency is framed,
not enforced yet since no code exists to violate it), L13 (secrets discipline — no literal secret in
any skeleton config), L15 (package-by-feature module boundaries, mirroring auth).

## Dependencies

Mirror of `services/auth/pom.xml`'s dependency set (already includes `software.amazon.awssdk:kms`);
plus web3j and a Tron gRPC client, exact coordinates to be verified against Maven Central at Phase 5/6
(not guessed from memory).

## Inputs

Current root `pom.xml` (one module: `services/auth`); current `SECURITY-THREAT-MODEL.md` (8-threat
stub, all `designed`); `services/auth/pom.xml` as the mirror source.

## Outputs

A buildable two-module Maven reactor (`services/auth`, `services/crypto`); `services/crypto/pom.xml`
with virtual threads enabled; an updated `SECURITY-THREAT-MODEL.md`.

## State Changes

None to runtime behavior — no application code exists yet to change. Build-graph and documentation
changes only.

## Files to Create

- `services/crypto/pom.xml`

## Files to Modify

- `/pom.xml` (root — add the module entry)
- `SECURITY-THREAT-MODEL.md` (repo root — threats #1–#6)

## Files NOT to Modify

Everything under `spec/crypto-service/`; `services/auth/**` (read-only mirror source); `libs/**`.

## Acceptance Criteria

- AC1 — `SECURITY-THREAT-MODEL.md` updated for threats #1–#6. **Blocked on Open Questions below**:
  exact scope of "updated" is not pinned down by the task statement.
- AC2 — `services/crypto` registered in the root `<modules>`. No blocker.
- AC3 — `services/crypto/pom.xml` created, mirroring `services/auth` + web3j + Tron gRPC + AWS KMS
  SDK. **Partially blocked**: KMS is already covered by a straight mirror; web3j/Tron coordinates and
  the SAS-starter-vs-resource-server-only question need resolution first.
- AC4 — Java 21 virtual threads enabled. No blocker (mechanical Boot 3.5.4 config).

## Required Tests

None — no code exists for this task to test.

## Constraints

- **Security**: no secret, real or placeholder, committed in the new pom or any config file (L13).
- **Module boundaries**: the new pom's own structure must not preclude the package-by-feature layout
  `design.md` §6 specifies (L15) — nothing to violate yet, but the skeleton shouldn't foreclose it.
- **Build**: the reactor must remain buildable — `mvn -pl services/auth verify` must continue to pass
  unaffected by this task (a new sibling module must not break the existing one).

## Open Questions

**Blockers, both requiring a Phase 4 human-gate decision:**
1. What "update `SECURITY-THREAT-MODEL.md` for threats #1–#6" concretely means at the skeleton
   stage, given every threat is currently `Status: designed` and no code exists yet to mark
   `implemented`.
2. Whether `services/crypto/pom.xml` mirrors auth's full
   `spring-boot-starter-oauth2-authorization-server` (issuer-side) or only the resource-server half
   (crypto-service validates tokens, per R27, but never issues them).

Exact web3j/Tron-gRPC artifact coordinates are not a Phase 4 blocker — they're a Phase 5/6
verify-against-Maven-Central task, same discipline as auth-service T31's own Bucket4j lesson.

---

**Phase 2 complete — implementation brief written.** Proceed to Phase 3 (Design Challenge) on
approval.
