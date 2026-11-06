<!-- MODEL: Claude Opus 5 — Phase 9 (Review Resolution) -->

# frontend · T33 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T33 — Clean re-auth on failed renewal |
| **Consumes** | `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |
| **Gate** | Human Approval — not a stop under the standing workflow |

Thirteen findings. Every cited `file:line` was read against source first. All thirteen citations are
accurate. Six are taken into code, one is answered with a measurement, six need no action — four of them by
the review's own conclusion.

## I1 — the Blocker. Held at resolution (c), and the premise is narrower than the finding states.

Kimi's three options are the same three phase 4 considered, and phase 4 already established that **(a)
"identify and assign the wiring task" is unavailable**: no unit of `tasks.md` constructs
`createSessionBoundary`. Phase 9 checked the remaining half of the argument — whether **(b)**, T33 doing
the wiring itself, would actually make the routing reachable. **It would not.**

```
grep -rn "createAuthApi\|readAccount\|readProfile" frontend/src --include='*.ts*' | grep -v '\.test\.'
```

returns **definitions and doc comments only**. No production file calls `createAuthApi`. No production file
calls `readAccount` or `readProfile`. So even with the boundary constructed in `mountShell` and a
`navigate` backed by the router:

1. no production code would issue an authenticated request,
2. so no 401 would arrive,
3. so no renewal would be attempted,
4. so the handler would never run.

**Wiring moves the dormancy up one level; it does not remove it.** It would also add a `BroadcastChannel`
and a Web Locks request to every page load for no benefit, and edit `mountShell.tsx`, which no task owns.

So the gap is not "T33 lacks its wiring". It is that **the SPA's entire authenticated data path is dormant
by task order**: tasks 5, 6 and 33 built the renewal machinery, tasks 30 and 31 built the readers, and
nothing calls any of it, because the screens that would are tasks 173/174 and beyond. That is a roadmap
fact, visible in one grep, and it is the owner's to act on.

**Resolution (c) is held and sharpened.** T33's acceptance criteria are satisfied **at the module level**,
driven through the real boundary rather than a stub. AC2, AC3, AC7 and AC8 are statements about how the
hook *will* be called, and the phase 12 artifact will say so in the traceability matrix rather than
claiming a runtime proof. Kimi's closing line — "honesty does not make the task complete" — is right, and
the honest completion status is **"complete as specified in `tasks.md:61`, dormant as built"**.

## Taken into code

**I3 — Major — TAKEN.** The AC8 case removed the runner's `uncaughtException` listeners *before* entering
the `try`, so a throw between `removeAllListeners` and the `try` would have left the runner with no
handlers and never reached the `finally`. Both calls are now inside the `try`. The window is closed, not
narrowed.

**I4 — Minor/Major — TAKEN, and the finding is right about the cost.** `/\bcatch\b/` blocked a legitimate
future change — guarding a throwing `navigate` — in a test whose subject is the failure *reason*. The ban
is now `/catch\s*\(/`: a binding can inspect what was thrown, `catch {}` cannot. A control proves the
narrowed pattern still fires on `catch (error) { report(error) }` and lets `catch { }` by.

**I9 — Minor — TAKEN.** A scan now pins that `cleanReauth.ts` imports no router and does not reach for
`globalThis.history`. Injection was the only thing holding that decision, and a direct import would have
passed every behavioural case in the file. It also has a lower bound, so it cannot pass over nothing.

**I5 — Minor — TAKEN, and it was the most useful of the small findings.** The forbidden-word list was my
judgement presented as a rule. It is now grounded in L4's own text, group by group — account states, token
conditions, steps and factors — with the fourth group (`server`, `network`) marked explicitly as **not**
L4's text but mine. A new case draws the boundary the list leaves implicit: **L4 forbids naming the cause
or the account, not the event**, which is why `session` is absent and why the sentence may say a session
has ended. Kimi suggested adding `session` and proving the test then fails; the case states the trade
directly instead, since a list that banned the event word would reject the sentence this task is required
to show.

**I10 — Minor — TAKEN as the comment it asked for**, with the reason: excusing the return target *inside*
the leak sweep would mean writing a needle exception, and an exception in a leak sweep is the one place a
real leak can hide. Taking it first means the sweep has no exceptions at all.

**I2 — Minor/Major — TAKEN.** NR17 is now scored apart. The harness prints `regressions: n/n killed` and a
separate `canaries:` line, and a canary that survives still fails the run. The reasoning is Kimi's and it
is right: a mutation score measures the current code, and a row everyone expects to change state teaches a
reader to discount the number.

## Answered with a measurement, not taken

**I6 — Minor — DECLINED, on evidence.** Extracting `stripBasename` would edit `authorizeRequest.ts`, which
is T15's production code, and **T15's suite reads that file's source** (`authorizeRequestsExactlyTheDeclaredScopes.test.ts:67`,
`moduleSource`) and scans it. So the refactor is not additive: it is a cross-task production edit under a
source-shape instrument, for a seven-line function in two places. The duplication is real and is recorded;
the trade is not worth it inside T33.

## No action

**I7 — Minor/Major — NOT ACTIONED, with the reason stated rather than deferred.** Kimi's argument is that
a new `src/testing/boundaryHarness.ts` is additive and so safer than editing the closed tasks' tests. True,
but it does not reduce duplication: tasks 5 and 6 keep their inline copies, so the result is three
implementations with one of them shared-*ready*. The benefit arrives only when the closed tasks adopt it —
which is the edit being avoided. Recorded as the second helper family in the **OQ-T32-4** debt, now raised
by five reviews across two tasks.

**I8, I11, I12, I13 — no action**, as the review itself concludes for each. I8 notes the three key-set pins
are correct and justified and asks for nothing. I11 leaves live-region behaviour to T15/T18, which own it.
I12 and I13 are informational and confirm the zero-parameter handler and the phase 3 destination question
resolved.

## Gates after the repair

| Gate | Result |
|---|---|
| T33 cases | **31 passed** (29 → 31) |
| Whole suite | **1568 passed / 1 failed of 1569** — the known `oxlint` environmental case |
| Mutation | **16/16 regressions killed + 1/1 canary**, 0 survivors, 0 broken, `HARNESS_EXIT=0` |
| `tsc --noEmit` | exit 0 |
| `oxlint src` | exit 0 |

## Carried to the owner

- **OQ-T33-2, sharpened** — not "who wires the boundary" but: tasks 5, 6, 30, 31 and 33 are all built and
  **nothing calls any of them**, because no screen yet makes an authenticated request. Wiring T33 alone
  would not change that. Is the dormancy intended until tasks 173/174?
- **OQ-T32-4** — helper duplication, now measured in two families (scan helpers, boundary harness) plus the
  three catalogue key-set pins, and raised by five independent reviews.
- **Spec drift** recorded in phase 1: `package.md:171` maps a named test to a task `tasks.md:62` removed,
  and `package.md` §11's Q5 is not struck through though `requirements.md:69-76` records it resolved.

**Phase 9 complete.**
