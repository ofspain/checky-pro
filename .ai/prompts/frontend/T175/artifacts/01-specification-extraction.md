<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction). No code written. -->

# frontend · T175 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

**No code was written in this phase.**

---

## 1 · The requirements, verbatim

> **R2.** WHEN the registration request returns, THEN the SPA SHALL show the same *"check your email"* screen
> whether or not the email already exists, **with identical copy and layout, and SHALL NOT add client-side
> branching or delay.** *(backend R2)*
>
> **R3.** WHEN a visitor opens a verification link **and the token is valid**, THEN the SPA SHALL show the
> verified success state. *(backend R4)*
>
> **R4.** IF the verification request **fails for any reason**, THEN the SPA SHALL show one uniform failure
> message with a neutral resend action. *(backend R5)*

## 2 · R3 and R4 collide here — and reading them together settles it

The measured case is: **the token is valid, the server verified the email, and the reply body did not parse.**

| | |
|---|---|
| **R3** says | the token is valid → **show the verified success state**. Today's behaviour **violates R3.** |
| **R4** appears to say | it *"failed for a reason"* → show the uniform failure. Today's behaviour **satisfies a literal R4.** |

**This is not a trade-off, and the resolution is in R4's own subject.** R4 governs *"**the verification
request** fails"*. A request that returned `204 Email verified` **did not fail** — it succeeded, and the
`SyntaxError` happened afterwards, in the client, to a body nothing needed. **The failure is the client's, not
the request's, so R4 is not engaged at all** and R3 is unopposed.

That reading also matches R4's remedy: a *"neutral resend action"* only makes sense for a token problem. **An
already-verified account has nothing to resend**, which is the tell that the current state is the wrong one —
a requirement's remedy being useless is evidence the requirement was never meant to cover the case.

**So the behaviour T175 must produce is not a new policy.** It is R3, applied to a case the implementation
accidentally routed to R4.

## 3 · R2 is violated outright, and more simply

For `RegisterScreen` there is no interpretive question. An unparseable `202` currently produces **no
navigation and no message** — the visitor presses the button and nothing visible happens.

R2 requires *"the same 'check your email' screen"* with *"identical copy and layout"*. **Showing no screen is
not showing the same screen.** No reading of R2 permits it.

## 4 · L4, and the argument that is now familiar

> **L4.** *"**Copy**, redirects, styling, and timing are **identical** across unknown or missing, locked,
> suspended, and deleted accounts and across **bad, expired, and used tokens**, on every public or
> self-service surface."*

T51's argument transfers exactly: **the rendering currently depends on the shape of the response body.** A
proxy or future backend that answered differently-shaped bodies for different token states would turn these
screens into an oracle, and the client would render the difference faithfully.

On `VerifyScreen` the stakes are higher than on the resend form, because **this screen is *supposed* to
distinguish** — R4 requires one failure state — so it already has a branch for an attacker to aim at. T51's
screen had none.

## 5 · The fix hypothesis, and what would falsify it

`parseAs: 'stream'` keeps `response` and only turns `data` into a stream, so `response.ok` should still
distinguish a genuine `4xx`/`5xx`. **Unmeasured.** Phase 2 must check:

| Must hold | |
|---|---|
| `response.ok` still readable | otherwise the fix destroys R4 |
| A real `400`/`410` still renders the **uniform failure** | T49's nine-status loop must stay green |
| A **dropped connection** still renders the failure | R4's *"any reason"* genuinely covers this |
| A `204` still renders **success** | T48's path |
| A `200` + HTML now renders **success** | the defect |

**The fourth row is the one that could sink it:** if `parseAs: 'stream'` made the reject arm unreachable, a
dropped connection would stop showing the failure and the fix would trade one R4 violation for another.

## 6 · Scope

| In | Out |
|---|---|
| `VerifyScreen` (R3/R4), `RegisterScreen` (R2) — both named by `tasks.md` | Any change to failure **copy** — if the fix routes parse failures to success, no new string is needed, and a new string would mean a new catalogue key, D7 and an L11 question |
| **An enumeration of every `.POST(`/`.GET(` in `src/`**, classified by what its reject arm does | Backend behaviour; `spec/` edits beyond the task entry already added |
| The suites `tasks.md` names | T51's suite, which is closed to this task |

## 7 · Open questions for Phase 3

| ID | |
|---|---|
| **OQ-T175-1** | Is the R3-over-R4 reading above correct, or does the owner want a **third** state (*"we could not confirm — try the link again"*)? **I recommend R3-over-R4**: it needs no new copy, and a third state on a public surface is a new L4 channel to defend. |
| **OQ-T175-2** | Does `RegisterScreen` belong in the same unit? **I recommend yes** — one mechanism, one fix, and splitting it leaves a known defect open for no benefit. |
| **OQ-T175-3** | If a third caller is found by the sweep, is it in scope or a follow-up? |
| **OQ-T175-4** | Should `parseAs: 'stream'` become a **house rule** — enforced by a scan — rather than three screens remembering it? **This is the most valuable question in the task**: a lint-style pin would stop the fourth screen from reintroducing the bug, and the project has precedent for exactly that kind of tree-wide scan. |

---

**Phase 1 complete. Next is Phase 2 — Codebase Probe.**
