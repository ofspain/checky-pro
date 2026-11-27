# frontend · T47 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Task** | T47 — Registration acknowledgement is identical |
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produced** | 2026-11-27 |

No design, no implementation. Sources: `requirements.md`, `design.md`, `package.md`, `tasks.md`,
`agents.md`, `contracts/api/auth.yaml`. Nothing under `spec/` was modified.

---

## Business Rules

| ID | Rule |
|---|---|
| **R2** *(primary)* | When the registration request returns, the SPA shows the same "check your email" screen whether or not the email already exists, with **identical copy and layout**, and adds **no client-side branching or delay**. (backend R2) |
| R1 *(context only)* | The 12–128 code-point password check is advisory; the server is the authority. Already shipped by T43–T46. **T47 must not re-prove it.** |
| R3 *(adjacent, not this task)* | A valid verification link shows the verified success state — task 48 owns `verify`. |
| R5 *(adjacent, not this task)* | The resend-verification form is public and uniformly acknowledged — tasks 50/51 own `resend-verification`. |

R2 is one sentence with **four** separable obligations, and they are not the same claim:
**(a)** a "check your email" screen exists; **(b)** its copy is identical across both cases;
**(c)** its **layout** is identical across both cases; **(d)** the client adds no branching and no delay.

## Locked Decisions

| ID | Constraint on this task |
|---|---|
| **L14** `[ALL]` | The public route set is LOCKED and names **"registration, *its acknowledgement*"**. `register/sent` is therefore a *required* public route, not an optional one. A new public route needs an ADR — **T47 adds none; it fills one that is already declared.** |
| **L4** `[ALL]` | Enumeration-safe UI: **copy, redirects, styling and timing** identical across account states. The word *redirects* is load-bearing here — two different destinations would breach L4 even with identical words. |
| **L11** `[ALL]` | WCAG AA, keyboard operation, managed focus, live regions for async results, **catalogue-only strings**. |
| **L16** `[ALL]` | sessionStorage may hold only PKCE/return-target and R29 mid-task form input, and **"no PII is stored in either case"**. An email address is PII ⇒ **the submitted address may not be persisted to carry it to the acknowledgement.** |
| **L6** `[ALL]` | Generated client only (`@themistra/api-client`). No hand-written `fetch`. |
| **L13** `[ALL]` | Confirmed stack. TanStack Query and Zustand remain uninstalled; introducing either needs a real consumer, not this task. |
| **L10 / L17** | No secrets in the bundle; service-worker navigation boundaries unchanged by this task. |

## Files involved

**Existing — to read, and possibly extend**

| Path | Why |
|---|---|
| `frontend/src/app/routes.tsx` | `'register/sent'` is already in `PUBLIC_ROUTE_PATHS` with **no element**. T47 attaches one by matching a constant, never by adding a literal (L14 house rule). A `REGISTER_SENT_PATH` constant does not yet exist. |
| `frontend/src/features/account/RegisterScreen.tsx` | Holds the `'sent'` status and the `StatusRegion`. Whether it changes is the open design question below. |
| `frontend/src/i18n/catalogue.ts` | `register.*` currently holds six keys including `success`, whose comment reserves this claim for T47. |
| `frontend/src/app/ui/` | `Screen`, `Heading`, `Text`, `StatusRegion`, `TextLink` all exist. **No new primitive should be needed.** |
| T44 / T45 suites | Must keep passing; must not be re-proved. |

**New — this task's**

| Path | Role |
|---|---|
| `frontend/src/features/account/RegisterSentScreen.tsx` *(name provisional)* | The acknowledgement screen. |
| `frontend/src/features/account/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx` | The named suite (`package.md` §8). |
| Catalogue keys under `register.*` or a sibling namespace | Subject to the D7 question below. |

## Dependencies

- **Contract `contracts/api/auth.yaml` → `POST /accounts`** — `security: []` (public), request
  `RegisterAccountRequest {email, password}`, and **exactly one response: `202`**, documented
  *"Always returned regardless of whether the email was already registered (enumeration-safe)."*
  There is no 409 and no error response in the contract at all.
- **Schema `RegistrationAcknowledgement`** — `{ message: string }`, `message` **required**. See OQ-T47-1.
- `createPublicAuthApi()` (`frontend/src/api/client`) — already used by `RegisterScreen`.
- `REGISTER_PATH`, `PUBLIC_ROUTE_PATHS`, `APP_BASENAME` from `app/routes`.
- `createI18n()` + `en` catalogue for the suite.
- **No new runtime dependency.** No store, no query client, no new primitive.

## Acceptance Criteria

| AC | Criterion | Requirement |
|---|---|---|
| **AC1** | `/app/register/sent` renders a non-empty acknowledgement screen — a real element, not a blank document. | R2(a), L14 |
| **AC2** | The acknowledgement renders **identically** for a submit with a brand-new email and one already registered: same rendered text, same element structure. | R2(b), R2(c), L4 |
| **AC3** | The destination after a successful submit is the **same** in both cases. | R2(d), L4 ("redirects") |
| **AC4** | The client performs **no branching on the response** — no status read, no body read, no retry. | R2(d) |
| **AC5** | The client adds **no delay** on either path. | R2(d) |
| **AC6** | Every user-facing string on the screen comes from the catalogue. | L11 |
| **AC7** | The screen is keyboard-operable and announces itself per L11; focus is managed on arrival. | L11 |
| **AC8** | The screen renders correctly on a **cold arrival** — a deep link or refresh with no preceding submit and no in-memory state. | L14 (it is a public route), L16 |
| **AC9** | No PII is written to any client-side storage on this path. | L16 |
| **AC10** | `register/sent` is attached by matching an exported constant; the public route **set** is unchanged in membership and order. | L14 |

## Tests required

**Named (`package.md` §8, Phase 1a):** `registrationAcknowledgementIsIdenticalForExistingAndNewEmails` → R2.

The name says "for existing and new emails", and the honest difficulty is this: **the client cannot
tell the two apart, because the server returns 202 for both.** A naive suite would mock two
responses that are byte-identical and assert they render the same — which is a tautology that
would pass against a screen that branched on something else entirely. The suite must therefore
discriminate by **driving two genuinely different server answers** (for example 202-with-one-body
vs 202-with-another, and the 409 a less careful backend might send) and asserting the rendered
result is *still* identical. That is the only shape in which this test can fail for the right reason.

Boundary cases implied:
1. New email → acknowledgement. (AC1, AC2)
2. Already-registered email → **identical** acknowledgement, compared structurally, not just by text. (AC2)
3. Destination identical across both. (AC3)
4. A response the client *could* branch on (different body; non-202 status) → still identical. (AC4)
5. Cold arrival at `/app/register/sent` with no prior submit. (AC8)
6. Strings resolve from the catalogue, not literals. (AC6)
7. Nothing written to `sessionStorage`/`localStorage` on this path. (AC9) — **note T42's lesson:
   an instance spy on jsdom storage does not intercept, and a NEGATIVE assertion can be vacuous
   from birth. This case must prove it can fail.**
8. Route-set membership and order unchanged. (AC10)

## Open Questions

**OQ-T47-1 — Does the acknowledgement render the server's `message`?** *(genuine, and the sharpest)*
`RegistrationAcknowledgement` declares `message` as a **required** string, so the server supplies
user-facing text. L11 LOCKS "catalogue-only strings". Rendering the server's message would put
copy on screen that no catalogue owns and that R2's "identical copy" cannot police from the client;
ignoring it means the contract's only response field is unused. `RegisterScreen` today already
"does not read the body". **Both readings are defensible and this is Phase 3's target.** Recording
rather than resolving, per the guardrail.

**OQ-T47-2 — Does `RegisterScreen` navigate, or keep its `StatusRegion`?**
T43's Phase 3 settled its own scope with: *"Task 47 will later replace or extend this behavior with
the `register/sent` acknowledgement"* — explicitly deferring the choice here. L14 requires the route
to exist and be filled (AC1). It does **not** state that the form must navigate to it. Three readings:
**(i)** navigate and drop the `'sent'` status; **(ii)** navigate and keep the live-region announcement
for the interim; **(iii)** fill the route for direct arrival but leave the form announcing in place.
This decides whether T47 modifies `RegisterScreen` at all. **Phase 2 proposes; Phase 3 challenges.**

**OQ-T47-3 — May the acknowledgement name the address it was sent to?**
"Check your email" screens commonly echo the address. L16 forbids persisting PII, so it could only
arrive as in-memory router state — which is absent on a cold arrival (AC8), producing *two different
layouts* for the same route and breaching R2(c). The conservative reading is that the screen never
names the address. Flagged because it is the kind of thing a reviewer adds later without noticing
it reintroduces the variance R2 removes.

**OQ-T47-4 — Is D7's six-key freeze on `register.*` still binding?**
`catalogue.ts` records that T43's D7 "froze this screen's key set at six". An acknowledgement screen
plausibly needs a heading and body. Whether D7 scoped `RegisterScreen` only, or `register.*` as a
namespace, is not stated where I can read it: **grep over `.ai/prompts/frontend/T43/artifacts/` found
no `D7` entry in the frozen brief.** I do not know, and Phase 2 must locate D7 in T43's Phase 4
artifact and quote it rather than infer it from a downstream comment.

### Not blockers, recorded

- **`package.md` §11 holds no open question touching R2.** Q6 (route set) is resolved as proposed,
  which is what makes L14's acknowledgement route binding.
- **Environmental, outside the spec — resolved in part, and an earlier claim here was wrong.**
  First measurement 2026-11-27: **1990 / 1992**, both failures in
  `src/api/lintRejectsHandWrittenBackendFetch.test.ts` with `spawn pnpm ENOENT`, because `pnpm` was
  absent (the repo pins `pnpm@12.14.0` via `packageManager`; Node 26 removed corepack, which used to
  supply it). `pnpm@12.14.0` was then installed, and the end-to-end case passes again.

  **Baseline for this task is 1992 / 1992** — fully green, lint and typecheck clean. It was briefly
  1991 / 1992 (matching T46's recorded close) until the remaining failure was fixed; see below.

  The one remaining failure is **a stale assertion, not a dead gate**, and the distinction matters
  because this file is the only instrument enforcing L6. Measured directly: **oxlint 1.87.0 prints no
  summary line at all** — not on a clean run, and not even on a failing one — so
  `expect(output).toContain('Found 0 warnings and 0 errors')` (line 118) and `'with 2 rules'`
  (line 119) assert strings the tool no longer emits. **The L6 rule itself still fires**: a
  hand-written `fetch` to a backend route produces
  `error eslint(no-restricted-globals) ... Call the backend through @themistra/api-client (L6)` and
  exit 1. So the protection was intact and only the assertion about the tool's chatter was stale.
  Recorded as **FU-T47-1** and then **fixed as its own change outside this pipeline** (`9c711680`),
  at the author's instruction — not inside T47, which has no mandate over that file.

---

**Phase 1 complete.** One artifact written. Next is Phase 2 (Task Implementation Brief, Claude Sonnet),
which proposes answers to OQ-T47-2 and OQ-T47-3, must quote D7 for OQ-T47-4, and hands OQ-T47-1 to
Phase 3 as its primary target.
