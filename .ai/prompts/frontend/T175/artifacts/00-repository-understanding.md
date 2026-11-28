<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding). No code written. -->

# frontend · T175 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Task** | T175 — **A reply the client cannot parse is not a verification failure.** Verifies `verifyFromLinkShowsSuccessState`, `verifyFailureShowsOneUniformMessage`. Cites **R3, R4, L4**. Execute after 51. |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

**No code was written in this phase.** T51 is parked at its own Phase 11, and its suite count is the baseline
Kimi re-runs, so **nothing in `frontend/` moves until T51 closes.**

---

## 1 · Why this task exists, and why it is not T51

T51 measured that **`openapi-fetch` parses the reply body itself and its promise rejects on a `SyntaxError`**,
so any screen passing `.then(ok, fail)` treats *"the body did not parse"* as *"the request never arrived"*. T51
fixed its own screen with `parseAs: 'stream'`. Two other public screens have the same shape.

`VerifyScreen`, measured with a **valid** token:

```
204, no body (the declared success)  -> "Your email has been verified."
200, valid JSON                      -> "Your email has been verified."
200, HTML body (an edge error page)  -> "This verification link is not valid."   ← WRONG
200, JSON truncated mid-document     -> "This verification link is not valid."   ← WRONG
```

**A visitor whose verification succeeded is told it failed**, and R4's uniform failure then offers a resend for
an account that is already verified.

T51 declined to widen because the fix needs a decision — **what does R4's *"fails for any reason"* mean when
the failure is the body rather than the token?** — and that is a requirements question. **Making that decision
is this task's Phase 3 and Phase 4 gate.**

## 2 · The nuance that explains why this survived, and why it still matters

`contracts/api/auth.yaml` declares **one** response for `verifyEmail`:

```yaml
  operationId: verifyEmail
  security: []
  responses:
    '204':
      description: Email verified
```

**A `204` has no body, so there is nothing to parse, so a conforming server can never trigger this.** That is
why 2137 tests and four tasks over this screen never saw it: every fixture answers the way the contract says.

**It is reachable through an intermediary**, and that is not hypothetical here — **this project's edge is
Cloudflare** and the demo ran at a real domain. An interstitial, a challenge page, a `502` body served with
`200`, or a response truncated mid-document all arrive as *a body where the contract promised none*.

So the bug lives exactly where fixtures do not: **between a conforming server and a conforming client.**

## 3 · What exists

| | |
|---|---|
| `features/account/VerifyScreen.tsx` | `.then(({ response }) => setStatus(response.ok ? 'success' : 'failure'), () => setStatus('failure'))`. **It genuinely needs `response.ok`** — unlike T51's screen, R4 requires it to distinguish failure — so the fix cannot simply be *"read nothing"*. |
| `features/account/RegisterScreen.tsx` | `.then(() => navigate(REGISTER_SENT_PATH, { replace: true }), () => setStatus('idle'))`. Declared response `202`. **An unparseable reply means no navigation and no message**: the visitor presses the button and nothing visible happens. |
| `verifyFromLinkShowsSuccessState.test.tsx` (T48) | The success path. |
| `verifyFailureShowsOneUniformMessage.test.tsx` (T49) | 38 cases, the nine-status loop, `teardownCurrent`. |
| `resendVerificationAcknowledgement…test.tsx` (T50/T51) | 51 cases, the 15-arm pattern this task should borrow. |

## 4 · The hypothesis, stated so Phase 2 can try to break it

`parseAs: 'stream'` **should work here too**, because `openapi-fetch` still resolves with `response` — only
`data` becomes the stream — so `response.ok` keeps distinguishing a real failure.

**I have not measured that**, and it is the first thing Phase 2 must check. If it holds, both screens take the
same one-line change and R4's semantics are preserved rather than redefined. **If it does not**, the task needs
a different shape and the Phase 3 question becomes much larger.

## 5 · Unknowns

| | |
|---|---|
| **Is `parseAs: 'stream'` compatible with reading `response.ok`?** | **The hypothesis above. Unmeasured.** |
| **What should a genuinely unreachable server do here?** | A dropped connection on a verification link is a real failure, and R4 says *"fails for any reason"*. So the reject arm must keep meaning *failure* — the task narrows it to **transport** failure, not **parse** failure. Phase 3's to confirm. |
| **Does `RegisterScreen` belong in this task?** | `tasks.md` names both screens. It is a different requirement (R1/R2) and a different symptom. **I lean to fixing both** — the defect is one mechanism — but Phase 3 should rule on whether a second screen in one unit is scope creep. |
| **Is any third caller affected?** | **Unknown and cheap to find.** Phase 2 should enumerate every `.POST(`/`.GET(` in `src/` and classify each by what a reject arm does, rather than fixing the two screens that happen to be named. |
| **Does the uniform-failure copy need to change?** | If a parse failure becomes *success*, nothing changes. If it becomes a **third** state, that is new copy, a catalogue key and an L11 question. |

## 6 · Conventions carried in

Commit tests before the harness · run with the Docker stack stopped · no production file touched until T51
closes · the 15-arm comparison pattern and `installTypedTransport` are in T51's suite and should be borrowed
rather than reinvented (**FU-T43-2** is the open follow-up for the shared test-util module none of these live
in).

---

**Phase 0 complete. Next is Phase 1 — Specification Extraction.**
