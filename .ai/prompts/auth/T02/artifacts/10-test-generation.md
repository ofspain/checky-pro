# auth · T02 · Phase 10 — Test Generation

Consumes `artifacts/09-review-resolution.md`. No production code exists for this task (task
statement: decision-and-spec-update only, "before writing `mfa/` code"), and `package.md` §8 has no
named test mapped to task 2. No test file is written — consistent with the same pattern established
for T01's Phase 10.

## Test manifest

| Acceptance criterion (Phase 4, AC1–AC7) | Verification method | Result |
|---|---|---|
| AC1 — `D-01N` entry recorded (option B, full rationale) | Manual review of `auth-decisions.md` D-025 against the established D-001–D-024 shape (Context/Selected/Trade-offs/Impact/Reference-influence) | Verified present, Phase 6 |
| AC2 — new ADR documents scope/prohibition/rationale | Manual review of `docs/adr/0003-...md` Decision/Context/Consequences | Verified present, Phase 6; extended, Phase 9 |
| AC3 — `design.md` gains LOCKED entry, O1 resolved, §4c confirmed | `grep -n "L14\|O1"` against `design.md` | Verified present, Phase 6/9 |
| AC4 — `package.md` §11 Q1 resolved, Q6 pattern | `grep -n "Q1\."` against `package.md`; structural diff against Q6 | Verified present, Phase 6; corrected to match Q6 pattern exactly, Phase 9 |
| AC5 — `agents.md` D-010 line narrowed, not blanket-removed | Manual review of the edited Security-section line | Verified present, Phase 6; wording corrected, Phase 9 |
| AC6 — full technical decision (ciphertext layout, nonce/tag, rotation, config keys, local-dev story) | Manual review of ADR-0003's envelope table, local-dev paragraph, key-spec sentence, and D-025's rotation/config statements | Verified present and complete, Phase 6 + Phase 9 additions (local-dev key source, KeySpec, AAD/threat-model note) |
| AC7 — no file under `mfa/` created/modified | `git status --short -- services/auth/src/main/java/com/themistra/auth/mfa/` | Empty output, confirmed Phase 6 and re-confirmed Phase 7/8 |

## Cross-reference consistency check (not an AC, but load-bearing for correctness)

`grep -rn "D-025\|L14\.\|ADR-0003\|0003-narrow"` across all five touched files, re-run after Phase 9's
fixes — every reference still resolves to the same identifiers, no stale numbering (the corrected
`D-025`, not the originally planned `D-015`) survives anywhere.

## Why no automated test is added

- No application behavior exists to test — this task produces specification and decision-record text
  only.
- The eventual behavioral guarantees (seed never stored raw, decrypt round-trips, wrong/rotated key
  fails correctly) are exactly the "Testing implications" note now recorded in ADR-0003's Consequences
  (Phase 9, Finding 8) — that guidance is for task #16/#22's actual test suite, not this task's.
- `agents.md` testing conventions (unit/JUnit/fixed `Clock`, Testcontainers integration, contract
  tests) all presuppose runtime code; none apply to a documentation-only task.
