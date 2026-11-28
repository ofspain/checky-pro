<!-- MODEL: Claude Opus 5 — Phase 1 (Specification Extraction). No code written. -->

# frontend · T49 · Phase 1 — Specification Extraction

| | |
|---|---|
| **Consumes** | `artifacts/00-repository-understanding.md` |
| **Produces** | `artifacts/01-specification-extraction.md` |

---

## Business rules

| ID | One sentence |
|---|---|
| **R4** (`requirements.md:16-17`) | If the verification request fails **for any reason**, the SPA shows **one uniform failure message with a neutral resend action**. |
| **R3** (`:14-15`) | T48's success state — in scope only as the branch this task must not disturb, and must not flash past (see AC6). |
| **R15** (`:45-50`) | Not this task, but its text says *"On any failure it SHALL show the uniform failure of R4"*, which makes R4's message **shared copy** with a known second consumer. |
| **R5** (`:19-21`) | Not this task: the resend **form** is tasks 50/51. Relevant only because R4's action must point somewhere, and Q12 confirms that endpoint is deliberately public and email-identified. |

## Locked decisions that constrain this task

| ID | The clause that bites |
|---|---|
| **L4** (`design.md:21-26`) | *"Copy, redirects, styling, **and timing** are identical across unknown or missing, locked, suspended, and deleted accounts and across bad, expired, and used tokens, on every public or self-service surface. No lockout timers."* **This is the task's governing rule**, and the `timing` word matters — see the analysis below. |
| **L11** (`:47-48`) | WCAG AA, keyboard operation, **managed focus**, **live regions for async results**, catalogue-only strings. The failure is an async result, so it belongs in a live region. |
| **L6** (`:38`) | Generated client only. No new client, no bare `fetch`. |
| **L14** (`:53`) | Route paths verbatim in §4c. `/app/verify` is already attached; `resend-verification` is a declared path **with no element**. |
| **L16** (`:58`) | The only `sessionStorage` carve-out — the verification token is not on it, so it is still stored nowhere. **This task renders new DOM, so "not rendered" needs a new instrument.** |
| **L9** (`:44`) | Single origin; nothing here changes it. |
| **L17** (`:61`) | Service-worker boundaries; `/app/verify` is not on the bypass list, unchanged. |
| **D7** | Every rendered catalogue key is measured. |

### What L4's `timing` clause actually requires here — and what it does not

The four reasons the task names (invalid, expired, used, deleted-account) **all take the same backend path**:
one `VerificationTokenRejectedException`, one handler, one fixed `400`
(`AccountExceptionHandler.java:46-52`; `AccountService.java:38, :46, :47-48`). So copy, styling **and timing**
are uniform across those four server-side, and the SPA satisfies L4 for them **as long as it does not branch
on anything in the response.**

A **tokenless arrival** renders its failure immediately, while a rejected token renders it after a round
trip, so those two are distinguishable *by timing*. **That is not an L4 violation**: L4's timing clause exists
to stop an observer inferring **account state**, and a URL with no token names no account, so the difference
leaks nothing about anybody. Worth stating explicitly, because it is the reason §5.2's extension is safe —
not something to paper over.

## Files involved

**Existing, to extend**

| Path | |
|---|---|
| `frontend/src/features/account/VerifyScreen.tsx` | The state machine and the new branch. T48's D1 created this file to be shared. |
| `frontend/src/i18n/catalogue.ts` | The failure sentence and the action's label. |
| `frontend/src/features/account/verifyFromLinkShowsSuccessState.test.tsx` | Its `PINS A LIMIT` case at `:267` **must fail and be replaced**, by its own instruction, not deleted. |
| `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | Render-site and union pins, **if** a new primitive call site appears (a `TextLink` in this screen is one). |

**New**

| Path | |
|---|---|
| `frontend/src/features/account/verifyFailureShowsOneUniformMessage.test.tsx` | The named suite (`package.md:108`). |

**Explicitly NOT changed:** `frontend/src/app/routes.tsx` — T48 attached the element; this task adds no path.

## Dependencies

| | |
|---|---|
| **Contract** | `POST /accounts/verify-email`, `security: []`, body `VerifyEmailRequest { token }`. Declares `'204'` **and nothing else** — OQ-T48-4, a contract-accuracy defect, not a blocker (R4 forbids reading the error body). |
| **Client** | `createPublicAuthApi()` from `src/api/client.ts`. **openapi-fetch resolves on HTTP errors**, so an HTTP failure is `response.ok === false` and the `.then` rejection arm sees **transport failures only** — both arms are in scope. |
| **Primitives** | `Screen`, `Heading`, `StatusRegion`, `Text`, **`TextLink`** (`to`, no `href`). No new primitive is needed; `Banner` is the header landmark, not a message banner. |
| **Backend** | `AccountExceptionHandler`, `AccountService` — read as evidence of uniformity, not called. |
| **Config keys** | None. |

## Acceptance criteria

| AC | Criterion | Rule |
|---|---|---|
| **AC1** | A rejected token (`400`) renders a uniform failure message from the catalogue. | R4 |
| **AC2** | Several distinct failure responses — e.g. `400`, `404`, `410`, `429`, `500`, with and without a problem body — render **byte-identical DOM**. | R4, L4 |
| **AC3** | A **transport failure** (the `.then` rejection arm, which T48 left as `() => {}`) renders the same uniform failure. | R4 |
| **AC4** | The failure state carries a **neutral resend action**. | R4 |
| **AC5** | The action's presence, label and target do not vary by failure reason. | R4, L4 |
| **AC6** | **A valid token never renders the failure copy, not even for one frame.** The in-flight state must be distinct from the failure state. | R3, R4 |
| **AC7** | The failure sentence is announced via `StatusRegion`, which is mounted **before** it has content; focus is managed per the Phase 3/4 decision. | L11 |
| **AC8** | Every new catalogue key renders as literal English and is measured; no untranslated key reaches the DOM. | L11, D7 |
| **AC9** | One call through `createPublicAuthApi()`, no `Authorization` header, no bare `fetch`. | L6 |
| **AC10** | In the **failure** state the token appears in no DOM, no storage and no console. | L16 |
| **AC11** | T48's `PINS A LIMIT` case is **replaced with uniform-failure assertions**, not deleted. | T48 handoff |
| **AC12** | Primitives only; no lowercase tag, no `className`/`style`; the design-system pins updated for any new render site. | design-system rules |
| **AC13** | R4's uniformity does not depend on the rate-limit notice staying dormant: a `429` renders the same uniform failure as a `400`. | R4, L4 |
| **AC14** | Whole suite green and **each touched file green when run alone**. | house rule |

## Tests required

**Named (`package.md` §8):** `verifyFailureShowsOneUniformMessage` → R4.

**Boundary and adversarial cases implied**

| | Why |
|---|---|
| Each of `400 / 404 / 410 / 429 / 500` renders identical DOM | AC2, AC13 — the only honest way to test "uniform for any reason" from the client side, since the backend collapses the four named reasons into one response before the SPA sees them |
| A problem body carrying `type` / `title` / `detail` changes nothing on screen | R4's real risk is a future edit *reading* the body |
| A source scan forbidding reads of `error`, `.title`, `.type`, `.detail`, `.status` | the same risk, structurally |
| Transport rejection (a `fetch` that throws) | AC3 — the arm T48 left empty |
| A valid token: the failure copy is absent throughout the in-flight window, then success appears | **AC6**, the flash |
| A tokenless arrival | AC1 under the §5.2 reading, pending ratification |
| Token in no DOM / storage / console **in the failure state** | AC10, and Kimi's T48 F3 asked for exactly this once this task rendered new DOM |
| The action is reachable by keyboard and has an accessible name | AC4, L11 |
| Catalogue keys as literal English; the key-set pin | AC8 |

## Open questions

| ID | Question | Blocking? |
|---|---|---|
| **OQ-T49-1** | **R4's "neutral resend action" has nowhere to go.** The natural target, `/app/resend-verification`, is a declared path with **no element** until tasks 50/51, so a link there lands on a blank document today. Link anyway and name the condition (the T43 → T47 precedent), or render the action differently? | **Yes — Phase 3/4.** R4 requires the action, so it cannot be silently omitted. |
| **OQ-T49-2** | **R4's trigger does not literally reach a tokenless arrival** — with no token there is no request to fail. T48's OQ-T48-3 decided this task covers it, and its handoff case assumes so. Ratify the extension? | **Yes** — it decides whether a case is a requirement or an invention. |
| **OQ-T49-3** | **Task 54 reuses this copy** (`tasks.md:88`, and R15's own text). T48's group is `verify.*`. Does the shared sentence belong under `verify`, or somewhere neutral this task should create? | No — but deciding it late means task 54 moves a key. |
| **OQ-T49-4** | **What happens to focus when the awaited result lands?** T48 focuses the heading on arrival, before any response. Nothing in the package says whether an async result should move focus; moving it unprompted is itself an accessibility hazard. | No |
| **OQ-T49-5** | **What does "neutral" constrain?** Beyond not revealing which reason occurred, I cannot settle from the spec whether it also bars words like "expired" or "invalid" from the message. | No — Phase 3 should rule. |

### One finding that is not an open question, because it is a defect to avoid rather than a choice

**T48's OQ-T48-3 says "task 49 will render that state as the uniform failure state", and taken literally that
is a bug.** Measured: `VerifyScreen.tsx:61` initialises `status` to `'idle'` and `:78` moves it to `'success'`
**only on the response**, so `idle` is held for the **entire round trip** — and there is deliberately no
`verifying` key (D3). Rendering the uniform failure for `idle` would therefore **flash the failure message at
every successful visitor** before their success arrived.

So this task must **split the state machine** rather than relabel `idle`: an in-flight state that renders no
outcome, a failure state reached only by a failed request or (pending OQ-T49-2) a missing token, and T48's
success state untouched. **AC6 exists to hold that**, and it is the criterion most likely to be satisfied by
accident and broken later.

**Phase 1 complete. Next is Phase 2 — Task Implementation Brief.**
