<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding). No code written. -->

# frontend · T51 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Task** | T51 — **Resend acknowledgement is uniform.** *"The resend ack is identical whatever the outcome."* Verifies `resendVerificationAcknowledgementIsUniformAndNeedsNoSession`. Cites **R5**. |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

**No code was written in this phase.**

---

## 1 · This task has no screen to build

**T51 is the second half of a named test whose first half shipped yesterday.** T50 built
`ResendVerificationScreen` and proved R5's *call* and *sessionless* claims; `tasks.md:85` gives T51 the
remaining one — **the acknowledgement is identical whatever the outcome**.

So the likely shape is **a test-only task**, extending
`features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx` rather than creating
anything. **I do not know yet** whether any production change is needed; Phase 1 decides, and the honest
default is that none is, because T50 built the handler to make this property structural.

## 2 · What already exists

| | |
|---|---|
| `features/account/ResendVerificationScreen.tsx` | Four states. **The settle handler takes no argument and `response.ok` is never consulted**, so the screen cannot distinguish outcomes. A source scan in the suite forbids every read of the response **and asserts the handler's argument-less shape**. |
| The named suite | **30 cases.** |
| `app/routes.tsx`, `i18n/catalogue.ts` | The element and the seven `resend` keys. |

### What the suite already holds about outcomes — **exactly one case**

```
352:  it('acknowledges on a non-200 resolve too, because the status is never consulted', …)
```

A single `500` with a `null` body, asserting `container.textContent` equals the acknowledgement. Its own
comment says it is **not** a uniformity case: *"that is task 51's, across the whole range. This one holds the
single property task 50 owns about the handler's shape."*

**So T51 inherits a deliberate gap rather than a defect.** T50 declined to write these cases so that T51 would
have something to verify — the same discipline T49 used when it declined to invent task 54's copy.

### And one obligation T50 created

**OQ-T50-5.** D-T50-1 (the owner's reversal) added a second possible message to this surface: a neutral
`resend.unsent` line on a dropped connection. T50's freeze recorded that **T51 must assert the `unsent` line
appears for a transport failure *and for nothing else*** — not merely that the acknowledgement is uniform.
That is new work this task owns and no earlier task could have written.

## 3 · The pattern to follow — and it is a named sibling, not an analogy

`features/account/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx` (T47) is **the same
property for the neighbouring endpoint**, and it supplies the technique:

```ts
type Reply = { status: number; body?: string };

/** The two replies a registration can draw, written to DIFFER in every way the contract allows. */
const NEW_ADDRESS: Reply = { status: 202, body: JSON.stringify({ message: 'Verification sent to a new address.' }) };
const ALREADY_REGISTERED: Reply = { status: 202, body: JSON.stringify({ message: 'This address is already registered; no action taken.' }) };
```

Its cases are the shape T51 should take:

| T47 case | Why it transfers |
|---|---|
| *"renders **byte-identical markup** for a new address and an already-registered one"* | **Text equality is not enough** — L4 says *copy, redirects, **styling** and timing*. `outerHTML` catches a class that differs by outcome. |
| *"is identical even for a status the contract never declares"* | The contract declares one response; the interesting arms are the ones it does not. |
| *"is identical for a reply that is **not JSON at all**"* | A parse failure is an outcome too. |
| *"renders the catalogue sentence and **NOT** the message the server sent"* | T47's **D2**, which T50 already carried over. |

T49's `verifyFailureShowsOneUniformMessage` supplies the breadth: a loop over **nine statuses**
(`400, 401, 403, 404, 409, 410, 429, 500, 503`), each asserting the full rendered text.

## 4 · Testing conventions

Unchanged from T50, and the suite already carries the helpers this task needs: `installTransport(status, body)`
recording `path`/`method`/`body`/`authorization`, `installTransportFailure()`, `installNeverAnswering()`,
`arrive()`, `teardownCurrent()` (**written for exactly this — "lets a case arrive twice and compare the two
renderings"**), `type()` with its asserted descriptor, and `send()`.

**`teardownCurrent` has no caller in the suite today.** It was carried over from T49 with the helpers; a
byte-identical-markup case across two arms is its first real use here.

Also unchanged: the harness reverts with `git checkout` (**commit tests first**), runs with the local Docker
stack **stopped**, and its copy now **self-checks all sixteen names** before applying a mutant.

## 5 · The blast radius should be small, and that is checkable

T50 touched twelve files because it attached a screen. **A test-only task should touch one.** The pins that
fired for T50 — the route-set counts, `RENDER_SITES`, the credential table, the design-system key set — all
move only when a route, a file or a catalogue key changes, and T51 is expected to change none of them.

**If T51 does add a catalogue key**, the key-set pin fires (T49 **D-T49-3**, T50's experience), and the three
**second-order** pins in T174's, T42's and T43's suites fire only if `RENDER_SITES` changes. Phase 2 should
measure rather than assume — **and should measure the repair as well as the change**, which is the lesson T50
paid for.

## 6 · Known gaps and unknowns

| | |
|---|---|
| **Does T51 need any production change?** | **I do not know.** The handler already cannot branch on outcome. If the answer is no, this is the first purely test-only task since T39, and the brief should say so plainly rather than inventing work. |
| **Is `unsent` a hole in R5's uniformity?** | The owner decided it is not (D-T50-1): the server answers every account state with one `200`, so every *server* outcome is identical, and the line is reached only when the request never arrived. **T51's job is to prove that boundary holds** — which is the first time the decision gets an instrument rather than an argument. |
| **How many statuses, and which?** | T49 used nine. **I do not know** whether that set is right here; `401`/`403`/`409` are the ones that would tempt reason-specific handling, and `429` matters because the dormant rate-limit notice exists. Phase 1/3's call. |
| **Does the timing clause need an instrument?** | L4 says *timing*; `agents.md:44` narrows it to *"the SPA's own rendering only"*. The acknowledgement renders on resolve regardless of status, so it is identical by construction — but *by construction* is the phrase that preceded two vacuous cases in this project. |
| **Can a DIFFERENT EMAIL change the rendering?** | This is R5's actual enumeration risk, and T47's suite names it in its own filename. The server cannot tell this client which address is registered, so the arms must differ by **what the server says**, not by what is true. |

---

**Phase 0 complete. Next is Phase 1 — Specification Extraction.**
