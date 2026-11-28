<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation). Tests only; no production file changed. -->

# frontend · T49 · Phase 10 — Test Manifest

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md`, `10-mutation-run.txt` |

**No production file was changed in this phase.**

```
baseline whole suite: 0 failed of 2086, exit 0

regressions: 20/20 killed
canaries:    ['TR19'] - none survived
survivors:   ['TR17', 'TR20']   (both declared)
broken:      none
disagreements: none
```

---

## Three cases, and only two of them closed anything

The headline item was **F5 / Kimi's I4** — the missing handoff to task 50. Reading **which** cases died for
each new row then corrected one of my own claims, which is the recurring lesson here in its usual form.

### 1 · The handoff to task 50 — new, and it fires

R4's action is live and its destination is element-less until task 50, which the owner accepted at the Phase 4
gate. Measured first: `/app/resend-verification` renders **empty HTML** today, so the claim is writable.

**Written as a positive claim that inverts, not as a sameness**, because Phase 5 measured why that matters —
task 48's *labelled* handoff case did not fail when this task landed, while two unlabelled **equality**
assertions did. **TR23** does what task 50 will do in miniature, and it dies.

**But TR23 died in three files, and that tempers the finding rather than confirming it:**

```
TR23 └ in: ['publicRouteSetIsExactlyTheDeclaredSet', 'routesResolveUnderAppPrefixOnly',
            'verifyFailureShowsOneUniformMessage']
     └ HANDOFF TO TASK 50 - the resend target renders NOTHING today, so this case MUST FAIL then
```

So task 50 **was** already going to be stopped — by two screen-count pins. What was missing is not a gate but
**an instruction**: those pins say a count moved, and only this case's failure message says *"re-check this
screen's link"*. F5 was right that nothing pointed here, and slightly over-stated in implying nothing would
fail. Recorded as the more accurate version.

### 2 · The token must not reach the resend link's href — **this closed nothing, and the kill proves it**

A token in a link the visitor **clicks** is worse than one in a link they arrived on: it enters browser
history, is offered by autocomplete afterwards, and travels in the `Referer` of whatever loads next. The
forbidden edit is the *helpful* one — carrying the token over so the resend form can prefill.

**TR21 died in four cases, and the first of them is `offers a resend link that resolves under the app
basename` — a case written in Phase 6.** It asserts

```ts
expect(link!.getAttribute('href')).toBe(`${APP_BASENAME}/${RESEND_VERIFICATION_PATH}`);
```

and an **exact equality already forbids a query string**, token or not. **So this new case did not close an
open hole.** It adds the reasoning, and a narrower `?`-specific assertion that fails with a clearer message,
but the protection was already there.

**This is the same shape as Phase 5's finding, from the other side:** an equality against a full value is a
much stronger instrument than it looks, which is why T48's `textContent` equalities caught this task while
its labelled sameness did not. Kept for the comment; **not claimed as a gap closed.**

### 3 · A limit, measured and pinned rather than fixed — new, and TR22 dies only by it

Arrive with a token, then navigate to the same route **without** one while the request is in flight: the
one-request guard is already latched, so the effect returns early and the screen keeps a heading and empty
space — **the exact defect this task exists to remove, surviving in a corner.**

**Not reachable in the product today**: nothing in the SPA navigates to `/app/verify`, and every real arrival
is a fresh page load from an email, so `handled` is `false`.

**The fix was considered and rejected.** Moving the tokenless branch above the guard closes it — that branch
sends no request, so the guard need not cover it — but it would make a **Back navigation after a successful
verification** display *"This verification link is not valid"*, which is worse than a stale heading. **TR22 is
that fix**, and it dies in exactly one case: the limit. A row that *should* die, pinning a cost rather than a
feature.

## The three rows

| Row | Outcome | |
|---|---|---|
| **TR21** the token carried into the resend link | `KILLED`, 4 cases | All in this task's suite — and the first was already there (above) |
| **TR22** the tokenless branch moved above the guard | `KILLED`, 1 case | The limit, refusing its own "fix" |
| **TR23** an element attached to `resend-verification` | `KILLED`, 3 cases | The handoff, plus two count pins |

## Coverage map

| AC | Cases |
|---|---|
| **AC1 / AC15** | rejected token · tokenless **sends nothing** · empty token |
| **AC2 / AC13** | nine statuses identical · a problem document changes nothing and leaks no `detail` |
| **AC3** | a transport that never answers, with the call count asserted |
| **AC4 / AC5** | the link exists · its href carries the basename · **identical `outerHTML` across four arms** · absent on success · **no token in the href** |
| **AC6** | no failure copy in flight · an earlier failure cleared · success after failure · StrictMode |
| **AC7** | the region mounted **empty first** · the message inside it · the link **outside** it · focus stays on the heading · keyboard-reachable with a name |
| **AC8** | both keys literal · **ten reason-words forbidden** · no untranslated key |
| **AC9** | one call, no `Authorization`, body is the URL's token · **the response body is never read** · generated client, no bare `fetch`, no storage |
| **AC10** | token in no failure DOM · no storage, nothing logged (a **derived** console list) · **a positive control for both spies** |
| **AC11** | T48's three inherited cases rewritten |
| **AC12** | inherited: `VerifyScreen.tsx` is in the design system's tag and props sweeps |
| **AC14** | 2086/2086, and each touched file green alone |
| **new** | the task-50 handoff · the href's query string · the in-flight→tokenless limit |

## Not added, and why

- **No case for TR17 or TR20.** Both are declared unfalsifiable; a case killing either would be pinning a
  spelling or a hook rather than a behaviour.
- **Nothing for a wired rate-limit notice.** `createRateLimitNotice`, its middleware and `RateLimitNotice.tsx`
  have no production consumer, so there is nothing to render and nothing to forbid it *to*. **AC13 pins the
  `429` status**, which is the part that exists; a scan for a wiring that does not exist would be aimed at
  nothing.
- **No failure-state copy for task 54.** R15's reuse is that task's, and inventing its framing here would
  pre-empt it exactly as T48 declined to invent R4's.

**Phase 10 complete. Next is Phase 11 — Test Review (Kimi 2.7). STOP HERE.**
