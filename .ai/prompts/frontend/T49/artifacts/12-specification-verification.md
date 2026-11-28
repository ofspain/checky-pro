<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification). -->

# frontend · T49 · Phase 12 — Specification Verification

| | |
|---|---|
| **Consumes** | every prior artifact; `spec/frontend/{requirements,design,tasks}.md`, `agents.md` |
| **Produces** | `artifacts/12-specification-verification.md` |

```
suite 2086 / 2086  (40 files)  ·  lint 0  ·  typecheck 0  ·  named suite 38 cases
harness: 20/20 regressions · canary dead · survivors ['TR17','TR20'] declared · 0 broken · 0 disagreements
every touched file also green when run alone
```

---

## Requirement traceability

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R4** *"IF the verification request fails for any reason, THEN the SPA SHALL show one uniform failure message with a neutral resend action"* (`requirements.md:16-17`) | **Yes, both halves** | `VerifyScreen.tsx:94` (no token), `:108` (any non-`ok`), `:110` (transport rejection) → `'failure'`; `:117-120` the message in the live region; `:122` the resend `TextLink` | **Yes** — nine statuses render identical DOM; a problem document changes nothing; a dropped connection; identical `outerHTML` for the action across four arms | No | **D-T49-2** (tokenless), **D-T49-1** (the path constant) |
| **R3** (T48's success) | **Undisturbed, and never preceded by failure copy** | `:98` resets to `'idle'` when a request starts | **Yes** — no failure copy in flight, an earlier failure cleared, success after failure, StrictMode | No | No |
| **R15** | Not built here | — | — | Correctly out of scope — task 54 | No |

## Locked decisions

| ID | Verdict | |
|---|---|---|
| **L4** *copy, redirects, styling **and timing** identical* | **HELD, and by construction rather than by copy** | The four reasons arrive as one fixed `400` (`AccountExceptionHandler.java:46-52`), and the screen **reads nothing of the response but `ok`** — enforced by a source scan forbidding `error`, `.title`, `.detail`, `.type`, `response.status`, `statusText`, `.json(`, `.text(`. Nine statuses tested identical; ten reason-words forbidden from the DOM. |
| **L11** accessibility, managed focus, **live regions for async results**, catalogue-only | **HELD** | The region is mounted **empty first** (`:117`), filled on failure; the action sits **outside** it, so it is not re-announced; focus moves once, on arrival (`:87`), and never again; both strings from `catalogue.ts:180-181` |
| **L6** generated client only | **HELD** | `createPublicAuthApi()`; scan forbids a bare `fetch(` |
| **L14** route paths verbatim, attached by constant | **HELD** | `routes.tsx:60` `RESEND_VERIFICATION_PATH`; **no path added, renamed or attached** |
| **L16** the only `sessionStorage` carve-out | **HELD** | No storage call; `Storage.prototype` spy **with a positive control**; the token in no DOM and no console, the console list **derived** at runtime |
| **L9**, **L17** | Unchanged | — |
| **D7** every rendered key measured | **HELD** | Both keys spelled out as literal English; the key-set pin updated (**D-T49-3**) |

## Acceptance criteria

All fifteen **PASS**. The three that were most at risk:

| AC | |
|---|---|
| **AC6** | *A valid token never renders the failure copy, not even for one frame.* Held **unconditionally**, including after a tokenless arrival, by the reset at `:98`. TR06 (deleting it) dies in exactly one case. |
| **AC9** | The screen reads **no part of the response body**. TR14 reads it and renders nothing, and dies **on the scan** — which is the row's purpose: if it had died only behaviourally, the scan would not be doing its job. |
| **AC11** | T48's three inherited cases rewritten, not deleted, with `PINS A LIMIT` gaining the assertion that both arms **contain** the failure message — without which it would go on passing for the same reason it passed before this task. |

## Deviations — all four ratified

| ID | |
|---|---|
| **D-T49-1** | `routes.tsx` modified for `RESEND_VERIFICATION_PATH`. Phase 4. |
| **D-T49-2** | A tokenless arrival shows the uniform failure. Phase 4, **reversing Kimi's Phase 3**. |
| **D-T49-3** | The catalogue key-set pin updated — list entries only. Phase 9. |
| **D-T49-4** | The two keys flat, not nested: the nested form **crashes** the copy scan. Phase 9. |

---

## Comment audit

### One false comment found — **in a file this task modified, so it was mine, and I walked past it twice**

`verifyFromLinkShowsSuccessState.test.tsx:15-19` still said:

> `idle` is the rendering for three different situations … R4's single uniform failure message is task 49's,
> and inventing its copy here would pre-empt it. So the cases below pin the no-token branch by its
> CONSEQUENCE (no request is sent) rather than by anything on screen.

**All three clauses were false**, and the third was falsified **by this file's own edits**: `idle` now means
in-flight and nothing else, the failure copy exists, and the two AC12 cases now assert the full rendered
failure text rather than only the silence.

**I edited this file in Phase 6 and again in Phase 10 and did not read its header either time.** Corrected
here, with the correction recording what happened rather than quietly replacing the text. Every assertion in
the file passed throughout, because **none of them was ever about that paragraph** — which is this project's
recurring finding in its usual form: a comment is the only part of a suite with no instrument, and the ones
that rot are those whose assertions still pass for an unrelated reason.

**Everything else is accurate**, including `VerifyScreen.tsx:72-73`'s *"the human is shown task 49's uniform
failure — which R4 forbids task 49 from explaining"*, which reads correctly now that this task is that task.

## Kimi's Phase 11 — disposition

Eight findings: seven positive or observational, **one factually wrong**.

**F6 says D-T49-3 and D-T49-4 *"remain for Phase 9 ratification"*. Phase 9 ratified both** — its own status
line reads *"COMPLETE — owner-approved 2026-11-28: ratify both deviations as built."*

**This is the third consecutive task with this exact error** (T47 F6, T48 F9, T49 F6), and it is structural
rather than careless: Kimi's `Consumes` line is `artifacts/10-test-generation.md` **alone**, and
`.ai/generate.py:301` is why —

```python
prevref = f"`{prev}`" if i > 0 else "— (entry phase)"
```

Every phase is given **only the immediately preceding artifact**, so Phase 11 structurally cannot see Phase 9.
Recorded as **FU-T49-1**: Phase 11 (and Phase 8) should consume `09-review-resolution.md` as well. **Not
fixed here** — `.ai/` is generated and regenerating it rewrites the prompts of every task in every service,
including 48 closed ones, which is a change for the owner to greenlight rather than a side effect of T49.

F7's minor point is accepted as written: asserting more than *"renders nothing"* about the handoff would
require knowing task 50's structure, which is out of scope.

## Principal engineer's answers

**1 · Is the task fully complete?** Yes. Every way verification can fail now shows one message and one way
forward: a rejected token, any other non-`ok` status, a connection that never answered, and a link carrying
no token at all. Before this task, all four showed a heading and empty space.

**2 · Does it satisfy every acceptance criterion?** Yes — all fifteen, with the uniformity claim held
**structurally** as well as behaviourally, because a screen that never reads the response body cannot leak
which reason occurred.

**3 · Does it violate any LOCKED decision?** No. **L4 is the one worth stating:** its *timing* clause is
satisfied for the four reasons that matter because they share one backend path, and the tokenless arrival's
different timing leaks nothing, since a URL with no token names no account.

**4 · Remaining risks.**

| Risk | Reading |
|---|---|
| **R-T48-1 — a gateway burns the token before the human clicks** | **T49 now owns the visible consequence.** That visitor's account really is verified and they are shown *this* task's failure message, which R4 forbids explaining. Not solvable with copy; the mitigation point is documented in the one effect that would change. **Still the item I would put in front of the owner first.** |
| **The live link into a blank document** | Accepted at Phase 4. Now carries an instrument that **fails when task 50 lands** and says to re-check this screen's link — which two count pins would not have said. |
| **The in-flight → tokenless limit** | Measured, pinned, and the fix rejected for a stated reason: it would make Back after a *successful* verification say *"not valid"*. TR22 is that fix, dying by one case. |
| **Uniformity depends on the rate-limit path staying dormant** | A `429` is pinned identical to a `400`. If anyone wires `RateLimitNotice` into the public client, that row is what fails. |
| **FU-T49-1** | The generator gives every phase only the previous artifact — three false findings so far. |
| **OQ-T49-3a**, **OQ-T48-4**, **FU-T48-1**, **FU-T48-2** | Unchanged; none is this task's. |

---

**PASS** — R4 is implemented in both halves, all fifteen ACs hold with 2086/2086 green and 20/20 mutants killed, no LOCKED decision is violated, every deviation is ratified, and the one false comment found was corrected rather than carried.
