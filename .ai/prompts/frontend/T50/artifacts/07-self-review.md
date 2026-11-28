<!-- MODEL: Claude Opus 5 — Phase 7 (Self Review). -->

# frontend · T50 · Phase 7 — Self Review

| | |
|---|---|
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md`, `07-mutation-harness.py`, `07-mutation-run.txt` |

```
baseline whole suite: 0 failed of 2113, exit 0

regressions:   18/18 killed
canaries:      ['TR19'] - none survived
survivors:     ['TR20']   (declared unfalsifiable)
broken:        none
disagreements: none
```

**Three runs.** The first two each found something, and both findings were mine rather than the product's.

---

## F1 · TR09 left invalid JSX, and that is the second time in one harness

**Run 1: `17/18 killed, 1 BROKEN`.** TR09 replaced only the *opening* of the acknowledgement ternary and left
`{true ? ( <Form/> )}` behind — a ternary with no alternative. **A file did not load and 1506 tests never
ran.** The harness reported `BROKEN`, which is the only reason it was not scored as a kill.

**TR05 had already needed the same lesson in this same harness**, half an hour earlier: its one-line version
failed `TS2657` when I hand-applied it, because a second element inside a ternary arm needs a fragment. So
**T48's rule — a JSX mutant must leave valid JSX behind — was learned twice in one afternoon**, once by
hand-checking before the run and once by not hand-checking. The difference between those two experiences is
the whole argument for hand-applying every structural mutant.

Both are now anchored on the **whole** construct rather than a line inside it, and both were hand-verified to
compile before run 3.

## F2 · Two rows were declared in a place the harness does not read

Run 1 also reported `disagreements: ['TR09', 'TR16', 'TR20']`.

- **TR16** was tagged `"own"` and mutates **another task's test file**, so its kill necessarily lands in an
  inherited one.
- **TR20** was in the `UNFALSIFIABLE` set *and* tagged `"own"` on its row — and `main()` reads the expectation
  from **the row**:

  ```python
  elif expect == "unfalsifiable":
      flag = "as expected" if outcome == "SURVIVED" else "DISAGREES - it has an observable effect"
  ```

**Task 49 made this exact correction for its own TR17**, and the lesson is unchanged: *a declaration must be
made everywhere the harness reads one.* Carrying the harness forward by hand carries its traps forward too.

## F3 · The copy lost two constants, and the check I had written could not see it

The first attempt died with `NameError: name 'FILES' is not defined` — **after** the baseline run had already
completed. I built this copy by splitting T49's file at its first `import` and reusing the prelude and tail
byte-for-byte precisely to avoid the splice bug that cost T48 `FRONTEND` and T49 `os`/`re`/`signal`, and I
asserted the **imports** survived. The loss was in the **constants** (`FILES`,
`KNOWN_ENVIRONMENTAL_FAILURES`), which that assertion could not see.

**The harness now checks itself** before applying a single mutant, over all sixteen names the machinery needs,
so copy 21 fails in a second rather than forty minutes in.

## F4 · Two findings the product earned

**TR18 proves the replaced handoff is not decoration.** It changes `resend.heading` so it no longer equals
`verify.failureAction`, and it dies **in `verifyFailureShowsOneUniformMessage.test.tsx`**, by the case that
replaced T49's handoff. One sentence held across two features, in neither screen, because neither can see the
other.

**TR10 proves D-T50-2's case has teeth.** It restores the frozen design's second `<Heading>` and dies by four
cases. The deviation was argued from a measurement; the mutant is the measurement standing in the harness.

## F5 · What is NOT measured — **two frozen clauses with no instrument**

This is the finding Phase 10 should act on, and it is the same shape as T49's: **the brief did an assertion's
job.**

**AC11 reads:** *"no `sessionStorage` write, no token read, **the email in no URL and no console**."* The
storage half has a case with a positive control. **The other two clauses have nothing at all.**

| Clause | Instrument |
|---|---|
| no storage write | ✅ `Storage.prototype` spy **with a control** |
| the email in no URL | ❌ **nothing.** The acknowledgement case asserts the *pathname* is unchanged, which says nothing about a query string or a hash. |
| the email in no console | ❌ **nothing.** T49's suite derives every function on `console` and asserts the derivation; this suite does not spy on `console` at all. |

An email is not a credential the way a token is, but it is the one piece of personal data this screen handles,
**L4's whole subject is not disclosing who has an account**, and a `console.info(email)` left by a debugging
session would ship silently. Phase 10 owes this a case, and the console one must derive the method list and
**assert the derivation**, because a reflection returning nothing makes a negative assertion vacuous.

**Second gap: `'unsent'` → `'acknowledged'` is never driven end to end.** AC16's clearing case submits again
with a hanging transport and asserts the line is gone mid-flight. **Nothing drives a failure followed by a
successful retry** — the actual journey D-T50-1 exists to enable. The state machine makes it hard to get
wrong, which is exactly the argument that was wrong about T42's storage spy.

## F6 · Where this task's claim is weaker than it looks, stated plainly

**"Renders with no session" is nearly vacuous on its own.** No case in this file establishes a session, so a
case asserting the screen renders without one is asserting the circumstances every other case already runs
in. What actually holds R5's sessionless claim is the **pair**: the case that **seeds the storage a session
uses** and still demands a bare request, and **TR06**, which swaps the client factory and dies by fourteen
cases. Recorded because a reader skimming case names would credit the wrong one.

## Open questions carried

| ID | |
|---|---|
| **OQ-T50-5** | **T51 must assert the `unsent` line appears for a transport failure and for nothing else**, alongside one acknowledgement across every server outcome. D-T50-1 created this. |
| **OQ-T50-6** | Acknowledging on any resolve means a `500` says *"Check your email."* Deliberate; branching on status is the one thing that could leak an outcome. |
| **OQ-T50-7** | **New.** AC11's URL and console clauses, and the failure→retry→success journey — Phase 10's. |
| **FU-T43-2** | No shared test-util module; the `type` helper is now copied a **fourth** time, with its assertions, because the descriptor-vs-instance trap has already cost two closed tasks. |
| **R-T48-1** | Unchanged. This screen is the remedy a burnt-token visitor is sent to. |

**Phase 7 complete. Next is Phase 8 — Independent Code Review (Kimi 2.7). STOP HERE.**
