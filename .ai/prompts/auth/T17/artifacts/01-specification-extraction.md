# auth · T17 — Phase 1: Specification Extraction

Consumes `artifacts/00-repository-understanding.md`. Extracting only what T17 ("MfaEnrollment
entity/repository") needs — nothing about tasks 16, 18, 19, or 20.

## Business Rules

- **R22** (partial — this task covers only the persistence shape, not generation/encryption [T16,
  done] or the endpoint [T19]): an unconfirmed `MfaEnrollment` row must be persistable, holding an
  encrypted secret with `confirmed_at` null.
- **R23** (partial — persistence shape only, not the confirm/generate flow itself [T18/19]): a
  confirmed `MfaEnrollment` row must be distinguishable from an unconfirmed one (`confirmed_at`
  set), and up to 10 `RecoveryCode` rows per account must be persistable, storing only a hash
  (never the raw code) with `used_at` initially null.

## Locked Decisions

- **L6.** RFC 6238 (30s, 6 digits, HMAC-SHA1) — not directly exercised by an entity/repository
  task, but its second clause governs `recovery_codes.code_hash`: "Recovery codes are random
  single-use values; only SHA-256 hashes are stored." The existing `CHAR(64)` column is already
  sized correctly for a SHA-256 hex digest — the entity mapping must not silently accept a
  different-length value class of hash without narrowing it. Hashing itself is task 18's job, not
  this one.
- **L12** (not in this task's scoped list, but clearly governing, same as L14 was widened into
  T16's scope at Phase 0/1): "No feature module may import an entity class from another feature
  module." Directly constrains how `MfaEnrollment`/`RecoveryCode` reference `accounts` — a plain
  `Long accountId` column, resolved via native query, never a JPA relation to `Account`. This is
  the single most load-bearing constraint on this task's design and is widened into scope here
  the same way L14 was for T16.

## Files Involved

**New** (none exist yet beyond `package-info.java`):
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaEnrollment.java` (entity)
- `services/auth/src/main/java/com/themistra/auth/mfa/MfaEnrollmentRepository.java`
- `services/auth/src/main/java/com/themistra/auth/mfa/RecoveryCode.java` (entity — not named in
  `design.md` §6's file tree, but required by the task statement's own text; see Phase 0's note)
- `services/auth/src/main/java/com/themistra/auth/mfa/RecoveryCodeRepository.java`

**Existing, read as pattern precedent (not modified):**
- `services/auth/src/main/java/com/themistra/auth/authn/LockoutState.java` and
  `LockoutStateRepository.java` — the exact shape to mirror (plain FK column, package-private
  repository, native UUID-resolution query).
- `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql:24-42` — the
  authoritative, unchanged schema for both tables.
- `services/auth/src/main/java/com/themistra/auth/mfa/package-info.java` — already states the
  ArchUnit "modules expose services, never entities" boundary; no change expected.

**Existing, read-only context (never imported):**
- `services/auth/src/main/java/com/themistra/auth/account/Account.java` — its internal id is
  what `account_id` resolves to; the class itself must never be imported (L12).

## Dependencies

- Spring Data JPA (`JpaRepository`) — already a project dependency, no new one.
- `accounts.account_uuid` → `accounts.id` resolution, via native `@Query`, mirroring
  `LockoutStateRepository.findAccountIdByUuid`.
- No new config keys, no new contracts. `contracts/api/auth.yaml` defines the future
  `/accounts/me/mfa/*` endpoints (task 19) but is not consumed by an entity/repository task.
- No dependency on T16's `TotpGenerator`/`MfaSeedEncryption` — this task only stores the
  already-encrypted `BYTEA` and already-hashed `CHAR(64)` values that those classes' *future*
  caller (task 18) will produce; T17 itself does no encryption or hashing.

## Acceptance Criteria

- **AC1 (R22).** `MfaEnrollment` maps `mfa_enrollments` exactly (all columns, `UNIQUE(account_id,
  type)`); a new row can be persisted with `confirmed_at` null and `secret_encrypted` populated.
- **AC2 (R23).** An existing `MfaEnrollment` can be transitioned to confirmed (`confirmed_at` set)
  without needing a new row.
- **AC3 (R23, L6).** `RecoveryCode` maps `recovery_codes` exactly; multiple rows (up to 10) can be
  persisted per account, each holding a `code_hash` and a nullable `used_at`.
- **AC4 (L12).** Neither entity nor repository imports `com.themistra.auth.account.Account`;
  account linkage is a plain `Long` column resolved via native query against `account_uuid`.
- **AC5 (task statement's "enforce one confirmed enrollment per account").** At minimum, the
  existing `UNIQUE(account_id, type)` constraint prevents more than one row per account per MFA
  type. Whether a dedicated repository finder (e.g. distinguishing confirmed from unconfirmed) is
  also required to make this checkable by task 18 is left to Phase 2's design — not committed to
  here as it wasn't settled at Phase 0 either.

## Tests Required

- **Named test** `shouldConfirmTotpEnrollmentAndReturnSingleUseRecoveryCodes` (`package.md` §8,
  mapped there to R20 — a stale mismatch already flagged at T16 Phase 4 and again at this task's
  Phase 0; R23 is the semantically correct match and what this task's own header uses). Like
  T16's named test, this one is **not fully satisfiable by T17 alone** — it requires the confirm
  flow and recovery-code generation (task 18) and the controller (task 19). T17's own test suite
  proves the underlying persistence shape those later tasks will depend on.
- **Boundary tests implied** by the schema and L12:
  - Persisting a second `mfa_enrollments` row for the same `(account_id, type)` violates the
    unique constraint.
  - `confirmed_at`/`last_used_at`/`used_at` all correctly nullable and independently settable.
  - Resolving `account_id` from `account_uuid` via the native query returns empty for an unknown
    UUID (mirrors `LockoutStateRepository.findAccountIdByUuid`'s contract).
  - Cascade behavior on `accounts` row deletion (`ON DELETE CASCADE` on both tables) — worth at
    least one integration-level check if Testcontainers is available; see Phase 0's noted
    Docker/Testcontainers limitation in this sandbox.
  - Multiple `RecoveryCode` rows for one account persist and query correctly (not just a
    single-row case).

## Open Questions

None from `package.md` §11 apply to this task (Q1 is resolved and belongs to T16; Q2-Q6 concern
rate limits, API keys, email links, lockout event publication, and the agents.md meta-question —
none touch entity/repository mapping). The one genuine design question — whether "enforce one
confirmed enrollment per account" needs a repository method beyond the existing unique constraint
— is not a blocker; it's deferred to Phase 2 as a design decision, per Phase 0's note.
