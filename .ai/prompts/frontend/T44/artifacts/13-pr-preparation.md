<!-- MODEL: Claude Opus 5 — Phase 13 (PR Preparation). Phase 12 verdict: PASS. -->

# frontend · T44 · Phase 13 — PR Preparation

## Commit title

```
test(frontend/T44): prove registration accepts 128 code points, at both ends and in both directions
```

## Commit message

```
Make R1's upper bound falsifiable. The clause was already written - RegisterScreen
evaluates `length <= ADVISORY_MAXIMUM_CODE_POINTS` and the constant has always been
128 - but the suite's table stopped at 13 code points, so deleting that half of the
comparison broke nothing. This task adds the rows and the pins that make it break.

Add three rows to the PASSWORDS table (128 ASCII, 128 astral, 129) and two cases.
The astral row is the one task 43 could not write: at the LOWER bound a String.length
check over-counts a short password INTO range and accepts what R1 excludes, while at
the UPPER bound the same over-count pushes a VALID password out - 128 emoji are 128
code points and 256 UTF-16 units, so `.length` reads 256 > 128 and marks a passphrase
invalid. A false reject is the worse half, because the visitor cannot see what the
client counted. Extend the table control to pin a row ON the maximum and a row above
it: a control naming only the minimum cannot see a maximum widened to 999.

Forbid `maxLength` and `minLength` on every design-system primitive, by a source scan
and on the rendered field. The attribute counts UTF-16 units, so `maxLength={128}`
truncates a valid 128-code-point passphrase to 64 characters BEFORE any handler runs,
and the advisory check then measures a value the visitor never typed - the browser
overruling the server, which is what R1 forbids the client to do. The independent
review held that the existing body-equality assertions would catch this. They do not:
`maxLength` constrains USER EDITING, not a programmatic value set, and every case in
the suite types through HTMLInputElement.prototype's value setter. Measured in jsdom -
with maxlength=128 on the element, a 256-unit value went in and 256 units came back.
The harness confirms it: the two mutants that add a cap die in exactly the two new
pins and nowhere else, out of 1946 cases.

Both pins assert an absence, so both carry a control that ships with them rather than
relying on a mutation run - the shape that left an earlier storage pin vacuous for
seven tasks.

No production file changed. The upper bound was correct and unmeasured; this task
measures it.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

> **Template note (carried from T43, still unfixed):** the phase 13 prompt asks for a
> `Claude Opus 4.8 (1M context)` trailer. The session's standing attribution is
> `Claude Opus 5`, which is what every commit in this task used. The prompt template is stale;
> flagged rather than followed.

## Files changed

| file | change |
|---|---|
| `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | +107 / −8 · three rows, the astral DOM case, the rendered no-cap pin with its probe control, the extended table control, three comments the task falsified |
| `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | +49 · the length-cap scan over `PRIMITIVE_FILES` and its control |
| `.ai/prompts/frontend/T44/artifacts/*` | the fourteen phase artifacts, the 35-row harness and five run logs |

**No production file is in the diff.** `RegisterScreen.tsx`, `passwordLength.ts`, the primitives, the
catalogue, the routes and the contract are all untouched.

## Summary

Task 44 is a measurement task. R1 bounds the password at *12 to 128 Unicode code points, as advisory
guidance only*; task 43 built the screen, the counter and the table and proved the 12. This task proves the
128, and in doing so closes the named test `registrationAcceptsTwelveAndOneTwentyEightCodePoints`, whose
own header had described its name as half a promise since the day it was written.

Three things came out of it that outlived the task:

1. **The counting bug inverts across the range.** Everything written before phase 4 assumed the upper bound
   was a mirror of the lower one. It is not: the same `.length` implementation accepts what R1 excludes at
   12 and rejects what R1 allows at 128.
2. **A length cap on a field is invisible to a behavioural suite.** Not one of 1946 cases could see it,
   because `maxLength` constrains user editing and tests set values programmatically. Two structural pins
   now hold it, each proven live by a mutant that dies in it alone.
3. **A long-standing flake was diagnosed.** FU-T42-1 had been sighted three times across two tasks. Two
   harness refusals in a row localised it: it is load-sensitive, reproducing only with the local Docker
   stack running (9 clean runs on a quiet machine, 3 flaky of 7 under load), always in the sign-in cases
   that count hand-overs and storage writes.

## Testing performed

| | |
|---|---|
| named suite | **45 / 45** |
| `screensRenderThroughDesignSystemPrimitivesOnly` | **65 / 65** |
| whole suite | **1945 / 1946** — one known environmental failure (this host's `oxlint@1.87.0` prints no summary line) |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| mutation harness, 35 rows | **four clean runs** (phase 9 ×2, phase 10, phase 12): 32/32 regressions killed, 0 broken, 2 canaries killed, 1 expected survivor (TR24), **0 disagreements**; the phase 10 and phase 12 runs are identical row for row |
| two refused runs | recorded, not hidden — the baseline guard declined to score against a flaky floor, which is what produced the FU-T42-1 diagnosis |

## Specification references

| | |
|---|---|
| **Task** | `spec/frontend/tasks.md` task **44** — *"Registration accepts 128 characters."* |
| **Requirement** | **R1** (`requirements.md:9-11`), the only requirement in scope |
| **LOCKED decisions** | **L12** (phase additivity, the governing one) · **L11** · **L6** · **L4** · **L3** · **L2** · **L14** · T43's **D6** |
| **Contract** | `contracts/api/auth.yaml` — `RegisterAccountRequest.password` is an uncapped string, which is why the client check is advisory by construction |
| **Named tests** | `registrationAcceptsTwelveAndOneTwentyEightCodePoints` **complete** · `registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints` → task 45 · `passwordLengthCountsCodePoints` → task 46 |
| **Open for the owner** | **OQ-T44-1** (the scan forbids on `EmailField` an attribute the contract caps at 254) · **FU-T42-1** (diagnosed, cause-in-code open) · T43's OQ-T43-6..11 · the stale phase 13 trailer |

**Branch:** `spec/service-specs-and-ai-framework`, 15 commits, pushed. `main` stays deployable — this task
adds tests only.
