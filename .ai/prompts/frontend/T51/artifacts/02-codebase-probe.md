<!-- MODEL: Claude Opus 5 — Phase 2 (Codebase Probe). Everything below was MEASURED. No production file left changed. -->

# frontend · T51 · Phase 2 — Codebase Probe

| | |
|---|---|
| **Consumes** | `artifacts/01-specification-extraction.md` |
| **Produces** | `artifacts/02-codebase-probe.md` |

**Working tree clean at the end of this phase.** Every number below comes from a run.

---

## 1 · THE HEADLINE: R5's uniformity is broken today, and 2116 tests cannot see it

I drove twelve outcome arms through the real screen, each in its own case, and compared the rendered markup.

```
200 declared   ack=Y unsent=n form=n focus=H1   len=362
200 other msg  ack=Y unsent=n form=n focus=H1   len=362
200 no body    ack=Y unsent=n form=n focus=H1   len=362
200 NOT JSON   ack=n unsent=Y form=Y focus=BODY len=859   ← ■
200 empty str  ack=Y unsent=n form=n focus=H1   len=362
400            ack=Y unsent=n form=n focus=H1   len=362
403            ack=Y unsent=n form=n focus=H1   len=362
404            ack=Y unsent=n form=n focus=H1   len=362
429            ack=Y unsent=n form=n focus=H1   len=362
500            ack=Y unsent=n form=n focus=H1   len=362
503            ack=Y unsent=n form=n focus=H1   len=362
204 no content ack=Y unsent=n form=n focus=H1   len=362
```

**Eleven arms are byte-identical. A `200` whose body is not valid JSON renders a different screen** — the
form, still mounted, under *"We could not send that just now. Try again."*

**The server said the email was sent. The visitor is told it was not.**

### The mechanism, measured directly

```
MECHANISM  REJECTED SyntaxError: Unexpected token 'o', "not json" is not valid JSON
```

`openapi-fetch` parses the body itself and its promise **rejects**. `ResendVerificationScreen` passes
`.then(acknowledge, unsent)`, so a parse failure is indistinguishable from a dropped connection.

### Content-type does **not** gate it

```
html body, says JSON   ct=application/json                 ack=n unsent=Y
html body, says HTML   ct=text/html                        ack=n unsent=Y
html body, NO ct       ct=null                             ack=n unsent=Y
truncated JSON         ct=application/json                 ack=n unsent=Y
plain text             ct=text/plain                       ack=n unsent=Y
JSON + charset ct      ct=application/json; charset=utf-8  ack=Y unsent=n
```

**Any non-empty body that does not parse as JSON takes the failure arm**, whatever the headers say. Only valid
JSON and an *empty* body resolve.

### It is reachable, and the reachability is specific to this deployment

| Route to it | |
|---|---|
| **An edge interstitial or error page carrying `200`** | **This project's edge is Cloudflare** (`demo-deployed-and-destroyed`, the live run at `checkychain.com.ng`). An HTML challenge or error page is exactly this shape. |
| **A truncated response** | A connection cut mid-body gives a real status and half a JSON document. |
| **Any proxy ahead of the service** | Returns its own body with its own content type. |

### And the enumeration consequence is the one L4 exists to close

The screen's rendering now depends on **the shape of the response body**. A proxy, WAF or future backend that
answered differently-shaped bodies for different account states — a JSON acknowledgement for a pending
account, an HTML error for an unknown one — would turn this form into an **enumeration oracle**, and the
client would faithfully render the difference. L4 forbids precisely that: *"Copy, redirects, styling, and
timing are identical across unknown or missing, locked, suspended, and deleted accounts."*

### The irony is exact, and it is the lesson

**T50 proved the screen never READS the response** — a source scan forbidding `response`, `data`, `error`,
`.ok`, `.status`, `statusText`, `.json(`, `.text(`, `.message`, and asserting the handler is argument-less.
That scan is the structural defence for R5's uniformity, and **it is still green**, because
**the parse happens inside the library, not in the screen's source.**

*Never reading the response* was necessary and **not sufficient**. A source scan cannot see a dependency the
source does not name.

## 2 · The repair, also measured — and invisible to every existing test

The fix removes the dependency rather than catching the symptom: tell the client not to parse a body this
screen never reads.

```diff
-  .POST('/accounts/resend-verification', { body: { email } })
+  .POST('/accounts/resend-verification', { body: { email }, parseAs: 'stream' })
```

```
             WITH THE CANDIDATE FIX
200 good JSON    ack=Y unsent=n len=362
200 html body    ack=Y unsent=n len=362
200 truncated    ack=Y unsent=n len=362
200 plain text   ack=Y unsent=n len=362
200 no body      ack=Y unsent=n len=362
500 html body    ack=Y unsent=n len=362
503 good JSON    ack=Y unsent=n len=362
DROPPED          ack=n unsent=Y len=859     ← preserved, which is the half that must NOT change
```

All seven server arms collapse to the same 362 characters; **a dropped connection still shows the retry
line**, so D-T50-1 survives. `tsc --noEmit` clean.

**Whole suite with the fix applied: 2116 / 2116 — not one test moved.**

That is the sharpest fact in this phase. **The hole is invisible to 2116 tests and so is its repair**, so T51
brings the only instrument that will ever hold this. Phase 2's standing lesson — *a probe measures the blast
radius of the change, not of the fix* — was applied in both directions here, and this time the fix's radius is
what mattered.

**Open, honestly:** `parseAs: 'stream'` leaves the body stream unconsumed. For a one-shot form submit that is
negligible, but it is a real difference from today's behaviour and Phase 3 should say whether it prefers
catching the `SyntaxError` instead. **I prefer `parseAs`** — it states the intent (*this screen has no
interest in the body*) where a `catch` would re-describe a symptom.

## 3 · So T51 is **not** a test-only task

Phase 0 expected no production change and said that if one were needed, *"that is a finding, not a task."*
**It is a finding.** T50 shipped a uniformity defect that its own suite could not catch, and the requirement
T51 owns is the one it violates. One line of production code, plus the cases that hold it.

## 4 · A Phase 0 claim that measurement falsified

Phase 0 said `teardownCurrent()` was already in this suite, *"carried over from T49 with the helpers"*, and
listed it among what T51 needs. **It is not there.**

```
$ grep -rln teardownCurrent src/
src/features/account/verifyFailureShowsOneUniformMessage.test.tsx
```

Only T49's suite has it. T50's suite unmounts in `afterEach`, so **a case can arrive exactly once**, and the
byte-identical comparison needs two arrivals inside one case. **T51 must port it.**

Measured that porting works, since a claim about a helper is what started this:

```
TWO-ARM  identical=true  firstLen=362  secondLen=362
```

## 5 · Probe bugs worth recording, because both wasted a cycle

| | |
|---|---|
| **`type()` is `async` and I called it without `await`** | The floating `act` produced `TypeError: 'set value' called on an object that is not a valid instance of HTMLInputElement` on **every arm after the first** — an error that reads like a cross-realm teardown problem and is nothing of the kind. **A confusing error in a probe is usually the probe.** |
| **The first run showed `ack=n` for the declared `200`** | Same cause. **Had I trusted it, I would have reported that the screen never acknowledges at all** — a false headline, from a probe that was wrong in a way the output did not advertise. |

Both were caught because the arm list had a *known-good* row at the top. **A probe needs a control as much as
a case does.**

## 6 · Blast radius

| | |
|---|---|
| **Production** | `ResendVerificationScreen.tsx`, one line. |
| **Tests** | The named suite. Expected: **no other file.** |
| **Measured** | The whole suite passes **with the fix applied**, so no pin fires. The source scan is unaffected: `parseAs: 'stream'` contains none of its forbidden words — **checked, not assumed**, since a scan tripping on the fix is exactly the second-order surprise T50 met three times. |
| **Catalogue** | No new key expected, so **D7's key-set pin should not fire.** If a key is added, it fires, and `RENDER_SITES` is untouched either way — so the three second-order pins that surprised T50 cannot fire here. |

## 7 · Questions for Phase 3

| ID | |
|---|---|
| **OQ-T51-5** | **`parseAs: 'stream'` or a `catch` on the `SyntaxError`?** I prefer `parseAs`; measured equivalent for every arm tried. |
| **OQ-T51-6** | Should the **other three public screens** be checked for the same hole? `RegisterScreen` and `VerifyScreen` both read `response.ok`, so a parse failure reaches them differently. **Out of T51's scope by `tasks.md`, and it is a real question** — recorded, not silently widened. |
| **OQ-T51-1 … 4** | Carried from Phase 1: the status set, raw vs normalised `outerHTML`, a differing-email arm, and whether the timing clause gets an instrument. |

---

**Phase 2 complete. Next is Phase 3 — Design Challenge (Kimi 2.7). STOP HERE.**
