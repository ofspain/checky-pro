<!-- MODEL: Claude Opus 5 — Phase 9 (Review Resolution) -->

# frontend · T173 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T173 — Design system installed |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` (16 findings) |
| **Produces** | `artifacts/09-review-resolution.md`, `artifacts/09-mutation-run.txt` |

## Gates after resolution

| Command | Result |
|---|---|
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1780 / 1781** — the known host `oxlint` case |
| `vitest run` (the two suites whose comments changed) | **84 / 84** |
| `python3 -I artifacts/07-mutation-harness.py` | **12/16 killed, 0 BROKEN, 4 survivors, 1 canary killed, 2 disagreements** |

**`broken: none` is now true.** Phase 7 printed the same words while RD11 was a syntax error; the number that
changed is not the score but whether the score means anything.

---

## 1 · Every finding, and what happened to it

| | finding | verdict |
|---|---|---|
| **I1** | the four corrected ratios are arithmetically right | **confirms F1.** Kimi recomputed independently — 17.49 / 7.94 / 6.71 / 5.89 — rather than taking the corrected file on trust. That is the check that would have caught a wrong fix |
| **I2** | RD11 is broken; the harness has no broken-detection | **ACCEPTED · FIXED** — §2 |
| **I3** | the environmental set is keyed by file, masking real kills | **ACCEPTED · FIXED** — §2 |
| **I4** | the FAIL-line regex truncates `.tsx` to `.ts` | **ACCEPTED · FIXED** — §2 |
| **I5** | RD15 survives; the classes-only regex anchors only the start | **confirms F6 · D4 CORRECTED** — §3 |
| **I6** | the R30 pin was grown correctly | confirmation; no action |
| **I7** | the `Only` sweep's "thirteen" is ambiguous | **confirms F7 · phase 10's**, with the construction named |
| **I8** | **NEW** — two closed suites still call `AppShell` "a bare div" | **ACCEPTED · FIXED** — §4 |
| **I9** | `RateLimitNotice` has no production consumer | confirms F8; no action (§5 excludes new mounting) |
| **I10** | `Surface` keeps `data-testid` as a prop | confirmation |
| **I11** | `FocusHolder`'s required children are justified | confirmation of phase 4's rejection of challenge F11 |
| **I12** | the bundle allowlist admits `https://tailwindcss.com` | confirmation; *"a clean way to turn an allowlist into a pin that also requires"* |
| **I13** | `shellBuildsAndMounts` asserts no CSS asset | **ACCEPTED as phase 10's** — verified: `:54-91` filters `.js` only |
| **I14** | the primitive layer follows its own three rules | confirmation |
| **I15** | the long single-line `className` is the right trade | confirmation |
| **I16** | the host could not reach npm | informational — §6 |

**Nothing was rejected, and that is itself worth recording.** Every prior gate in this package has turned up
at least one finding resting on a false premise — T41's I10, T37's pattern aimed at nothing, T29's claim about
account-state words. All sixteen citations here were checked against source before acceptance and all sixteen
were accurate. The standing rule did its job in the other direction for once.

## 2 · The harness: three fixes, and the one that mattered

**I2 — RD11 was a syntax error, and the guard against that is one line.** The old row spliced a ternary into
an expression position, making `return ( { … } )`, where `{` opens an object literal. 23 suites failed to
**load**; the raised failure count was credited as a kill. The fix has two halves:

- **The row** is now an early return — `if (children === null) return null;` before the existing `return (`.
  It compiles, and **it measures its own description**: 12 cases in
  `rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx` die, led by *"puts the copy in a `role="status"`
  region that exists and is empty before the 429 (AC5, L11)"* — the case that pins the exact rule
  `StatusRegion` was built to centralise. Phase 7 left that regression unmeasured while reporting it killed.
- **The guard** is `total < base_total → BROKEN`. A file that cannot be parsed contributes **no tests**, so
  the total drops; the failure count alone cannot tell "a case caught it" from "nothing ran". This is the
  guard T36 needed too, which is why the harness comment now names both tasks.

The row also states what it does **not** cover: `children === null` is true only where the consumer passes a
single expression, so it bites `RateLimitNotice` and never `SignInScreen`, whose three ternaries make
`children` an array of nulls. **No row covers the sign-in screen's region**, and saying so is better than a
kill count that implies otherwise.

**I3 — attribution is now by case name.** The baseline run records which cases already fail
(`["passes today's frontend/src through the lint script…"]`) and every attribution subtracts that set. The
effect is visible immediately: RD16's kill now prints
`in: ['src/api/lintRejectsHandWrittenBackendFetch.test.ts'] └ stops build at lint, so tsc and Vite never run
on a violation` instead of `killed in: []`. The repository's only end-to-end build gate is no longer invisible
to the harness that consults it.

**I4 — `(\S+\.tsx?)\b`.** Nine copies recorded every `.tsx` failure under a filename that does not exist. The
new run shows real paths (`shellBuildsAndMounts.test.tsx`, `routesResolveUnderAppPrefixOnly.test.tsx`).

**A residual, found by reading the new output rather than by either review.** Every row's `in:` list contains
`lintRejectsHandWrittenBackendFetch.test.ts`, because that file *does* hold a failing case — the baseline one.
Cases are excluded by name, but the **file list is not**, so the list overstates where a mutant was caught.
The fix belongs to the eleventh copy: parse `FAIL` lines and `×` lines in document order so each case is
attributed to the file it follows. Recorded rather than patched, because changing the parser mid-phase would
invalidate the run this phase just took.

## 3 · D4 was wrong and is corrected in place

Phase 6's D4 claimed a wrapped `className` is *"invisible in a browser and very visible to the R30 pin."*
RD15 survives the whole suite. The newline lands **identically in all five states**, so sameness holds,
non-emptiness holds, and `/^[a-z0-9]+\[class=/` anchors only the start. The inference went from *"the pin
compares attribute strings"* to *"the pin catches any attribute change"*, which fails for a change that is the
same everywhere — **the exact mistake AC13's non-emptiness half exists to prevent, made in the same breath as
making it.** The entry is corrected with the old text quoted, not rewritten; the collapse to one line was
right, only its stated reason was wrong. Phase 10 adds the row that would have caught it: no `className`
literal contains a newline.

## 4 · I8 — two comments this task made false

`publicRouteSetIsExactlyTheDeclaredSet.test.tsx:261` and
`unauthenticatedRoutesRedirectToSignIn.test.tsx:68` both described `AppShell` as *"a bare `<div>`"*. It renders
`Surface` now. Both are corrected, and both now name the half of the sentence that was actually load-bearing:
**the shell is not mounted in those harnesses, so its class never reaches the container.** That was true
before this task and after it; *"bare"* was never the reason the claim held. This is the third instance in one
task of a comment made false by the change (D3, D7, I8), and the pattern is consistent enough to state: when a
task inserts a layer, the comments that justified the old shape become claims about a shape that no longer
exists, and they fail silently because no test reads prose.

## 5 · What phase 10 now owns, with the survivors as its brief

| survivor | criterion | what the case must do |
|---|---|---|
| **RD07** deleting `--color-accent` | **AC9, AC14** | parse the `@theme` declarations, recompute the three pairs, and assert the built CSS asset exists and is non-empty. **F1's remaining half:** print the computed ratio in the assertion message |
| **RD13** `AppShell` back to a bare `div` | **AC2, AC3** | the `Only` sweep — and per **I7**, assert *three* things: the derived tag-bearing set equals the eight-primitive list, its intersection with the five render sites is empty, and the union is thirteen. Never `expect(derived).toHaveLength(13)` |
| **RD14** a bare `main` in `SignInScreen` | **AC2, AC3** | the same sweep, from the other direction |
| **RD15** a wrapped `className` | **no criterion today** | the new row: no `className` literal contains a newline |

Plus **I13**: the build case asserts an emitted `.css` asset carrying the Tailwind banner, which is AC14 and is
also what makes RD05 and RD06 killable by something other than the URL allowlist.

## 6 · Kimi could not run anything, and found a real defect anyway

Every `pnpm` fetch for the Tailwind packages timed out, so `tsc`, `oxlint`, `vitest` and the build were all
unavailable and no number in phase 7 was independently reproduced. Two notes, because a review that ran
nothing is easy to over- or under-weight:

- **It is not a discrepancy.** No finding contradicts a measured number; the one arithmetic claim it *could*
  check by hand (I1) it did check, and independently.
- **I8 was found by reading**, not by running — the one genuinely new finding in the gate came from the half of
  the review the missing toolchain did not touch. Worth remembering the next time an unreproducible gate looks
  like a wasted one.

## 7 · Open questions

Unchanged: **OQ-T173-3** (no visual direction in the package; the `@theme` block is the whole diff) ·
**OQ-T173-4** (`phase1ScreensPassAccessibilityChecks` orphaned) · **OQ-T173-5** (L13 names TanStack Query and
Zustand, neither installed).

**Phase 9 complete.** Sixteen findings resolved: 4 fixed, 1 correction applied in place, 1 already fixed early,
6 phase-10 items recorded, 4 confirmations, 0 rejected. 12/16 regressions killed with 0 broken.
