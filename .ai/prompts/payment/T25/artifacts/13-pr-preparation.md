# payment · T25 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**)

This artifact is documentation only; there is no code in it.

**Branching:** as in every prior task in this pipeline, the work ran on the long-running
`spec/service-specs-and-ai-framework` branch, not a fresh per-task branch off `main`. `main` was
never touched and stays deployable.

**Trailer:** the template names "Claude Opus 4.8 (1M context)". The trailer below names the
model that actually did the work, following the T23 and T24 precedent.

## Commit title

`payment T25: ArchUnit public-endpoint allowlist rule; confirm L3/L6/L12 guards`

## Commit message

```
payment T25: ArchUnit public-endpoint allowlist rule; confirm L3/L6/L12 guards

Add shouldEnforcePublicEndpointAllowlist to ArchitectureTest: no class in
com.themistra.payment other than ResourceServerConfig may call
AuthorizedUrl.permitAll() (L10). It mirrors the identical rule in auth and
notification. It is the stray-caller guard only: PublicEndpointsTest still
owns the allowlist contents and the single scoped call inside
ResourceServerConfig.

Add three plain @Test methods, with no @ArchTest because of the Surefire 3.5.3
bug:
- a real-code canary
- a violation canary against the new RoguePermitAllUser fixture, covering a
  direct call and the realistic lambda form
- a load-bearing test proving ResourceServerConfig contains exactly one real
  permitAll() call, so the canary cannot pass vacuously

The L12, L6 and L3 rules already existed (T09, T05, T15) and are confirmed
unchanged and green. Test-only: no production code changed.

Full suite: 493/493 passing (490 baseline + 3).

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

## Files changed

**Tests:**
- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java`
  - new rule, with Javadoc
  - 3 new tests
  - 3 new imports, plus an `assertThat` static import
- `services/payment/src/test/java/com/themistra/payment/archtestfixtures/RoguePermitAllUser.java`
  (new): the violation fixture, with a direct call and a lambda call

**Production code:** none.

**Pipeline artifacts:** `.ai/prompts/payment/T25/artifacts/00`–`13`.

## Summary

T25 is the module-boundary hardening close-out.
- **Three of its four guards already existed:** L12 (T09), L6 (T05) and L3 (T15). Each was
  re-confirmed with its own canaries.
- **The L10 gap is now closed:** a `permitAll()` call outside `ResourceServerConfig` is caught at
  build time.
  - Previously, `PublicEndpointsTest`'s source sweep read only that one file.
  - Auth and notification already had this ArchUnit rule.

**Human decisions that shaped it:**
- **Phase 4:**
  - Kept the sibling-parity rule name, with Javadoc on its scope and limits.
  - Covered only `permitAll()`, not `anonymous()` or `web.ignoring()`, and documented that.
  - Deferred the layering rule and the `@PreAuthorize` sweep.
- **Phase 9:** tightened the violation assertion, and added the load-bearing test.
- **Phase 11:** added the lambda violation case, and pinned exactly one real call.

**Open follow-ups (human-owned, out of T25's scope):**
1. **FU-1.** `agents.md:32` says ArchUnit enforces `api → application → domain` within each
   module. No service has that package structure, so the rule is unenforced platform-wide.
   Either reword the rule or create a restructuring task.
2. **FU-2.** Add an ArchUnit sweep requiring `@PreAuthorize` on every `@RestController` handler.
   Every handler has one today.

## Testing performed

- **Full suite:** `mvn -o test` in `services/payment`, with Testcontainers Postgres and Kafka,
  passes **493/493**. The pre-task baseline was 490.
- **`ArchitectureTest`:** 12/12. All tests appear by name in the surefire XML.
- **Mutation checks** (temporary edits, each reverted and the restore confirmed):

  | Phase | Edit | Effect |
  |---|---|---|
  | 6 | Remove the `ResourceServerConfig` exclusion | The real-code canary fails at `ResourceServerConfig.java:57` |
  | 9 | Break the load-bearing predicate and the violation assertion | Each one's own test fails |
  | 11 | Turn the lambda call into `denyAll()`, and change `hasSize(1)` to `hasSize(2)` | Each one's own test fails |

## Specification references

- **Task:** `spec/payment-service/tasks.md` task 25 (`:43`).
- **Requirements:** none scoped. R28 and R4 are continuity only (Phase 1).
- **LOCKED decisions:**
  - L3, L6 and L12: confirmed.
  - L10: strengthened.
  - All from `design.md` §4a, `:7-16`.
- **Named tests (`package.md:109-110`):**
  - `shouldPreventCrossModuleEntityImports`
  - `shouldRejectFloatingPointMoneyTypesAtArchUnit`
- **Standing rules:**
  - `agents.md:57` (the ArchUnit layer)
  - `agents.md:73` (the chain and KMS ban)
  - `agents.md:32` (layering): deferred as FU-1
