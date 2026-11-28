<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification). -->

# frontend · T50 · Phase 12 — Specification Verification

| | |
|---|---|
| **Consumes** | every prior artifact; `spec/frontend/{requirements,design,tasks}.md`, `agents.md` |
| **Produces** | `artifacts/12-specification-verification.md` |

```
suite 2116 / 2116  (41 files)  ·  lint 0  ·  typecheck 0  ·  named suite 30 cases
harness: 21/21 regressions · canary dead · survivors ['TR20'] declared · 0 broken · 0 disagreements
every touched file also green when run alone
```

---

## Requirement traceability

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R5** *"WHEN a visitor enters an email on the resend-verification form, THEN the SPA SHALL call the public `POST /accounts/resend-verification` and SHALL show the same acknowledgement regardless of outcome. The form requires no signed-in session, as `auth.yaml` declares."* (`requirements.md:18-20`) | **Yes — the call and the sessionless half.** | `ResendVerificationScreen.tsx:77-79` the POST through `createPublicAuthApi()`; `:79` acknowledges on resolve **without reading the response**; `routes.tsx:131-132` the element on the already-public path | **Yes** — one call, `POST`, body by equality, **no bearer with a session seeded**, acknowledgement on resolve including a `500` | **No** | **D-T50-1**, **D-T50-2** |
| **R5's uniformity clause** | **Not proven here, by design** | — | — | **T51's**, same named suite. T50 renders one acknowledgement; nothing here claims it is the same for every reply | No |
| **R4** (T49) | **Undisturbed, and its action now lands somewhere** | `verifyFailureShowsOneUniformMessage.test.tsx:505-551` | **Yes** — the destination's `h1` is pinned to `verify.failureAction` | No | No |

**R5's third sentence is stale** and `package.md` Q12(a) says so: it cites the backend divergence as open when
the resolution was that *"`auth.yaml` and the real code were both already correct"* — it was backend R6's own
text that was wrong. Recorded, not edited; `spec/` is not ours.

## Locked decisions

| ID | Verdict | |
|---|---|---|
| **L14** route paths verbatim; *"a new public route requires an ADR"* | **HELD, and measured in both directions** | The element is attached to the **already-declared** constant; a case asserts `PUBLIC_ROUTE_PATHS` contains it **before** rendering, and another asserts the pathname is **unchanged** after acknowledging. `design.md:138-139` has both `/app/register` **and** `/app/register/sent` but only `/app/resend-verification`, which is why the acknowledgement renders **in place** — the spec decided this, not the design phase. |
| **L4** enumeration-safe on every public surface | **HELD, structurally** | The screen reads **nothing** of the response — a source scan forbids `response`, `data`, `error`, `.ok`, `.status`, `statusText`, `.json(`, `.text(`, `.message`, **and asserts the settle handler's argument-less shape**, because T49's F3 found a pattern aimed at an impossible call. **D-T50-1 does not breach it:** the new line varies by **transport**, never by account state, and the server answers every account state with one `200`. |
| **L11** accessibility, managed focus, live regions, catalogue-only | **HELD** | Region **mounted empty** and filled in flight; focus moves to the heading on acknowledgement and is **deliberately untouched on arrival** (asserted separately, so the first claim cannot pass for the wrong reason); the field is labelled by `for`/`id`; **one `h1` in both states** (D-T50-2); seven catalogue strings, no raw key in the DOM. |
| **L6** generated client only | **HELD** | `createPublicAuthApi()`; scans forbid a bare `fetch(` and `createAuthApi`. |
| **L16** the only `sessionStorage` carve-out | **HELD** | No write; the single read is **named** (`remix-router-transitions`, react-router's own) rather than reads being left unmeasured; `Storage.prototype` spies **with a positive control**. |
| **L2 / L5** no credential, no second-factor logic in the SPA | **HELD** | The screen collects an **email and nothing else** — no password field, no one-time-code input. The `PERMITTED_CREDENTIAL_HITS` row is a **table entry only**, and its sibling case asserts the table is exact in both directions, so a row added without a real form element fails. |
| **L12** *"Nothing outside the public routes is reachable without a valid session"* | **HELD, as its converse** | The route is asserted to be a **direct child of the root**, read from the table — a behavioural case cannot tell a public route from a gate that let it through. |
| **D7** every rendered key measured | **HELD** | Seven keys added to the key-set pin. |

## Acceptance criteria

All sixteen **PASS**. The three most at risk:

| AC | |
|---|---|
| **AC9** | The response is unread **and the scan is aimed at something** — it asserts the handler's exact argument-less shape, not merely the absence of words. TR04 reads `response.ok` and dies by two cases. |
| **AC11** | *"the email in no URL and no console"* **had no instrument until Phase 10**, and the URL case's first version **measured nothing** (below). |
| **AC16** | The `unsent` line, the form still mounted, the acknowledgement **absent**, the line cleared by a new send, **and a retry that succeeds**. TR02, TR03 and TR23 each die. |

## Deviations — both ratified **at Phase 4, before implementation**

| ID | |
|---|---|
| **D-T50-1** | A transport failure shows the neutral `unsent` line with the form mounted — **owner's reversal** of Kimi's Phase 3, the second consecutive task reversing a "render nothing" choice. |
| **D-T50-2** | One `<Heading>` whose text changes — **forced by measurement**: all seven pre-existing screens render exactly one `<h1>`, and the design-system suite pins that for three of them. TR10 restores the frozen version and dies by four cases. |

**Phase 9 therefore had nothing to ratify — the first time in four tasks.** T47, T48 and T49 each reached that
gate with deviations discovered during implementation.

---

## Comment audit

### One false comment, and I falsified it myself — in a file I edited, one task after the identical mistake

`routes.tsx:52-59` said:

> **THIS TASK ADDS THE CONSTANT AND NOT THE ELEMENT** … The path stays element-less until task 50, so the
> link really does land on a blank document today; that is a known interim state.

**Both clauses are now false, and the second was falsified by an edit forty lines below it in the same file.**
T49's Phase 12 found exactly this shape — a header describing the screen it used to test, in a file that task
had edited twice — and I recorded the lesson and then repeated it. Rewritten, not deleted: the interim state
is *why* this constant reads the way it does, and T49's handoff case existed precisely to bring someone back
here.

### And my attachment line was the only one in the file without the house comment

Every other `if (path === …)` in that block carries a `// TASK n -` note explaining what arrived and why.
Mine had none — a small thing, found by reading rather than by any instrument, and now written.

**Everything else is accurate**, including `routes.tsx:117-121`'s past-tense record of what the file *used to
say* about `register/sent`, which is the convention the rewrite above follows.

## Kimi's Phase 11 — disposition

**Nine findings, all positive or observational. No gaps, no corrections required** — and **for the first time
in four tasks there was no false claim that deviations awaited ratification** (F6: *"Phase 10 is test-only, so
it cannot introduce new D-T50-* deviations"*).

**I will not claim the generator fix caused that, because this task could not have produced the error
anyway.** Two confounds:

1. Both deviations were ratified at **Phase 4**, so nothing was outstanding for a Phase 11 to misreport.
2. **Kimi's own artifact header reads `Consumes: artifacts/10-test-generation.md`**, while the regenerated
   prompt reads `artifacts/09-review-resolution.md, artifacts/10-test-generation.md`. So the fix is live in
   the prompt and Kimi reported the **old** line.

**FU-T49-1 is therefore shipped but unproven.** The real test is the next task that reaches Phase 11 with a
deviation ratified at Phase 9.

F4 is accepted as the right reading of the TR21 disagreement: *"The mutant was valid; the instrument was
incomplete."*

## Principal engineer's answers

**1 · Is the task fully complete?** Yes, for the half `tasks.md` assigns it. `/app/resend-verification` was a
declared, publicly linked route rendering an empty document; it is now a form that works with nothing signed
in, posts to the public operation with no bearer, and acknowledges in place.

**2 · Does it satisfy every acceptance criterion?** Yes — all sixteen, including the two AC11 clauses that had
no instrument before Phase 10.

**3 · Does it violate any LOCKED decision?** No. **L4 is the one worth stating:** D-T50-1 adds a second
possible message to a surface R5 describes with one, and it is safe because a dropped connection is
orthogonal to every account state L4 enumerates — the SPA's rendering varies by transport, never by who the
email belongs to.

**4 · Remaining risks.**

| Risk | Reading |
|---|---|
| **A new case can measure nothing and look right** | **The sharpest lesson here.** A case written specifically for *"the email in no URL"* passed through the exact leak it was written to catch, because a memory router never reads `window.history`. The second such case in two tasks, after T42's instance storage spy. **Only the harness caught it, and only because the row's kill came from the wrong file.** |
| **OQ-T50-6** — a `500` says *"Check your email"* | Deliberate: branching on status is the one thing that could leak an outcome. A falsehood in a case the contract does not declare. **The item I would put to the owner first.** |
| **OQ-T50-5** — T51 must prove the `unsent` line appears for a transport failure **and nothing else** | Created by D-T50-1. |
| **FU-T50-1** — Phase 8 consumes the self-review alone | Cost Kimi three of twelve files this task. Fix at a task boundary, not mid-measurement. |
| **FU-T49-1** | Shipped, **unproven** (above). |
| **FU-T43-2** — no shared test-util module | `type` copied a **fourth** time, with its assertions. |
| **R-T48-1** | Unchanged — and this screen is now the working remedy a burnt-token visitor is sent to. |

---

**PASS** — R5's call and sessionless claims are implemented, all sixteen ACs hold with 2116/2116 green and
21/21 mutants killed, no LOCKED decision is violated, both deviations were ratified before implementation, and
the one false comment found was corrected rather than carried.
