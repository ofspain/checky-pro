<!-- MODEL: Claude Opus 5 — Phase 13 (PR Preparation). No code. -->

# frontend · T50 · Phase 13 — PR Preparation

| | |
|---|---|
| **Consumes** | `artifacts/12-specification-verification.md` — **PASS** |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 returned **PASS**, so this phase proceeds. `main` stays deployable.

> **Trailer.** This prompt now asks for a `Co-Authored-By:` naming *the model actually running*, rather than
> the hardcoded `Claude Opus 4.8 (1M context)` that T47, T48 and T49 each had to decline. **The regenerated
> prompt's own instruction is followed here**, and the model is `Claude Opus 5`.

---

## Commit title

```
feat(frontend): public resend-verification form, with no session (T50, R5)
```

## Commit message

```
feat(frontend): public resend-verification form, with no session (T50, R5)

/app/resend-verification was a declared public route rendering an empty document.
Task 49 promoted its path constant and deliberately attached no element, so R4's
uniform failure offered a resend action whose destination was a blank page - a known
interim state, recorded at that task's phase 4 gate.

Build the form. Four states, one POST through the generated public client, and the
acknowledgement replacing the form in place - because design.md's verbatim route list
has both /app/register and /app/register/sent but only /app/resend-verification, so
unlike registration this screen cannot send the visitor anywhere.

THE RESPONSE IS NEVER READ, AND THAT IS THE WHOLE OF R5'S UNIFORMITY ON THIS CLIENT.
auth.yaml declares one 200 whose description is "Always returned regardless of whether
the email matches an account", and resendVerificationIfPending never throws for a
non-match, so the server distinguishes nothing for this client to leak. The settle
handler takes no argument and response.ok is deliberately not consulted - the mirror
image of VerifyScreen, where R4 required failure to be distinguished and ok was the
one thing read.

A DROPPED CONNECTION SHOWS A NEUTRAL RETRY LINE WITH THE FORM STILL MOUNTED
(D-T50-1, owner's decision, reversing the design challenge). It costs R5 nothing: the
server answers every account state with one 200, so every server outcome still renders
the identical acknowledgement, and L4 is about account states and token states, which
a dropped connection is orthogonal to. The rendering varies by transport, never by
who the email belongs to.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

---

## Files changed

`frontend/: 12 files changed, 955 insertions(+), 42 deletions(-)` over `6ef16e55..HEAD`.

**Production (3)**

| File | |
|---|---|
| `features/account/ResendVerificationScreen.tsx` | **New.** `'idle' \| 'submitting' \| 'acknowledged' \| 'unsent'`; one POST; the acknowledgement replacing the form; the retry line on a rejection; focus moved to the heading; **nothing read from the response**. |
| `app/routes.tsx` | The element on the **already-declared** constant — and the constant's own header rewritten, because it still said the path was element-less. |
| `i18n/catalogue.ts` | The flat `resend` group, seven keys. |

**Tests (9)** — the new 30-case named suite (**shared with T51**), T49's handoff **replaced**, and **seven
pins**, three of which no probe could have predicted.

**Artifacts (23)** — `00` … `13`, the harness, and three mutation-run logs.

---

## Summary

The screen task 49 deliberately did not build. What is worth a reviewer's attention is not the form — it is
that **three separate instruments in this task measured nothing until something forced the question.**

1. **A case written for *"the email in no URL"* passed through the exact leak it was written to catch.** A
   memory router never reads `window.history`, so `router.state.location.search` stayed empty while the mutant
   put the email in the real browser URL. The row still died — in the **profile** suite, by a tree-wide scan
   forbidding a template literal before an `/accounts` path, which the mutant tripped incidentally. **A real
   kill, by an instrument with no interest in the claim, and the only reason the row did not survive.** The
   case now reads `window.location` too: the router's location is what the SPA *believes*, `window.location`
   is what history, autocomplete and `Referer` actually *carry*. Second such case in two tasks, after T42's
   instance storage spy.
2. **The frozen brief did an assertion's job.** AC11 said *"no sessionStorage write, no token read, the email
   in no URL and no console"* and only the first clause had an instrument. The console one now **derives**
   every function on `console` and **asserts the derivation**, because a reflection returning nothing makes
   every negative assertion vacuous.
3. **A probe measures the blast radius of the change, not of the fix.** Phase 2 measured six failures across
   five files; the task touched **twelve**. The three it could not see are second-order — T174's, T42's and
   T43's suites each re-pin the design system's counts by reading its **source**, and those numbers move only
   once the new file joins `RENDER_SITES`, which a probe of a production change never does.

**Two deviations, both ratified before implementation, and that is the process finding.** `D-T50-1` was the
owner's — the second consecutive task reversing a "render nothing" choice. `D-T50-2` was forced by
measurement: the frozen design's second `<Heading>` would have made this the first screen in the bundle with
two `<h1>`, against a rule the design-system suite pins for three other screens. **Phase 9 consequently had
nothing to ratify, the first time in four tasks** — T47, T48 and T49 each arrived there with deviations found
mid-implementation.

**Also fixed this session, outside the task:** `.ai/generate.py` gave every phase only its immediate
predecessor, which is why three consecutive Kimi test reviews reported already-ratified deviations as
outstanding. Phases 6, 9, 10 and 11 now receive the earlier artifact they need. **It is shipped and unproven
—** this task could not have reproduced the error, and Kimi's own Phase 11 header still quoted the old
`Consumes` line. **FU-T50-1** records the remaining half: Phase 8 reads the self-review alone and missed three
of twelve files because of it.

## Testing performed

| | |
|---|---|
| Whole suite | **2116 / 2116**, 41 files |
| Named suite | **30 / 30** |
| `oxlint` / `tsc --noEmit` | 0 / 0 |
| Mutation harness | **21/21 regressions**, canary dead, `survivors: ['TR20']` declared, **0 broken, 0 disagreements** |
| Per-file runs | every touched file green **alone** |
| Independent | Kimi 2.7 re-ran the suite, `tsc`, `oxlint` and the harness at Phase 8 and Phase 11, reproducing every number |
| Runs | Harness executed **five times**; the first three each found something, and every finding was mine |

**Declared unfalsifiable:** `TR20` (`useEffect` → `useLayoutEffect`), inherited from T48 and T49.

**Harness lessons, both already written down and both repeated:** T48's *a JSX mutant must leave valid JSX
behind* cost two cycles in one afternoon — TR05's caught by hand-applying before the run, TR09's caught only
by a `BROKEN` verdict after **1506 tests never ran**. And the copy lost `FILES` and
`KNOWN_ENVIRONMENTAL_FAILURES`: I had asserted the **imports** survived the splice, which is what broke in
copies 18 and 19, and this time the loss was in the **constants**. The harness now self-checks all sixteen
names before applying a mutant.

## Specification references

| | |
|---|---|
| **Task** | `spec/frontend/tasks.md` **T50** — resend is public |
| **Requirement** | **R5** (`requirements.md:18-20`; backend R6, whose own text was the stale half — `package.md` Q12(a)) |
| **LOCKED** | **L2/L5** no credential in the SPA · **L4** enumeration-safe UI · **L6** generated client only · **L11** accessibility, managed focus, live regions, catalogue-only · **L12** public-route reachability · **L14** route set under `/app`, verbatim · **L16** the only sessionStorage carve-out |
| **Design** | **D7** every rendered catalogue key is measured |
| **Deviations** | **D-T50-1**, **D-T50-2** — both ratified at **Phase 4** |
| **Closes** | task 49's handoff, replaced by a case pinning the destination's heading to `verify.failureAction` |
| **Unblocks** | **T51**, which owns R5's uniformity on this same named suite, and **OQ-T50-5** |
| **Open for the owner** | **OQ-T50-6** (a `500` says *"Check your email"* — deliberate, documented) · **FU-T50-1** (Phase 8's inputs) · **FU-T49-1** (shipped, unproven) · **FU-T43-2** (no shared test-util module; `type` copied a fourth time) · **R-T48-1** |

**Phase 13 complete. T50 is ready for merge.**
