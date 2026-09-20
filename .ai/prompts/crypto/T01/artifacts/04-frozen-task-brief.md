<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). -->

# crypto · T01 · Phase 4 — Frozen Task Brief

**STATUS: FROZEN**

## Decision packet

All 8 Phase 3 (Kimi) findings verified before disposition.

| # | Finding | Disposition |
|---|---|---|
| 1 | "Update the threat model" has no concrete acceptance criterion | **Human-gate decision: Status → `tracked` for threats #1–#6 + a new "Implementing task" column** pointing each to the crypto-service task that closes it (#1 quorum → T09, #2 fake-contract → T11, #3 reorg → T14/T18, #4 KMS custody → T20, #5 ledger integrity → T08/T09, #6 address poisoning → T13). Concrete, not cosmetic, and gives the doc real tracking value as tasks land. |
| 2 | Mirroring auth's `oauth2-authorization-server` starter is wrong for crypto | **Verified accurate, accepted.** Crypto validates tokens (R27) but never issues them — `agents.md`'s own Security rule says "resource server." Pom includes only `spring-boot-starter-oauth2-resource-server` + `spring-security-oauth2-resource-server`, not the issuer-side starter. |
| 3 | AWS KMS dependency needs its own named decision, not a silent copy | **Accepted, human-gated on location: `services/auth/docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md`**, mirroring ADR-0003's structure, cited in the pom's KMS dependency comment. |
| 4 | Web3j/Tron gRPC coordinates unspecified, risk of guessing | **Resolved via live verification, not deferred.** Checked Maven Central directly: `org.web3j:core:6.0.0` (published ~1 month ago) and `io.github.tronprotocol:trident:1.0.0` (the official Tron-maintained successor to the older `wallet-cli`/raw-gRPC approach, published 13 days ago) — both real, current, verified now, not guessed from memory or deferred to T06/T07. |
| 5 | Global `spring.threads.virtual.enabled=true` may be premature before pinning-risk code exists | **Human-gate decision: enable globally now**, matching the task statement's literal instruction. Accepted risk: modern PostgreSQL JDBC and AWS SDK v2 are both designed against the classic `synchronized`-block pinning trap; revisit with `-Djdk.tracePinnedThreads` in a later integration test if a real pinning regression appears once watcher/KMS code exists. |
| 6 | Copying auth's Flyway plugin (with a literal local-only password) would violate this task's own secrets constraint | **Accepted — plugin omitted from T01 entirely.** No migration exists yet (that's T02); the plugin isn't needed until then, so the question of how to configure it without a committed credential is deferred to T02, not solved by copying it prematurely. |
| 7 | Root POM module ordering should be explicit | **Accepted.** A comment is added noting modules are listed in dependency order and that `libs/java/*` must precede services that depend on them. |
| 8 | No verification that adding `services/crypto` doesn't break `services/auth`'s build | **Accepted — added as AC5.** `mvn -pl services/auth verify` will be run for real after the change, not assumed. |

## Frozen brief (Phase 2 TIB, as amended)

### Task

Update `SECURITY-THREAT-MODEL.md` (threats #1–#6 → `tracked`, owning-task column added); register
`services/crypto` in the root `<modules>` (with an ordering comment); create
`services/crypto/pom.xml` mirroring `services/auth`'s dependency set minus the OAuth2-issuer starter,
plus `org.web3j:core:6.0.0`, `io.github.tronprotocol:trident:1.0.0`, and the already-present AWS KMS
SDK (now backed by a new, named ADR); enable Java 21 virtual threads globally
(`spring.threads.virtual.enabled=true`).

### Scope

**In**: the four artifacts above, plus the new ADR file. **Out**: any `services/crypto/src` code;
the Flyway plugin (deferred to T02); resolving `package.md` §11's Q1–Q7.

### Files to Create

- `services/crypto/pom.xml`
- `services/auth/docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md`

### Files to Modify

- `/pom.xml` (root — module entry + ordering comment)
- `SECURITY-THREAT-MODEL.md` (threats #1–#6, Status + new column)

### Files NOT to Modify

`spec/crypto-service/**`; `services/auth/**` (read-only mirror source, except the new ADR file which
is additive, not a modification to existing auth-service content); `libs/**`.

### Acceptance Criteria

- AC1 — Threats #1–#6 marked `tracked` with an owning-task reference each.
- AC2 — `services/crypto` registered in root `<modules>`, with an ordering comment.
- AC3 — `services/crypto/pom.xml` created: auth's dependency set minus the issuer starter, plus
  `org.web3j:core:6.0.0`, `io.github.tronprotocol:trident:1.0.0`, KMS (ADR-backed).
- AC4 — `spring.threads.virtual.enabled=true` set.
- AC5 (new, Kimi Finding 8) — `mvn -pl services/auth verify` still passes after the sibling module is
  added; result recorded in the Phase 6 implementation notes, not assumed.

### Constraints

No secret, real or placeholder, in any new file (L13) — the Flyway-plugin omission (Finding 6) is
what keeps this true. Module boundaries (L15) — the new pom's structure doesn't preclude
package-by-feature layout, though no packages exist yet to violate it.

### Open Questions

No blockers remaining. All three genuine judgment calls (threat-model update shape, virtual-threads
scope, ADR location) resolved above via human gate.

---

**Phase 4 complete — task brief frozen and approved.** Proceed to Phase 5 (Implementation Plan).
