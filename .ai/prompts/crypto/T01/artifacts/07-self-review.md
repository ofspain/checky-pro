<!-- MODEL: Claude Sonnet — Phase 7 (Self Review). -->

# crypto · T01 · Phase 7 — Self Review

Re-read all five touched files (`services/crypto/pom.xml`,
`docs/adr/0004-narrow-kms-exception-for-crypto-attestation.md`,
`services/crypto/src/main/resources/application.properties`, root `/pom.xml`,
`SECURITY-THREAT-MODEL.md`) against the frozen brief's AC1–AC5 and against sibling conventions in
`services/auth`, not just against my own Phase 5/6 plan.

## Issue found and fixed

**Dangling internal-tooling reference copied into committed source.** The Testcontainers-version
comment in `services/crypto/pom.xml` (copied from `services/auth/pom.xml`'s own comment) cited "see
docker-testcontainers-handshake-issue" — the title of an assistant memory note from an earlier
session, not a real, discoverable artifact in this repository. It was already a latent quality issue
in auth's own pom (pre-existing, not authored this task, out of scope to fix there), but mechanically
copying it into a second file compounds rather than inherits the problem. Rewrote the crypto copy to
state the reason plainly and point to `services/auth/pom.xml` itself (a real, readable file) instead
of the memory-note title. Re-ran `mvn -pl services/crypto validate` after the edit — still clean.

## Checked and found correct

- **ADR-0004's technical claim** ("`kms:Sign` ... on ciphertext-free, already-hashed receipt data")
  verified against `requirements.md` R20 directly: `POST /internal/v1/attest` is called "with a
  receipt digest" — a digest is exactly hashed data, so the ADR's framing is accurate, not asserted
  from memory.
- **`spring.application.name` convention**: compared `services/crypto/application.properties`
  against `services/auth/application.properties` — same property, same shape. Auth's file also
  carries datasource/JPA/Flyway/Kafka config that crypto's does not yet need (T02+ scope); the
  minimal T01 file is a correct subset, not an oversight.
- **Root `dependencyManagement`**: confirmed the root pom carries none, so crypto's own
  `dependencyManagement` block (AWS KMS BOM) is correctly self-contained rather than duplicating or
  conflicting with anything inherited.
- **Dependency versions** (ShedLock `7.7.0`, ArchUnit `1.3.0`, Awaitility `4.2.2`, Testcontainers
  `1.21.4`, AWS BOM `2.50.2`): all match `services/auth/pom.xml`'s own pinned versions exactly —
  intentional consistency, not coincidence.
- **AC1–AC4** re-verified directly against the current file contents (not assumed from the Phase 6
  notes): threats #1–#6 read `tracked` with a populated `Implementing task` cell each; root
  `<modules>` lists both services with the ordering comment; the new pom's dependency set matches the
  Phase 5 plan exactly (issuer starter and Flyway plugin both absent, web3j/Tron/KMS all present);
  `spring.threads.virtual.enabled=true` is set.

## Disposed, not fixed — documented reasoning

**`SECURITY-THREAT-MODEL.md`'s header (line 3) is now slightly stale.** It still reads "Status: stub
— must be completed before the first line of crypto-service code," but `services/crypto` now has a
buildable skeleton (pom + properties file). AC1 scoped the change to the table only (threats #1–#6 +
new column), and the frozen brief's Finding 1 disposition never named the header line. The header's
own claim is arguably still true in spirit — no *application* code exists, only build scaffolding —
and a table-only change keeps this task's diff minimal and matched to what was actually asked.
Leaving the header untouched; noting it here so a future task (plausibly T02, which adds the first
real schema/code) isn't surprised by it.

## Verification re-run

`mvn -pl services/crypto validate` — clean after the comment fix. No further build re-runs needed;
Phase 6's AC5 evidence (two `mvn -pl services/auth verify` runs, mechanism argument for
non-regression) stands unchanged since no file under `services/auth` was touched by this phase's
one edit.

---

**Phase 7 complete — self review done, one issue found and fixed.** Proceed to Phase 8 (Independent
Review) on approval.
