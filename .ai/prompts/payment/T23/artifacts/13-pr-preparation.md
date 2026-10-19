# payment · T23 · Phase 13 — PR / Commit Preparation

Consumes: `artifacts/12-specification-verification.md` (**PASS**)

No code in this artifact — documentation only, per this phase's own instructions.

**Branching note:** same established practice as every prior task this session — executed
directly on the long-running `spec/service-specs-and-ai-framework` branch, not a fresh per-task
branch off `main`. `main` itself was never touched and stays deployable.

## Commit title

`payment T23: ProblemTypes - stable RFC 9457 type catalogue (R29)`

## Commit message

```
payment T23: ProblemTypes - stable RFC 9457 type catalogue (R29)

Replaces every one of this service's twelve "about:blank" problem-detail emission sites with a
real, named, stable catalogue type (common/ProblemTypes, 11 constants, mirroring auth-service's
own already-built ProblemTypes shape and base URL exactly - the only cross-service precedent for
this pattern anywhere in the platform; crypto-service and notification-service have neither).
ResourceServerConfig's shared writeProblemJson helper now takes a URI type parameter rather than
hardcoding the literal, so its two callers (401, 403) each emit their own correct type.

Kimi's own Phase 3 design challenge caught a real self-contradiction in my own TIB: it claimed to
mirror auth-service's generic NOT_FOUND constant, then named it RESOURCE_NOT_FOUND with a
different URI - renamed to match auth-service exactly. Independent review (Phase 8) found the
CryptoServiceUnavailableException exception's only handler lives in a feature module
(InvoiceExceptionHandler) despite the exception itself living in common - disclosed via Javadoc
as a latent risk for a future second CryptoClient caller, not relocated (judged scope creep
beyond "add a type catalogue"). Also added a new ArchitectureTest rule banning any inline URI
construction for a problem type outside ProblemTypes itself - mutation-verified it independently
catches both realistic construction paths (URI.create and the URI(String) constructor), not just
one.

Test review (Phase 11) found real coverage gaps beyond the mechanical "about:blank" ->
<constant> swap: zero coverage existed anywhere for MALFORMED_REQUEST (invalid JSON body) or
INTERNAL_ERROR (the 500 catch-all's own no-leak guarantee) - both now have dedicated tests, the
latter stubbing a deliberately sensitive-looking raw exception message and asserting it never
reaches the response body.

Full suite: 461/461 passing (451 pre-task baseline + 10 new: 4 ProblemTypesTest, 2 ArchitectureTest,
and incremental assertion/test additions across InvoiceControllerTest/ExportControllerTest).

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_01X8S7DqTs5nXBPSMMnxQqch
```

## Files changed

**Production code:**
- `services/payment/src/main/java/com/themistra/payment/common/ProblemTypes.java` (new).
- `services/payment/src/main/java/com/themistra/payment/common/ApiExceptionHandler.java` —
  7 call sites updated; Javadoc refined to describe current behavior accurately.
- `services/payment/src/main/java/com/themistra/payment/invoice/InvoiceExceptionHandler.java` —
  3 call sites updated; Javadoc refined.
- `services/payment/src/main/java/com/themistra/payment/export/ExportExceptionHandler.java` —
  1 call site updated.
- `services/payment/src/main/java/com/themistra/payment/common/ResourceServerConfig.java` —
  `writeProblemJson` signature frozen to take a `URI type` parameter; both callers updated.
- `services/payment/src/main/java/com/themistra/payment/common/CryptoServiceUnavailableException.java` —
  new disclosed-risk Javadoc block (no behavior change).

**Tests:**
- `services/payment/src/test/java/com/themistra/payment/common/ProblemTypesTest.java` (new).
- `services/payment/src/test/java/com/themistra/payment/ArchitectureTest.java` — new rule
  `shouldNotInlineProblemTypeUris` + 2 tests.
- `services/payment/src/test/java/com/themistra/payment/archtestfixtures/RogueInlineProblemTypeUser.java`
  (new).
- `services/payment/src/test/java/com/themistra/payment/invoice/InvoiceControllerTest.java` —
  6 existing assertions updated; 4 new test methods.
- `services/payment/src/test/java/com/themistra/payment/export/ExportControllerTest.java` —
  8 new/updated `$.type` assertions across existing tests.
- `services/payment/src/test/java/com/themistra/payment/common/ResourceServerConfigTest.java` —
  1 assertion updated.
- `services/payment/src/test/java/com/themistra/payment/common/AccessDeniedHandlerTest.java` —
  1 assertion updated.

No migration file — this task touches zero persistence/schema.

## Summary

T23 closed a gap that had existed since this service's very first controller (T06): `agents.md`'s
own standing rule has always said "a stable `type` catalogue," but every handler added since then
used the generic `"about:blank"` placeholder instead, with the real catalogue deferred to this
exact task. The work itself was mechanical by design (Phase 3's own summary called it "a low-
risk, mechanical refactor"), but both review cycles still surfaced genuine, non-mechanical
findings: a self-contradiction in my own naming choice, a latent cross-module handler-placement
risk, a real bypass in the first draft of the new ArchUnit guard, and two previously-untested
error paths (`MALFORMED_REQUEST`, `INTERNAL_ERROR`) that had silently had zero coverage since the
day they were first added.

## Testing performed

- `mvn -o -pl services/payment -am compile`, `test-compile`: clean throughout every phase.
- `mvn -o -pl services/payment -am test -Dtest=ProblemTypesTest`: 4/4 passing.
- `mvn -o -pl services/payment -am test -Dtest=ArchitectureTest`: 9/9 passing.
- `mvn -o -pl services/payment -am test -Dtest=InvoiceControllerTest,ExportControllerTest,ResourceServerConfigTest,AccessDeniedHandlerTest`:
  51/51 passing.
- Mutation-testing this task: a deliberate duplicate catalogue URI (confirmed red, reverted); the
  ArchUnit rule's `new URI(String)` branch isolated from its `URI.create` branch to prove it's
  independently load-bearing, not redundant (confirmed red in isolation, restored, confirmed
  green).
- `mvn -o -pl services/payment -am test` (full suite, Docker up —
  `services/auth/compose.local.yaml`): **461/461 passing** (451 pre-task baseline + 10 new).

## Specification references

- **Task:** T23 — RFC 9457 errors (`spec/payment-service/tasks.md`).
- **Requirement IDs:** R29.
- **LOCKED decisions:** L10, L12 (both continuity).
- **Process decisions (not numbered requirements):** the exact 11-constant catalogue and base
  URI, the `NOT_FOUND` naming correction, the `writeProblemJson` signature, the governance rule
  for future types, and the ArchUnit hardening scope — all resolved via Phase 2/4/9, with human
  sign-off via `AskUserQuestion` at both human-approval gates (Phase 4, Phase 9).
