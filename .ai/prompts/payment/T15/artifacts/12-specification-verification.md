# payment · T15 · Phase 12 — Specification Verification

Consumes: `artifacts/11-test-review.md`

## Traceability matrix

| Requirement/Decision | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R19** (partial — the client mechanism; the full digest/decide/record flow is T16/T17's own scope) — obtain the receipt signature by calling `POST /attest`, never sign locally | Yes | `CryptoClient.java:133` (`attest(...)`) | `shouldSendCorrectBodyAndBearerTokenOnAttest`, `shouldReturnSignedOutcomeWhenAttestResponseIsSigned`, `shouldReturnBlockedOutcomeWhenAttestResponseIsBlocked`, + 7 Phase 11 strengthenings (lowercase outcomes, null-reason, per-field-missing, method/path, problem+json 409) | No | No |
| **L3** — this service never touches a blockchain or KMS; no chain client, no KMS signing client is imported; ArchUnit enforces it | Yes | `ArchitectureTest.java:185` (`shouldBanKmsAndChainClientImports`) | `shouldBanKmsAndChainClientImportsIsCheckedDuringStandardBuild` (canary) + `...ActuallyFailsAgainstAGenuineViolation` (negative proof, via `RogueKmsAndChainClientUser` + 3 stub classes) | No | No |
| **L1/L12** (continuity) — module boundaries, no cross-module entity import | Yes, unaffected | `CryptoClient` stays in `common` (Phase 2/3/4 decision); no entity touched by this task at all | `shouldPreventCrossModuleEntityImportsIsCheckedDuringStandardBuild` (re-run, unaffected, passing) | No | No. Disclosed explicitly: `design.md`/`package.md` both describe an `attestation` module owning this client; `CryptoClient` stays in `common` instead, for the reasons in `CryptoClient`'s own Javadoc (Phase 6) — a deliberate, recorded deviation from the spec's own module diagram, not a silent one. |
| **Error mapping** (task 15's own text: "with timeouts and error mapping") — `409`/`400`/`5xx`/connection-failure/timeout/malformed-response are all mapped to a defined exception, never silently swallowed | Yes | `CryptoClient.java` — `HttpClientErrorException.Conflict` → `AttestationRefusedException` (:142-143); every other `RestClientException` → `CryptoServiceUnavailableException` (:144-147); null/unrecognized outcome → `CryptoServiceUnavailableException` (:149-151, :162-164) | 10 of the 17 `CryptoClientTest` `attest` methods cover this directly | No | No |

## Principal-engineer review

**(1) Is the task fully complete?** Yes, for this task's own scope. `CryptoClient.attest(...)`
exists, is fully tested against every realistic response shape from the real, built
crypto-service, and the new L3 ArchUnit rule is enforced with a genuine positive+negative proof
pair. R19's own full flow (computing the digest, deciding what `BLOCKED`/`REFUSED` means for the
payment/invoice) is explicitly T16/T17's own scope, not this task's.

**(2) Does it satisfy every acceptance criterion (frozen brief, Phase 4)?** All six (AC1-AC6) are
satisfied: AC1/AC2 (`SIGNED`/`BLOCKED` mapping), AC3 (`409` → `AttestationRefusedException`), AC4
(`400`/`5xx`/connection-failure/timeout/malformed-response → `CryptoServiceUnavailableException`),
AC5 (the new L3 rule, proven to fail), AC6 (existing timeouts respected, re-proven for the new
method).

**(3) Does it violate any LOCKED decision?** No. L3 is the one LOCKED decision this task scopes,
and it is now enforced for the first time (previously an unwritten `pom.xml`-level convention
since T01). L1/L12 continuity holds — no entity, no cross-module import.

**(4) Remaining risks (carried forward, not blocking this task's own PASS):**
- **The `common`-vs-`attestation` module-placement deviation** (Phase 2/3/4, disclosed in
  `CryptoClient`'s own Javadoc) will keep looking like an oversight to anyone who reads
  `design.md`/`package.md`'s own module diagrams without also reading that Javadoc. Not a defect
  — a reasoned, recorded choice — but worth keeping in mind for whoever authors T16/T17/T18/T19
  (the rest of the "Attestation, receipts, and ledger" group), since those tasks will add more
  code that could plausibly also land in either `common` or a new `receipt`/`attestation`
  package; this task's own precedent (favor `common` for anything genuinely cross-feature) should
  be the one they follow unless a given class is genuinely single-module-only.
- **`AttestationRefusedException`'s carried message is a fixed, generic string on the real
  crypto-service today** (confirmed Phase 3/4/9) — a future change on the Crypto Service's own
  side to include a more specific reason would flow through this client automatically (it's
  passed through as raw text, never parsed), but no test here specifically exercises that future
  shape — appropriately, since inventing a test for a wire shape that doesn't exist yet would be
  speculative.
- **Rejected Kimi Phase 8 New Finding 4** (structured `ProblemDetail` parsing of the 409 body) —
  re-confirmed correct to reject at Phase 9 (contradicted the already-frozen Phase 4 decision);
  revisit only if a future task's own real requirement needs the parsed `detail` field
  specifically, not as a speculative improvement.

## Verdict

**PASS** — `CryptoClient.attest(...)` is fully implemented, tested against every realistic
response shape (including two full Phase 11 strengthening rounds), and the new L3 ArchUnit rule
is enforced with a genuine, verified-to-fail negative proof. The one spec-vs-implementation
deviation (module placement) is explicitly disclosed in code, not silent. `ArchitectureTest`:
7/7. `CryptoClientTest`: 32/32, confirmed stable across 3 consecutive runs. Full payment-service
suite: 316/316 passing.
