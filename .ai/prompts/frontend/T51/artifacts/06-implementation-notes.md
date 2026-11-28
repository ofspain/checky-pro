<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation). -->

# frontend · T51 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

```
suite 2133 / 2133  (41 files, +17 cases)  ·  lint 0  ·  typecheck 0
```

---

## 1 · The cases failed before the fix, and that is the deliverable

Step 1 of the plan was *write the failing cases first*, because **the hole is invisible to 2116 tests** and a
case that passes before the fix measures nothing. Run against the unfixed screen:

```
× renders markup identical to the declared reply for: 200 with an HTML body, as an edge error page sends
× renders markup identical to the declared reply for: 200 with a plain-text body
× renders markup identical to the declared reply for: 200 with JSON truncated mid-document
× ends with focus on the same element for every outcome
× ends with the live region saying the same thing for every outcome
  Tests  5 failed | 42 passed (47)
```

Then **one production line**, and `47 / 47`.

**Five failures, not three** — and the extra two are the whole argument for **D-T51-2**. The focus case and the
live-region case detected the defect **independently of the markup comparison**, which is exactly what they
were ratified for: `innerHTML` does not carry `document.activeElement`, so a screen that moved focus for a
`200` and not for a `404` would compare byte-identical and pass every arm while a screen reader announced the
difference.

Had I accepted Kimi's Phase 3 unamended, the suite would still have caught this defect — but it would have
been **blind to the focus channel forever**, and nothing would have said so.

## 2 · The production change

One call site in `ResendVerificationScreen.tsx`, plus the paragraph explaining it. `parseAs: 'stream'` states
the screen's actual contract — *it has no interest in the body* — where a `catch` on `SyntaxError` would
re-describe a symptom.

## 3 · Files changed — **two**, as the brief froze

| File | |
|---|---|
| `features/account/ResendVerificationScreen.tsx` | The one line and its rationale. |
| `features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx` | 17 cases, `installTypedTransport`, `teardownCurrent`, and the two timer patterns. |

**No second-order pin fired, and that was measured rather than assumed.** Phase 2 had already run the whole
suite with the fix applied (2116/2116, nothing moved); the final run adds 17 cases and touches nothing else.
T50's three second-order surprises all keyed off `RENDER_SITES`, and this task adds no render site, no
catalogue key and no route — so the three suites that re-pin those counts by reading source cannot see it.

## 4 · Decisions taken during implementation

### `installTypedTransport` is a fourth transport helper, deliberately

`installTransport` hardcodes `Content-Type: application/json`, and three arms need `text/html`, `text/plain`
and no header at all — because **Phase 2 measured that the content type does not gate the parse**. Extending
the existing helper with a third optional parameter would have changed a function eleven existing cases call;
a new one leaves them untouched.

### Focus is captured as `TAGNAME:text`, not as a tag

`<h1>Check your email</h1>` and `<h1>Resend verification email</h1>` are both `H1`. Comparing tags alone would
call the acknowledged and form states the same element, and the form state is what the defect rendered.

### The two cross-arm cases collect, then compare once

An assertion inside the loop stops at the first disagreeing arm, so the arms after it are unmeasured **and the
failure names one arm when the truth might be nine**. Both cases build a `label -> value` list for all
thirteen and compare the whole list, so every arm is measured on every run.

### The baseline is captured and then guarded

Three guards run before any comparison: the capture is longer than 100 characters, its text **equals**
`ACK_TEXT`, and it contains no `<form`. Without them a **uniformly broken** screen satisfies every comparison
— the trap that let five empty lists satisfy a sameness pin in T173.

### AC12 gets no new case, and the owning pin is named

The catalogue key set is already pinned in `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:1011-1013`
and T51 adds no key, so a case here would be a duplicate instrument. T173's rule applies: **decline the
duplicate, but name the owner by its exact location** so a rename breaks something.

## 5 · Deviations from the plan

**None.** The five harness rows are Phase 7's; everything else landed as planned, in the planned order.

## 6 · Open items

| | |
|---|---|
| **OQ-T51-6** | `RegisterScreen` and `VerifyScreen` read `response.ok`, so a `SyntaxError` reaches them by a different path. **Out of scope by `tasks.md`; recorded, not widened.** This is the highest-value open item in the task. |
| **OQ-T51-7** | The body stream is left unconsumed. |
| **OQ-T51-8** | **The error inverts** — an edge interstitial now says *"Check your email"*. The posture L4 already chose. |
| **FU-T43-2** | `teardownCurrent` is now its **second** copy, `type` its fourth. |

---

**Phase 6 complete. Next is Phase 7 — Self-Review and the mutation harness.**
