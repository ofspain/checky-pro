<!-- MODEL: Claude Opus 5 — Phase 13 (PR / Commit Preparation). -->

# frontend · T175 · Phase 13 — PR / Commit Preparation

| | |
|---|---|
| **Consumes** | `artifacts/12-specification-verification.md` — **PASS** |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 is **PASS**: all thirteen ACs held, 2148/2148, lint 0, types 0, harness 9/9 with no survivors, no
broken rows and no disagreements.

---

## Commit title

```
fix(frontend): a reply the client cannot parse is not a verification failure (T175)
```

## Commit message

```
fix(frontend): a reply the client cannot parse is not a verification failure (T175)

Pass `parseAs: 'stream'` on the two generated-client calls that never read the
reply body, and add a scan so the third screen to make this mistake is the last.

`openapi-fetch` parses the reply body on its own behalf. For a call whose settle
handler never looks at `data`, that parse is pure liability: a reply whose body
is not valid JSON makes the promise REJECT with a SyntaxError, and a handler
written as `.then(ok, failure)` cannot tell that from a dropped connection. So
an edge interstitial, a proxy's plain-text answer or a connection cut
mid-document took the failure arm of a request the SERVER HAD ACCEPTED:

  - VerifyScreen told a visitor whose email was VERIFIED that their link was not
    valid, violating R3.
  - RegisterScreen left the form untouched, so a visitor whose account had been
    created and emailed saw nothing happen and would register again, violating
    R2's "the same screen, identical copy and layout".

Both were invisible to the whole suite, and the reason is the contract: auth.yaml
declares ONE response for verifyEmail, a 204, and A 204 CANNOT CARRY A BODY AT
ALL - `new Response(body, { status: 204 })` throws. A conforming fixture can
never reproduce the defect. A control case pins that, so if a 204 ever accepts a
body the arms above stop being the only reachable shape.

R4 is preserved and measured rather than asserted: inverting VerifyScreen's
`response.ok` test kills 31 cases, the whole nine-status loop among them. The
option changes only `data`, so `response` survives and a genuine 4xx or a dropped
connection still renders the uniform failure.

THE SCAN IS THE PART THAT OUTLIVES THE FIX. Its rule is narrower than "always
pass parseAs" and narrower than banning it, because three calls legitimately READ
the body and for them a parse failure IS a failure: if you do not read the body,
do not let the library parse it. It asserts its own census as a whole table, and
audits its own reach against both api/client.ts and the package - a first draft
keyed on factory calls was structurally blind to the two readers that take their
client as a PARAMETER, which is the shape a testable module has.

No new catalogue key, no route change, no primitive change. Two lines of
production code.

Refs: spec/frontend/tasks.md task 175; R2, R3, R4; L4
Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

| File | Change |
|---|---|
| `frontend/src/features/account/VerifyScreen.tsx` | **+1 code line** (`parseAs: 'stream'`) and the R3 rationale; one false claim in the header corrected against measurement |
| `frontend/src/features/account/RegisterScreen.tsx` | **+1 code line** and the R2 rationale |
| `frontend/src/api/generatedClientCallsDoNotParseBodiesTheyNeverRead.test.ts` | **new** — the AC10 scan: census, reach audit, classifier fixture |
| `frontend/src/features/account/verifyFromLinkShowsSuccessState.test.tsx` | two arms + the `204`-cannot-carry-a-body control |
| `frontend/src/features/account/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx` | two arms, a byte-identical comparison with three baseline guards, and the executable attribution pin |
| `spec/frontend/tasks.md` | task 175 appended **at the owner's explicit instruction** |
| `frontend/src/features/account/ResendVerificationScreen.tsx` | **comment-only, and T51's** — it appears in the range because task 175 was appended mid-T51 |

## Summary

Two lines of production code, seven new behavioural cases, a scan, and 2148/2148.

**What the task was worth beyond the bug:** the defect was invisible because the dependency arrived
**through a library the source never names**, so nine source patterns and 2116 tests all stayed green while
three screens shipped it. The scan exists to make the *shape* of the call auditable rather than the symptom.

**Three things this task got wrong and corrected by measurement** — recorded because each was caught by an
instrument rather than by reading:

1. A **scan keyed on factory calls** could not see the two readers that take their client as a parameter. The
   better a module's seams, the more invisible it was. Fixed by keying on factory **or type**, with both lists
   audited.
2. Phase 9 answered *"the reject arm has no case"* from **a survey piped through `head -4`**, against a case
   at line 899. The duplicate was withdrawn in favour of the T43 owner, which is stronger on every axis, and
   replaced with a pin that makes the attribution executable. **TR39 killing two cases instead of one was the
   only thing that caught it.**
3. **Kimi's Phase 11 verdict was right and its account wrong in five places**, including an inverted
   description of the row that justifies declining its own recommendation. Corrected against source at
   Phase 12.

## Testing performed

| | |
|---|---|
| Whole suite | **2148 / 2148** (42 files) |
| `oxlint src` | **0** |
| `tsc --noEmit` | **0** |
| Mutation harness | **9/9 regressions killed**, canary died, **0 survivors, 0 broken, 0 disagreements** |
| AC13 | every new case **failed before the fix** — 2 verify, 3 registration, 2 scan |
| Non-vacuity, **after** the fix | removing `parseAs` makes the scan fail **and name the file** |

## Specification references

| | |
|---|---|
| **Task** | `spec/frontend/tasks.md` task **175** |
| **Requirements** | **R3** (scoped), **R4** (scoped), **R2** (reached via `RegisterScreen`, named in the brief at Phase 4) |
| **LOCKED decisions** | **L4** (scoped) |
| **Named tests** | `verifyFromLinkShowsSuccessState`, `verifyFailureShowsOneUniformMessage` — both green; the second **untouched**, which is the AC6 evidence |

## Open items handed to the owner

| | |
|---|---|
| **OQ-T175-5** | **Caused by this fix.** A `403` HTML interstitial from an edge now shows *"Check your email."* `RegisterScreen` reads neither `data` nor `response`, where `VerifyScreen` reads `response.ok`. Needs a decision, not a patch |
| **OQ-T175-6** | `codeExchange` under an edge interstitial — a reader, so a parse failure there is genuine, but the visitor-facing result is unmeasured |
| **OQ-T43-9** | a failure word for `RegisterScreen`; **OQ-T175-8 was withdrawn into it** |
| **FU-T43-2** | no shared test-util module — `installTypedTransport` is now copied a third time |

---

**Phase 13 complete. T175 CLOSED — PASS.**
