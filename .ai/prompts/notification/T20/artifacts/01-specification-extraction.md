# notification · T20 · Phase 1 — Specification Extraction

Consumes: `artifacts/00-repository-understanding.md`. This artifact contains no code; it is documentation only.

## Business rules (the task's own)

| ID | Rule |
|---|---|
| T20-1 | **Precondition, questions.** §11 questions are closed, "esp. Q1, Q2, Q3, Q4, Q7" (`tasks.md:41`). |
| T20-2 | **Precondition, tests.** Tests pass (`mvn -pl services/notification verify`, the same command as §9's last item). |
| T20-3 | **Action.** Status `DRAFT` → `READY FOR IMPL`, Version `0.1` → `0.2` (`package.md:6`, `:9`). |

## Requirements this task certifies (indirectly)

- **Every rule:** R1–R19 (`requirements.md`) through §9 item 1: "All §3 acceptance criteria have a passing named test from §8".
- **Every LOCKED decision:** L1–L11 (`design.md` §4a) through §9 item 2: "implemented as written (no silent deviation)".
- **§4c VERBATIM artifacts** through §9 item 3.

## Locked decisions relevant to the edit itself

- **The `agents.md` convention.** The spec references `agents.md` rather than restating it, and the header's "Standing rules" row stays as it is.
- **L2 and §4c.** Their current text conflicts with T07's human-sanctioned deviations (D1, D2). Any edit to them is a spec change, not a status change.
- **No other spec package may be touched.** That includes payment, auth and frontend.

## Files involved

| File | Status |
|---|---|
| `spec/notification-service/package.md` | **Certain.** Header; §9 checklist; §11 question resolutions. |
| `spec/notification-service/design.md` | **Possible.** O1/O2/O4/O5 resolution notes (O3 is already marked, `:21`); the §4c matrix and L2 alignment (FU-T07-3). |
| `spec/notification-service/requirements.md` | **Possible.** R3–R5 wording ("both parties", "receipt link"). |
| `services/notification/src/test/.../T01SkeletonRegressionTest.java` | **Possible.** Guard tests (payment T29 precedent). |
| Named-test additions | **Possible.** See OQ3 and OQ4. |

## Acceptance criteria (derived)

| AC | Criterion |
|---|---|
| AC1 | The header shows exactly `Version 0.2` and `Status READY FOR IMPL`. Nothing else in the header changes. |
| AC2 | Every §11 question Q1–Q7 carries a dated resolution that cites the implementing task and code. The resolution says plainly where it is partial or deviates (Q4, Q7). |
| AC3 | Every §9 item is either ticked with evidence, or left `[ ]` with a **Partial —** reason. Nothing is ticked that isn't true. |
| AC4 | The suite passes after the edits. |
| AC5 | A guard test pins AC1, and every test named in the §9 citations exists (the payment T29 pattern). |
| AC6 | No file outside `spec/notification-service/` and this module's tests changes. |

## Tests required

- A header-bump guard (version and status, header table only).
- A check that every cited test exists.
- A check that partial §9 items stay unticked.
- The full suite stays green.

## Open questions (Phase 4 decisions)

**OQ1 — Is the gate met, given Q4 and Q7?** Q4 is partial: the base-URL source is decided, the link paths are provisional, and there is no receipt link. Q7 is decided but deviates from §4c. The options:
- (a) Bump, with explicit partial resolutions (the payment T29 precedent: "READY FOR IMPL — disclosed gaps …").
- (b) Hold again until the receipt link exists (payment FU-T29-1).
- (c) Bump to a narrower status.

**OQ2 — How far may T20 edit the spec text?** FU-T07-3 put the alignment of the §4c matrix, L2 wording and R3–R5 here. The options:
- (a) `package.md` only: header, §9, §11. §11's Q7 and Q4 resolutions state the deviations, and `design.md` / `requirements.md` stay as authored.
- (b) Also add dated annotations to `design.md` (L2 interpretation, §4c recipient note, O1/O2/O4/O5 resolved) and to `requirements.md` (R3–R5 partial notes), without rewriting the original sentences.
- (c) Rewrite those sentences to match the code.

§4c is titled "VERBATIM artifacts — copy exactly", which argues against rewriting it.

**OQ3 — R15's missing named test.** Add `shouldNotLeakSecretsOrTokensIntoRenderedMessagesOrLogs` (a new test, or a named aggregate over the existing guards), or cite T10's tests as covering R15 under other names and leave §9 item 1 partial.

**OQ4 — T17's buildable payment half.** "Opt a recipient out of PAYMENT email → `SUPPRESSED`, in-app still delivered" for a payment event, end to end. Build it in T20 as test-only work, or leave it partial and cite the generic T08/T11 suppression tests.

**OQ5 — L11's named test.** `shouldPreventCrossModuleEntityImports` is a rule field plus a canary (the Surefire bug). Cite it as is (recommended), or rename.
