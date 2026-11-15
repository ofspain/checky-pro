<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# frontend · T45 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T45 — Registration rejects out-of-range passwords |
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md`, `artifacts/09-mutation-run-1.txt`, `artifacts/09-mutation-run-2.txt` |

## Resolution log

Phase 7 returned eleven findings, phase 8 fourteen. They overlap almost completely: I1–I7 of phase 8
confirm F1–F7 of phase 7 independently, by reading the assertions rather than re-measuring them. The
table below is keyed by the pair, and the one-line reasons are in the sections after it.

| # | Finding | Decision | Change |
|---|---|---|---|
| 1 | **F1 / I1** — a screen interpolating literals survives everything | **ACCEPTED** | source pin on both constants + harness row **TR44** |
| 2 | **F2 / I2** — the bounds case credits the wrong half of itself | **ACCEPTED** | the comment rewritten, assertion by assertion |
| 3 | **F3 / I3** — no mutant aims at the digit identity | **ACCEPTED** | harness row **TR45** |
| 4 | **F7 / I4** — the screen claims a reading order nothing measures | **ACCEPTED** | asserted with `compareDocumentPosition` + harness row **TR46** |
| 5 | **F6 / I5** — the id lookup leaves the container | **ACCEPTED** | `container.querySelector('#' + CSS.escape(id))` |
| 6 | **F5 / I6** — the verdict list exists twice | **ACCEPTED** | one `VERDICT_WORDS`, read by the scan and its control |
| 7 | **F4** — the copy contract rests on one non-derived line | **ACCEPTED (comment only)** | one sentence above the literal pin — landed *after* both runs, see below |
| 8 | **F11 / I7** — "one id" and the email exemption have no mutant | **DECLINED, with a measurement** | none — see §7 |
| 9 | **F10** — FU-T43-2, third sighting | **DEFERRED to the owner** | none |
| 10 | **F8** — `GUIDANCE` uses a non-global `replace` | **DECLINED** | none |
| 11 | **I14** — the harness flaked on TR24 again | **ACCEPTED (procedure)** | two full runs, both reported below |
| 12 | **F9, I8–I13** | positive / observation | none |

---

### 1 · F1 / I1 — the screen end of the provenance wire · **ACCEPTED**

The most valuable finding of the task, and it only exists because phase 7 stopped arguing and applied the
mutant: a screen passing `{ min: 12, max: 128 }` to the real template passed **126 of 126** cases in the
three files this task owns. The freeze and TR41 both located provenance at the catalogue; it has two ends,
and the DOM can see neither.

Changed — `…registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx`, the source-scan
case:

```ts
expect(code, 'the screen interpolates a literal minimum instead of the constant that enforces it')
  .toMatch(/min:\s*ADVISORY_MINIMUM_CODE_POINTS\b/);
expect(code, 'the screen interpolates a literal maximum instead of the constant that enforces it')
  .toMatch(/max:\s*ADVISORY_MAXIMUM_CODE_POINTS\b/);
```

and harness row **TR44**, which is that exact mutant. The scan runs against source with comments stripped,
so the comment explaining the pin cannot satisfy it — the same discipline TR40 taught.

The scan is a **source** instrument, which this package has twice found to be the weakest kind of claim
(T37's `/accessToken\s*\(/` was aimed at an impossible call shape). This one is narrow on purpose: it names
the two identifiers and the two keys they are bound to, so a rename of either constant fails it, which is
the drift it exists to catch.

### 2 · F2 / I2 — the comment credited the wrong assertion · **ACCEPTED**

The comment said the digit identity was "THE ONLY ASSERTION THAT CAN KILL THE LITERAL-BOUNDS MUTANT", and
quoted the freeze saying the same. Both were wrong: 12 and 128 *are* the constants, so TR41's literals
satisfy the identity. The `{{min}}`/`{{max}}` pin two assertions down is what kills it.

The comment now says which assertion catches which mistake, and that a third mistake — literals in the
screen — is visible to neither, which is why §1 exists. The freeze's §3 clause is left as written: it is
frozen, and phase 12 is where a frozen clause gets corrected rather than quietly re-read.

### 3 · F3 / I3 — the digit identity had no mutant · **ACCEPTED**

**TR45** swaps the template's bounds: `'Use a password of {{max}} to {{min}} characters.'` Nothing in the
source moves — the catalogue still interpolates, the screen still names the constants — and the sentence
still carries both numbers. Phase 7 measured it failing in two cases before the row existed; the row makes
that measurement repeatable.

### 4 · F7 / I4 — a criterion stated in production prose · **ACCEPTED, asserted**

`RegisterScreen.tsx:98-102` claims the sentence sits "between the field and the control so the reading
order matches the visual one". Nothing held it. Of the two options both reviews offered — assert it or
delete the clause — asserting is right: the claim is a real accessibility criterion (`aria-describedby`
makes the association order-independent for software that follows it; a visitor reading or tabbing
linearly meets the sentence where the DOM puts it), and it costs two lines.

```ts
const precedes = (first: Element, second: Element) =>
  (first.compareDocumentPosition(second) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0;
```

plus harness row **TR46**, which moves the element after the submit control. The reorder is one edit of two
adjacent lines, per T36.

### 5 · F6 / I5 — the id lookup left the container · **ACCEPTED**

`container.ownerDocument.getElementById(id)` → `container.querySelector('#' + CSS.escape(id))`. The walk is
unchanged — the id still comes off the field, which is what makes finding the sentence evidence of the
association — and the second half of it no longer trusts document order. `CSS.escape` is present in jsdom;
the suite was run to confirm it rather than assumed.

### 6 · F5 / I6 — the verdict list existed twice · **ACCEPTED**

One `const VERDICT_WORDS`, read by the scan and by the control that claims to control it. Before this, the
control proved something true of its own copy of the list.

### 7 · F11 / I7 — "one id" and the email exemption · **DECLINED, and the reason is the finding**

Both reviews called this optional and preferred the id-accumulation mutant. I measured what that mutant
would be worth before declining it:

- **The id list.** Any mutant that joins two ids with a space also breaks the `getElementById`/
  `querySelector` walk that four other cases make, so the row would record a kill that those four produce
  anyway. A mutant must isolate the hole it records (T36); this one cannot, because there is no second
  element with an id on this screen to point at. Declined as unisolatable, not as unimportant.
- **The email exemption.** `EmailField` has no `aria-describedby` prop and no `aria-invalid` prop. A screen
  that tried to describe or mark the address would not compile, so the assertion at `:279-280` is held by
  the type system, which is stronger than a mutant. Worth knowing, and now written down.

### 8 · F10 — FU-T43-2, third sighting · **DEFERRED**

~120 lines of helpers copied for the third time, and the copies have started to diverge: this file has
`describedElement`, T44's does not. A shared `features/account/test-helpers.tsx` is a task of its own and
this task has no mandate to add one. Recorded for the owner, unchanged.

### 9 · F8 — the non-global `replace` · **DECLINED**

Both failure modes are loud: a whitespace variant (`{{ min }}`) or a twice-named bound would leave braces
in `GUIDANCE` and every equality in the file would fail. The bounds case pins the exact spelling, so the
coupling is enforced. No change; the finding is recorded rather than acted on.

### 10 · I14 — the TR24 flake, again · **ACCEPTED as procedure**

Kimi's run promoted TR24 — `Form drops noValidate`, the one honestly unfalsifiable row — to
`KILLED/inherited` through two unrelated cases in
`loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx`. Mine, with Docker stopped, reported
`SURVIVED` as expected. That is FU-T42-1's fourth sighting and the second time it has inverted a verdict
rather than merely added failures.

Accepted as procedure: **two full runs below, both reported**. Declined as a code change — the harness
machinery is not this task's to harden, and the cause is a frontend test's load sensitivity rather than the
harness's revert step. The one thing the harness already does right is print the failing case *names*, which
is how the flake is recognisable at all.

---

## Verification after the accepted changes

| | |
|---|---|
| named suite | **16 / 16** |
| whole suite | **1961 / 1962** — the known environmental lint-script failure, excluded from every attribution |
| `tsc --noEmit` · `oxlint src` | **0** · **0** |
| harness rows | 43 → **46** |

The test changes were **committed and pushed before either harness run** (`99ee454a`): the harness reverts
`frontend/src` with `git checkout --` and `git clean -fq`, and uncommitted test edits die silently — the rule
that cost 19 cases in T14 and 5 in T15.

### Two full runs, both reported (I14)

| | run 1 | run 2 |
|---|---|---|
| baseline | 1 failed of 1962 | 1 failed of 1962 |
| regressions | **42 / 42 killed** | **42 / 42 killed** |
| canaries | TR25, TR34, TR43 — all killed | TR25, TR34, TR43 — all killed |
| unfalsifiable | TR24 — **SURVIVED, as expected** | TR24 — **SURVIVED, as expected** |
| broken | none | none |
| disagreements | **none** | **none** |

`artifacts/09-mutation-run-1.txt`, `artifacts/09-mutation-run-2.txt`. The two runs are identical row for row
with **one** difference, and it is the finding:

> **TR27** killed with 3 named failures in run 1 and **4** in run 2. The extra one is in
> `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` — the same file, and the same class of case,
> that inverted TR24 for Kimi.

That is FU-T42-1's **fifth** sighting, and the clearest one yet about what it costs. The flake landed
harmlessly here because TR27 already had two genuine kills in the file it belongs to, so the verdict
(`KILLED/own`) could not move. The row it *can* invert is a row with no genuine kill — which is exactly
TR24, the one honestly unfalsifiable row in the harness. A verdict that depends on whether an unrelated
suite was flaky that minute is not a verdict, and the only reason both of my runs read it correctly is that
the Docker stack was stopped for both. **The owner should expect this to recur** until FU-T42-1 is diagnosed
in code; the harness's habit of printing failing case *names* is what makes it survivable.

### The three new rows, and what each one proves

| row | killed by | and nothing else |
|---|---|---|
| **TR44** the screen interpolates literals | `reaches the catalogue for the sentence and spells no part of it` | ✔ one case |
| **TR45** the template's bounds swapped | `renders the two bounds, and they ARE the constants…` + `delivers guidance, not a verdict` | ✔ two cases |
| **TR46** the sentence after the submit control | `points the field at the element that holds the sentence…` | ✔ one case |

Each new assertion is killed by the case written for it and by nothing else, in both runs. TR44 is the one
that matters: the hole phase 7 measured at **126 of 126 passing** now dies in a single named case, and that
case is a source scan, because the DOM cannot see where a number came from.

## One change that landed after the runs

**F4's comment** — the sentence above the literal copy pin saying it is the only non-derived copy claim in
the file and must stay non-derived. It is comment-only, in a test file, and cannot change a mutant's fate,
but it was applied **after** both runs rather than before so that the runs measured one tree. The named suite
was re-run after it: **16 / 16**. Said plainly rather than folded into the table, because "the runs cover the
tree" is the kind of claim this package has learnt to check.

## Open items carried forward

| | |
|---|---|
| **FU-T42-1** | fifth sighting, now with a measured mechanism: it can only invert a row that has no genuine kill. Cause in code still unknown. |
| **FU-T43-2** | third sighting. ~120 lines of helpers copied again, and the copies have begun to diverge (`describedElement` exists in this file only). Owner's call. |
| **OQ-T45-4** | `EmailField` accepts neither `aria-describedby` nor `aria-invalid`, so the email-field exemption is held by the type system. If a later task gives the address a message, that assertion becomes a real claim needing a real mutant. |
| **OQ-T44-1**, **OQ-T43-6..11** | unchanged, owner-scoped. |

**Phase 9 complete.** Seven findings accepted (six as code, one as a comment), two declined with reasons, one
deferred to the owner, one accepted as procedure. Two clean harness runs reported in full.
