<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding). No code written. -->

# frontend · T49 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T49 — Verify failure is uniform |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

> **Verbatim (`tasks.md:83`):** **Verify failure is uniform.** Invalid, expired, used, or deleted-account
> tokens show one uniform failure. Verifies `verifyFailureShowsOneUniformMessage`. Cites R4.

**R4 verbatim (`requirements.md:16-17`):** *IF the verification request fails for any reason, THEN the SPA
SHALL show one uniform failure message **with a neutral resend action**. (backend R5)*

**The task line understates the requirement.** `tasks.md` says "show one uniform failure"; R4 requires a
message **and a neutral resend action**. The action is a control this screen does not have, and the only
route it could point at has no element. That is this task's central design problem, not its message copy.

---

## 1 · Architecture summary

A single-page React application, served at one origin behind the edge, with **no persistence of its own**.

| | |
|---|---|
| **Stack** | React `19.3.1` · react-router `8.4.0` · i18next `26.4.2` / react-i18next `17.0.16` · Tailwind 4 (CSS-first, no config file) · Vitest + jsdom |
| **Backend access** | `@themistra/api-client` (generated from `contracts/api/*.yaml`) wrapped by three factories in `src/api/client.ts`: `createAuthApi(boundary)` (session-bound), **`createPublicAuthApi()`** (`security: []` endpoints), `createOidcApi(boundary)`. L6 forbids a bare `fetch`. |
| **Routing** | One route table, `src/app/routes.tsx`, under basename `/app`. Nine public paths, eleven authenticated behind a pathless `RequireSession` gate and an `AuthenticatedLayout`. |
| **Copy** | Every user-facing string comes from `src/i18n/catalogue.ts` (L11). D7: every rendered key is measured. |
| **Design system** | 13 primitives in `src/app/ui/`. Screens compose primitives only — no lowercase tags, no `className`/`style`, which are absent from the primitives' types. |
| **Storage** | Nothing, except L16's single `sessionStorage` carve-out for the access token and renewal. |
| **Events / outbox / JPA / Flyway / Testcontainers / ArchUnit** | **Not applicable** — those are backend-service concerns. The prompt's phase-0 checklist is the shared one; this service has no persistence layer, no outbox and no `Clock`. |

## 2 · Existing code this task touches

**Nothing here is new. This task adds a branch to a screen that already exists** — which is D1 of T48's
design, decided so task 49 would not have to replace a closed task's file.

| Path | State | What T49 does to it |
|---|---|---|
| `src/features/account/VerifyScreen.tsx` | **Exists** (T48, 88 lines). `status: 'idle' \| 'success'`; one `useEffect` focuses the heading unconditionally, a second POSTs the token once and sets `success` on `response.ok`; the rejection arm `() => {}` swallows transport failures. | Extend the state machine with the failure branch, and render the message + resend action. The `() => {}` arm is where a transport failure must become a failure state. |
| `src/i18n/catalogue.ts` | `verify: { heading, success }` | Add the failure copy and the action's label. **Where those keys live is a design question (see §5.3), because task 54 reuses them.** |
| `src/features/account/verifyFromLinkShowsSuccessState.test.tsx` | **32 cases, all green** | Contains the deliberate handoff: `it('PINS A LIMIT - idle is one rendering for three different situations (task 49 owns this)')` at `:267`, which asserts a rejected token and a tokenless arrival render identically and **says in its own comment that it MUST FAIL when task 49 lands**, and that whoever changes it should *"replace it with the uniform-failure assertions rather than delete it."* |
| `src/app/routes.tsx` | `VERIFY_PATH` declared and attached | **No change needed** — T48 attached the element. |
| `src/features/account/verifyFailureShowsOneUniformMessage.test.tsx` | **Does not exist** | The named suite this task owes (`package.md:108`). |

**Primitives already available, so T49 should need no new one:**

- **`StatusRegion`** — the live region. Renders unconditionally; the consumer passes children *only when there
  is text*, because a live region mounted with content already in it announces nothing. This is how the
  success sentence is announced and is the obvious home for the failure sentence.
- **`TextLink`** — react-router's `<Link>`. Takes `to` and children; **`href` is deliberately not a prop**.
  `SignedOutScreen` is its only production consumer today, which is precedent for "a link back to a route."
- **`Heading`**, **`Screen`**, **`Text`**.
- **`Banner` is not what its name suggests** — it is the `<header>` landmark for the authenticated chrome, not
  a message banner, and its only consumer is `AuthenticatedLayout`. **It is not a candidate here.**

## 3 · Established patterns to follow

| | |
|---|---|
| **Act-on-arrival** | `CallbackScreen` and now `VerifyScreen`: a `handled` ref set **before** the request and **after** the no-work return, because StrictMode invokes effects twice. T48 measured that the ordering matters in both directions. |
| **Error handling** | **openapi-fetch resolves on HTTP errors** and rejects only on transport failure. So an HTTP failure arrives as `{ error, response }` with `response.ok === false`, and the `.then`'s rejection arm sees network faults only. T49 must handle **both** arms, and T48's rejection arm is currently empty. |
| **Enumeration-safe UI (L4)** | The house pattern is one acknowledgement whatever the outcome — `RegisterScreen`/`RegisterSentScreen` (R2, T47) are the worked example, and T47's acknowledgement deliberately **offers nowhere to go** (OQ-T47-4). R4 is the opposite instruction: the failure must carry an action. |
| **Catalogue only** | No literal copy in a screen; `t()` from the catalogue, and every key measured (D7). |
| **The rate-limit notice is dormant** | All three client factories accept an optional `RateLimitNoticeState`, and **no production code passes one** — both production call sites (`RegisterScreen.tsx:95`, `VerifyScreen.tsx:72`) call `createPublicAuthApi()` bare, so `createRateLimitNotice` has no consumer. This matters to R4; see §5.4. |

## 4 · Testing conventions

| | |
|---|---|
| **Named suite** | One file per task named after `package.md` §8 — here `verifyFailureShowsOneUniformMessage`. |
| **Mutation harness** | A per-task copy of `07-mutation-harness.py` with declared expectations per row, a **baseline guard** that refuses to attribute anything if the suite is not green, canary rows scored apart (a surviving canary means the instrument is inert), and rows declared `UNFALSIFIABLE` rather than dressed up as kills. |
| **Commit before mutating** | The harness reverts with `git checkout`, so uncommitted test edits are destroyed silently. |
| **Run the harness with the local Docker stack STOPPED** | FU-T42-1 is load-sensitive. |
| **Per-file runs are mandatory, not ceremony** | T48's Phase 5 measured an unhandled rejection that **fails a whole file while every assertion in it passes**, counted under vitest's `Errors`, and **masked in the whole-suite run** by another file's leaked global stub. `pnpm test` cannot see it. |
| **Current baseline** | 2048 / 2048 across 39 files; `lint` 0; `typecheck` 0. |

## 5 · Known gaps and unknowns

### 5.1 · R4's resend action has nowhere to go until task 50 — **the task's main open question**

R4 requires a **neutral resend action**. The natural target is `/app/resend-verification`, which is a
declared public path (`routes.tsx:64`) with **no element**: tasks **50 and 51** own that form (both cite R5).
A `TextLink to="/resend-verification"` would therefore land the visitor on a blank document today.

This repeats a situation the package has handled twice — T43 wrote a case choosing *against* navigating to "a
declared path with no element", and T47 attached the element and inverted it — so there is precedent for
both naming the condition and for linking anyway. **It is a design decision, not mine to take here.**

### 5.2 · R4's trigger does not literally reach a tokenless arrival

R4 fires *"IF the verification **request** fails."* With no token in the URL there **is no request**, so R4's
condition is not met. T48's **OQ-T48-3** nevertheless decided that *"task 49 will render that state as the
uniform failure state"*, and its `PINS A LIMIT` case is built on that reading.

Rendering the uniform failure there is clearly right **for the visitor** — a malformed or truncated link is
exactly when someone needs "this didn't work, here's how to get another" — and it is enumeration-safe. But it
is an **extension of R4's literal trigger** and should be ratified, not assumed.

### 5.3 · Task 54 reuses this failure, so the copy has a second consumer

`tasks.md:88` — task 54, *"Reset failure is uniform … shows the uniform failure. Cites **R15, R4**"* — and
R15's own text (`requirements.md:50`) says *"On any failure it SHALL show the uniform failure of R4."* So the
sentence T49 writes is **shared copy**, while T48's catalogue group is `verify.*`. Whether the key belongs
under `verify`, or somewhere shared, is a design question with a known second consumer five tasks away.

### 5.4 · A latent hazard to R4's uniformity: the dormant rate-limit notice

R4 says one uniform message **for any reason**. That holds today partly by accident: a `429` reaches
`VerifyScreen` as plain `!response.ok`, because the client is built with no `RateLimitNoticeState`. **If
anyone later wires the notice into the public client, a rate-limited verification would render a
distinguishable notice and R4's uniformity would break** without a single line of this screen changing.
Worth an instrument.

### 5.5 · What the backend actually returns — measured, and it favours this task

| | |
|---|---|
| `AccountExceptionHandler.java:46-52` | **One** mapping for `VerificationTokenRejectedException`: fixed `400`, fixed `ProblemTypes.INVALID_TOKEN`, fixed title, with a javadoc stating it *"never distinguishes 'not found' from 'expired' from 'already used' from 'wrong account state'"* |
| `AccountService.java:38, :46, :47-48` | All three paths throw that same exception — token missing/expired/consumed, account missing, and account not `PENDING_VERIFICATION` (covering already-verified, suspended **and deleted**) |

**All four reasons named in the task line already arrive as one indistinguishable response.** R4's uniformity
is enforced server-side; this task's job is to not undo it.

**`contracts/api/auth.yaml` still declares `'204'` and nothing else for `verifyEmail` (OQ-T48-4).** That is a
contract-accuracy defect, **not a blocker**: the screen branches on `response.ok`, and R4 forbids reading the
error body anyway, since reading `type` or `title` is how uniform failure stops being uniform.

### 5.6 · Carried in from T48

**R-T48-1.** The POST runs on arrival and `markConsumed` is durable, so an email gateway that executes
JavaScript spends the token before the human clicks. **T49 inherits the consequence**: that visitor's account
really is verified, and they will be shown *this task's* failure message — which R4 forbids this task from
explaining. Owner-decided; recorded so this task does not try to solve it with copy.

### 5.7 · What I do not know

- **What "neutral" constrains**, beyond not revealing which of the four reasons occurred. Whether it also
  bars words like "expired" or "invalid" from the message itself is a reading of R4 I cannot settle from the
  spec. **Phase 1's job.**
- **Whether the failure state should keep focus on the heading or move it to the message.** T48 focuses the
  heading on arrival, before any response; the failure arrives later, and nothing in the package says what
  should happen to focus when an awaited result lands. **I do not know.**
- **Whether `tasks.md:83`'s silence on the resend action is deliberate.** It names only the message, while R4
  names both. I cannot tell whether the task line is a summary or a narrowing.

**Phase 0 complete. Next is Phase 1 — Specification Extraction.**
