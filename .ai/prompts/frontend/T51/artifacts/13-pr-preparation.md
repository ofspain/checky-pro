<!-- MODEL: Claude Opus 5 — Phase 13 (PR Preparation). No code. -->

# frontend · T51 · Phase 13 — PR Preparation

| | |
|---|---|
| **Consumes** | `artifacts/12-specification-verification.md` — **PASS** |
| **Produces** | `artifacts/13-pr-preparation.md` |

---

## Commit title

```
fix(frontend): a reply that does not parse is not a failed send (T51, R5)
```

## Commit message

```
fix(frontend): a reply that does not parse is not a failed send (T51, R5)

R5 requires the resend form to "show the same acknowledgement regardless of outcome".
Task 50 built the screen so it never reads the response - the settle handler takes no
argument, and a nine-pattern source scan forbids every way of reading one. R5's
uniformity was still broken, and 2116 tests could not see it.

openapi-fetch parses the reply body on its own behalf. A 200 whose body is not valid
JSON - an edge interstitial, a response truncated mid-document, a plain-text answer
from a proxy - makes its promise REJECT with a SyntaxError, and .then(acknowledge,
unsent) cannot tell that from a dropped connection. So the server said the email was
sent and the visitor was told it was not.

Worse than wrong: the rendering then depended on the SHAPE OF THE RESPONSE BODY. A
proxy or a future backend answering differently-shaped bodies for different account
states would have turned this public form into the enumeration oracle L4 exists to
close.

NOT LOOKING AT THE RESPONSE WAS NECESSARY AND NOT SUFFICIENT. The source scan was green
throughout, because the parse happens inside the library and not in the screen's source,
and a scan cannot see a dependency the source does not name. The screen's own comment
asserted the opposite - "the only way this screen could leak which of those happened is
by looking" - and this change corrects it.

parseAs: 'stream' states the screen's actual contract: it has no interest in the body.
Fifteen outcome arms now render byte-identical markup, and a dropped connection still
shows the retry line, which is the half that must not move.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

---

## Files changed

`3 files changed, 359 insertions(+), 5 deletions(-)`.

| File | |
|---|---|
| `features/account/ResendVerificationScreen.tsx` | `parseAs: 'stream'` and its rationale; **the falsified premise corrected**. |
| `features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx` | **+24 cases** (27 → 51), `installTypedTransport`, `teardownCurrent`, 15 arms. |
| `features/account/VerifyScreen.tsx` | **Comment only** — a false claim T51 falsified, now carrying the measured table for task 175. |

**No catalogue key, no route, no render site**, so none of the counting pins moved — predicted by the probe
and confirmed by the run.

---

## Summary

**The task found a defect rather than building a feature.** T50 shipped a screen that could not read the
response and whose uniformity was still broken; T51's probe measured eleven of twelve outcome arms rendering
byte-identical markup and one rendering the form again under *"We could not send that just now."*

Three things in this task are worth a reviewer's time more than the one-line fix.

### 1 · The hole and its repair were both invisible to the existing suite

The whole suite passed **with the fix applied** — 2116/2116, nothing moved. So no existing instrument could
see the defect *or* its correction, and the cases written here are the only ones that will ever hold it. They
**failed before the fix and passed after**, which is the only evidence that they measure the defect rather
than describe it.

### 2 · A deviation added to the frozen design caught the defect independently

`innerHTML` does not carry `document.activeElement`. **D-T51-2** added a focus comparison and a live-region
comparison across every arm, and both **failed before the fix** — so they are two of `TR24`'s five kills.
Without that deviation the suite would still have caught this defect and would have been **blind to the focus
channel forever**, with nothing to say so. A screen reader announces that channel.

### 3 · A deviation was retired by measurement, and two of my own claims were wrong

**D-T51-3** added timer patterns to a local scan, on a premise I supplied and never measured. Phase 1 said a
scan already forbade timers here; Phase 4 *"corrected"* that to say the clause had **no** instrument. **Both
were wrong**: a tree-wide scan with a pinned allow-list already owned it. The harness found this only because
the control was built to isolate — a status-changing timer killed 23 cases and proved nothing, while a
**behaviourally inert** one killed 3 in two files, and the second file was one nothing in the task had
mentioned. The patterns are gone and the owner is named where they were.

**And a mutant I nearly adopted was BROKEN.** Trying to make `TR26` isolating, `<Text data-sent-to={email}>`
compiled clean and killed exactly one case — the source scan, because it forbids `\bdata\b` and `data-sent-to`
contains `data`. Nothing else fired: **no primitive spreads props**, so the attribute rendered nothing. Caught
only by reading *which* case died and finding the kill made no sense.

## Testing performed

| | |
|---|---|
| Whole suite | **2137 / 2137**, 41 files |
| Named suite | **51 / 51** |
| `oxlint` / `tsc --noEmit` | 0 / 0 |
| Mutation harness | **6/6**, canary dead, **0 survivors, 0 broken, 0 disagreements** |
| Before the fix | **5 cases failed**, by design |
| Independent | Kimi 2.7 reproduced every number at Phase 8 and Phase 11 |

## Specification references

| | |
|---|---|
| **Task** | `spec/frontend/tasks.md` **T51** |
| **Requirement** | **R5** (`requirements.md:18-20`) |
| **LOCKED** | **L4** (now satisfied where it was not) · **L6** · **L11** · **L12** · **L14** · **L16** · **L2/L5** · **D7** |
| **Deviations** | **D-T51-1, D-T51-2, D-T51-4** ratified at Phase 4; **D-T51-3 retired** at Phase 7 |
| **Creates** | **Task 175** — the same mechanism on `VerifyScreen` and `RegisterScreen`, measured and appended to `tasks.md` |
| **Open for the owner** | **OQ-T51-7** (unconsumed stream) · **OQ-T51-8** (the error inverts) · **OQ-T175-4** (should `parseAs` be a house rule? three screens now remember it and **there is no scan**) · **FU-T43-2** · **FU-T49-1** (shipped, unproven, three tasks running) |

**Phase 13 complete. T51 is ready for merge.**
