# auth · T02 · Phase 7 — Self Review

Consumes `artifacts/06-implementation-notes.md`. Reviews the diff across `docs/adr/0003-...md`,
`services/auth/docs/architecture/auth-decisions.md`, `spec/auth-service/{design,package,agents}.md`
against the frozen brief and `agents.md`. No code exists in this diff, so most of the standard
checklist categories (null-safety, thread-safety, transaction boundaries, module boundaries,
idempotency, money types) are **N/A**. Findings below are the ones that do apply: correctness,
enumeration-safety/secret-handling, readability, and internal consistency.

## Findings

### 1. Redundant wording introduced in `agents.md`

**Issue:** The edited line reads "no AWS SDK code in application code, **except** ..." — "code" is
used twice in immediate succession, reading awkwardly. The original phrase was "no AWS SDK
secret-retrieval in application code"; removing "secret-retrieval" without restructuring the
sentence left a duplication rather than a clean edit.
**Severity:** Low
**Evidence:** `spec/auth-service/agents.md:46`
**Recommendation:** Reword to match `auth-decisions.md` D-010's own canonical phrasing — "no AWS SDK
code in the service, **except** a single scoped KMS `GenerateDataKey`/`Decrypt` call inside
`mfa.MfaSeedEncryption`..." — dropping the redundant second "in application code."

### 2. `package.md` Q1 resolution strikes the bold label, diverging from the Q6 precedent in the same section

**Issue:** The established convention in this exact section (§11) is Q6: the bold topic label stays
visible and unstruck (`**Agents / standing rules file.**`), and only the stale descriptive sentence
that follows it is struck through. The Q1 edit instead strikes the entire bold label
(`~~**TOTP seed encryption KMS approach.**~~`), so anyone scanning §11's bold labels for topics loses
Q1's label entirely — inconsistent with Q6, five lines below it in the same list.
**Severity:** Low-Medium (direct precedent violated within the same file/section, reduces scannability)
**Evidence:** `spec/auth-service/package.md:148` vs. the Q6 precedent at `spec/auth-service/package.md:153`
**Recommendation:** Restructure to: `Q1. **TOTP seed encryption KMS approach.** ~~<original question
text>~~ **Resolved (2026-07-22):** ...` — keeping the bold label unstruck, matching Q6 exactly.

### 3. `design.md` O1 resolution has the same label-strikethrough inconsistency

**Issue:** Same pattern as Finding 2, applied to `design.md` §4b: the original bold label ("TOTP seed
encryption implementation.") is struck along with the rest of the line, rather than kept as an
unstruck heading with only the stale body text struck.
**Severity:** Low (no precedent exists within §4b specifically, since no other OPEN decision has been
resolved yet in this file — but it should still match the Q6/§11 convention for consistency across the
package)
**Evidence:** `spec/auth-service/design.md:22`
**Recommendation:** Restructure to: `O1. **TOTP seed encryption implementation.** ~~<original
proposal text>~~ **Resolved (2026-07-22):** see L14...` — same fix as Finding 2, applied here too.

## Checked and confirmed correct (no issue)

- **Cross-reference correctness:** `D-025`, `L14`, and `ADR-0003` all resolve consistently across
  all five touched files (re-verified by grep during this review, matching Phase 6's own verification).
- **Secret-handling:** no literal secret or credential value was introduced anywhere in this diff —
  only the existing `${MFA_SEED_KEK_ARN:}` placeholder syntax, references to a KMS `KeyId` parameter
  name, and IAM action names (`kms:GenerateDataKey`, `kms:Decrypt`). Nothing here violates L13.
- **Enumeration-safety:** N/A — no user-facing endpoint behavior in this task's diff.
- **Scope discipline:** confirmed no file outside the five authorized targets was touched (`git diff
  --stat` matches the frozen brief's Files to Modify/Create exactly); nothing under `mfa/` or
  `V1__auth_baseline_schema.sql` was touched.
- **`D-025` numbering correction (from Phase 6):** re-confirmed correct — `D-024` is genuinely the
  last existing entry before this diff; `D-025` is the correct next id.
