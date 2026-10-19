# payment · T23 · Phase 9 — Review Resolution

Consumes: `artifacts/07-self-review.md`, `artifacts/08-independent-review.md`

Approved by the human via `AskUserQuestion`, 2026-10-19 ("Approve as proposed").

## Disposition of Kimi's 6 Phase 8 findings

| # | Finding | Disposition | Exact change made |
|---|---|---|---|
| 1 | AC2 doesn't distinguish code from Javadoc comments | **REJECTED, with direct citation** | The frozen brief's own AC2 text (Phase 4, `04-frozen-task-brief.md` line 82) already reads "Zero remaining literal `\"about:blank\"` in any **executable** `setType(...)`/`writeProblemJson(...)` call" — explicitly scoped to executable code, not the generic/unscoped AC2 wording Kimi's finding assumes. Kimi reviewed against a stale or pre-amendment understanding of AC2, not the actual frozen text. No change needed; the two historical Javadoc mentions remain by design. |
| 2 | No `ProblemTypesTest` exists yet | **ACKNOWLEDGED, no action** | Already correctly scheduled for Phase 10 (named explicitly in Phase 1/2/4/5's own test lists) — not a Phase 6/7/8 defect. |
| 3 | `CryptoServiceUnavailableException`'s only handler lives in `InvoiceExceptionHandler` despite the exception itself living in `common` | **ACCEPTED, documented not relocated** | Added a disclosed-risk Javadoc block to `CryptoServiceUnavailableException` itself explaining the latent gap (a future second `CryptoClient` caller outside `invoice` would silently lose 503/catalogue-type semantics) and why the handler wasn't moved (relocating it is scope creep beyond "add a type catalogue" — R29 is about the catalogue, not handler reorganization). |
| 4 | 7 existing tests still assert the old `"about:blank"` type | **ACKNOWLEDGED, no action** | Already correctly scheduled for Phase 10 — confirmed, not new information (Phase 0/4/6 all already named these exact 7 sites). |
| 5 | No automated guard against a future inline `URI.create(...)` | **ACCEPTED** | New `ArchitectureTest` rule `shouldNotInlineProblemTypeUris` (`noClasses().that().resideInAPackage(...).and().doNotBelongToAnyOf(ProblemTypes.class).should().callMethod(URI.class, "create", String.class)`), mirroring the existing L6/L3/L12 rule pattern in the same file exactly. New fixture `RogueInlineProblemTypeUser` (deliberately calls `URI.create(...)`) proves the rule can actually fail, not just pass on clean code — the same "canary + genuine-violation" test pair every other rule in this file already uses. |
| 6 | Named test `shouldReturnProblemJsonWithNoInternalDetailOn4xx` doesn't exist yet | **ACKNOWLEDGED, no action** | Already correctly scheduled for Phase 10. |

## Verification performed this phase

- `mvn -o -pl services/payment -am test-compile`: clean.
- `mvn -o -pl services/payment -am test -Dtest=ArchitectureTest`: **9/9 passing** (7 prior + 2
  new — the canary proving the new rule holds on real code, and the genuine-violation test
  proving `RogueInlineProblemTypeUser` actually fails it).
- `mvn -o -pl services/payment -am test` (full suite, Docker up): **453 tests total, 446
  passing, 7 failing** — exactly, and only, the same 7 pre-identified sites from Phase 6,
  confirming neither accepted fix (the documentation addition, the new ArchUnit rule)
  introduced any regression.
