<!-- MODEL: Claude Opus 5 — Phase 7 (Self-Review). -->

# frontend · T175 · Phase 7 — Self-Review

| | |
|---|---|
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md`, `07-mutation-harness.py`, `07-mutation-run.txt` |

```
baseline whole suite: 0 failed of 2146, exit 0
regressions: 5/5 killed   canaries: ['TR36'] none survived
survivors: none   broken: none   disagreements: none
```

---

## F1 · TR33 answers the question the task was most at risk of getting wrong

The fix makes a reply the client **cannot parse** succeed. The danger is a fix that makes **everything**
succeed — one that trades an R3 violation for an R4 violation and passes every new case while doing it.

```
TR33 CONTROL the ok test is inverted, so a real 4xx would report success  (31 failed of 2146)
  └ renders the uniform failure for 400 … 401 … 403 … 404 … 409 … 410 … 429 … 500 … 503
  └ a tokenless arrival and a rejected token render the SAME uniform failure (R4, T49)
  └ names no failure reason anywhere on screen (L4)
  └ renders the success state for a 204 … (AC1, AC4)
```

**Thirty-one cases, the whole nine-status loop among them.** R4 is emphatically still held, and
`verifyFailureShowsOneUniformMessage` — a suite this task did not touch — is what holds it.

**Honest about its limits:** TR33 and the canary TR36 have nearly the same blast radius, 31 cases each, so
TR33 does not *discriminate* finely. Its value is **the direction of the answer, not the count** — it asks one
question (*did the fix stop `ok` from mattering?*) and answers no.

## F2 · The scan is a genuine second instrument, not a restatement

```
TR31 parseAs removed from VerifyScreen    (4 failed) └ in: [the scan, verifyFromLinkShowsSuccessState]
TR32 parseAs removed from RegisterScreen  (5 failed) └ in: [the scan, registrationAcknowledgement…]
```

Each kills **both** its screen's behavioural cases **and** the scan's two cases. The behavioural cases prove
the defect is gone; the scan proves it cannot come back **in a screen nobody has written yet**, which is the
part no behavioural case can do.

## F3 · TR34 measures the claim I cared most about, and it fires

**The first draft of that scan lost reach silently** — keyed on factories while two call sites take their
client as a parameter. The reach audit exists for exactly that, and TR34 proves it is load-bearing:

```
TR34 the scan forgets a client type …  (2 failed)
  └ knows every factory and client type that api/client.ts exports, so its reach cannot shrink quietly
  └ finds every generated-client call, and the census is ASSERTED so an empty scan fails (AC10)
```

**Two instruments fire, not one** — the audit catches the shrunken list, and the census catches the call sites
that went missing because of it. A scan whose reach can shrink unnoticed is worse than no scan, because it
reports success.

**TR35 isolates the census exactly**: one case, nothing else.

## F4 · Every new case failed before the fix, and the scan's non-vacuity was re-measured after

| | |
|---|---|
| Before the fix | **2** verify cases, **3** registration cases, **2** scan cases |
| After | 2146 / 2146 |
| Re-measured after | Removing `parseAs` from `VerifyScreen` makes the scan fail **and name the file** |

The second row matters because **a scan that has only ever been green is not known to work.**

## F5 · Deviations, and one of them is a correction of my own brief

**D-T175-5** — AC4/AC5/AC6 got **no new cases**: `verifyFailureShowsOneUniformMessage` already holds all
three. **My Phase 4 brief was wrong to ask, and the fact was in my own Phase 0 artifact**, which described
that suite as *"38 cases, the nine-status loop"*. I had it and did not apply it one phase later. TR33's
31-case kill is the receipt that those instruments are real.

**D-T175-6** — a third `installTypedTransport` (FU-T43-2), plus a control case pinning that
`new Response(body, { status: 204 })` **throws**, so if that ever changes the `200` arms stop being the only
reachable shape.

**D-T175-1 … 4** were all ratified at Phase 4, before implementation.

## F6 · Gaps for Phase 10

| | |
|---|---|
| **`OQ-T175-7` is a real hole in the reach audit** | `codeExchange` imports `createOidcClient` from **`@themistra/api-client`**, not from `api/client.ts`. The call-finder catches it; the **audit does not**, so a factory added to the *package* would widen the surface without breaking a case. **The fix is cheap** — audit the package's exports too — and I would do it in Phase 10. |
| **No case proves the two screens' fixes are independent** | TR31 and TR32 are separate rows, which is better than one, but nothing asserts that fixing one did not rely on the other. |
| **`RegisterScreen`'s reject arm is now almost unreachable** | With `parseAs`, only a transport failure reaches `setStatus('idle')`. **Is that arm still tested?** I do not know, and *not knowing* is the reason to look. |
| **The scan does not cover `.PUT`/`.PATCH`/`.DELETE` in practice** | The pattern includes them; **no call site uses them**, so those branches are unexercised. A case with a fixture file would measure the pattern rather than the tree. |

## F7 · Carried open items

**OQ-T175-5** (the error inverts — the posture L4 already chose), **OQ-T175-6** (`codeExchange`'s
visitor-facing behaviour on an edge interstitial, unmeasured and out of scope), **OQ-T175-7** (above),
**FU-T43-2**.

---

**Phase 7 complete. Next is Phase 8 — Independent Review (Kimi 2.7). STOP HERE.**
