<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# crypto · T01 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `crypto-service` |
| **Task** | T01 — Threat-model update + Maven skeleton (`services/crypto/pom.xml`, root POM, virtual threads) |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

Reviewed the T01 brief, the root `pom.xml`, `services/auth/pom.xml` (the mirror source), `SECURITY-THREAT-MODEL.md`, and the crypto-service standing rules (`spec/crypto-service/agents.md`).

---

## Finding 1 — "Update the threat model" has no concrete acceptance criterion

**Issue.** AC1 says "`SECURITY-THREAT-MODEL.md` updated for threats #1–#6," but the document currently lists all eight threats as `Status: designed`. No code exists yet, so none can be marked `implemented`. The brief correctly flags this as a Phase 4 blocker, but the design challenge should record that without a decision, implementers will either (a) mark statuses speculatively, (b) add prose that duplicates `agents.md`/design.md, or (c) leave the table unchanged and claim the task is done.

**Evidence.**
- `SECURITY-THREAT-MODEL.md` lines 10-15: all rows are `designed`.
- `spec/crypto-service/tasks.md` line 3: "`SECURITY-THREAT-MODEL.md` must be completed/updated before Task 1."
- T01 brief lines 71-73 and 91-96: AC1 is blocked because "exact scope of 'updated' is not pinned down."

**Recommendation.** At the Phase 4 gate, decide one of:
- Change status to `tracked` (or `accepted`) for #1–#6 and add an `Implementing service` column pointing to `crypto-service` / `services/crypto`;
- Add a short crypto-specific note to each row's mitigation cell (not duplicating architecture) making clear that implementation is in `services/crypto` tasks T02–T25;
- Or bump the document header status from "stub" to "draft — threats #1–#6 tracked to crypto-service implementation."

Do not allow an "update" that is purely formatting.

**Confidence.** High.

---

## Finding 2 — Mirroring auth's OAuth2 Authorization Server dependency is wrong for crypto

**Issue.** The brief asks to mirror `services/auth/pom.xml`. Auth includes `spring-boot-starter-oauth2-authorization-server` because it is an OIDC issuer. The Crypto Service only validates service-to-service JWTs (R27, `agents.md` Security rule) — it is a resource server, never an issuer. Including the full SAS starter pulls in authorization-server code, endpoints, and attack surface that this service must not expose. It also contradicts the minimality principle for a skeleton.

**Evidence.**
- `services/auth/pom.xml` lines 51-63: includes both `spring-boot-starter-oauth2-authorization-server` and `spring-boot-starter-oauth2-resource-server`.
- `spec/crypto-service/package.md` lines 93-103: internal endpoints require `internal.crypto:write` scope; public endpoint allowlist is actuator + verification keys; no issuance behavior.
- `spec/crypto-service/agents.md` Security rule: "Internal endpoints require a service-to-service JWT ... validated as an OAuth2 resource server."

**Recommendation.** The crypto POM should include only:
- `spring-boot-starter-oauth2-resource-server`
- `spring-security-oauth2-resource-server`

Omit `spring-boot-starter-oauth2-authorization-server` entirely. Document this deviation from the "mirror auth" shortcut in the implementation notes.

**Confidence.** High.

---

## Finding 3 — AWS KMS dependency needs its own named exception, not a silent copy of auth's

**Issue.** Auth's KMS dependency is justified by ADR-0003/D-025 as a narrow exception to D-010 ("no AWS SDK in this service"). Crypto will use KMS only for attestation signing, governed by L11 (`agents.md`). Copying auth's `software.amazon.awssdk:kms` dependency without an equivalent decision record makes the dependency look unprincipled and will confuse future reviewers who check why the Crypto Service — which also has a "no AWS SDK outside attest" rule — includes it.

**Evidence.**
- `services/auth/pom.xml` lines 91-96: KMS dependency with explicit `ADR-0003/D-025` comment.
- `spec/crypto-service/agents.md` L11: `kms:Sign` is reachable only from the `attest` module; IAM + ArchUnit enforcement.
- T01 brief line 37: mirrors auth's dependency set (which already includes KMS).

**Recommendation.** Before adding KMS to `services/crypto/pom.xml`, create `docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md` (or add a D-0xx entry in `auth-decisions.md` if crypto gets its own decisions file) that mirrors the pattern of ADR-0003: KMS SDK is permitted only inside the `attest` module, and the signing API must not be referenced elsewhere. Reference that decision in the POM comment.

**Confidence.** High.

---

## Finding 4 — Web3j and Tron gRPC artifact coordinates are unspecified at design time

**Issue.** The brief defers exact coordinates to Phase 5/6 ("verify against Maven Central"). That is a good discipline, but the skeleton POM cannot be buildable without at least placeholder coordinates. Picking the wrong artifact (e.g., `web3j-core` vs `core` vs `contracts`) or a Tron gRPC artifact that does not exist on Maven Central will break AC3 the moment the module is added.

**Evidence.**
- T01 brief lines 38-39: "plus web3j and a Tron gRPC client, exact coordinates to be verified against Maven Central at Phase 5/6."
- `spec/crypto-service/design.md` §4a mentions Java tooling for Ethereum (web3j) and Tron (TronGrid / java-tron gRPC).

**Recommendation.** At Phase 4/5, select and pin explicit coordinates with a fallback plan:
- Ethereum: `org.web3j:core:4.12.0` (or current stable) for general use, plus `org.web3j:crypto` if separated.
- Tron: if no stable Maven Central artifact exists for `java-tron` gRPC, decide whether to use `io.github.tronprotocol:api` or a locally generated stub from Tron's `.proto` files; document the choice and the build step.

Do not leave the POM with guessed coordinates; if coordinates are genuinely unknown, omit web3j/Tron from T01 and add them in T06/T07 with a verified build.

**Confidence.** Medium.

---

## Finding 5 — Virtual threads enabled globally may interact badly with blocking I/O in non-watcher code

**Issue.** `agents.md` states virtual threads are "used for the watcher layer." Spring Boot 3.5.4's global toggle `spring.threads.virtual.enabled=true` enables virtual threads for all Tomcat request handling, `@Async`, and scheduler tasks. The Crypto Service will also do synchronous JDBC calls, AWS KMS signing, and possibly S3 uploads; pinning a virtual thread on a synchronized block or native call can degrade performance. Enabling virtual threads globally at T01, before any code exists to reason about carrier-thread pinning, is premature.

**Evidence.**
- `spec/crypto-service/agents.md` Language & build rule: "Virtual threads are used for the watcher layer."
- `spec/crypto-service/design.md` O2: watcher transport & concurrency maps watches onto Java 21 virtual threads.
- Spring Boot 3.5 docs: `spring.threads.virtual.enabled=true` is global; per-task executor configuration is required for selective use.

**Recommendation.** Either:
- Create an explicit `VirtualThreadPerTaskExecutor` bean named for the watcher layer only, or
- If global enablement is chosen, document the pinning risk and require a later JVM run with `-Djdk.tracePinnedThreads` in integration tests to catch regressions.

The skeleton should not silently adopt a global toggle that contradicts the standing rule's narrower scope.

**Confidence.** Medium.

---

## Finding 6 — The new POM should not blindly inherit auth's local-only Flyway password

**Issue.** Auth's POM contains a `flyway-maven-plugin` configuration with literal credentials (`checky-local-only`). The T01 brief's L13/Constraints say "no secret, real or placeholder, committed in the new pom or any config file." Copying that plugin block verbatim violates the constraint, even if the value is only for local development.

**Evidence.**
- `services/auth/pom.xml` lines 191-201: `flyway-maven-plugin` with `<password>checky-local-only</password>`.
- T01 brief lines 85 and 89: "no secret, real or placeholder, committed" and "reactor must remain buildable."

**Recommendation.** Either omit the Flyway plugin from T01 (it is not needed until T02 schema work) or configure it via Maven properties/defaults with a placeholder that fails if unset in non-local profiles. Do not copy the literal password block.

**Confidence.** Medium.

---

## Finding 7 — Root POM ordering and module registration are trivial but should be explicit

**Issue.** Adding `<module>services/crypto</module>` after `services/auth` is straightforward, but if `libs/java/outbox` (or another shared module) is introduced later, the order matters for reactor builds. The skeleton should not imply that module order is unimportant.

**Evidence.**
- Root `pom.xml` lines 21-24: only `services/auth` currently listed.
- T01 brief line 62: root POM is modified to add the module entry.

**Recommendation.** Add `services/crypto` immediately after `services/auth`. Add a comment in the root POM noting that modules are listed in dependency order and that shared `libs/java/*` modules must precede services that depend on them.

**Confidence.** Low.

---

## Finding 8 — No skeleton verification that `services/crypto verify` does not break `services/auth`

**Issue.** The brief's Constraints require that `mvn -pl services/auth verify` continue to pass unaffected by adding the sibling module. The acceptance criteria do not include running that command, nor is there a test surface for it. A malformed crypto POM (e.g., duplicate artifactId, invalid parent reference, or a dependency that pollutes the reactor BOM) could break auth's build even though auth itself is unchanged.

**Evidence.**
- T01 brief lines 88-89: "`mvn -pl services/auth verify` must continue to pass unaffected."
- T01 brief lines 69-82: acceptance criteria list AC1–AC4; none is a cross-module build check.

**Recommendation.** Add AC5: "Run `mvn -pl services/auth verify` from the reactor root and confirm it still passes after adding `services/crypto`." Record the result in `06-implementation-notes.md`.

**Confidence.** Low.

---

## Summary of Phase 4 gate decisions required

1. What concrete change to `SECURITY-THREAT-MODEL.md` constitutes "updated" for threats #1–#6.
2. Whether the crypto POM includes only the resource-server half of OAuth2 (recommended) or mirrors auth's full SAS.
3. Which ADR/decision record authorizes the KMS dependency and how it references L11.
4. Exact web3j and Tron gRPC coordinates, or a decision to defer them to T06/T07.
5. Whether virtual threads are globally enabled or restricted to the watcher executor.
6. Whether to include the Flyway plugin in T01 and, if so, how to avoid committed credentials.
