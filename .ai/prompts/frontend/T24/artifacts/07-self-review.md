# frontend · T24 · Phase 7 — Self Review

| | |
|---|---|---|
| **Service** | `frontend` |
| **Task** | T24 — Return target inside allowlist |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md` |

Reviewed the Phase 6 diff against the frozen brief and `agents.md`. **Nothing in it is wrong against the frozen
brief**, and the allowlist itself is as tight as the brief asked: set membership, derived, read inside a function,
total, guarded.

Five findings, in the order I would fix them. Two are behaviours the brief never decided and that the code decided
by accident — the kind of thing that is easiest to settle now and hardest to change once a test pins it. Findings
only; fixes are Phase 9's.

---

## S1 · A visitor who leaves the callback screen keeps their return target forever

**Severity.** Medium — a decided-by-accident behaviour, and the less L16-faithful of the two options.

**Evidence.** `CallbackScreen.tsx:192` is `if (onScreen.current) navigate(takeReturnTarget(), { replace: true });`
The call is **inside** the guard, so when the visitor has left, `takeReturnTarget()` never runs and the stored
target is never consumed. L16 calls the item *"single-use and deleted at callback"*; here a callback completed —
the token was held, the three PKCE items were consumed — and the fourth item survived it.

The consequence is small but real: the visitor abandons a callback, signs in again later in the same tab, and is
sent to a destination they were heading for in a previous journey. Harmless, because it is allowlisted, and
surprising, because nothing they just did asked for it.

**Recommendation.** Consume unconditionally and guard only the navigation:

```
const target = takeReturnTarget();
if (onScreen.current) navigate(target, { replace: true });
```

T24-D7 is about not moving someone who left; it is not about leaving storage behind. This keeps both promises, and
it is one line.

## S2 · `rememberReturnTarget` will store a value that can never be honoured

**Severity.** Medium.

**Evidence.** `returnTarget.ts:78-85` stores whatever string it is given. T24-D3 put the authority on the read,
and that part is right — the read is the trust boundary and the decision must be made once. But *authority* and
*filtering* are not the same thing: nothing stops a future caller from parking
`/app/invoices/123?token=eyJ…` in sessionStorage, where it sits until the callback rejects it. L3 and AC8 are
about what may be **in** storage, not only about what may be acted on.

**Recommendation.** Have the write call the **same predicate** and store nothing that could not be honoured:

```
if (!isAllowlistedTarget(target)) return;
```

This does not create a second decision point — it is the same function, and the read still decides, which matters
because the route table can change between the write and the read. It only narrows what can ever sit in the store.
The cost is that `rememberReturnTarget` becomes silently selective, which the doc comment must say plainly.

## S3 · A discarded callback leaves the target stored, and nothing decides whether that is right

**Severity.** Low — but it is unstated, and T25 will write tests against whatever it finds.

**Evidence.** The navigation and the consumption both live on the successful path only. Every discard therefore
leaves the target in storage, which is almost certainly what we want — the visitor retries and still gets where
they were going — and is the exact mirror of T23-D2's reasoning (a path that consumed nothing deletes nothing).

**Recommendation.** Record it as a decision with that reasoning, so T25 inherits a stated position rather than
inferring one from behaviour, and add the case to Phase 10: a discard leaves the target, and the retry honours it.
This is also the one place where "single-use" and "survives a failed attempt" could be read as contradicting each
other, and the artifact should say which reading won and why.

## S4 · T26's carve-out guard now has two sources to enumerate

**Severity.** Low — a handoff, not a defect.

**Evidence.** `authorizeRequest.ts:32-33` says the keys are published *"so task 26's carve-out guard enumerates
the same keys, rather than either copying the strings"*. T24 adds `RETURN_TARGET_KEY` in a different module
(T24-D4). Task 26 — *"session storage contains nothing beyond the L16 list"* — must now read **both**
`AUTHORIZE_STORAGE_KEYS` and `RETURN_TARGET_KEY`, and a guard that enumerates only the first would report the
return target as a violation of the very carve-out that names it.

**Recommendation.** Record the handoff in the artifact and name both constants in it. No code change: a single
combined constant would put the key back in the module whose deletion must not reach it, which is what T24-D4
exists to prevent.

## S5 · Five acceptance criteria are not verified, and the green suite does not say otherwise

**Severity.** Medium — status.

**Evidence.** Every case in the suite signs in **without** remembering a target, so the whole allowlist-admits
path is unexercised. The suite proves AC11, AC12, AC13, AC14 (by an inherited case that passes unchanged), and —
structurally — AC4. It proves nothing about **AC1** (an honoured target), **AC6/AC17** (the derivation in either
direction), **AC15** (a corrupted value, a throwing store), **AC16** (a throwing removal), or AC7's read-before-
delete ordering as a behaviour rather than a construction.

**Recommendation.** Phase 10's spine, and Phase 12 must not accept the seventeen updated inherited assertions as
evidence for any of them: they measure that the navigation happens, not that the allowlist decides correctly.
Add the module-scope scan for the third strand of the import cycle too — it is the only instrument that catches
that defect class, as T18's equivalent case says of its own.

---

## What the review found sound

- **The allowlist cannot drift.** It is derived from `AUTHENTICATED_ROUTE_PATHS` on each call, so a route added
  there is legal with no second edit and a stale entry cannot outlive its route. There is no second list.
- **Set membership refuses every attack family for one reason.** Nothing is stripped, normalised or decoded, so
  there is no normalisation step to get wrong — which is where open-redirect bugs usually live.
- **The third strand of the import cycle is closed deliberately**, with the measured reason written down: Vite
  yields `undefined` rather than throwing, and an empty allowlist fails **closed and silently**.
- **`takeReturnTarget` is total**, and the reason is the measured one: `discard` is `.then`'s second argument and
  does not see a synchronous throw from the first, so a throw here would blank the page after a successful
  sign-in.
- **T24-D7 is verified by an inherited test that passes unchanged**, which is the strongest form this package
  has: T21 wrote the intention in words before there was a navigation to break.
- **The two defects Phase 6 found were found by other tasks' tests**, and the production fixes are better than
  what they replaced — the conditional removal is the honest reading of "single-use", and the absolute-URL rule
  was right to cover comments.

## Open questions for the gate

1. **S1** — consume the target even when the visitor has left? (Recommended: yes, one line.)
2. **S2** — filter on write with the same predicate? (Recommended: yes.)
3. **S3** — confirm that a discard deliberately leaves the target for the retry, and record it.
