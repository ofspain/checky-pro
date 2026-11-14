<!-- MODEL: Claude Opus 5 — Phase 13 (PR Preparation). No code. -->

# frontend · T43 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` · **Task** T43 — Registration accepts 12 characters |
| **Consumes** | `artifacts/12-specification-verification.md` (**PASS**) |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12's verdict is **PASS**, so this phase proceeds. The work is already committed and pushed to
`spec/service-specs-and-ai-framework` in seven commits; this artifact is the merge-ready description.

> **Note on the template.** This prompt asks for the trailer `Co-Authored-By: Claude Opus 4.8 (1M context)`.
> That model string is stale — the session's standing instruction is
> `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`, which every commit in this task uses. Flagged
> again here rather than silently followed; the template should be corrected by whoever owns `.ai/`.

## Commit title

```
feat(frontend): registration form with the advisory 12-code-point password check (T43)
```

## Commit message

```
Add the registration screen, the first in features/, and the four form
primitives the design system needed to render it.

The password's length is checked in Unicode code points on submit and the
request is sent either way: R1 makes the check advisory and the server the
authority, and the contract agrees - auth.yaml declares password as a bare
string with no minLength or maxLength, so a client that enforced the bound
would invent a constraint the contract does not carry. Out of range marks the
field aria-invalid and blocks nothing; task 45 owns the words.

Four primitives rather than a screen rendering its own markup, because rule 2
of the design system sets a contract INSIDE a primitive and never accepts it
from a consumer: type="email", type="password", type="submit", the htmlFor/id
pair that names a control, and noValidate are all set inside. The field pair is
the first primitive to render TWO elements - a control's label is part of the
control - carried as a sized exception, not a relaxed rule.

Nothing branches on the response. The contract declares 202 as its only
answer, returned whether or not the address was already registered, so the
screen reports the same thing for 202, 409 and 500 and never reads the body -
including the acknowledgement's own message field, which L11 forbids showing
because no catalogue owns those words. A rejected transport, which is not a
response, returns the form to idle: it claims nothing and stays usable.

Six closed tasks' instruments were grown, and one of them had been wrong since
the day it was written: a vi.spyOn on a storage INSTANCE does not intercept in
jsdom, so task 42's "writes no storage" assertion was asserting that a spy
which never fires had not fired. Both cases now spy on Storage.prototype with
a control that proves the spy is alive.

Suite 1938 passed of 1939 (one documented host oxlint failure), tsc 0,
oxlint 0, mutation harness 26/26 regressions killed with one expected
unfalsifiable survivor.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

## Files changed

**New — 7**

| | |
|---|---|
| `src/app/ui/Form.tsx` | `<form noValidate>`, `preventDefault()` inside, ref → `FORM` |
| `src/app/ui/EmailField.tsx` | `<label htmlFor>` + `<input type="email">`, `useId()` inside, ref → the input |
| `src/app/ui/PasswordField.tsx` | the same shape with `type="password"`, plus `aria-invalid?` |
| `src/app/ui/SubmitButton.tsx` | `<button type="submit">`, `ActionButton`'s tokens, now pinned as a pair |
| `src/features/account/RegisterScreen.tsx` | the screen — **the first file in `features/`**, 43 tasks after the design named the directory |
| `src/features/account/passwordLength.ts` | `countCodePoints` and the two advisory bounds |
| `src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | the named suite, **40 cases** |

**Modified — 11**

| | |
|---|---|
| `src/api/client.ts` | `createPublicAuthApi(rateLimit?)` — no `SessionBoundary`, because `POST /accounts` declares `security: []` |
| `src/app/routes.tsx` | `REGISTER_PATH`, and the screen attached in the existing public `.map()` |
| `src/app/ui/index.ts` | four exports; the Radix paragraph and rule 2 corrected — both had been falsified by this task |
| `src/app/ui/ActionButton.tsx` | the now-false *"there is no `<form>` in this bundle"* sentence corrected |
| `src/i18n/catalogue.ts` | the `register` group, six keys |
| `src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | 13 primitives, 12 bearers, the two-element exception table, 8 render sites, union 21, four utility rows, four ref rows, the `register.*` keys, the button **pair pin**, the copy scan corrected from a substring match, the barrel-import regex widened to any depth |
| `src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | the focus guard grown to four screens; the union pin |
| `src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | five attached screens, `register` third |
| `src/app/routesResolveUnderAppPrefixOnly.test.tsx` | `SCREENS_ATTACHED_SO_FAR` in route order |
| `src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` | T16's credential scan narrowed to a three-row table with a staleness case and a never-excused-shapes case |
| `src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | T42's count guard; **its vacuous storage spy corrected** |

**Artifacts** — `.ai/prompts/frontend/T43/artifacts/06` … `13`, plus `07-mutation-harness.py` (28 rows) and
three run logs.

## Summary

A visitor can create an account. The screen collects an address and a password, counts the password's code
points on submit as advisory guidance, sends it to `POST /accounts` through the generated client with no
bearer, and announces the result in a live region that was mounted before the request so a screen reader
hears it. It does not navigate: `register/sent` is a declared path with no element until task 47, and
navigating there would hand the visitor a blank document.

The design system grew by four primitives because §6e(3) forbids a screen from rendering an element of its
own, and every contract those elements carry — the input types, the submit type, the label association,
`noValidate` — is set inside a primitive where no consumer can override it.

## Testing performed

| | |
|---|---|
| **Named suite** | `registrationAcceptsTwelveAndOneTwentyEightCodePoints` — **40 cases**, AC1–AC11 plus D1/D7, the transport-failure path, and the real-route arrival. The length cases are a **table** so task 44 adds the 128 end as a row |
| **Whole suite** | **1938 passed of 1939.** The one failure is the documented host `oxlint@1.87.0` case that prints no summary line; the same file's end-to-end build gate (a real `pnpm run build`) passes |
| **Types / lint** | `tsc --noEmit` exit 0 · `oxlint src` exit 0 |
| **Mutation harness** | 28 rows, run twice — after phase 10 and again after phase 11's edits. Both: **26/26 regressions killed**, 0 BROKEN, canary killed, one survivor (TR24, `noValidate`, unfalsifiable in jsdom), 0 disagreements. No row flipped and every row's **named killers are identical**; eight rows' failure counts rose, seven from phase 11's new case and TR18's from **FU-T42-1 flaking a second time** (same file, same two cases, one row in 28) — recorded in `11-mutation-run.txt`, because a raw count moves with both and only the named killers are a comparison. The three phase-7 survivors are killed and **each dies to exactly one case** |
| **Reviews** | Kimi 2.7 phase 8 (17 findings) and phase 11 (14). Twelve accepted and fixed, three declined with evidence, three carried to the owner as open questions |

## Specification references

**Task** `spec/frontend/tasks.md:77` (task 43) · **Requirements** `R1` (scoped), with `R2` upheld by the
response-agnostic rendering · **LOCKED decisions** `L2`, `L3`, `L4`, `L6`, `L11`, `L12`, `L13` (conformance
note), `L14` · **Contract** `contracts/api/auth.yaml` — `POST /accounts`, `registerAccount`, `security: []`,
`RegisterAccountRequest`, `RegistrationAcknowledgement` · **Named tests** `package.md:182` (this task's),
`:105` and `:183` (tasks 45 and 46) · **Standing rules** `spec/frontend/agents.md`, unmodified.

## Not in this change, deliberately

Advisory, rejection, strength or hint copy (task 45) · the acknowledgement screen and `register/sent`
(task 47) · the 128-code-point row (task 44) · the counting-unit claim (task 46) · any focus move on
arrival (D5, now enforced) · a state library (D9, L13's note) · `autoComplete` hints (OQ-T43-7) · a
double-submit guard (OQ-T43-11) · a failure sentence (OQ-T43-9).
