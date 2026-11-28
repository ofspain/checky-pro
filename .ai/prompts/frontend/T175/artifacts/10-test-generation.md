<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation). -->

# frontend · T175 · Phase 10 — Test Generation

| | |
|---|---|
| **Consumes** | `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md`, `10-mutation-harness.py`, `10-mutation-run.txt` |

```
suite 2148 / 2148  (42 files)  ·  lint 0  ·  typecheck 0
regressions 9/9 killed   canary TR36 died   survivors none   broken none   disagreements none
```

**No production file changed in this phase.**

---

## 1 · I10 — the reach audit now covers the package

The one Medium, and the hole Phase 7 reported on itself. `codeExchange` takes `createOidcClient` straight
from **`@themistra/api-client`**, so auditing only `api/client.ts` left the package free to grow a factory
that widened the surface without breaking a case. The audit now reads
`libs/ts/api-client/src/index.ts` as well and pins its exports to `createAuthClient`, `createOidcClient`.

**TR37 grows one and kills exactly that case** — one failure, nothing else. It is also the only row in this
task that mutates a file outside the app, which is why **`REVERT_PATHS` gained `libs/ts/api-client/src`**: the
revert is `git checkout -- REVERT_PATHS`, so a mutant written outside it is never undone, survives the run
and is attributed to every row after it.

## 2 · I11 — the classifier is measured, not widened

Kimi was right that `.then((r) => r.data)` classifies as a **non-reader**, and the recommendation to broaden
the test to `.data` anywhere was **declined on the asymmetry**: a false non-reader *fails loudly*, a false
reader is *exempt from the rule*. A safety scan must err toward the loud mistake.

So instead of arguing about a regex, **a fixture case measures the classifier on source** — one row per
shape, including the shape Kimi named, pinned as a non-reader on purpose:

| Shape | Classified |
|---|---|
| `const { data } = await f().GET()` | reader |
| `f().PUT().then(({ data }) => data)` | reader |
| **`f().PATCH().then((r) => r.data)`** | **non-reader** ← *pinned; flipping it means someone moved the classifier the unsafe way* |
| `api.DELETE()` where `api: AuthApi` | non-reader (found via the **type**) |
| `f().POST()` / with `parseAs` | non-reader, `streams` false / true |
| `thing.POST()` — not a client | **absent** |

**TR38 applies Kimi's own recommendation as a mutant**, which is the honest way to decline advice: if the
widening were harmless nothing would die. **It kills exactly one case — the fixture.** Every other
instrument in 2148 tests is indifferent to it, which is precisely the silent-failure signature the decline
was about.

The fixture also exercises `.PUT`, `.PATCH`, `.DELETE` (I13), which **no production call site uses**, so those
branches of the pattern had been matched against nothing — *a pattern can be aimed at nothing*, T37's lesson.
And it asserts the scan does **not** over-match: seven calls in, six rows out.

## 3 · I12 — **I got this wrong, and the harness caught it**

Phase 9 answered *"no case exists"*. **A case has owned that arm since task 43**, and Kimi's hedge was closer
to the truth than my measurement.

| | |
|---|---|
| The instrument | a per-file grep **piped through `head -4`** |
| The owning case | **line 899** of a 1000-line file |
| What let it through | nothing — an earlier grep had also been swallowed whole by zsh, so the claim rested on no evidence |
| What caught it | **TR39 killing two cases instead of one** |

The count was the tell; only reading *which* cases died made it a finding. **A survey used as a completeness
claim cannot be truncated** — task 33 established that here, and I re-learned it by writing a weaker copy of
an existing case.

The owning case is stronger than mine on every axis: it catches the rejection **escaping the handler**
(`process.on('unhandledRejection')`, invisible to any assertion on the rendering) and proves the retry really
retries.

**So the duplicate was withdrawn** and replaced with a pin that makes the attribution **executable**, because
a comment is not enforced and a rename is the one thing that would silently un-cover the arm. **TR40 renames
it and the pin fails.**

**OQ-T175-8 withdrawn** — it duplicates **OQ-T43-9**, which the owning case cites by name. A duplicate open
question about a duplicate case, from the same truncated survey.

## 4 · The harness, and a label that changed meaning

**TR39 is now `inherited`, and the label changing is itself the record.** It was `own` while the duplicate
existed and became `inherited` the minute the duplicate was withdrawn — the arm is measured, just not by
anything this task wrote. An expectation label is a claim like any other.

| | |
|---|---|
| TR31–TR36 | unchanged, re-run, all still killed |
| **TR37** | package factory added → the reach audit, 1 case |
| **TR38** | Kimi's I11 widening applied → the fixture, 1 case |
| **TR39** | reject arm inert → the **T43 owner**, 1 case, `inherited` |
| **TR40** | owning case renamed → the attribution pin, 1 case |

## 5 · Open items

**OQ-T175-5**, **OQ-T175-6**, **OQ-T175-7 → CLOSED by §1**, **OQ-T175-8 → WITHDRAWN** (see OQ-T43-9),
**OQ-T43-9** (carried, not mine to close), **FU-T43-2**.

---

**Phase 10 complete. Next is Phase 11 — Test Review (Kimi 2.7). STOP HERE.**
