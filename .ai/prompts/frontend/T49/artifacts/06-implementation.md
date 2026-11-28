<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation). -->

# frontend · T49 · Phase 6 — Implementation

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation.md` |

```
suite 2083 / 2083  (40 files, was 2048 / 39)   ·   lint 0   ·   typecheck 0
named suite verifyFailureShowsOneUniformMessage: 35 cases
each touched file ALSO green when run alone: 35/35 · 32/32 · 65/65
```

Built in the frozen order: constant → catalogue → screen → pins → inherited rewrites → named suite.

---

## Production

| File | |
|---|---|
| `app/routes.tsx` | `RESEND_VERIFICATION_PATH`, substituted into `PUBLIC_ROUTE_PATHS`. Its comment records that **this task adds the constant and not the element** — the reverse of how the five above arrived — and that the path stays element-less until task 50, so the link really does land on a blank document today. |
| `i18n/catalogue.ts` | `verify.failureMessage`, `verify.failureAction`, **flat** (D-T49-4), with the crash that forced the shape recorded in place. |
| `features/account/VerifyScreen.tsx` | `'idle' \| 'success' \| 'failure'`; a tokenless arrival sets `'failure'` and sends nothing; `setStatus('idle')` when a request starts; `'failure'` on both a non-`ok` response **and** a transport rejection; the message in the live region and the `TextLink` outside it. |

Four header paragraphs were added, each for something a later reader would otherwise undo: why a tokenless
arrival is a failure when R4 does not literally say so, why `setStatus('idle')` is reachable rather than
redundant, why the link sits outside the live region, and why nothing reads the response body.

## Tests

| File | |
|---|---|
| `verifyFailureShowsOneUniformMessage.test.tsx` | **New, 35 cases.** |
| `verifyFromLinkShowsSuccessState.test.tsx` | The two AC12 cases and `PINS A LIMIT` rewritten (AC11). |
| `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | Two key-set entries (D-T49-3), list only. |

### What the named suite does differently, and why

**It does not mock four reasons, because the four reasons do not exist on the wire.** The task line names
invalid, expired, used and deleted-account; the backend collapses all four into one fixed `400` before the
SPA sees any of them. Mocking them separately would be mocking a distinction that is not there. What the
suite holds instead is the property that would let a reason leak: **nine different statuses**
(`400/401/403/404/409/410/429/500/503`) render byte-identical DOM, a problem document carrying `type`,
`title`, `detail` and a timestamp changes nothing, and a **source scan forbids the screen reading the body at
all** — because a screen that reads `error.title` and does nothing with it passes every behavioural case, and
the edit that renders it comes later, by which time the reading looks deliberate.

`410` and `429` are deliberate: `410 Gone` is what a used token would be if the reasons ever got distinct
statuses, and **`429` is the one status with machinery already in the bundle that could render something
else.** `createRateLimitNotice`, its middleware and `RateLimitNotice.tsx` all exist with **no production
consumer**, so R4's uniformity for a rate-limited verification currently holds partly by accident. That row
is what fails if a later task wires the notice into the public client.

### Two cases were probed rather than trusted

The suite passed **35/35 on its first run**, which is when a green suite deserves the least trust. The two
cases carrying this task's novel claims were measured by breaking the thing they assert:

| Probe | Result |
|---|---|
| Remove `setStatus('idle')` | `clears an earlier failure while the next token is in flight (the step-5 line)` fails — **1 of 35** |
| Add `response.status === 418` to the screen | `never reads the response body…` fails — **1 of 35** |

Each fails alone, which is what isolation looks like. Both reverted; `VerifyScreen.tsx`'s only diff is this
task's own.

### The inherited rewrites

`PINS A LIMIT` was rewritten with the lesson in it, since Phase 5 measured that **it did not fail when this
task landed** although its own comment said it must. Its assertion was a **sameness**, true when both arms
rendered nothing and true again now that both render the uniform failure — and sameness between two screens
says nothing about what is on either. It now also asserts both arms **contain the failure message**, so
deleting the whole failure branch can no longer leave it green.

Its header records what actually caught this task: T48's two **AC12** cases, which carried no handoff label
and pinned `textContent` **equal to the heading alone**. The label was not the difference; **an equality
against the full rendered text was.**

## Deviations

| ID | Status |
|---|---|
| **D-T49-1** | `routes.tsx` modified for the constant. **Ratified at Phase 4.** |
| **D-T49-2** | A tokenless arrival shows the uniform failure. **Ratified at Phase 4**, reversing Kimi's Phase 3. |
| **D-T49-3** | `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` modified — its catalogue key-set pin fires on any new key. **Raised at Phase 5; needs ratification at Phase 9.** |
| **D-T49-4** | Catalogue keys flat, not nested — the nested form crashes the copy scan. **Raised at Phase 5; needs ratification at Phase 9.** |

## Open

**OQ-T49-6 (new).** The resend link is live and its destination is element-less until task 50, so following
it lands on a blank document. The owner accepted this at Phase 4 as a known interim state. **Nothing in the
repository will fail when task 50 attaches that element**, so nothing will prompt anyone to re-check this
screen's link — worth a handoff instrument, and Phase 7 should decide whether this task owes one.

Carried: OQ-T49-3a, OQ-T48-4, R-T48-1, FU-T48-1, FU-T48-2.

**Phase 6 complete. Next is Phase 7 — Self-Review.**
