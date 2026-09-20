STATUS: RESOLVED

# auth · T02 · Phase 9 — Review Resolution

Human Approval gate. Approved by: femi (this session, eight individual decisions — see table below).
Consumes `artifacts/07-self-review.md` and `artifacts/08-independent-review.md`.

## Resolution log

| # | Finding | Source | Severity/Confidence | Human decision | Applied |
|---|---|---|---|---|---|
| 1 | Redundant "code...in application code" wording | Phase 7, confirmed Phase 8 | Low / High | Apply fix | `spec/auth-service/agents.md:46` — reworded to "no AWS SDK code in the service, except..." |
| 2 | `package.md` Q1 strikes the whole bold label, diverges from Q6 precedent | Phase 7, confirmed Phase 8 | Low-Medium / High | Apply fix | `spec/auth-service/package.md:148` — restructured to match Q6: label stays bold/unstruck, only the stale question text is struck |
| 3 | `design.md` O1 has the same label-strikethrough inconsistency | Phase 7, confirmed Phase 8 | Low / High | Apply fix | `spec/auth-service/design.md:22` — same restructure applied |
| 4 | ADR doesn't specify the local-dev key source, only Phase 5's planning prose does | Phase 8 (new) | Medium | Add to ADR now | ADR-0003 gained a "Local-dev key (version `0x00` only)" paragraph specifying a fixed 32-byte constant, `local`-profile-only, enforced by the existing L13 startup guard |
| 5 | `L14` doesn't mention the local-dev fallback exists | Phase 8 (new) | Medium | Append fallback note to L14 | `design.md` L14 gained one sentence pointing at the ADR's local-profile fallback |
| 6 | ADR doesn't state `GenerateDataKey`'s key spec (256-bit) | Phase 8 (new) | Medium | Add to ADR now | ADR-0003 Decision section now states `KeySpec.AES_256`, 32-byte plaintext data key, explicitly |
| 7 | No AAD binding ciphertext to account/enrollment identity | Phase 8 (new) | Low-Medium | Document the threat-model assumption (not add AAD) | ADR-0003 gained an "AAD / threat model" paragraph explicitly scoping DB-write-tampering as out of scope, tied to the service's non-custodial/web2 posture (`agents.md`) |
| 8 | Forward test guidance lives only in the frozen brief, not a durable record | Phase 8 (new) | Low | Add "Testing implications" note to ADR | ADR-0003 Consequences gained a bullet restating the future task #16/#22 regression-test guidance |

All eight items were accepted; none were rejected. No item required a design change beyond what was
already decided in Phase 4/5 — all eight are clarifications/completions of the existing decision, not
new trade-offs.

## Verification after fixes

- `git diff --stat`: four files touched by this phase's fixes (`docs/adr/0003-...md`,
  `spec/auth-service/{agents,design,package}.md`) — matches the eight items above exactly, no
  unrelated changes.
- Re-grepped for the old defective patterns (`no AWS SDK code in application code`, `~~O1`,
  `~~**TOTP seed encryption KMS`) — zero matches remain; all three presentation fixes landed cleanly.
- Re-read `docs/adr/0003-narrow-kms-exception-for-totp-seed-encryption.md` in full: Decision, Context,
  and Consequences sections are internally consistent; the local-dev paragraph, key-spec sentence, AAD
  note, and testing-implications bullet all read coherently alongside the original text, no
  contradictions introduced.
- Cross-references (`D-025`, `L14`, `ADR-0003`) still resolve consistently across all five
  spec/decision files — unaffected by this phase's edits, which only added detail, not new references.

## Carried forward, not resolved here (unchanged from Phase 4)

- Task #16: TOTP seed entropy/length; `TotpGenerator`/`MfaSeedEncryption` implementation against this
  ADR.
- Task #18: recovery-code length/encoding.
- Task #13 / #20: whether failed MFA attempts feed the brute-force lockout counter.
- Infra/CDK: enable KMS automatic annual rotation on the CMK; scope the IRSA role as ADR-0003 specifies.
