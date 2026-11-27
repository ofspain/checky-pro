<!-- MODEL: Claude Opus 5 — Phase 12 (Specification Verification). -->

# frontend · T47 · Phase 12 — Specification Verification

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T47 — Registration acknowledgement is identical |
| **Consumes** | every prior artifact; `spec/frontend/{requirements,design,tasks}.md` |
| **Produces** | `artifacts/12-specification-verification.md` |

```
suite 2016 / 2016 · lint 0 · typecheck 0
harness: 17/17 regressions killed · 2/2 canaries dead · survivors ['TR15'] · broken none · disagreements ['TR14']
```

---

## Requirement traceability

| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| **R2** "the SPA SHALL show the same 'check your email' screen whether or not the email already exists, with identical copy and layout, and SHALL NOT add client-side branching or delay" (`requirements.md:12-13`) | **Yes** | `RegisterSentScreen.tsx:62-63`; `routes.tsx:102`; `RegisterScreen.tsx:96` | **Yes** — 5 identical-output cases (`:179`, `:206`, `:282`, `:302`, `:254`) + the no-branch source scan `:381` | No | No |
| **L4** enumeration-safe UI, no carve-out (`design.md:21`) | **Yes** — one screen, one sentence, reached for 202 and for an undeclared 409 alike | `RegisterSentScreen.tsx:62-63`; `RegisterScreen.tsx:96` (`.then` ignores the reply) | **Yes** — `:206` (undeclared status), `:302` (non-JSON body), `:179` (byte-identical) | No | No |
| **L11** WCAG AA, keyboard operation, managed focus, catalogue-only strings (`design.md:47`) | **Yes** | focus `RegisterSentScreen.tsx:57`; catalogue keys `:62-63`; one `<h1>` via `Heading` | **Yes** — focus `:438`, `:447`, `:456`, control `:432`; no tab stop `:463`; outline `:313`; catalogue `:254`, `:265` | No | No |
| **L14** route set under `/app`, paths verbatim in §4c (`design.md:53`) | **Yes** — an element attached to an already-declared path; the declared set is unchanged | `routes.tsx:41`, `:53`, `:102` | **Yes** — every inherited membership/order pin, incl. `publicRouteSetIsExactlyTheDeclaredSet.test.tsx:711` | No | No |
| **D7** every catalogue key the screen renders is measured | **Yes** | `catalogue.ts:137-139` (`registerSent.heading`), `:99` (`register.success`) | **Yes** — `:265` spells both as literal English; `:254` proves the catalogue sentence renders and the server's does not | No | **D4** — see below |

## Acceptance criteria

| AC | Verdict | Evidence |
|---|---|---|
| **AC1** the acknowledgement renders at `/app/register/sent` | **PASS** | case `:171` |
| **AC2** identical output for a new and an already-registered address | **PASS** | `:179` (byte-identical `innerHTML`), `:206`, `:302`, **`:282` cold arrival vs submit**, plus `:197` asserting the probes differ |
| **AC3** the post-submit destination is the same in both cases | **PASS** | `:223` (destination), **`:232`** (REPLACE, flag **and** consequence) |
| **AC4** nothing reads the status, body or route state; no retry | **PASS** | scan `:381` (now incl. `useNavigate`, `useHref`), `:401` (one request, **and the body equals what was typed**) |
| **AC5** no artificial delay | **PASS** | scan `:381` (`setTimeout`, `setInterval`) |
| **AC6** every string from the catalogue | **PASS** | `:254`, `:265` |
| **AC7** managed focus, no tab trap | **PASS** | `:432`, `:438`, `:447`, `:456`, `:463` |
| **AC8** renders on a cold arrival with no props and no router state | **PASS** | `:477`, and **`:282`** proves it renders *the same thing* |
| **AC9** no storage read or write | **PASS** | `:486` (`Storage.prototype` spy, keyed by key name), `:512` intercept control |
| **AC10** `PUBLIC_ROUTE_PATHS` unchanged | **PASS** | `routes.tsx:41` substitutes the constant for the same literal; every inherited pin held unchanged |
| **AC11** renders through primitives only | **PASS** | `:528`; `screensRenderThroughDesignSystemPrimitivesOnly` `RENDER_SITES` 9 |
| **AC12** the 22 re-aimed sites in closed suites stay green | **PASS** | whole suite 2016/2016 |

## Deviations

| ID | Status |
|---|---|
| **D1** touching closed tasks' files | Ratified (Phase 9). Task 43's `does not navigate` case **inverted in its own file**, which that case licensed by naming its condition. |
| **D2** the required contract field `message` is read by nothing | Ratified. Rendering it would **violate L11**, so the contract field is deliberately unused and the screen's header says so. |
| **D3 / OQ-T47-4** no onward link | Ratified, and now **pinned by an assertion** (`:323`) so it cannot be answered by a quiet edit. |
| **D4** `register.success` renders from a screen outside its catalogue group | Ratified. **The brief instructed Phase 12 to check the catalogue comment is true: it is.** Every clause verified — `catalogue.ts:83-97` says the key "is rendered by `RegisterSentScreen` now, which the form navigates to once the call settles", and `grep` finds exactly one render site, `RegisterSentScreen.tsx:63`; `RegisterScreen` retains only `register.submitting`. |
| **D-T47-6** the T45 suite modified despite the freeze | Ratified (Phase 9), alternative rejected on inspection. |
| **D-T47-7** four further files hold membership or count pins | Ratified (Phase 9); the process half is **FU-T47-2**. |

---

## One false comment, found here and nowhere earlier

`registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:700` read:

> `register/sent` still carries no element: task 47 owns the acknowledgement, and D1 is why this screen
> announces its result in place instead of navigating to a blank document.

**Both clauses were false.** The path carries an element and the screen navigates. Phase 6 corrected three
comments in this file and walked past this one; Phase 7, Phase 8, Phase 10 and Kimi's Phase 11 all read the
file afterwards. **The assertion underneath it never noticed, and could not** — it pins *membership* of the
declared path set, and membership did not change, because this task attached an element to a path that was
already there. Corrected, with the reason recorded in place.

This is the third task running in which Phase 12 has been the thing that caught a false comment. The pattern
is stable enough to name: **a comment is the only part of a suite with no instrument**, and the comments most
likely to go stale are the ones whose assertion still passes for an unrelated reason.

Two near-misses were checked and **correctly left alone**: `RegisterScreen.tsx:39` and
`registrationAccepts…:1023` both quote task 43's superseded wording *as a quotation*, which is the shape two
earlier Kimi findings mistook for drift.

---

## Principal engineer's answers

**1 · Is the task fully complete?** Yes. `register/sent` has been a declared path with no element since task
16; it now has one, reached by the form, and R2's identical-output claim has a named suite of 23 cases.

**2 · Does it satisfy every acceptance criterion?** Yes — all twelve, each with a case, and AC2/AC3/AC4 each
strengthened past the brief: AC2 by comparing a journey against *no journey*, AC3 by asserting Back cannot
resubmit rather than only that the flag is set, AC4 by proving what was typed is what left the tab.

**3 · Does it violate any LOCKED decision?** No. L4, L11 and L14 are satisfied, and **L11 is what dictated
D2** — the contract's required `message` is unread precisely because rendering server copy would break the
catalogue-only lock.

**4 · Remaining risks.**

| Risk | Reading |
|---|---|
| **A double submit sends two requests** (OQ-T43-11) | **The one real product defect in reach of this task.** Two presses before the first reply mean two verification emails for one registration. Measured, not assumed; pinned at today's behaviour so it cannot be closed silently; **not fixed, because no task has authorised a fix.** This is the item I would put in front of the owner first. |
| `TR15` and F3's role selectors are unfalsifiable | Both by design and both recorded at the point of use, not hidden in a score. |
| AC9 depends on react-router's private key `remix-router-transitions` | Accepted: the failure is loud and the comment says why. |
| `innerHTML` equality is attribute- and whitespace-brittle | Accepted twice by independent review as the price of "copy **and** layout". |
| OQ-T47-7 (the heading repeats the sentence's opening) | Copy, not behaviour; owner's call. |

---

**PASS** — R2 is implemented and proved identical across both outcomes, all twelve ACs hold with 2016/2016 green and 17/17 mutants killed, no LOCKED decision is violated, and the one real defect found (OQ-T43-11's double submit) is measured and pinned rather than left to be discovered.
