<!-- MODEL: Claude Opus 5 — Phase 4 (Frozen Task Brief). FROZEN. No code written. -->

# frontend · T51 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Consumes** | `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` — **FROZEN** |

**No code was written in this phase.** Kimi's Phase 3 design is **accepted** in substance: the fix, the status
set, the `innerHTML` comparison, the ported helper, the files touched and not touched. **Four deviations**
below, all ratified **before implementation** — the discipline that gave T50 the first clean Phase 9 in four
tasks.

---

## The one production change

```diff
-  .POST('/accounts/resend-verification', { body: { email } })
+  .POST('/accounts/resend-verification', { body: { email }, parseAs: 'stream' })
```

**Frozen.** Measured in Phase 2 across seven server arms, all collapsing to one 362-character rendering, with
the dropped-connection arm preserved. `tsc` clean, whole suite green.

### A consequence the owner should see, stated rather than buried

**The error inverts.** Today a `200` carrying an unparseable body says *"we could not send"* when the email
**was** sent. After the fix, an edge interstitial carrying `200` says *"Check your email"* when the request
never reached the service.

**This is not a new trade — it is the one L4 already chose**, and the same one `OQ-T50-6` records for a `500`.
A screen that cannot distinguish outcomes will sometimes be reassuring about a failure; the alternative is a
screen that distinguishes, which is the enumeration oracle the requirement forbids. **Recorded as
`OQ-T51-8`** so the owner can revisit the posture, not the implementation.

---

## Deviations from the frozen design

### D-T51-1 — Kimi's TR26 cannot die, so it is replaced

Kimi's third harness row is *"Replace the uniform-acknowledgement equality with a `toContain` → **KILLED**"*.

**A row that weakens an assertion cannot be killed.** Weakening makes tests pass; the harness detects mutants
by **failure**. As specified TR26 would be scored a survivor forever, and worse, it mutates **a test** while
being expected to behave like a regression row. T36 settled this: *the harness measures strength, and only a
control measures precision.*

**Replaced** by a row with a real kill: **the acknowledgement interpolates the submitted email**, which
differs by input and is caught only by the differing-email case Kimi's own OQ-T51-3 commissions.

### D-T51-2 — Add focus and live-region equality across arms

**`innerHTML` cannot see focus.** `document.activeElement` is not part of serialised markup, so under Kimi's
design a screen that moved focus to the heading for a `200` and left it on `<body>` for a `404` would compare
**byte-identical and pass**.

That is a **non-visual enumeration channel**, and it is exactly what a screen reader exposes. Phase 2 measured
the difference is observable — `focus=H1` on every uniform arm, `focus=BODY` on the broken one — so this is a
real axis, not a hypothetical. **L11 makes it in scope**, and it costs one assertion per arm.

### D-T51-3 — The timing clause gets an instrument, correcting a false claim of mine

Kimi declined a timing instrument (OQ-T51-4), partly on a premise **I supplied and never measured**: Phase 1
said *"The screen has no timer, which an existing source scan already forbids."*

**There is no such scan in this suite.** Measured:

```
$ grep -n 'setTimeout\|setInterval\|timer' …/resendVerificationAcknowledgement….test.tsx
(nothing)
$ grep -n 'timer\|setTimeout' …/registrationAcknowledgementIsIdentical….test.tsx
381:  it('reaches for no response, no route state and no timer', …)
394:    for (const shape of [… /setTimeout/, /setInterval/, …])
```

The scan I was describing is **T47's**, for a different screen. So **L4's fourth clause has no instrument at
all here**, and Kimi's reasoning — *"T50 already asserts the acknowledgement does not appear while in
flight"* — holds the in-flight state, not *"the SPA adds no delay that differs by outcome"*.

**`setTimeout` and `setInterval` are added to the existing source scan.** The cheapest possible instrument,
and `agents.md:44` is explicit that *"the timing requirement applies to the SPA's own rendering only"* — which
is precisely what a timer in this file would be.

### D-T51-4 — The pathname is asserted per arm, naming L4's *redirects* clause

L4 lists four things: copy, **redirects**, styling, timing. A navigation away from the screen would in fact
change `innerHTML`, so Kimi's design catches it **incidentally**. An incidental instrument is one nobody
maintains: it survives a rewrite of the case that happens to carry it. **One explicit pathname assertion per
arm**, so the clause is named by the thing that holds it.

---

## Acceptance criteria — FROZEN

| AC | Criterion |
|---|---|
| **AC1** | A `200` with the declared JSON body renders the acknowledgement. |
| **AC2** | **Every** arm in the frozen set renders markup **equal to one shared expected string**, compared as `container.innerHTML`. |
| **AC3** | The frozen arm set is: `200` valid JSON · `200` a *different* JSON message · `200` null body · `200` empty string · `200` HTML body (`text/html`) · `200` plain text · `200` truncated JSON · `204` null body · `400` · `404` · `429` · `500` · `503`. **Thirteen.** |
| **AC4** | **No arm renders `resend.unsent`** — the first half of OQ-T50-5. |
| **AC5** | A **rejecting transport** renders `resend.unsent` with the form mounted and **no** acknowledgement — the second half, so neither direction can pass alone. |
| **AC6** | A **different email** renders identical acknowledged markup (OQ-T51-3). |
| **AC7** | **Focus ends on the same element in every arm** (D-T51-2). |
| **AC8** | **The live region ends in the same state in every arm** (D-T51-2). |
| **AC9** | **The pathname is identical in every arm** (D-T51-4, L4 *redirects*). |
| **AC10** | The server's `message` is absent from the DOM **in every arm**, not only one — the contract makes it `required`, so this is the field a backend would leak through. |
| **AC11** | The source scan still passes, now **also forbidding `setTimeout` and `setInterval`** (D-T51-3). |
| **AC12** | **No new catalogue key**, so D7's key-set pin does not move. |
| **AC13** | Whole suite, `oxlint` and `tsc --noEmit` green. |

## Files touched

| Path | |
|---|---|
| `features/account/ResendVerificationScreen.tsx` | The one line. |
| `features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx` | The uniformity cases; `teardownCurrent` ported. |

**Not touched**, per Kimi and confirmed by Phase 2's measurement that the whole suite passes with the fix
applied: `catalogue.ts`, `routes.tsx`, `app/ui/**`, the design-system suite, the credential-table suite,
T49's suite.

## Open questions carried to the owner

| ID | |
|---|---|
| **OQ-T51-6** | Do `RegisterScreen` and `VerifyScreen` carry the same parse-failure hole? **Out of scope by `tasks.md`**, and a real question — they read `response.ok`, so a `SyntaxError` reaches them by a different path. |
| **OQ-T51-7** | `parseAs: 'stream'` leaves the body unconsumed. Accepted; revisit if the bundle adds response logging. |
| **OQ-T51-8** | **The error inverts** (above). The posture is the owner's, not this task's. |

---

**FROZEN. Next is Phase 5 — Implementation Plan.**
