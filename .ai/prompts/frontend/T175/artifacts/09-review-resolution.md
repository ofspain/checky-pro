<!-- MODEL: Human Approval — Phase 9 (Review Resolution). -->

# frontend · T175 · Phase 9 — Review Resolution

| | |
|---|---|
| **Consumes** | `artifacts/07-self-review.md`, `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

Kimi's Phase 8 verified independently — **64/64** in the affected suites, **2146/2146** whole, **5/5 killed** —
and raised **13 findings: nine positive, one Medium, two Minor, one Observation.** No finding disputes the fix.

---

## I10 — **ACCEPTED** · the reach audit does not cover the package

The one Medium, and **it is the hole I reported on myself** at Phase 7 (OQ-T175-7). `codeExchange` imports
`createOidcClient` from **`@themistra/api-client`**, so a factory added to the *package* widens the surface
without breaking a case. **Phase 10 extends the audit to the package's own exports.**

## I11 — observation accepted, **recommendation DECLINED, because it points the wrong way**

Kimi is right that `.then((result) => result.data)` would be classified a **non-reader**: `namesData` requires
a destructuring `{`. The recommendation is to broaden it to `\.data\b` anywhere in the statement.

**That makes the scan less safe, and the asymmetry is the whole argument.**

| Misclassification | What it costs |
|---|---|
| A false **non-reader** | The scan **demands `parseAs` on a call that reads the body**. It fails loudly; someone must look. Kimi's own Phase 3 design erred this way and Phase 6 caught it. |
| A false **reader** | The call is **exempt from the rule**. The scan goes green and the defect that cost three screens can ship a fourth time. |

A safety scan must err toward the **loud** mistake. Broadening reader detection manufactures the silent one —
and `\.data\b` is a particularly leaky widening, since `response.data` or any unrelated `.data` in the same
statement would grant the exemption.

**And Kimi's own evidence contains the reason no change is needed:** *"The census assertion would break if
such a pattern were introduced."* It would — the row's `kind` flips, the table stops matching, **and** the
invariant case fires too, so the brittleness is **self-announcing and fails twice**. That is what the census
is for.

**Instead, Phase 10 measures the classifier directly** with a fixture-based unit test of `sitesIn` on
synthetic source. That is strictly better than widening a regex on reasoning: it pins **which** shapes
classify as what, including the `.then((r) => r.data)` shape Kimi named, so the day someone widens the rule
they must change a case that says what the old answer was.

## I12 — **I ANSWERED IT WRONG, AND PHASE 10's HARNESS CAUGHT ME**

> ⚠️ **This section originally read "MEASURED. Kimi guessed and said so; the answer is *no case exists*."**
> **It was false.** It is rewritten rather than deleted, because the instrument that produced it is the
> finding.

Kimi hedged — *"the original T43 registration suite may have tested it"* — and **Kimi was closer to the
truth than my measurement.** The case exists:

```
registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:899
  it('returns the form to its idle state when the send rejects, and claims nothing')
```

**It has owned that arm since task 43**, and it is comprehensively stronger than the one I wrote to replace
it: besides the empty region it catches the rejection **escaping the handler** via
`process.on('unhandledRejection')` — half of the original defect, and invisible to any assertion on the
rendering — then proves the retry is a *real* retry that reaches the acknowledgement.

### Why the survey could not have seen it

```
grep -n "reject\|Failure\|…" "$f" | head -4        ← per file, truncated to four lines
```

The owning case is at **line 899 of a 1000-line file**, and the four lines the pipe let through were all
from the first 400. **A survey used as a completeness claim cannot be truncated** — which task 33 established
in this very project, and which I re-learned by writing a weaker copy of an existing case. The first grep
had also been swallowed whole by zsh (`no matches found` for one glob aborts the whole expansion), so the
"measurement" rested on nothing at all.

**Nothing caught it until TR39 killed two cases instead of one.** The count was the tell, and only reading
*which* cases died turned it into the finding.

### What Phase 10 did instead

The duplicate was **withdrawn**, and in its place a pin that makes the attribution **executable** — because
an attribution in a comment is not enforced, and the one thing that can silently un-cover this arm is the
owning case being renamed. **TR40 renames it and the pin fails.**

### **OQ-T175-8 is withdrawn**

It duplicates **OQ-T43-9**, which the owning case already cites by name. The observation stands — a dropped
connection still leaves `RegisterScreen` saying nothing, the third instance of a posture reversed in T49 and
D-T50-1 — but it belongs to an open question that already existed. **I filed a duplicate open question
about a duplicate case, from the same truncated survey.**

## I13 — **ACCEPTED, and done more thoroughly than asked**

Kimi recommended nothing. Still: the pattern matches `.PUT`/`.PATCH`/`.DELETE` and **no call site uses them**,
so those branches are unexercised — *a pattern can be aimed at nothing*, which T37 paid to learn when a
forbidden-call regex turned out to forbid an impossible call shape. **The same fixture test closes this**: the
synthetic source exercises every method, so the pattern is measured against input rather than trusted.

## I1 – I9 — confirmed, no action

Independent confirmation of the fix on both screens, the scan's design and dedicated file, TR33's preservation
of R4, TR31/TR32 firing both instruments, TR34/TR35 on the reach audit, pre-fix failure of every new case,
D-T175-5 and the `204` control.

---

## Phase 10's work, in order

1. **Widen the reach audit to `@themistra/api-client`'s exports** (I10 / OQ-T175-7).
2. **A fixture-based unit test of `sitesIn`** (I11, I13) — synthetic source covering `await`, destructured
   `.then`, `.then((r) => r.data)`, a parameter-typed client, and every HTTP method.
3. **The missing transport-failure case on `RegisterScreen`** (I12), pinning today's behaviour.
4. Harness rows for each, and **a row that proves the new audit is load-bearing**.

**Carried open:** OQ-T175-5, OQ-T175-6, **OQ-T175-8** (new, above), FU-T43-2.

---

**Phase 9 complete. Next is Phase 10 — Test Generation.**
