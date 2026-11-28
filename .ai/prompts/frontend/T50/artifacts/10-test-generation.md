<!-- MODEL: Claude Opus 5 — Phase 10 (Test Generation). Tests only; no production file changed. -->

# frontend · T50 · Phase 10 — Test Manifest

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md`, `10-mutation-run.txt` |

**No production file was changed in this phase.**

```
baseline whole suite: 0 failed of 2116, exit 0

regressions: 21/21 killed
canaries:    ['TR19'] - none survived
survivors:   ['TR20']   (declared unfalsifiable)
broken:      none
disagreements: none
```

---

## Three cases, for three clauses the brief stated and nothing measured

**AC11 reads:** *"no `sessionStorage` write, no token read, **the email in no URL and no console**."* Only the
first clause had an instrument. **This is the brief doing an assertion's job** — the same shape T49 found, and
Kimi's **I3** and **I11** confirmed it independently.

### 1 · The email in no URL — **and the first version of this case was blind**

**TR21 disagreed, and that disagreement is the most valuable thing in this phase.**

The mutant leaks the email with `window.history.replaceState`. **A memory router never reads
`window.history`**, so `router.state.location.search` stayed empty and my brand-new URL case passed straight
through the exact leak it was written to catch. The row still died — in
`profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx`, by a tree-wide scan forbidding a template literal
before an `/accounts` path, which my mutant tripped incidentally. **A real kill, by an instrument with no
interest in this claim, and the only reason the row did not survive.**

```
run 1  TR21 └ in: ['src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx']
run 2  TR21 └ in: ['…/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx',
                   'src/profile/profileIsReadFromUserinfoAndAccountStatusFromMe.test.tsx']
```

The case now asserts `window.location.search`, `.hash` and `.href` as well: **the router's location is what
the SPA believes; `window.location` is what the visitor's history, autocomplete and `Referer` actually
carry.** Hand-applying TR21 afterwards fails the case by itself, `1 failed of 30`.

**Without the harness this case would have shipped looking right and measuring nothing** — and it would have
been the second such case in two tasks, after T42's instance storage spy.

### 2 · The email in no console

The suite did not spy on `console` **at all**, where T49's derives every function on it. A
`console.info(email)` left by a debugging session would have shipped silently.

The list is **derived at runtime and the derivation is asserted first** — the eight names are the
**non-vacuity guard**, not the coverage — and the case ends with a positive control, because every assertion
in it is negative and a spy that does not intercept passes them all.

Both this case and the URL one also check the **local part alone**: if an encoder split the address,
`visitor` would still be enough to identify a person.

### 3 · Failure → retry → **success**, driven end to end

AC16's clearing case submits again with a hanging transport and asserts the stale line is gone **mid-flight**.
Nothing let the second attempt answer, so *"the form stays mounted so retrying is one click"* was a claim
about **the DOM** rather than about the outcome. The case also asserts **the typed email survives**, which is
the other half of keeping the form.

**TR23** is its mutant: a one-shot guard — the shape a later reader would add by copying `VerifyScreen`'s
`handled` ref without noticing that a **retry** is the whole point here.

## The three rows

| Row | Outcome | |
|---|---|---|
| **TR21** the email carried into the URL | `KILLED`, 2 files | **Disagreed on run 1**; the case was rewritten, not the row |
| **TR22** the email logged to the console | `KILLED`, 2 files | Mine, plus an existing console scan in `tokensAreNeverWrittenToAnyStorage` |
| **TR23** a failed send can never be retried | `KILLED`, 2 cases | Both in this task's suite |

All three were **hand-applied and verified to compile before the run** — after TR05 and TR09 each cost a cycle
for exactly that omission.

## Coverage map

| AC | Cases |
|---|---|
| **AC1, AC8** | the form renders · the path is in the declared set **before** rendering · acknowledgement **in place**, pathname unchanged |
| **AC2, AC3, AC11** | renders with nothing signed in · the route is a **direct child of the root**, read from the table · no storage write, the one allowed read **named**, with a control · **the email in no URL** · **the email in no console** |
| **AC4, AC5, AC6** | one call · `POST` · body by **equality** · **no bearer even with a session seeded** · StrictMode |
| **AC7** | acknowledgement replaces the form (`form`, `input`, `button` all gone) · nothing in flight · **a `500` acknowledges too** |
| **AC9** | seven literal strings · no raw key · a server sentence **not rendered** · the response-read scan **and** the handler's argument-less shape · generated client, no bare `fetch` |
| **AC10** | region mounted **empty** · filled in flight · focus moves on acknowledgement · **focus untouched on arrival**, asserted separately · implicit submit · the field's label |
| **AC12, D-T50-2** | no lowercase tag · no `className`/`style` · **one `h1` in both states** · named in the render-site list **and** the credential table |
| **AC13** | T49's handoff replaced, pinning the destination heading to `verify.failureAction` |
| **AC16** | the `unsent` line, form mounted, acknowledgement **absent** · cleared by a new send · **a retry that succeeds** |

## Not added, and why

- **No case for TR20.** Declared unfalsifiable; a case killing it would pin the hook rather than the behaviour.
- **Nothing about uniformity across server outcomes.** **T51's**, on this same suite. The `500` case holds
  only that the handler does not branch on status, which is what makes T51's proof possible.
- **No case for OQ-T50-6's falsehood.** A `500` saying *"Check your email"* is deliberate and documented; a
  case pinning it would freeze a cost rather than a behaviour, and the owner may revisit it.
- **No shared test-util module** (FU-T43-2). `type` is copied a fourth time, with its assertions.

**Phase 10 complete. Next is Phase 11 — Test Review (Kimi 2.7). STOP HERE.**
