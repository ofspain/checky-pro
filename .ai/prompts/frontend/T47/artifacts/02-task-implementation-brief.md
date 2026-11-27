# frontend · T47 · Phase 2 — Task Implementation Brief

> **This is the only specification the implementation and review phases use.**
> Consumes `artifacts/01-specification-extraction.md`. Produced 2026-11-27.

## Task

**T47 — Registration acknowledgement is identical.** *"Existing and new emails show the same
acknowledgement screen. Verifies `registrationAcknowledgementIsIdenticalForExistingAndNewEmails`.
Cites R2."* (`spec/frontend/tasks.md` task 47)

## Purpose

`/app/register/sent` has been a declared public route with **no element since task 16**. Today it
renders a blank document. L14 locks it into the route set as *"registration, its acknowledgement"*,
and T43's frozen brief (D1) chose an in-place `StatusRegion` announcement **explicitly because** this
route was empty — Kimi's F1 recorded that task 47 "will later replace or extend this behavior".
This task fills the route and proves the acknowledgement cannot differ between a new address and one
already registered.

## Scope

**In**
- One acknowledgement screen, attached to the existing `register/sent` path.
- One named suite proving the acknowledgement is identical across both cases.
- Growing the pinned instruments a new render site trips (see *Files to Modify*).
- Whatever catalogue keys the screen renders — each one measured by a case (D7's standing principle).

**Out**
- Re-proving R1's advisory password check (T43–T46 own it).
- The `verify`, `resend-verification` and `reset/*` routes (tasks 48–54).
- Any new public route — L14 would require an ADR. This task **fills** a declared path, adds none.
- TanStack Query or Zustand (L13; no consumer here).
- Any new `app/ui` primitive. The screen composes existing ones.
- Fixing `FU-T43-2` (no shared test-util module) — recorded a fourth time, not fixed here.

## Business Rules

| ID | One line |
|---|---|
| **R2** | The same "check your email" screen, identical copy **and layout**, for an existing or a new email, with no client-side branching and no delay. |

## Locked Decisions

| ID | Binding effect here |
|---|---|
| **L14** | `register/sent` is a required public route; membership and order of `PUBLIC_ROUTE_PATHS` must not change. |
| **L4** | Copy, **redirects**, styling and timing identical across account states — the destination is part of the claim, not only the words. |
| **L11** | WCAG AA, keyboard-operable, managed focus, catalogue-only strings. |
| **L16** | sessionStorage carve-out is exhaustive and holds **no PII** ⇒ the submitted email may not be persisted to reach this screen. |
| **L6** | Generated client only. |
| **L13** | Confirmed stack; no new state library. |
| **D7** *(T43, inherited principle)* | *"A key written here and never rendered is unused code"* — every catalogue key this task adds must be rendered **and** measured by a case. The literal six-key freeze was T43's own: T45 legitimately added `register.passwordGuidance`. The principle binds; the number does not. |
| **D1** *(T43)* | The form announces in place and does not navigate — **this task owns whether that still holds** (OQ-T47-2). |
| **D5** *(T174/T43)* | `Heading` made every `<h1>` focusable, so a `.focus()` on any screen is now live. The I9 guard asserts no screen takes focus on mount. |

## Dependencies

- `contracts/api/auth.yaml` → `POST /accounts`: public, **202 only**, documented *"Always returned
  regardless of whether the email was already registered (enumeration-safe)"*. No 409 in the contract.
- Schema `RegistrationAcknowledgement { message: string }` — **required** field. See OQ-T47-1.
- `createPublicAuthApi()`; `PUBLIC_ROUTE_PATHS`, `REGISTER_PATH`, `APP_BASENAME` from `app/routes`.
- `app/ui`: `Screen`, `Heading`, `Text`, `TextLink`, `StatusRegion`. No new primitive.
- `createI18n()` + `en` for the suite.

## Inputs

A browser navigation to `/app/register/sent`, arriving **either** from a successful submit **or**
cold — a deep link, a refresh, a shared URL. The screen receives no email address and no server body.

## Outputs

A rendered acknowledgement screen, identical in both arrival modes and for both email cases.

## State Changes

**None.** No client-side storage is written or read on this path. No session, no token, no cache.

## Files to Create

| Path | Role |
|---|---|
| `frontend/src/features/account/RegisterSentScreen.tsx` | The acknowledgement screen. |
| `frontend/src/features/account/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx` | The named suite. |

## Files to Modify

| Path | Change | Why it is in scope |
|---|---|---|
| `frontend/src/app/routes.tsx` | Add `REGISTER_SENT_PATH` and attach the element by matching it. | A path is attached by matching a constant, never by adding a literal (L14 house rule). |
| `frontend/src/i18n/catalogue.ts` | Add the keys the screen renders. | L11; each key measured (D7). |
| `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | `RENDER_SITES` 8 → 9. | A ninth render site must be added deliberately; the list's own comment says so, and the barrel-import scan's equality is against this list. |
| `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | The I9 focus-guard loop, four entries → five **or a reasoned exclusion**. | See OQ-T47-3. |
| `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | **Comment only**, if `RegisterScreen` keeps its behaviour. | Its comment states *"`register/sent` still carries no element"*, which this task makes **false**. Its assertions pin membership and order only, so **nothing fails** — which is exactly how T42 shipped two false comments past two reviews. A false comment that no assertion catches is this task's to fix. |

## Files NOT to Modify

- Anything under `spec/` (guardrail).
- `frontend/src/features/account/passwordLength.ts` and the T44/T45 suites — R1 is closed.
- `frontend/src/app/ui/*` — no primitive change is required; if one appears to be, that is a finding, not an edit.
- `frontend/src/auth/*` — `features/account/` is forbidden from importing it.
- `infra/`, `docs/`, `services/` — unrelated to this task.

## Acceptance Criteria

| AC | Criterion | Rule |
|---|---|---|
| **AC1** | `/app/register/sent` renders a non-empty acknowledgement screen. | R2, L14 |
| **AC2** | Rendered output is **identical** — text *and* element structure — for a new email and an already-registered one. | R2 |
| **AC3** | The post-submit destination is the same in both cases. | R2, L4 |
| **AC4** | The client reads neither status nor body, and does not retry. | R2 |
| **AC5** | No artificial delay on either path. | R2 |
| **AC6** | Every user-facing string resolves from the catalogue. | L11 |
| **AC7** | Keyboard-operable; focus behaviour is deliberate and asserted. | L11 |
| **AC8** | Renders correctly on a **cold arrival** with no preceding submit. | L14, L16 |
| **AC9** | Nothing is written to `sessionStorage` or `localStorage` on this path. | L16 |
| **AC10** | `PUBLIC_ROUTE_PATHS` membership and order unchanged; the element is attached by constant. | L14 |
| **AC11** | `RENDER_SITES` grows to 9 and the barrel scan still passes. | house instrument |

## Required Tests

**Named:** `registrationAcknowledgementIsIdenticalForExistingAndNewEmails` → R2.

**The trap this suite must avoid.** The server returns 202 for both cases, so two mocked responses
that are byte-identical render identically **by construction**. A suite built that way is vacuous —
it would pass against a screen that branched on something else entirely. The cases must drive
**genuinely different** server answers (differing bodies; a non-202 status the contract does not
declare) and assert the rendered result is *still* identical. That is the only shape in which this
test can fail for the right reason.

Cases: (1) new email → acknowledgement; (2) already-registered → **structurally** identical, compared
by element shape not only text; (3) destination identical; (4) a response the client *could* branch on
→ still identical; (5) cold arrival; (6) catalogue provenance — assert rendered text **is** the
catalogue value, since a typed-in literal renders identically; (7) no client storage written —
**must be shown capable of failing**, since T42 shipped a negative storage assertion that was vacuous
from birth because an instance spy on jsdom storage does not intercept; (8) route set unchanged.

## Constraints

- **Security:** public, unauthenticated route. No token, no session, no `auth/` import. No PII to
  storage, logs or analytics. Nothing identifying in the URL.
- **Module boundaries:** `features/account/` must not import `auth/`. Screens compose `app/ui` only;
  `className`/`style` are absent from the primitives' types by design.
- **Null handling:** the screen takes no required props and must render with no router state at all.
- **Performance / timing:** no timer, no `setTimeout`, no artificial delay — R2 forbids it and L4
  makes timing part of the enumeration claim.
- **Thread-safety / transactions:** not applicable (client).
- **House style:** a dense header comment stating what the file does, why, which rule it serves, and
  what it deliberately did not do.

## Open Questions

**OQ-T47-1 — Does the screen render the server's `message`? (Phase 3's primary target.)**
`RegistrationAcknowledgement.message` is a **required** string, so the server supplies user-facing
copy. L11 locks catalogue-only strings. Rendering it puts uncatalogued copy on screen that R2's
"identical copy" cannot police from the client; ignoring it leaves the contract's only response field
unused. `RegisterScreen` already reads no body. **Recommended for challenge, not decided here.**

**OQ-T47-2 — Does `RegisterScreen` navigate, or keep its `StatusRegion`?**
D1 chose in-place announcement *because* the route was blank; that reason expires with this task.
(i) navigate and drop `'sent'`; (ii) navigate and keep the announcement; (iii) fill the route for
direct arrival and leave the form unchanged. This decides whether `RegisterScreen` is touched at all,
and whether AC3 is about a redirect or about nothing. **Blocker for Phase 5.**

**OQ-T47-3 — Should the acknowledgement take focus on mount?**
The I9 guard asserts no screen does, and its comment justifies that with *"none of them is an arrival
from a redirect"*. If OQ-T47-2 resolves to navigation, **this screen is the first that IS such an
arrival**, so the guard's own stated reason does not cover it, and L11's "managed focus across
redirect" may require the opposite. Adding it to the loop unexamined would pin the wrong behaviour.
**Blocker, and it is downstream of OQ-T47-2.**

**Not a blocker, recorded — FU-T47-1.** Baseline for this task is **1991 / 1992**, matching T46's
recorded close. `pnpm` had been missing (Node 26 dropped corepack; the repo pins `pnpm@12.14.0`),
which failed two cases in `src/api/lintRejectsHandWrittenBackendFetch.test.ts` with
`spawn pnpm ENOENT`; `pnpm@12.14.0` was installed and the end-to-end case passes again.

The one remaining failure is **a stale assertion, not a dead gate.** oxlint 1.87.0 emits no summary
line on any run, so lines 118–119 assert strings (`'Found 0 warnings and 0 errors'`, `'with 2 rules'`)
the tool no longer prints. **The L6 rule still fires** — a hand-written backend `fetch` is reported
and exits 1, measured directly. Phase 6 should expect exactly this one failure and must not treat it
as licence for a second. Fixing it is outside T47's mandate.

---

**Phase 2 complete.** Next is **Phase 3 — Design Challenge (Kimi 2.7)**, whose primary target is
OQ-T47-1, with OQ-T47-2 and OQ-T47-3 behind it. **STOP HERE.**
