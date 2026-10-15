# payment · T15 · Phase 4 — Freeze Task Brief

**STATUS: FROZEN** — approved as written via `AskUserQuestion`.

Consumes: `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md`

## Disposition of Phase 3 (Kimi) findings

| # | Finding | Disposition | Reason |
|---|---|---|---|
| 1 | Medium — `SIGNED`/`BLOCKED` should be a sealed interface, not a nullable-field record | **ACCEPTED** | Correct: a nullable-field record lets a caller read `signature()` on a `BLOCKED` outcome and get a silent `null` instead of a compile-time guarantee. `AttestationResult` sealed interface (`Signed`/`Blocked` records) replaces the brief's own nullable-record proposal. |
| 2 | Medium — rogue ArchUnit fixture can't compile; banned SDKs aren't on the classpath | **ACCEPTED** | Verified directly against `services/payment/pom.xml`: confirmed no `web3j`, `tron`, or `kms` dependency exists (only `software.amazon.awssdk:s3`). Minimal stub classes under the banned package names in `src/test/java` (e.g. `org.web3j.StubWeb3jClass`) let the negative-proof test compile without adding real SDK weight — the standard technique for this exact situation, no better alternative exists. |
| 3 | Low — `AttestationRefusedException` should carry the 409 body's detail | **ACCEPTED, with a factual correction to the stated rationale** | Verified directly against `services/crypto/.../AttestationRefusedException.java`'s own Javadoc and constructor: the real message is **always** the fixed, generic string `"Attestation preconditions not met for {chain}:{txHash}"` — by explicit upstream design (that class's own Phase 4 Finding #15, enforcing `agents.md`'s "no internal detail" leak policy). It **never** actually contains "quorum/finality unmet" or "screening error" detail as Kimi's finding claims — carrying it over will NOT give T17/operators that level of diagnosis. Still worth doing: it at least confirms which `chain:txHash` was refused, useful for log correlation, and costs nothing. Accepted for that narrower reason, not Kimi's stated one. |
| 4 | Low — brief is silent on `400` responses | **ACCEPTED, Option 1** | A `400` (bean-validation failure on crypto-service's side — bad hex digest or unsupported chain) is wrapped in the existing `CryptoServiceUnavailableException`, same as any other non-2xx/non-409 response. Consistent with this client's established "trust the caller, no local validation" stance (mirrors `registerWatch`). Documented explicitly in AC4 below, closing the silent gap. |
| 5 | Low — module-placement deviation should be documented | **ACCEPTED, with a scope correction** | Kimi's own suggestion to "add a short note to `package.md`" is rejected — every phase's own standing guardrail is explicit: "Never modify the specification files under `spec/`." Applying only the code-side half: a Javadoc note on `CryptoClient` itself records the deviation (already drafted in the Phase 2 TIB's own "Module placement decision" section; Phase 5 carries it into the actual class Javadoc). |

No finding was rejected outright. Finding 3 is accepted for a narrower, source-verified reason than Kimi stated — disclosed here, not silently substituted.

## Amendments folded into this frozen brief (supersede the Phase 2 TIB where they conflict)

- **Outputs** (supersedes TIB's "one record with nullable fields" proposal):
  ```java
  public sealed interface AttestationResult {
      record Signed(String signature, String kmsKeyId, Instant signedAt) implements AttestationResult {}
      record Blocked(String reason) implements AttestationResult {}
  }
  ```
  `CryptoClient.attest(...)` returns `AttestationResult`.
- **`AttestationRefusedException`** gains a constructor/field carrying the 409 response body's
  content (best-effort — read as a plain `String`, not a strict `ProblemDetail` parse, since the
  exact body shape isn't contract-tested per `crypto-internal.yaml`'s own disclosed scope limit).
  Its own Javadoc must disclose that the carried text is expected to be a fixed, generic string on
  the real crypto-service today — set correct caller expectations, don't imply more diagnostic
  value than actually exists.
- **AC4 (400 handling, now explicit):** a `400` response is treated identically to any other
  non-2xx/non-409 response — wrapped in `CryptoServiceUnavailableException`. No new exception type
  for it.
- **New Files to Create addition:** `src/test/java/org/web3j/StubWeb3jClass.java`,
  `src/test/java/org/tron/StubTronClass.java`,
  `src/test/java/software/amazon/awssdk/services/kms/StubKmsClass.java` — minimal, empty marker
  classes existing solely so the ArchUnit negative-proof rogue fixture can compile a real import
  statement against each banned package, without adding any real SDK dependency to the module.
- **`CryptoClient` Javadoc addition:** a note recording the module-placement decision (already
  drafted in the TIB), so a future reader doesn't mistake the `common` placement for an oversight
  against `design.md`/`package.md`'s own module diagrams.

## Final scope, files, and acceptance criteria (carried from Phase 2 TIB, as amended above)

**Scope:** unchanged from Phase 2 (`attest` method + DTOs + `AttestationResult` + the new L3
ArchUnit rule). **Out of scope:** unchanged (no `ReceiptDigest`, no `ReceiptService` logic, no S3/
ledger/export work).

**Files to Create:**
- `common/crypto/AttestRequest.java`
- `common/crypto/AttestResponse.java` *(wire-shape-mirroring DTO used internally by `CryptoClient`
  to deserialize the raw JSON before mapping into `AttestationResult` — not itself returned to
  callers)*
- `common/crypto/AttestationResult.java` (sealed interface, `Signed`/`Blocked`)
- `common/AttestationRefusedException.java` (with the 409-body-text field, per Finding 3)
- `src/test/java/org/web3j/StubWeb3jClass.java`
- `src/test/java/org/tron/StubTronClass.java`
- `src/test/java/software/amazon/awssdk/services/kms/StubKmsClass.java`
- An ArchUnit rogue fixture (package TBD at Phase 5, likely `archtestfixtures`, importing the
  three stubs above) proving the new L3 rule genuinely fails.

**Files to Modify:** `common/CryptoClient.java` (new `attest` method + Javadoc per Finding 5),
`ArchitectureTest.java` (new L3 rule + paired canary/negative tests), `CryptoClientTest.java`
(new `attest` coverage).

**Files NOT to Modify:** unchanged from Phase 2 (`CryptoClientProperties.java`, `registerWatch`/
`unregisterWatch` and their tests, anything under `spec/`, anything outside `services/payment`).

**Acceptance Criteria** (AC1/AC2/AC5/AC6 unchanged from Phase 2; AC3/AC4 amended):
- AC1 (R19 partial): `200 SIGNED` → `AttestationResult.Signed`.
- AC2 (R19 partial): `200 BLOCKED` → `AttestationResult.Blocked`, not thrown.
- AC3 (amended): `409` → `AttestationRefusedException`, carrying the response body's text
  best-effort.
- AC4 (amended, closing Finding 4): `400`, `5xx`, connection failure, timeout, or a malformed/
  unparseable response → `CryptoServiceUnavailableException`, unchanged from today.
- AC5 (L3): new ArchUnit rule, proven to fail against the three stub-backed rogue imports.
- AC6: existing configured timeouts respected, no new config.

## Open Questions

None remaining. Both Phase 1 open questions were resolved in Phase 2 and stress-tested without
reversal in Phase 3 (module placement: kept in `common`, Finding 5 only asked for documentation,
not relocation; `REFUSED`-vs-unavailable split: confirmed correct design in Phase 3's own
Positive Confirmations).
