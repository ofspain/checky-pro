<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). -->

# frontend · T47 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T47 — Registration acknowledgement is identical |
| **Spec section** | Phase 1 — Auth & account: registration and reset |
| **Model** | Human Approval |
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |

**STATUS: FROZEN** — approved by the owner on 2026-11-27. Downstream phases may not renegotiate it.

**Task statement (verbatim from `spec/frontend/tasks.md`, task 47):**
> **Registration acknowledgement is identical.** Existing and new emails show the same acknowledgement
> screen. Verifies `registrationAcknowledgementIsIdenticalForExistingAndNewEmails`. Cites R2.

---

## The gate decision

The owner chose **navigate, reuse the reserved key, and re-aim AC11** — Phase 3's central decision accepted,
its catalogue design and its closed-task impact amended. The amendment exists because the packet Phase 3
presented was wrong about what the change costs, and the correction is recorded in full below rather than
quietly applied.

---

## Phase 3 disposition

| Item | Disposition | Reason |
|---|---|---|
| **OQ-T47-1** — do not render the server's `message` | **ACCEPTED, unamended** | The reasoning holds and the premise checks out. `RegistrationAcknowledgement` really is `required: [message]` with `message: string` (`contracts/api/auth.yaml:611-616`, generated at `libs/ts/api-client/src/generated/auth.ts:466-468`), so the field is not hypothetical and ignoring it is a real decision. L11 locks copy to the catalogue, D7 requires every rendered key to be measured, and server text has no key and therefore no client-side instrument. `RegisterScreen` already reads no body; extending that to the acknowledgement is consistent rather than novel. |
| **OQ-T47-2** — navigate to `register/sent` | **ACCEPTED with amendment** | The decision is right: L14's route exists to be filled, AC3's word "destination" reads as a location, and `RegisterScreen`'s own comment says the in-place `StatusRegion` exists *because* the route was empty — a reason that expires here. **The amendment is to its catalogue design and its accounting of closed-task impact.** See the two rows below. |
| **OQ-T47-2's catalogue design** — two new `registerSent.*` keys, `register.success` dropped | **REJECTED** | The dead-key argument runs backwards. Phase 3 rejected reuse because it "would leave `RegisterScreen` with a key that no longer renders there, creating a dead catalogue entry (D7)" — but a key rendered by `RegisterSentScreen` is not dead, it has moved. It is Phase 3's own proposal that orphans `register.success`, while `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:922` pins it as a rendered key and `:966-967` pins its exact words. And the key was **reserved for this task in writing**: `catalogue.ts:84-86` says "`success` says nothing about whether the address was already registered - R2 and L4 require the same words either way, and **task 47's acknowledgement screen is where that claim gets its own suite**." Reuse honours a reservation one task made for this one. |
| **OQ-T47-2's "update the false comments only"** | **REJECTED as an account of the work** | It is not comments. Removing the success `StatusRegion` collides with **nine live assertions** in a closed task's suite, enumerated under *Files to Modify*. One of them, `registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:820`, is the **AC11 instrument that proves the screen does not branch on the response** — R2 and L4, the exact requirement this task cites. A packet that calls that a comment fix understates the decision the owner was asked to make. |
| **OQ-T47-3** — focus the heading on mount | **ACCEPTED with a corrected premise and a corrected target** | The decision is right and the template is real: `HomeScreen` is `useRef` + `useEffect(() => ref.current?.focus(), [])` (`app/HomeScreen.tsx`), and `Heading` already sets `tabIndex={-1}` inside with a forwarded ref, so the screen needs only the effect. **Two factual corrections** are in the row below. |
| **OQ-T47-3's "first screen arrived at through a client-side navigation/redirect"** | **CORRECTED — false** | `HomeScreen` is a navigation arrival and has been since task 17: `CallbackScreen` navigates there when `takeReturnTarget()` finds nothing remembered, which its own docstring states. It already moves focus. `RegisterSentScreen` is therefore the **second** focus-moving screen, not the first redirect arrival. |
| **OQ-T47-3's "rewrite the I9 loop's comment"** | **CORRECTED — wrong line** | The loop's stated reason, "`<body>` is the correct expectation for all four: none of them is an arrival from a redirect" (`homeRouteRendersLandingScreenAndManagesFocus.test.tsx:430`), stays **true** of its four screens, which this task does not touch. The text that actually goes false is the **assertion message at line 462** — `` `${name}` took focus on mount, which only task 174's screen may do`` — and the describe title at line 424, *"no OTHER screen takes focus"*. Both become false the moment a second screen may legitimately take focus. Aiming the correction one line off is how T42 shipped two false comments past two reviews; the frozen target is lines 424 and 462. |
| **D1** — touching a closed task's file | **ACCEPTED, with its size restated** | Anticipated by the brief, but the size was not. See *Files to Modify*. |
| **D2** — ignoring a required contract field | **ACCEPTED** | Deliberate, localized, and recorded in the screen's header comment so a later engineer does not "fix" it. |
| **D3** — no back link | **ACCEPTED** | Carried as OQ-T47-4, deferred. |
| **Screen structure** — `Screen` / `Heading` / `Text`, no `StatusRegion` | **ACCEPTED** | All three are in the barrel (`app/ui/index.ts`). The focus move is the announcement, so a live region would announce the same words twice. |
| **Traps 1–4** | **ACCEPTED in full** | Trap 1 (byte-identical mocks are vacuous) and trap 2 (`Storage.prototype` not an instance spy — T42's precedent) are the two that have actually cost this project cases. |
| **FU-T43-2** | **Deferred, recorded a fourth time** | Out of scope by the brief. |

---

## Verification log

Every Phase 3 claim was checked against source before this freeze, because several Kimi findings in this
project have been factually wrong or already resolved.

| Claim | Verdict | Evidence |
|---|---|---|
| `RegistrationAcknowledgement.message` is required | **TRUE** | `contracts/api/auth.yaml:611-616` — `required: [message]` |
| `Heading` makes `<h1>` focusable without a prop | **TRUE** | `app/ui/Heading.tsx` — `forwardRef`, `tabIndex={-1}` set inside |
| `HomeScreen` is the focus template | **TRUE** | `app/HomeScreen.tsx` — `useRef` + `useEffect`, empty dep array, `?.` |
| `Screen`, `Heading`, `Text` are in the barrel | **TRUE** | `app/ui/index.ts` |
| `RENDER_SITES` holds 8, so a ninth is deliberate | **TRUE** | `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx:86-102` |
| `'register/sent'` is a bare literal, and L14 wants a constant | **TRUE** | `routes.tsx:46`; the attach comment at `:85-87` says "a path is attached by matching its constant, never by adding one (L14)" |
| The I9 loop holds four screens with the cited reason | **TRUE** | `homeRouteRendersLandingScreenAndManagesFocus.test.tsx:424-466` |
| "Comment only" in the T44/T45 suite | **FALSE** | Nine assertions, enumerated below |
| `register.success` would be orphaned by reuse | **FALSE — inverted** | Reuse renders it; Phase 3's own design orphans it |
| `RegisterSentScreen` is the first redirect arrival | **FALSE** | `HomeScreen` has been one since task 17 |

---

## Scope

**In**
- `RegisterSentScreen`, attached to the declared-but-element-less `register/sent`.
- The named suite `registrationAcknowledgementIsIdenticalForExistingAndNewEmails`.
- `RegisterScreen` navigates to that path after the call resolves, and stops rendering the success state.
- Growing the pinned instruments a new render site trips.
- **Re-aiming** the closed suite's assertions that measured the success state in place.
- Whatever catalogue keys the screen renders — each one measured by a case (D7).

**Out**
- Re-proving R1's advisory password check (T43–T46 own it).
- The `verify`, `resend-verification` and `reset/*` routes (tasks 48–54).
- Any new public route. This task **fills** a declared path and adds none.
- TanStack Query or Zustand (L13; no consumer).
- Any new `app/ui` primitive.
- Fixing FU-T43-2.
- A back link or any call to action (OQ-T47-4, deferred).

---

## Open questions at freeze

| ID | Resolution |
|---|---|
| **OQ-T47-1** | **RESOLVED** — the server's `message` is not read. Recorded in the screen's header comment. |
| **OQ-T47-2** | **RESOLVED** — navigate; reuse `register.success`; add one new heading key; re-aim AC11. |
| **OQ-T47-3** | **RESOLVED** — focus the heading on mount; reasoned exclusion from the I9 loop; correct lines 424 and 462. |
| **OQ-T47-4** | **DEFERRED, owner = the repository owner.** Should the acknowledgement carry a sign-in link? Not needed for R2, and adding one would introduce catalogue keys and navigation R2 does not ask for. |
| **FU-T43-2** | **DEFERRED** — no shared test-util module; recorded a fourth time. |

---

## Files to Create

| Path | Role |
|---|---|
| `frontend/src/features/account/RegisterSentScreen.tsx` | The acknowledgement screen (AC1, AC2, AC7, AC8). |
| `frontend/src/features/account/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx` | The named suite (`package.md` §8). |

## Files to Modify

| Path | Change | Why it is in scope |
|---|---|---|
| `frontend/src/app/routes.tsx` | Add `REGISTER_SENT_PATH = 'register/sent'`; replace the literal in `PUBLIC_ROUTE_PATHS` with it; attach the element by matching it. Correct the task-43 comment that says the path "stays element-less because task 47 owns the acknowledgement". | L14's house rule, stated in this file. The array's **strings** are unchanged, so every membership and order pin still passes (AC10). |
| `frontend/src/i18n/catalogue.ts` | Add `registerSent.heading`. Amend the `register.success` comment to say where it now renders. | L11; the key is reserved here for this task, and the reservation is now spent. |
| `frontend/src/features/account/RegisterScreen.tsx` | Navigate after the call resolves; drop the `'sent'` status and the success branch of its `StatusRegion`; correct the header comment and the `register/sent` claim. | OQ-T47-2. **The no-branching promise is unchanged**: still no read of body or status, no retry, no delay. |
| `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | **Nine assertions re-aimed, not deleted**, plus the false comments. Lines `516` (rendered-keys list), `539-543` (the success-sentence case), `820` (**the AC11 `it.each`, three rows — 202, 409, 500**), `883`, `917`, `943`. | This is the amendment the owner approved. Each assertion measured "the screen shows the success sentence in place"; the sentence now lives one navigation away. **Each row must assert the new destination AND that the sentence is rendered there** — never be deleted, and never be relabelled. A row re-aimed keeps its claim; a row deleted silently retires R2's no-branching instrument, which is the one thing this task must not do while citing R2. |
| `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | `RENDER_SITES` 8 → 9. Move `register.success` in the catalogue-keys pin from task 43's group to this task's, keeping the key present. | A ninth render site is added deliberately; the barrel-import scan's equality is against this list. |
| `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | Keep the four-entry loop. Correct the describe title (`424`) and the assertion message (`462`). Add one case asserting `RegisterSentScreen` moves focus to its heading **through the real route table**. | OQ-T47-3, as corrected. The brief's "five entries or a reasoned exclusion" is satisfied by the reasoned exclusion: the four screens mount in place and must not take focus; this one arrives by navigation and must. |

**Contradiction in the Phase 2 brief, resolved here:** it listed
`registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` under *Files to Modify* (comment only) **and**
its *Files NOT to Modify* said "the T44/T45 suites — R1 is closed", which is that same file. The freeze
resolves it one way: **the file is in scope for the six sites above and for nothing else.** R1's advisory
rows, its bound interpolation and its code-point counting are untouched. If a change outside those sites
looks necessary, that is a finding for Phase 7, not an edit.

## Files NOT to Modify

- Anything under `spec/` (guardrail).
- `frontend/src/features/account/passwordLength.ts`, and every assertion in the T44/T45 suite other than the
  six sites named above. R1 is closed.
- `frontend/src/app/ui/*`. No primitive change is required. If one appears to be, that is a finding, not an edit.
- `frontend/src/auth/*` — `features/account/` is forbidden from importing it.
- `frontend/src/api/lintRejectsHandWrittenBackendFetch.test.ts` — FU-T47-1 was fixed outside this pipeline
  (`9c711680`); this task does not touch it again.
- `infra/`, `docs/`, `services/`.

---

## Acceptance Criteria (final)

| AC | Criterion | Rule |
|---|---|---|
| **AC1** | `/app/register/sent` renders a non-empty acknowledgement screen. | R2, L14 |
| **AC2** | Rendered output is **identical** — text *and* element structure — for a new email and an already-registered one. | R2 |
| **AC3** | The post-submit destination is the same in both cases, **and it is a location**: `REGISTER_SENT_PATH`. | R2, L4 |
| **AC4** | The client reads neither status nor body, and does not retry. | R2 |
| **AC5** | No artificial delay on either path. | R2 |
| **AC6** | Every user-facing string resolves from the catalogue: `registerSent.heading` and `register.success`, both asserted. | L11, D7 |
| **AC7** | Focus moves to the heading on mount, asserted through the real route table and under StrictMode; no tab trap. | L11 |
| **AC8** | Renders correctly on a **cold arrival** with no preceding submit and no router state. | L14, L16 |
| **AC9** | Nothing is written to `sessionStorage` or `localStorage` on this path, proven by a `Storage.prototype` spy rather than an instance spy. | L16 |
| **AC10** | `PUBLIC_ROUTE_PATHS` membership and order unchanged; the element is attached by matching a constant. | L14 |
| **AC11** | `RENDER_SITES` grows to 9 and the barrel scan still passes. | house instrument |
| **AC12** | **The closed suite's nine re-aimed assertions all still measure their original claim**, and the three AC11 rows still fail if `RegisterScreen` starts branching on the response. | this freeze |

AC12 is new at the freeze and is the owner's amendment made testable. Without it, "re-aim, never delete" is
a sentence in a brief; with it, Phase 7 has to show the rows still bite.

---

## Required Tests

`registrationAcknowledgementIsIdenticalForExistingAndNewEmails` — the named suite from `package.md` §8.

## Traps the suite must avoid

1. **Byte-identical mocks are vacuous.** The server answers 202 for both cases, so a suite that mocks the
   same reply twice proves only that a pure function is pure. Mock **different** bodies, and the statuses the
   contract does not declare, and still assert identical rendered output.
2. **A negative storage assertion needs a real intercept.** T42's "writes no storage" was vacuous from birth
   because an instance spy on jsdom storage does not intercept. Spy on `Storage.prototype.setItem`.
3. **Assert the catalogue literals, not the mocked body.** Comparing rendered text to the response body would
   pass while copy drifted.
4. **Focus must be asserted through the real route table**, not only a direct mount. A direct mount is the
   screen test; the route-table mount is the journey.
5. **A re-aimed row must be seen to fail.** Each of the three AC11 rows must be run against a `RegisterScreen`
   that branches on the response and observed to fail. A row that cannot fail measures nothing, whatever its
   name says.

## Deviations / risks

| | |
|---|---|
| **D1** | **A closed task's suite is modified in nine places.** Approved by the owner at this gate with the size stated. Limited to the six sites named; every other assertion in that file is out of bounds. |
| **D2** | **A required contract field is deliberately unread.** `RegistrationAcknowledgement.message` stays required for the server and unused by this client. Recorded in the screen's header so it is not "fixed" later. |
| **D3** | **No back link.** OQ-T47-4, deferred. |
| **D4** | **`register.success` is rendered by a different screen than the one that owns its catalogue group.** The key keeps its name because renaming it would churn four pins for no behavioural gain; the catalogue comment is amended to say where it renders. Phase 12 should check that comment is true. |

---

**Phase 4 complete. STATUS: FROZEN.** Next is **Phase 5 — Implementation Plan**.
