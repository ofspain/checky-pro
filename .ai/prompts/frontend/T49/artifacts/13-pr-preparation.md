<!-- MODEL: Claude Opus 5 — Phase 13 (PR Preparation). No code. -->

# frontend · T49 · Phase 13 — PR Preparation

| | |
|---|---|
| **Consumes** | `artifacts/12-specification-verification.md` — **PASS** |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 returned **PASS**, so this phase proceeds. `main` stays deployable.

> **Trailer note.** The phase prompt asks for a `Claude Opus 4.8 (1M context)` trailer. That name is stale;
> every commit here uses `Claude Opus 5`, and writing the prompt's literal string would be a false
> attribution. Raised identically at T47 and T48 — **three tasks running** — and the fix belongs in
> `.ai/generate.py`, which is also where **FU-T49-1** lives. Two generator defects now, found a task apart.

---

## Commit title

```
feat(frontend): show one uniform verification failure with a resend action (T49, R4)
```

## Commit message

```
feat(frontend): show one uniform verification failure with a resend action (T49, R4)

/app/verify showed the verified state and NOTHING AT ALL when verification did not
succeed: `idle` was held for the round trip and kept being held after a failure, so
an expired, used or truncated link left the visitor on a heading and empty space with
no way forward.

Render R4's single uniform failure message plus a neutral resend link for every
failure - any non-ok response, a transport that never answered, and a link carrying
no token at all. Split the state machine rather than relabelling `idle`, which now
means exactly one thing: the request is in flight.

A TOKENLESS ARRIVAL IS A FAILURE HERE AND R4 DOES NOT LITERALLY SAY SO (D-T49-2,
owner's decision, reversing the design challenge). R4 fires "IF the verification
REQUEST fails" and with no token no request is made. It is still the right screen: a
truncated or wrapped URL is the likeliest broken link anyone meets, the remedy is
identical to a rejected token, and the alternative left that visitor on the blank
screen the demo rehearsal had already found once.

Nothing reads the response body. `response.ok` is the only thing consulted, because
the backend maps every rejection reason onto one fixed 400 and the only way this
screen could leak which reason occurred is by looking. A source scan forbids it,
since one uniform message is a property a future edit removes one `if` at a time.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

---

## Files changed

`frontend/: 6 files changed, 772 insertions(+), 28 deletions(-)` over `d859c785..HEAD`.

**Production (3)**

| File | |
|---|---|
| `features/account/VerifyScreen.tsx` | `'idle' \| 'success' \| 'failure'`; tokenless → failure with no request; `setStatus('idle')` when a request starts; failure on a non-`ok` response **and** on a transport rejection; the message in the live region, the link outside it. Four new header paragraphs, each for something a later reader would otherwise undo. |
| `app/routes.tsx` | `RESEND_VERIFICATION_PATH` (**D-T49-1**). **This task adds the constant and not the element** — the reverse of how the five above it arrived. |
| `i18n/catalogue.ts` | `verify.failureMessage`, `verify.failureAction`, flat (**D-T49-4**). |

**Tests (3)**

| File | |
|---|---|
| `verifyFailureShowsOneUniformMessage.test.tsx` | **New, 38 cases**, the named suite (`package.md:108`). |
| `verifyFromLinkShowsSuccessState.test.tsx` | Three inherited cases rewritten (**AC11**) and its **falsified header corrected**. |
| `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | Two key-set entries (**D-T49-3**), list only. |

**Artifacts (16)** — `.ai/prompts/frontend/T49/artifacts/00` … `13`, plus the harness and three mutation-run
logs.

---

## Summary

`/app/verify` could already say "verified". It could not say anything else. This task gives every failure one
message and one way forward.

**What is worth a reviewer's attention is not the screen — it is that measurement overturned a decision, a
design, and two of my own claims.**

1. **The owner reversed the design challenge on the tokenless arrival**, which had chosen to render a bare
   heading and called the result *"a product gap … left for future ownership to close"*. A truncated URL is
   the likeliest broken link anyone meets, and the remedy is identical to a rejected token.
2. **That reversal created a requirement the design did not have.** T48's measured case drives *tokenless
   arrival → navigate with a real token*, so the screen is already in `failure` when a good token arrives.
   Without resetting to `idle` when the request starts, the failure sentence would sit on screen for the whole
   round trip of a verification about to succeed. Remove the line and exactly one case fails.
3. **Phase 5's probe found my own frozen brief wrong twice.** The design-system file *was* in scope — my
   measurement was right about the render-site pins and **blind to a second pin in the same file**. And the
   nested catalogue shape the design specified **crashes** an existing scan, because it flattens one level and
   assumes strings; a crash says the instrument is broken where a failure says the screen is.
4. **T48's labelled handoff case did not fire.** It says *"when task 49 lands, this case MUST FAIL — that is
   its purpose."* It passed: its assertion was a **sameness**, true when both arms rendered nothing and true
   again now both render the failure. What caught this task was two **unlabelled** cases asserting an equality
   against the full rendered text. **The label was not the difference; the equality was** — and the same
   lesson arrived again in Phase 10, where a new case of mine closed nothing because an exact-`href` equality
   written in Phase 6 already forbade a query string.
5. **A survivor that was not a gap, and a kill that was not a kill.** TR16 survived all 2083 cases because it
   was anchored where the token is `null` by definition — it recorded a hole that cannot exist. TR17's
   apparent kill was the FU-T42-1 flake, caught because the named failure was a **sign-in** suite; reading the
   count instead would have shipped a false finding.

One limit ships **pinned rather than fixed**: arrive with a token, then navigate tokenless while in flight,
and the one-request guard leaves the screen on its heading. Unreachable in the product today. The fix was
considered and rejected — it would make Back after a *successful* verification say *"This verification link
is not valid"* — and TR22 is that fix, dying by exactly one case.

## Testing performed

| | |
|---|---|
| Whole suite | **2086 / 2086**, 40 files |
| Named suite | **38 / 38** |
| `oxlint` / `tsc --noEmit` | 0 / 0 |
| Mutation harness | **20/20 regressions**, canary dead, `survivors: ['TR17','TR20']` both declared, **0 broken, 0 disagreements** |
| Per-file runs | every touched file green **alone** |
| Independent | Kimi 2.7 re-ran the suite, `tsc`, `oxlint` and the harness at Phase 8 and Phase 11, reproducing every number |
| Runs | Harness executed **four times**; the first three each changed something |

**Declared unfalsifiable, at the point of use rather than hidden in the score:** `TR17` (a path constant and
the identical literal are the same string — `TR18`, the rename, is what measures what the constant buys) and
`TR20` (`useEffect` → `useLayoutEffect`).

## Specification references

| | |
|---|---|
| **Task** | `spec/frontend/tasks.md` **T49** — verify failure is uniform |
| **Requirement** | **R4** (`requirements.md:16-17`; backend R5) |
| **LOCKED** | **L4** enumeration-safe UI, *including its timing clause* · **L6** generated client only · **L9** single origin · **L11** accessibility, managed focus, live regions · **L14** route set under `/app` · **L16** the only sessionStorage carve-out · **L17** service-worker boundaries |
| **Design** | **D7** every rendered catalogue key is measured |
| **Deviations** | **D-T49-1, D-T49-2** (Phase 4) · **D-T49-3, D-T49-4** (Phase 9) — all four ratified |
| **Unblocks** | **task 54** reuses `verify.failureMessage` (R15's text requires R4's uniform failure) |
| **Hands off to** | **task 50**, with an instrument that **fails when the resend element lands** and says to re-check this screen's link |
| **Open for the owner** | **R-T48-1** (the gateway hazard, whose visible consequence this task now owns) · **FU-T49-1** (`.ai/generate.py:301` gives every phase only the previous artifact — three false Kimi findings so far) · the stale trailer, same file · **OQ-T49-3a** · **OQ-T48-4** · **FU-T48-1** · **FU-T48-2** |

**Phase 13 complete. T49 is ready for merge.**
