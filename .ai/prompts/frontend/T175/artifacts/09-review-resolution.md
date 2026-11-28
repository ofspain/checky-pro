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

## I12 — **MEASURED.** Kimi guessed and said so; the answer is *no case exists*

> *"…do not appear to test transport failure … the original T43 registration suite may have tested it."*

**It did not.** Of the seven suites that render `RegisterScreen`, **none installs a failing transport** — the
only `installTransportFailure` in `features/account/` belongs to the resend suite. Phase 7's F6 said *"I do
not know, and not knowing is the reason to look."* Looked:

```
grep -rln RegisterScreen src --include=*.test.tsx   → 7 suites, 0 with a failing transport
```

**Phase 10 adds the case.** Its observable surface is small but real: `status` feeds nothing but the live
region, so the arm's whole effect is *the in-flight line clears and the form comes back*.

### And the measurement surfaced something larger than the gap — **OQ-T175-8**

**On a dropped connection, `RegisterScreen` tells the visitor nothing at all.** The region empties, the form
returns, and the screen looks exactly as it did before they pressed the button.

**That is the third occurrence of a posture this owner has now reversed twice:**

| | |
|---|---|
| **T49** | the tokenless arrival rendered nothing → reversed |
| **T50 / D-T50-1** | resend's dropped connection returned to `'idle'` showing nothing → reversed, with the reason *"a visitor who presses a button and sees the screen go back to how it looked has been told nothing at all"* |
| **`RegisterScreen`** | **unchanged, and it is the screen the other two were compared against** |

`ResendVerificationScreen`'s own header records that its design *"matched `RegisterScreen`"* before being
reversed — so the precedent was set **against** this screen and never applied **to** it. It needs an owner
decision and a catalogue key, so it is **out of T175's scope**, which is unparseable bodies.

But the new case is not neutral about it: by pinning *today's* behaviour it makes that decision a **visible
change to a named case** rather than a silent one.

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
