# notification · T16 · Phase 9 — Review Resolution

**Human Approval gate.** Resolution log for the Phase 7 self-review (2 findings) and Phase 8
independent review (5 findings — 2 concur with self-review, 3 new). Every Phase 8 factual claim was
re-verified directly against actual source before disposition, not accepted on word — one citation
error was caught doing so (Finding 3, below).

## Finding 1 (self-review + Kimi concur) · A FROZEN file path was amended after the freeze

**ACCEPTED, no code change — process note only.** Both reviews agree the Phase 5 correction
(`RogueHttpClientUser.java`: `archtestfixtures` → `channel`) is right on the merits — confirmed
again directly: `shouldMakeNoSynchronousCrossServiceCall`'s subject condition genuinely would not
select a fixture at the original frozen path. Surfacing to the user as a standing process question
(should a Phase-5 path correction to a FROZEN output require a fresh freeze?) rather than deciding
it unilaterally here.

## Finding 2 (self-review + Kimi concur) · Frozen constraint text assumes `@AnalyzeClasses`, which no longer exists

**ACCEPTED, no code change.** Both reviews agree the constraint's substance (one shared,
eagerly-initialized `JavaClasses` instance for every canary) still holds; only its literal wording,
which names an annotation this class no longer has, is stale. Already explained in the class's own
Javadoc — no further change needed.

## Finding 3 (Kimi, new) · The L2 negative-proof assertion is weaker than sibling precedent — with a citation correction

**ACCEPTED and fixed**, but Kimi's own supporting citation was wrong and had to be corrected before
accepting the recommendation. Kimi attributed the dual-assertion pattern ("assert both the violating
class name and the target dependency's name") to `services/auth`'s entity negative-proof test,
naming `RogueWatchEntityReferencer`/`TokenAllowlist`. Verified directly: `services/auth`'s own
`ArchitectureTest.java` has **no negative-proof test at all** for `shouldPreventCrossModuleEntityImports`
— only its canary. `RogueWatchEntityReferencer`/`TokenAllowlist` actually live in **`services/crypto`'s**
`CrossModuleEntityArchitectureTest.java` (line 150, `.importClasses(RogueWatchEntityReferencer.class,
TokenAllowlist.class)`; lines 155–156, `.hasMessageContaining("RogueWatchEntityReferencer")` **and**
`.hasMessageContaining("TokenAllowlist")`) — misattributed to the wrong sibling service.

The underlying recommendation survives the correction anyway, on stronger grounds than Kimi cited:
`services/crypto`'s own `KmsSignerArchitectureTest` does the identical dual-assertion (confirmed,
lines 171–180: both `RogueAttestReferencer`+`KmsSigner` and `RogueAttestReferencer`+`KmsClient`), and
— more directly — **this very file's own L11 negative-proof test already does it**
(`shouldPreventCrossModuleEntityImportsActuallyFailsAgainstAGenuineViolation` asserts both
`"RogueChannelEntityReferencer"` and `"DeliveryLog"`). The L2 negative-proof test asserting only
`"RogueHttpClientUser"` was an internal inconsistency within this same class, not just a missed
external precedent.

**Change:** `shouldMakeNoSynchronousCrossServiceCallActuallyFailsAgainstAGenuineViolation` now also
asserts `.hasMessageContaining("RestTemplate")`, naming the actual dependency the rule fired on, not
just the class that depends on it — ruling out the scenario Kimi correctly raised (an unrelated
`AssertionError` that happens to contain the class's own name would no longer pass this test).
Verified: `ArchitectureTest` still 6/6 after the change.

## Finding 4 (Kimi, new) · Removal of `@ArchTest`/`@AnalyzeClasses` is a material departure — recommends repo-wide tracking

**ACCEPTED as a correct characterization; tracking action deferred, not taken here.** Kimi could not
independently verify the Surefire claim itself (no `mvn` in its own environment, honestly disclosed)
but correctly read the Javadoc's own claim and correctly flagged it as repo-wide, not task-local. The
underlying finding was already independently, empirically verified before Kimi's review — three
isolated repro classes run through real `mvn test`, plus Surefire's own exact forked classpath fed
directly into the real JUNit Platform `Launcher` API outside Surefire (see Phase 6 notes and
[[surefire-archtest-field-bug]] memory). Per the user's own explicit, already-given instruction
(asked directly, mid-pipeline, before Phase 6 was written): fix notification's T16 only, raise
auth/crypto as a separate finding rather than retrofitting them in this task. Kimi's suggestion to
open a repo-wide issue/ADR is reasonable but is new scope beyond what the user asked for — not
actioned without asking first.

## Finding 5 (Kimi, new) · The L2 rule's name reads narrower than its actual breadth

**ACCEPTED as accurate, no change — informational only, as Kimi itself scored it.** Confirmed
directly: the rule bans generic HTTP-client packages broadly (`RestTemplate`,
`WebClient`, `java.net.http`, Apache HttpClient), not only literal sibling-service calls — exactly
as the frozen brief's own Finding #2 disposition already chose (broader scope, kept deliberately).
The existing `because(...)` text and class Javadoc already explain this; a rename isn't required to
satisfy any acceptance criterion, and the frozen brief's own naming was already human-approved.

## Summary

One real code change applied: `RestTemplate` added to the L2 negative-proof's own assertion
(Finding 3), after correcting a citation error in Kimi's own supporting evidence first. Every other
finding is documentation/process-only or already correctly handled, per both reviews' own agreement.
Full suite re-verified: `mvn -pl services/notification clean verify` — 368 tests, 0 failures, 0
errors (unchanged count — this phase only strengthened an existing assertion, added no new test
method).
