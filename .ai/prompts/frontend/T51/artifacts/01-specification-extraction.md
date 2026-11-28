<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction). No code written. -->

# frontend · T51 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

**No code was written in this phase.**

---

## 1 · The requirement, verbatim

`spec/frontend/requirements.md:18-20`:

> **R5.** WHEN a visitor enters an email on the resend-verification form, THEN the SPA SHALL call the public
> `POST /accounts/resend-verification` **and SHALL show the same acknowledgement regardless of outcome**. The
> form requires no signed-in session, as `auth.yaml` declares. *The divergence from backend R6, which requires
> a signed-in caller, is Q12.*

T50 delivered the first clause and the third. **T51 owns the five words in bold** — and `tasks.md:85` phrases
it as *"the resend ack is identical whatever the outcome"*.

**The third sentence is stale.** `package.md` Q12(a) closed it: `auth.yaml` and the real code were both
already correct, and it was **backend R6's own text** that was wrong. Recorded, not edited — `spec/` is not
ours to change.

## 2 · What "outcome" can even mean to this client — the central extraction

`contracts/api/auth.yaml:61-78` declares the operation with **one** response:

```yaml
  /accounts/resend-verification:
    post:
      operationId: resendVerification
      security: []                      # ← the sessionless half, T50's
      responses:
        '200':
          description: Always returned regardless of whether the email matches an account.
          content: { application/json: { schema: { $ref: '…/RegistrationAcknowledgement' } } }
```

**Two consequences decide this task's whole shape.**

### (a) The account-state axis is invisible from here, so the arms cannot be account states

L4 lists uniformity *"across unknown or missing, locked, suspended, and deleted accounts"*. **This client
cannot observe any of those** — the server answers all of them with the same `200`, and
`resendVerificationIfPending` never throws for a non-match. There is no reply the SPA could receive that says
*"that address is locked"*.

**So a case that seeds a "locked account" arm would be fiction.** The honest arms are **what a server could
actually put on the wire**: a different status, a different body, no body, a body that is not JSON. T47 wrote
this down as *"the two replies a registration can draw, **written to DIFFER in every way the contract
allows**"* — and here the contract allows almost nothing, which is why the undeclared statuses matter.

### (b) The contract **requires** the one field the SPA is **forbidden** to render

`auth.yaml:611-616` — `RegistrationAcknowledgement` has `required: [message]`. **The server must always send a
sentence, and T47's D2 forbids ever showing it.** That is not a tension to resolve; it is the single sharpest
uniformity instrument available, because a `message` is exactly the vehicle a well-meaning backend change
would use to say *"no account with that address"*.

T50 holds this for **one** sentence (*"renders nothing the server sent, even when the server sends a
sentence"*). **T51's job is to hold it across arms that differ**, so a screen that echoed the message would
render two different documents and fail on comparison rather than on a literal.

## 3 · LOCKED decisions this task is measured against

| ID | Text (abridged) | What T51 must hold |
|---|---|---|
| **L4** `design.md:21-26` | *"Enumeration-safe UI … **Copy, redirects, styling, and timing are identical** across unknown or missing, locked, suspended, and deleted accounts and across bad, expired, and used tokens, on every public or self-service surface."* | **Four clauses, and the task should instrument all four.** Copy → full text equality. **Styling → byte-identical markup**, because text equality would miss a class that varied by outcome. Redirects → the pathname after each arm. Timing → below. |
| **agents.md:44** | *"The SPA cannot control server response timing. **The timing requirement applies to the SPA's own rendering only.**"* | So timing here is *"does the SPA add a delay of its own that differs by outcome"* — not round-trip time. The screen has no timer, which an existing source scan already forbids. |
| **agents.md:43** | *"No client-side account-state gating. No lockout timers. **Uniform copy only.** Confirmed by the author."* | Author-confirmed, and the strongest single sentence behind this task. |
| **L11** | accessibility, catalogue-only copy | The acknowledgement must be **announced and focused identically** in every arm — a focus move that happened for a `200` and not for a `503` would be a non-visual enumeration channel, which is the kind of hole L4 is for. |
| **L14** | route set verbatim | The pathname is unchanged in every arm (T50 holds it for one). |
| **D7** | every rendered catalogue key measured | T51 is expected to add **no** key, which Phase 2 should confirm rather than assume. |

**The carve-out does not reach here.** L4's resolved exception is the ADMIN-only `adminGetAccount` view; this
is an anonymous public form, *"the anonymous-probing scenario this rule exists to close"* in the spec's own
words.

## 4 · The obligation T50 handed over

**OQ-T50-5.** D-T50-1 (the owner's reversal of Kimi's Phase 3) put a second possible message on this surface:
`resend.unsent`, on a dropped connection. T50's freeze recorded that T51 must prove it appears **for a
transport failure and for nothing else**.

**This is the only part of R5's uniformity that is not structural.** Everything else follows from the handler
not reading the response; `unsent` is reached by an actual branch (`.then(resolve, reject)`), so it is the one
place where a future edit could make the SPA's rendering depend on something other than "did the request come
back". The claim to instrument is therefore **two-directional**:

1. every **server** reply — any status, any body — renders the acknowledgement and **no** `unsent` line;
2. only a request that never completed renders `unsent`.

T50 proved (2) for one rejection. **(1) is unmeasured across the range, and that asymmetry is T51's subject.**

## 5 · Acceptance-criteria candidates

Phase 4 freezes these; this is the extraction.

| # | Candidate |
|---|---|
| 1 | A loop over statuses the contract never declares — T49's nine (`400, 401, 403, 404, 409, 410, 429, 500, 503`) plus the declared `200` — each rendering the **full acknowledgement text**. |
| 2 | **Byte-identical markup** between two arms written to differ in every way the contract allows (status **and** `message`), compared as `outerHTML`. |
| 3 | Identical for a reply that is **not JSON at all**, and for one with **no body**. |
| 4 | **No arm renders `resend.unsent`** — the first half of OQ-T50-5. |
| 5 | A **transport failure** renders `unsent`, and a **server reply never does** — the second half, stated as a contrast so neither arm can pass alone. |
| 6 | The **pathname** is identical in every arm (L4's *redirects*). |
| 7 | **Focus and the live region** end in the same state in every arm (L11 as an enumeration channel). |
| 8 | The server's `message` is absent from the DOM **in every arm**, not just one. |

## 6 · Explicitly out of scope

| | |
|---|---|
| **Anything about the request** | T50's: one call, `POST`, body by equality, no bearer. |
| **The sessionless claim** | T50's, measured twice. |
| **Any production change** | Expected to be none — **and if the task needs one, that is a finding, not a task**: it would mean T50 shipped a uniformity defect its own suite did not catch. |
| **OQ-T50-6** (a `500` says *"Check your email"*) | The owner's open question, and T51 **hardens** the behaviour it asks about rather than resolving it. Worth stating plainly: **T51 makes the falsehood uniform, which is the trade L4 already chose.** |
| **Rate-limit copy for `429`** | The notice is dormant (**FU-T43-2**'s neighbour); `429` appears here only as one more undeclared status. |

## 7 · Open questions for Phase 3

| ID | Question |
|---|---|
| **OQ-T51-1** | Which status set? T49's nine, or the nine plus `200` and a `3xx`? A `302` is interesting because `openapi-fetch` would follow or surface it differently. |
| **OQ-T51-2** | Is `outerHTML` the right comparison, or should it be normalised? A raw comparison is stronger but could couple the case to React's attribute order. **T47 compared raw and it has held for four tasks**, which is evidence rather than proof. |
| **OQ-T51-3** | Should the two arms also differ by **email**, holding that the address itself changes no rendering? The server cannot tell us which is registered, so this measures the client only — but that **is** the enumeration claim. |
| **OQ-T51-4** | Does the timing clause get an instrument, or is *"the screen reaches for no timer"* (already scanned) sufficient? **I lean to adding a tick-count comparison**, because *"identical by construction"* is the phrase that preceded two vacuous cases in this project. |

---

**Phase 1 complete. Next is Phase 2 — Codebase Probe.**
