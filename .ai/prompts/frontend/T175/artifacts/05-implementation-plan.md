<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan). No code written. -->

# frontend · T175 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

**No code was written in this phase.**

---

## Order of work

| Step | Why here |
|---|---|
| **1 · Write every new case first, against both unfixed screens** | **AC13.** The repair's blast radius is zero — 2137/2137 with both fixes applied — so a case that passes before the fix measures nothing. Target: the `VerifyScreen` pair fails on *"not valid"*, the `RegisterScreen` pair fails on *"the form is still there"*. |
| **2 · The scan, also before the fix** | It must **fail** now, naming the two unfixed calls. A scan written after the fix has never been seen to fail. |
| **3 · Apply the two-line fix** | Everything goes green together. |
| **4 · Commit tests** | **Before the harness** — it reverts with `git checkout`. |
| **5 · Harness rows** | Phase 7's. |

## Where each case lands (D-T175-4)

| Suite | Cases | Helper on hand |
|---|---|---|
| `verifyFromLinkShowsSuccessState.test.tsx` (T48) | **AC1–AC3** | `installTransport(status = 204)` — **takes no body**, so a `installTypedTransport` is needed |
| `verifyFailureShowsOneUniformMessage.test.tsx` (T49) | **AC4–AC6** | `installTransport(status, body)` **and** `installTransportFailure()` — both already there |
| `registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx` (T47) | **AC7–AC9** | `installTransport(reply)` with a `Reply = { status, body? }` — **no content type**, so it needs one |

**Two suites need a content-type-carrying transport**, which T51's `installTypedTransport` already is. It is
copied a third time rather than shared — **FU-T43-2**, stated again rather than quietly worsened.

## The scan — its own file

`src/api/generatedClientCallsDoNotParseBodiesTheyNeverRead.test.ts`, **not** inside
`lintRejectsHandWrittenBackendFetch.test.ts`: that file is about the **lint script** as a build gate, and a
source scan sharing it would make a failure ambiguous between two unrelated claims.

**Three things it must do, two of them from D-T175-2:**

1. Find every generated-client call across **all** factories — `createPublicAuthApi`, `createAuthApi`,
   `createOidcClient` — because `codeExchange` uses the third and Kimi's sketch missed it.
2. Classify by **whether the result is consumed**, covering `await` as well as `.then`, since **all three
   readers `await`** and the sketch's callback rule would have demanded `parseAs` on exactly them.
3. **Assert its own census** — the number of call sites found — because **a scan that finds nothing passes**,
   and a new factory or call shape would otherwise slip past by being invisible to the pattern rather than by
   satisfying it. T35 learned this; T36 found the scan it added had the hole it closed.

**And the exemptions are named individually, not pattern-matched**: three readers, listed by path, so a fourth
reader has to be added deliberately.

## Case plan

| Case | AC |
|---|---|
| `200` + HTML body + valid token → success, and the token was still sent | **AC1** |
| `200` + truncated JSON → success | **AC2** |
| `204` declared → success (the control: proves the suite's existing path still works) | **AC3** |
| `400`, `500` → uniform failure | **AC4** |
| A rejecting transport → uniform failure | **AC5** |
| Registration `202` + HTML → acknowledgement | **AC7** |
| Registration `202` + truncated JSON → acknowledgement | **AC8** |
| The three registration arms render **byte-identical** markup | **AC9** |
| The scan passes, exempts three named readers, and asserts its census | **AC10** |

**AC6 needs no new case** — T49's nine-status loop already exists and must simply stay green. Writing a second
one would be the duplicate instrument T173's rule forbids; the loop is named in the notes instead.

## Harness rows planned

| Row | Mutant | Expectation |
|---|---|---|
| **TR31** | `parseAs` removed from `VerifyScreen` | **own** — the two new verify cases |
| **TR32** | `parseAs` removed from `RegisterScreen` | **own** — the two new registration cases |
| **TR33** | `VerifyScreen`'s success test flipped to `!response.ok` | **own** — the control that `response.ok` is still consulted, so the fix did not silently make everything succeed |
| **TR34** | The scan's reader-exemption list loses an entry | **own** — proves the exemptions are load-bearing |
| **TR35** | The scan's census number changed | **own** — proves the census is asserted, not decorative |
| **TR36** | CANARY — `VerifyScreen` renders neither state | **own** |

**TR33 is the row that matters most.** The fix makes a non-parsing reply succeed; the danger is a fix that
makes *everything* succeed. A mutant inverting the `ok` test must die, or AC4/AC5 are decoration.

## Risks

| Risk | Handling |
|---|---|
| **The fix makes genuine failures succeed** | **TR33**, plus AC4/AC5 cases, plus T49's untouched nine-status loop. |
| **A case passes before the fix** | Step 1. |
| **The scan is brittle or vacuous** | The census (TR35) and the exemption list (TR34) are both mutated. |
| **A `204` fixture with a body** | Impossible — D-T175-1. The arms use `200`. |
| **T47/T48/T49 suites destabilise** | Three suites gain cases; each is run alone as well as in the whole suite. |

---

**Phase 5 complete. Next is Phase 6 — Implementation.**
