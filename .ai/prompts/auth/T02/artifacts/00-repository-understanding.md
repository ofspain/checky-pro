# auth · T02 · Phase 0 — Repository Understanding

Read: `spec/auth-service/{package,requirements,design,tasks,agents}.md`, `services/auth/docs/architecture/{auth-decisions,target-design}.md`, `services/auth/src/main/resources/db/migration/V1__auth_baseline_schema.sql`, `services/auth/src/main/java/com/themistra/auth/mfa/package-info.java`. No code written.

## 1. Architecture summary

`auth-service` is the platform's OIDC/OAuth2 identity issuer (Spring Authorization Server), package-by-feature under `com.themistra.auth`, one Postgres schema (`auth`) via Flyway, JPA for simple find/save. State changes other services care about go through a transactional outbox (`libs/java/outbox`) to Kafka, topics under `contracts/events/auth/`. Security is zero-trust: every non-public endpoint validates a JWT; the public-endpoint set is exhaustive and CI-enforced. Secrets (DB creds, JWT signing keys, and explicitly the **TOTP-seed KEK reference** — `target-design.md` §16) are injected by External Secrets Operator; no AWS SDK calls are permitted in application code (D-010).

## 2. Existing code this task touches

- **`mfa/` package** (`services/auth/src/main/java/com/themistra/auth/mfa/`) exists but is empty apart from `package-info.java`, whose javadoc already states the module's job: "TOTP enrollment, recovery codes, **envelope-encrypted** seed storage." No entities, services, or encryption code exist yet — this task must not create them (task statement: "before writing `mfa/` code").
- **`mfa_enrollments` table** (V1, immutable per L1) already has a column `secret_encrypted BYTEA NOT NULL` with an inline SQL comment: `-- AES-GCM, KMS-enveloped data key`. This is existing, locked schema — and it already encodes an assumption (KMS-enveloped key) that is exactly the D-010 conflict Q1/O1 exist to resolve. Whatever option is selected must be reconcilable with this column's shape (a `BYTEA` ciphertext blob), or the conflict between the column comment and the chosen approach needs to be called out explicitly — it is not this task's job to alter V1, only to flag if the resolution and the existing column comment diverge.
- **`recovery_codes` table** (V1) — out of scope for T02 (recovery codes are SHA-256 hashed per L6, no encryption question there).
- **`spec/auth-service/design.md` §4b O1** and **`package.md` §11 Q1** — the two spec locations the task explicitly requires updating with the chosen approach once the author decides.
- **`services/auth/docs/architecture/auth-decisions.md`** — the task requires adding/updating a decision entry here (following the existing `D-00N` pattern used by D-001–D-014) with the chosen approach.

## 3. Established patterns to follow

- **Persistence:** JPA entities, Flyway DDL-only migrations, V1–V4 immutable (L1) — not applicable to this task directly since no schema or Java code is authorized here, but relevant to note the target column this task's decision will eventually flow into.
- **Secrets:** External Secrets Operator injection only; `agents.md` already lists "TOTP-seed KEK reference" alongside DB credentials and JWT signing keys as an ESO-injected secret (target-design.md §16) — i.e. the platform's baseline assumption is that *some* KEK/key reference reaches the pod via ESO regardless of which O1 option is chosen; the open question is what the application does with it (local AES-GCM vs. a KMS call vs. delegating to a Crypto Service), not whether ESO is involved.
- **Decision-log convention:** `auth-decisions.md` entries (D-001…D-014) each follow a fixed shape — **Context**, **Selected** (or **Alternatives** + **Selected**), **Trade-offs**, **Reference influence/verdict**, occasionally **Impact** / **Revisit trigger**. Any new entry this task produces should match that shape (e.g., `D-015`).
- **Spec convention:** OPEN decisions (§4b) get resolved by moving the outcome into §4a LOCKED once decided, per how O2–O5 are structured relative to L1–L13; Open Questions (§11) get a `~~struck~~` note plus a `**Resolved (date):**` line, per the existing Q6 precedent in `package.md`.

## 4. Testing conventions

Not applicable — this task authors no code and the frozen brief for T01 already established the "no named §8 test → no new test file" pattern for spec/decision-only work. `package.md` §8 has no named test mapped to task 2 either.

## 5. Known gaps / unknowns

- **Guardrail conflict (flag, not silent).** Every generated phase prompt for T02 carries the fixed guardrail "Never modify the specification files under `spec/`," but the task's own literal deliverable is to update `spec/auth-service/design.md` and `package.md`. This is the same class of generator artifact documented in `AI_CONTEXT_ANALYSIS.md` (blanket `Contracts:` header, fixed Opus co-author trailer) — a template constraint that doesn't account for this specific task's nature. I do not resolve this now; it must be explicitly addressed at the Phase 4 human-approval gate, alongside the actual Q1 option decision.
- **I do not know** whether the intended resolution mechanism is a literal spec-file edit (as the task states) or an addendum/changelog approach that avoids touching `design.md`/`package.md` directly — that is itself part of what Phase 4 needs to settle with the author, not something to assume here.
- **I do not know** which of the three Q1 options (local AES-GCM via ESO-injected key, narrow KMS envelope call, or Crypto Service delegation) the author prefers. Phase 2/3 will lay out trade-offs; Phase 4 is where the human decides.
- **I do not know** whether `secret_encrypted BYTEA`'s existing "KMS-enveloped data key" comment is itself binding (since V1 is LOCKED/immutable) or merely descriptive of the original `target-design.md` proposal that Q1 was opened to challenge. This ambiguity should be surfaced, not silently assumed either way.
