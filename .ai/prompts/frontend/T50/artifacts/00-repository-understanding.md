<!-- MODEL: Claude Opus 5 — Phase 0 (Repository Understanding). No code written. -->

# frontend · T50 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Task** | T50 — **Resend is public.** *"The resend form works without a session."* Verifies `resendVerificationAcknowledgementIsUniformAndNeedsNoSession`. Cites **R5**. |
| **Consumes** | — (entry phase) |
| **Produces** | `artifacts/00-repository-understanding.md` |

**No code was written in this phase.** Everything below is read from the repository or measured.

---

## 1 · Architecture

The `frontend` is a Vite + React 19 SPA, not a Spring service, so the prompt's persistence/outbox/ArchUnit
headings have no subject here; the equivalents are given instead.

| | |
|---|---|
| **Routing** | `react-router` 8 data router. One table in `app/routes.tsx` under `APP_BASENAME = '/app'`. **Nine public paths** as direct children; eleven authenticated ones under a single pathless `RequireSession` gate (T40). |
| **API** | `openapi-fetch` clients generated from `contracts/api/*.yaml` via `@themistra/api-client`. `createPublicAuthApi()` for `security: []` operations, `createAuthApi()` behind the session boundary (**L6** forbids a bare `fetch`). |
| **Copy** | Every user-facing string from `i18n/catalogue.ts` through `t()` (**L11**); **D7** requires every rendered key to be measured. |
| **UI** | 13 primitives in `app/ui`, with `className` and `style` **absent from the types** (T173). Screens compose primitives only. |
| **Session** | `sessionStorage` is the single carve-out (**L16**); tokens live in memory. |
| **Tests** | Vitest + jsdom; one **named suite per task** from `package.md` §8, plus cross-cutting pins in `app/`. |

## 2 · What already exists, and what is new

### Exists, and **T50 consumes it**

| | |
|---|---|
| `routes.tsx:60` **`RESEND_VERIFICATION_PATH = 'resend-verification'`** | Already declared and **already in `PUBLIC_ROUTE_PATHS`** — but **element-less**. T49 added the constant *and not the element*, with a header saying so. |
| `api/client.ts:61` `createPublicAuthApi(rateLimit?)` | Exactly the client this task needs. The optional rate-limit notice **has no production consumer**. |
| `app/ui/` | `Form`, `EmailField`, `SubmitButton`, `Heading`, `Screen`, `Text`, `StatusRegion`, `TextLink`, `FocusHolder` — **every primitive this task needs already exists.** |
| `features/account/RegisterScreen.tsx` | **The public-form pattern**: local state, `submit()`, `createPublicAuthApi().POST(...)`, `StatusRegion` for the in-flight line. |
| `features/account/RegisterSentScreen.tsx` | **The acknowledgement pattern**, and the single most relevant file in the repository (§3). |
| `contracts/api/auth.yaml:61-78` | `POST /accounts/resend-verification`, **`security: []`**, body `{email}` (`format: email`, required), one response: **`200`, *"Always returned regardless of whether the email matches an account"***, schema `RegistrationAcknowledgement`. |

### New in T50

- A resend screen component, its catalogue keys, and the element attached to the existing path.
- The named suite `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx` — **does not exist**.

### The backend, measured rather than assumed

`AccountController.java:97-103` — `@PostMapping("/resend-verification")`, **`@PreAuthorize("permitAll()")`**, takes
**no `Authentication` parameter**, returns `RegistrationAcknowledgement.standard()`, and its service call is
`resendVerificationIfPending`, which *"never throws for a non-match, so there is nothing here to distinguish
outcomes on."* **The uniformity R5 asks for is already structural on the server side.**

**Q12(a) is closed and it matters to this task.** `package.md:387-393`: backend R6 appeared to require a
signed-in caller, and the resolution was that *"`auth.yaml` and the real code were both already correct"* —
the endpoint is deliberately public and email-identified, and it was **`requirements.md`'s own R6 text that
was stale**. So T50's "works without a session" is not a client-side liberty; it is what the contract and the
server both already are.

## 3 · The pattern that decides this task: **the server's `message` is required and deliberately unread**

`RegisterSentScreen.tsx:22-30` (T47's **D2**):

> **THE CONTRACT'S `message` IS REQUIRED, AND DELIBERATELY UNREAD.** … the server always sends a sentence and
> this client always ignores it. That is not an oversight to be tidied later: L11 locks every user-facing
> string to the catalogue, and a server-supplied sentence has no catalogue key, which means no `t()` call, no
> D7 case measuring it, and no way for the owner to change the words without a server deploy. Rendering it
> would also move R2's guarantee off the client — the identical-copy claim would become the **server's** to
> keep, and the named suite could no longer prove it. **Do not "fix" this by rendering the body.**

**This reasoning transfers to R5 unchanged**, and it is the same conclusion T49 reached from the other
direction: a screen that never reads the response body cannot leak which outcome occurred. `resendVerification`
returns the same `RegistrationAcknowledgement` shape as registration, so the temptation is identical.

Other patterns to follow:

| | |
|---|---|
| **Announcement** | Two established and *different* choices: `RegisterScreen` uses a `StatusRegion` for its in-flight line; `RegisterSentScreen` has **none**, because *"THE FOCUS MOVE IS THE ANNOUNCEMENT"*. T49 added a third: a region mounted **empty** and filled on settle, with any interactive control **outside** it. |
| **Catalogue** | Keys flat within a group (T49 **D-T49-4**: the nested shape *crashes* an existing copy scan). |
| **Navigation** | `RegisterScreen` navigates **absolute and replacing** to a separate ack screen. |
| **Route constants** | L14 attaches by constant, never a literal. |

## 4 · Testing conventions

- One named suite per task, driving the **real route table** through `createMemoryRouter(appRoutes, { basename })`.
- A **per-task mutation harness** under `.ai/prompts/frontend/T<n>/artifacts/07-mutation-harness.py`, with
  declared `CANARIES` and `UNFALSIFIABLE` rows, an asserted baseline, and `git checkout` reverts — **so tests
  must be committed before it runs**, and it runs with the local Docker stack **stopped** (FU-T42-1 is
  load-sensitive).
- Cross-cutting pins in `src/app/`: the public-route set, the `/app`-prefix walker, the design-system render-site
  sweeps and its **catalogue key-set pin** (set equality — **any new key fires it**).
- Baseline today: **2086 / 2086** across 40 files; `oxlint` and `tsc --noEmit` clean.

## 5 · The blast radius is already measured

T49's harness row **TR23** did exactly what this task will do — attach an element to `resend-verification` —
and recorded which files fail:

```
TR23 └ in: ['publicRouteSetIsExactlyTheDeclaredSet', 'routesResolveUnderAppPrefixOnly',
            'verifyFailureShowsOneUniformMessage']
```

So **three files must change, and one of them is a deliberate handoff**:

| File | What T50 must do |
|---|---|
| `routesResolveUnderAppPrefixOnly.test.tsx:18-32` | Insert `'resend-verification'` into `SCREENS_ATTACHED_SO_FAR` **in ROUTE order — sixth, directly after `'verify'`** — not at the end. The list's own comments warn that *"a list in task order would fail while nothing was wrong."* |
| `publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | Fires because the derived probe table's shape changes when a path gains an element. |
| `verifyFailureShowsOneUniformMessage.test.tsx` | **T49's handoff case**, written to fail here, whose message reads *"task 50 has landed, so re-check this screen's link."* **Replace it with an assertion that the resend form renders** — and check `verify.failureAction`'s wording still describes what the visitor now finds. |

Plus, not in TR23 because that mutant added no copy: the **design-system catalogue key-set pin** will fire on
every new key (T49 **D-T49-3**), and the render-site sweeps will pick up the new screen.

## 6 · Known gaps and unknowns

| | |
|---|---|
| **What splits T50 from T51** | They verify **the same named test**. T50 is *"works without a session"*; T51 is *"the ack is identical whatever the outcome."* **I do not know** yet where the line falls — Phase 1's job. The reading I will take in is that T50 owns the screen, its publicness (reachable signed out, no bearer sent) and the ack existing, while T51 owns the *uniformity across outcomes*. |
| **Where the acknowledgement renders** | Registration **navigates** to a separate screen; R5 says *"show the same acknowledgement regardless of outcome."* In place, or a second path? A second path would need a new route (**L14**, and the path is not in `PUBLIC_ROUTE_PATHS`), which argues for in place. Not decided here. |
| **Whether a transport failure must show the ack** | R5 says *"regardless of outcome"*, and a connection that never answered **is** an outcome. `RegisterScreen` currently answers this by going back to `idle` — showing nothing. T49 took the opposite view for verify. This is the sharpest open question and it belongs to T51's half, yet it shapes T50's state machine. |
| **Rate limiting** | `createPublicAuthApi` accepts a notice state that **nothing passes**; a `429` is just another `200`-shaped non-outcome to this screen. Consistent with the repo's thrice-confirmed convention of documenting only success responses (`package.md` Q12(b)). |
| **Email validation** | `format: email` is in the contract. Whether the client validates before posting — and whether rejecting client-side would itself leak an outcome — is undecided. |
| **`RegistrationAcknowledgement.standard()`'s exact sentence** | **I do not know** and deliberately did not look it up as a design input: under D2 this client never renders it. |

---

**Phase 0 complete. Next is Phase 1 — Specification Extraction.**
