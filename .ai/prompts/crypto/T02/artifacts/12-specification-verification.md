<!-- MODEL: Claude Sonnet — Phase 12 (Specification Verification). -->

# crypto · T02 · Phase 12 — Specification Verification

## Acceptance criteria (frozen brief)

| AC | Verified how | Result |
|---|---|---|
| AC1 | `v1MigrationFileIsByteForByteIdenticalToDesignDocVerbatimBlock` (automated, mutation-tested) | `V1__chain_baseline.sql` is byte-identical to `design.md` §4c's verbatim fence. |
| AC2 | `allTenBaselineTablesExistAndNoOthers` + `bothMigrationsAreRecordedAsSuccessfulInFlywayHistory` + real local `mvn flyway:migrate` runs (Phase 6/9) | 10 tables exist, no stray tables, both migrations recorded successful. |
| AC3 | 4 tests: INSERT+SELECT succeed / UPDATE+DELETE denied on the 3 named tables; all 7 non-named tables fully denied; no DDL rights; tables owned by the migration role, never `crypto_app` | All pass, each mutation-tested at least once across Phases 9-11. |
| AC4 | `runtimeFlywayIsDisabledInApplicationProperties` (automated) + Phase 9's real app-boot smoke test (correct password boots clean/zero Flyway activity, wrong password fails with a real auth error) | `spring.flyway.enabled=false` confirmed both statically and behaviorally. |

## Locked decisions (`design.md` §4a)

- **L3** (observation log verbatim, written first, append-only): `observations`' schema is unchanged
  from the verbatim spec; AC3's grant is what makes "append-only" DB-enforced rather than
  conventional — verified directly (UPDATE/DELETE denied).
- **L5** (deterministic idempotency key): `outbox.idempotency_key` unchanged, `UNIQUE`-constrained,
  per the verbatim schema — no logic yet to violate this, shape only.
- **L6** (reorg as first-class transition): `chain_cursors` shape unchanged, no logic yet.
- **L7** (token identity by contract address, signed/versioned allowlist): `token_allowlist` shape
  unchanged (`UNIQUE(chain, contract_address, version)`, `signature TEXT NOT NULL`).
- **L12** (screening gates attestation, fail-closed): `screening_results` shape unchanged; no client
  exists yet.
- **L13** (secrets discipline): re-verified after the Phase 9 rewrite — `V2` contains no password;
  the local-only placeholder lives only in `application.properties`' default and
  `services/crypto/README.md`'s documented one-time step, matching the class of already-reviewed
  local-only placeholder auth's own Flyway plugin uses, not a new kind of committed credential.

## `package.md` §9 whole-service checklist — items relevant to T02

- "No secret... committed" (L13) — verified above; strengthened by the Phase 9 fix (no longer even a
  literal password inside a versioned migration file, only a documented manual local step).
- "`mvn -pl services/crypto verify` passes" — verified, `BUILD SUCCESS`, full suite (16 tests across
  both T01 and T02's test classes) green together.
- Every other item (quorum, KMS signer, observation-log *logic*, finality, reorg, token matching,
  event contracts) requires feature code that doesn't exist yet — correctly out of scope.

## Cross-task regression check

Ran the **full** `services/crypto` test suite, not just T02's own new class: `T01SkeletonRegressionTest`
(6 tests, T01's own AC1-AC4 guards) still passes unchanged alongside `ChainBaselineMigrationIntegrationTest`
(10 tests) — confirms T02's `pom.xml` additions (Flyway plugin) didn't regress anything T01 established
(dependency versions, module registration, threat-model tracking, ADR linkage, virtual threads).

## Spec status

`spec/crypto-service/package.md`'s header (`Version 0.1`, `Status DRAFT`) is unchanged — that bump is
task T29, matching the established auth-service precedent (T40). Not touched here.

---

**Phase 12 complete — AC1-AC4, L3/L5/L6/L7/L12/L13 all verified against final state; full 16-test
suite green; no cross-task regression.** Proceed to Phase 13 (PR Preparation) on approval.
