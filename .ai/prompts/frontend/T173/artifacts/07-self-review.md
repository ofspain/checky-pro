<!-- MODEL: Claude Opus 5 — Phase 7 (Self Review) -->

# frontend · T173 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T173 — Design system installed |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py`, `artifacts/07-mutation-run.txt` |

## Verification performed

| Command | Result |
|---|---|
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |
| `vitest run` | **1780 / 1781** — the known host `oxlint@1.87.0` case |
| `python3 -I artifacts/07-mutation-harness.py` | **11/16 regressions killed, 1 BROKEN, 4 survivors, 1 canary killed, 3 disagreements** |

**The harness's own printed score is `12/16 killed … broken: none`, and it is wrong.** RD11 is a broken
mutant that the harness credited as a kill; the corrected score is above and §2 is why. Taking a harness's
summary line at face value is the thing this phase exists to not do.

---

## 1 · The mutation run

| | mutant | expected | outcome |
|---|---|---|---|
| RD01 | `Screen` renders a `div`, so no screen has a landmark | own | **KILLED/own** — 21 cases, incl. *"is one landmark holding a level-one heading, a paragraph and the way back, in that order"* |
| RD02 | `Heading` renders an `h2` | own | **KILLED/own** — 18 cases |
| RD03 | `TextLink` renders a bare anchor (D3) | own | **KILLED/own** — incl. *"is a real anchor, not a button wired to navigate"*, the case phase 6 grew |
| RD04 | **five primitives lose their class at once: the task reverted** | own | **KILLED/own** — *"renders no class and no style attribute, for any of the five states"* |
| RD05 | the stylesheet is never imported | own | **KILLED/own** — *"contains exactly the enumerated vendor URLs and no others"* |
| RD06 | the Tailwind plugin is dropped | own | **KILLED/own** — same case |
| RD07 | the accent token is deleted; `bg-accent` emits nothing | phase 10 | **SURVIVED** — as expected |
| RD08 | `FocusHolder` stops forwarding its ref | inherited | **KILLED/inherited** — in the file phase 6's D7 named |
| RD09 | `ActionButton` stops forwarding its ref | inherited | **KILLED/inherited** — same file, same case |
| RD10 | `StatusRegion` loses `aria-live` | inherited | **KILLED/own** — *stronger than claimed*; §3 |
| RD11 | `StatusRegion` renders only when it has text | inherited | **BROKEN** — §2 |
| RD12 | `Surface` drops the `data-testid` | inherited | **KILLED/inherited** — 3 files, incl. *"mounts the empty shell into the container"* |
| RD13 | `AppShell` goes back to a bare `div` (AC2) | phase 10 | **SURVIVED** — as expected |
| RD14 | `SignInScreen` renders a bare `main` (AC2) | phase 10 | **SURVIVED** — as expected |
| RD15 | a `className` wrapped across lines (D4) | phase 10 | **SURVIVED** — and D4 said otherwise; §4 |
| RD16 | a screen styles itself through `Surface` (AC4) | phase 10 | **KILLED** — by `tsc`, via an existing build case; §5 |
| RD17 | **CANARY:** the pin's walker stops reporting inline styles | own | **KILLED/own** — the controls are awake |

**Four survivors, and all four are phase 10's brief, which is the result this phase wanted:** RD07 is AC9 and
AC14, RD13 and RD14 are AC2/AC3's `Only` sweep, and RD15 is owned by **no criterion at all** (F6).

The six `own` kills all land in the three files phase 6 grew, and the three `inherited` kills all land where
phase 6 said they would. The `inherited` expectation was introduced for exactly this and it earned its place:
under the old scoring all three would have printed `INHERITED-ONLY` and been reported as disagreements.

---

## Findings

### F1 — Every contrast number in the token comments is wrong (all four still pass)
· **Severity:** **Major**
· **Evidence:** `frontend/src/app/styles.css`'s `@theme` comments state text **15.9:1**, muted **7.2:1**,
  text-inverse on accent **7.0:1**, focus **3.4:1**. Recomputed from the committed hex values with the formula
  §6d freezes (sRGB → relative luminance, `(L1+0.05)/(L2+0.05)`): **17.49**, **7.94**, **6.71**, **5.89**.
· **Assessment:** The thresholds hold — §6d's three pairs clear 4.5:1, 4.5:1 and 3:1 with room — so AC9's
  substance is intact and no colour needs re-picking. But four of four stated measurements are wrong, one by
  73% (focus: 3.4 against 5.89), and `3.4` sits suspiciously just above the 3:1 threshold it is quoted
  against, which is the shape of a number reasoned toward rather than computed. The same file's header claims
  the contrast is *"measured arithmetically … rather than asserted by eye"*, and
  `06-implementation-notes.md` repeats `3.4:1` in prose and marks AC9 *"done arithmetically in the token
  comments"*. **This is D7's defect one file over: a comment asserting a measurement nobody ran** — and that
  makes it the second time in one task, which is a habit rather than a slip. Phase 10's AC9 case computes from
  the declarations, so it will pass while the comments misstate: the test cannot catch this class of error,
  which is precisely why it had to be caught here.
· **Recommendation:** Phase 9 replaces all four numbers with the computed ones and corrects the two sentences
  in `06-implementation-notes.md` that quote them. Phase 10's AC9 case prints the computed ratio in its
  assertion message, so the next reader of a wrong comment has the real number beside it.

### F2 — RD11 is a BROKEN mutant that the harness scored as a kill
· **Severity:** **Major**
· **Evidence:** `artifacts/07-mutation-run.txt` reads
  `KILLED inherited RD11 … (2 failed of 530, exit 1)` and ends `broken: none`. Applying RD11 by hand gives
  `StatusRegion.tsx`:
  ```tsx
  return (
    {children === null ? null : (
      <div ref={ref} role="status" aria-live="polite" …>
  ```
  `return ( { … } )` is a **syntax error**. The `2 failed of 530` is the tell: 530 of 1781 tests ran at all,
  and the 23 files listed under *killed in* did not fail — they failed to **parse**.
· **Assessment:** The mutant measures nothing. A file that cannot be parsed falsifies every assertion in the
  suite at once, so the raised failure count carries no information about whether anything was watching the
  live region's conditional rendering — which is the regression RD11 was written for, and which therefore
  remains **unmeasured**. This is T36's lesson recurring verbatim: *a mutant that cannot compile measures
  nothing, and a BROKEN mutant was counted as KILLED.* The preflight cannot see it: it replays anchor
  substitution in memory and checks each anchor matches exactly once, which RD11 passes. **Uniqueness is not
  syntactic validity.**
· **Recommendation:** Phase 9 (a) rewrites RD11 to wrap the whole return value rather than splice a ternary
  into an expression position — `const region = (<div …>{children}</div>); return children === null ? null :
  region;` — and re-runs; (b) adds a guard to `score()`: a mutant whose run reports a **total below the
  baseline total** is BROKEN, not killed. That one line would have caught this, and would have caught T36's
  too.

### F3 — A genuine kill inside the known-environmental file is reported as `killed in: []`
· **Severity:** **Major**
· **Evidence:** RD16 printed `└ killed in: []` and was still scored `KILLED/inherited`. `score()` computes
  `sorted(where - KNOWN_ENVIRONMENTAL_FAILURES)`, and RD16's only failing file is
  `src/api/lintRejectsHandWrittenBackendFetch.test.ts` — which is in that set.
· **Assessment:** The environmental set is keyed by **file**, but only **one case** in that file is
  environmental (the host's `oxlint` summary line). The file also holds *"stops build at lint, so tsc and Vite
  never run on a violation"*, which runs a real `pnpm run build` over a copy of `src` and whose control arm
  asserts today's sources build cleanly. That is **the only end-to-end build gate in the repository**, and the
  harness is currently blind to kills inside it — it printed an empty attribution for the strongest instrument
  it consulted. A per-file exclusion is also unsound in the other direction: a mutant that breaks that file's
  other 32 cases would be absorbed into "environmental".
· **Recommendation:** Phase 9 narrows the set to the one case name rather than the file, and attributes by
  case. Until then, every `inherited` row's listing carries the baseline failure *"passes today's frontend/src
  through the lint script"* as noise, which is visible in RD08, RD09 and RD12 above.

### F4 — The FAIL-line regex truncates every `.tsx` path to `.ts`, and has for nine copies
· **Severity:** **Minor**
· **Evidence:** `07-mutation-harness.py`'s `re.search(r"\bFAIL\b\s+(\S+\.(?:ts|tsx))")`. RD11's attribution
  lists `src/auth/callbackValidatesNonce.test.ts`, `src/app/shellBuildsAndMounts.test.ts` and 21 more — none
  of which exist; every one is `.tsx`. Alternation tries `ts` first and nothing anchors the end, so the `x` is
  left unmatched.
· **Assessment:** Cosmetic for the verdict — attribution is computed from a separate `OWN_TESTS` run, not from
  these names — but it corrupts every file list the harness prints, and it silently weakens the baseline
  guard: `files_failed <= KNOWN_ENVIRONMENTAL_FAILURES` compares truncated names, so a failing `.tsx` file is
  checked against the set under a name it does not have. It happens to be sound today only because the one
  environmental failure is genuinely a `.ts` file.
· **Recommendation:** `(\S+\.tsx?)\b` in phase 9. Carried forward to the eleventh copy.

### F5 — AC4 is already enforced end-to-end, which is stronger than the brief claimed
· **Severity:** **Minor/Major** (a result, not a defect)
· **Evidence:** RD16 makes `AppShell` pass `className` to `Surface` and `Surface` apply it. `tsc --noEmit`
  then fails **twice** — `AppShell.tsx(13,43) TS2322: Property 'className' does not exist` and
  `ui/Surface.tsx(15,55) TS2339` — and the build case's control arm fails with it.
· **Assessment:** §6c froze `className`'s absence *"so a screen that tries fails `tsc` rather than a scan"*,
  and §6f called that a compile error *"not a test failure"*. The measurement says it is **both**: an existing
  case runs the real build and asserts today's sources pass, so the type-level guarantee has a test behind it
  without this task writing one. My expectation of `phase 10` was wrong in the stronger direction. This also
  resolves what AC4's phase-10 source scan is **for**: it is not the primary instrument, it is the readable
  one, and it should say so rather than imply it carries the claim.
· **Recommendation:** Phase 10's AC4 case cites the build case as the primary enforcement and scans source as
  corroboration. No production change.

### F6 — A wrapped `className` survives everything, and no acceptance criterion owns it
· **Severity:** **Minor/Major**
· **Evidence:** RD15 wraps `Screen`'s `className` across three lines, putting a literal newline into the
  rendered `class` attribute. **SURVIVED** — 1 failed of 1781, the baseline.
· **Assessment:** Phase 6's **D4 asserted the opposite**: *"That is invisible in a browser and very visible to
  the R30 pin, which compares attribute strings across five states."* It is invisible to the pin too, and the
  reason is the pin's own logic: a newline lands **identically in all five states**, so sameness holds,
  non-emptiness holds, and the classes-only regex `/^[a-z0-9]+\[class=/` anchors only the start. D4 reasoned
  from "the pin compares attribute strings" to "the pin catches any attribute change", which does not follow
  for a change that is the same everywhere — the identical mistake the non-emptiness half of AC13 exists to
  prevent, made in the same breath as making it. Nothing in AC1–AC15 forbids it either, so this is a genuine
  uncovered hole rather than a mis-stated one.
· **Recommendation:** Correct D4 in phase 9 (it is a committed claim that is false). Phase 10 adds one row to
  the primitive source scan: no `className` literal contains a newline. Cheap, and it is the only thing that
  would have caught the defect the three primitives actually shipped with before phase 6 collapsed them.

### F7 — §6e(2)'s "thirteen" is a count of listed files, not of files the sweep finds
· **Severity:** **Minor/Major** (a trap for phase 10)
· **Evidence:** §6e(2): *"After this task there must be thirteen, and every one must appear in exactly one of
  the two lists."*
· **Assessment:** After this task the five render sites contain **no** lowercase JSX tag — that is AC2 — so
  the set of files that *contain* one is the **eight primitives**. Thirteen is the size of the **union of the
  two pinned lists** (5 + 8). Both readings fit the sentence and only one is true.
  `expect(derived).toHaveLength(13)` fails; `toHaveLength(8)` passes but stops cross-checking the render-site
  list, which is the hole §6e exists to close.
· **Recommendation:** Phase 10 asserts all three: the derived set equals the primitive list, its intersection
  with the render-site list is empty, and the union is thirteen — naming which number is which.

### F8 — `RateLimitNotice` is mounted by nothing, so AC6's evidence is a test-only tree
· **Severity:** **Minor**
· **Evidence:** `RateLimitNotice.tsx`'s header: *"Don't mount it in `AppShell` until the first screen adds
  that provider tree."* No production file renders it. RD08 and RD09 are killed in its own suite.
· **Assessment:** The refs are the thing under test and they are genuinely tested, so AC6 is not weakened. But
  `FocusHolder` has no production consumer, so an error in its layout classes would first surface in a later
  task, and the focus behaviour L11 cares about is not exercised anywhere a user can reach.
· **Recommendation:** None — §5 excludes new mounting. A note for whoever mounts the notice.

### F9 — `Surface` sets `min-h-dvh`, which only its single consumer wants
· **Severity:** **Minor**
· **Evidence:** `ui/Surface.tsx` renders `min-h-dvh bg-surface text-text`; `AppShell` is its only consumer.
· **Assessment:** Correct and simplest at one consumer. The first panel or card wanting the background without
  a full-viewport minimum is the trigger §6c already names (*"a variant prop arrives with the first screen
  that needs one"*). Recorded so that arrival is recognised rather than treated as a surprise.
· **Recommendation:** None.

### F10 — The barrel is a convention, not a boundary
· **Severity:** **Minor**
· **Evidence:** `ui/index.ts` re-exports the eight; nothing prevents `import { ActionButton } from
  '../app/ui/ActionButton'`.
· **Assessment:** Harmless — the primitives have no private helpers, so a deep import bypasses nothing. Worth
  stating only because §6c's props tables are the real guarantee and they travel with the files.
· **Recommendation:** Optional and low value: one phase-10 case that every render site imports the barrel path.

---

## 2 · What the harness proved about the task, as opposed to about itself

Three claims phase 6 made and could not then measure are now measured:

1. **The primitives kept the obligations they inherited.** RD08, RD09, RD10 and RD12 are all killed — a
   forwarded ref, a live region's `aria-live`, and the shell's `data-testid`. Phase 6 asserted these survived
   the move; the kills are that assertion under test, and three of the four land in the files phase 6 named.
2. **The vendor-URL admission (D2) is a pin, not a relaxation.** RD05 and RD06 remove the stylesheet import
   and the build plugin, and both die on *"contains exactly the enumerated vendor URLs and no others"*. An
   allowlist entry that only ever permits is weaker than one that also requires; this one requires.
3. **AC13's non-emptiness half is load-bearing.** RD04 is the only mutant that kills it, and it had to span
   five files to do so — stripping one primitive's class leaves four identical non-empty lists and passes.
   The harness grew multi-file mutants for this row, and without it the assertion would have looked like dead
   weight.

And one claim is refuted: **D4 (F6)**. One is corrected upward: **AC4 is enforced today (F5)**.

## 3 · The two disagreements the harness flagged, both upward

**RD10** was expected `inherited` and killed `own`: `identityErrorsIndistinguishableAcrossAccountStates.test.tsx:889`
pins a rendered tree snapshot containing `div [aria-live=polite role=status]`, so dropping the attribute fails
a file this task grew as well as the three suites that query `[aria-live]` directly.

**RD16** was expected `phase 10` and killed by `tsc` through the build case — F5.

Neither is a defect. Both are instruments covering more than I predicted, which is the direction a
disagreement should ideally run; the one running the other way is RD11, and that is F2.

## 4 · Open questions

Unchanged: **OQ-T173-3** (no visual direction in the spec package; the `@theme` block is the whole diff — and
F1 means its *comments* need correcting regardless of whether its values do) · **OQ-T173-4** · **OQ-T173-5**.

**Phase 7 complete.** Ten findings returned; no code changed. 11/16 regressions killed, 1 BROKEN, 4 survivors
all owned by phase 10, 1 canary killed, 3 disagreements — two upward, one a harness defect.
