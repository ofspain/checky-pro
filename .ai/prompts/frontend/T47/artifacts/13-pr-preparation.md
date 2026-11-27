<!-- MODEL: Claude Opus 5 — Phase 13 (PR Preparation). No code. -->

# frontend · T47 · Phase 13 — PR Preparation

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T47 — Registration acknowledgement is identical |
| **Consumes** | `artifacts/12-specification-verification.md` — **PASS** |
| **Produces** | `artifacts/13-pr-preparation.md` |

Phase 12 returned **PASS**, so this phase proceeds. `main` stays deployable; the work is on
`spec/service-specs-and-ai-framework`, which is already branched off `main`.

> **Trailer note.** The phase prompt asks for a `Claude Opus 4.8 (1M context)` trailer. That name is stale —
> every commit in this task, and the project's standing rule, use `Claude Opus 5`. Writing the prompt's
> literal string would be a false attribution, so the correct trailer is used below. `.ai/` is generated, so
> the fix belongs in `.ai/generate.py`, not in a hand-edit of the prompt.

---

## Commit title

```
feat(frontend): fill register/sent with the registration acknowledgement (T47, R2)
```

## Commit message

```
feat(frontend): fill register/sent with the registration acknowledgement (T47, R2)

Attach a screen to `register/sent`, a public path declared since task 16 that has
carried no element for thirty-one tasks, and navigate the registration form to it.

R2 requires the SPA to show the same "check your email" screen whether or not the
address already exists, with identical copy and layout and no client-side
branching. Keep that by giving the screen NOTHING TO BE IDENTICAL ABOUT: no props,
no router state, no search params, no storage read, and no read of the response.
The server answers 202 either way; the client cannot branch on an answer it never
looks at.

Invert task 43's `does not navigate` case in its own file rather than delete it.
That case chose against navigating because `register/sent` was "a declared path
with no element", and it wrote the condition down. The condition has expired, so
the decision is spent rather than reversed.

Navigate absolutely and with `replace`. Absolutely because a relative
`navigate('register/sent')` resolves against the form's own route and lands on
`/register/register/sent`. With `replace` because Back from the acknowledgement
must not return to a filled form whose resubmission posts the same address again.

Reuse `register.success` instead of writing new copy. Task 43 reserved the key for
this task in the catalogue in writing, two pins name it, and one pins its exact
words; a second pair of keys would have orphaned the string those pins protect.

Leave the contract's required `message` field unread. Rendering server copy would
break the catalogue-only lock (L11), so R2's identical-copy claim stays provable
from the client.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
```

---

## Files changed

**Production (4)**

| File | |
|---|---|
| `frontend/src/features/account/RegisterSentScreen.tsx` | **New.** The acknowledgement: two catalogue strings, focus to the heading, no props. |
| `frontend/src/app/routes.tsx` | `REGISTER_SENT_PATH` added and substituted for the same literal; the attach branch. |
| `frontend/src/features/account/RegisterScreen.tsx` | Navigates on settle; drops the in-place success copy; header rewritten. |
| `frontend/src/i18n/catalogue.ts` | `registerSent.heading`; the `register.success` comment records where it now renders. |

**Tests (9)**

| File | |
|---|---|
| `…/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx` | **New, 23 cases** — the named suite `tasks.md` §8 requires. |
| `…/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | 22 re-aimed sites; task 43's case inverted; a false comment corrected. |
| `…/registrationRejectsPasswordsOutsideTwelveToOneTwentyEightCodePoints.test.tsx` | 13 cases that crashed once navigation landed (**D-T47-6**, ratified). |
| `app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | Four pins: render sites 8→9, union 21→22, catalogue keys, membership. |
| `app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | The I9 focus guard grown by a **reasoned exclusion**, not a fifth entry. |
| `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | Five screens → six. |
| `app/routesResolveUnderAppPrefixOnly.test.tsx` | `register/sent` added to the attached set. |
| `auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | Cross-file counts; a number stale since task 43. |
| `api/lintRejectsHandWrittenBackendFetch.test.ts` | **FU-T47-1**, landed as its own commit (`9c711680`) — see below. |

**Artifacts (18)** — `.ai/prompts/frontend/T47/artifacts/00` … `13`, including four mutation-run logs and the harness.

`31 files changed, 4169 insertions(+), 75 deletions(-)` over `03e78b78..HEAD`.

### FU-T47-1, carried in this branch but not part of T47

`oxlint 1.87.0` prints **no summary line on any run**, clean or failing, so the only end-to-end L6 gate
asserted text that no longer exists. Fixed as its own commit before the pipeline resumed: the case now
asserts **the absence of all output**, which is strictly stronger — `code === 0` passes while *warnings*
print, because oxlint exits 0 for them.

---

## Summary

`register/sent` has been a declared public path with no element since task 16. This task fills it, and in
doing so makes R2's claim — the same acknowledgement whether or not the address exists — provable from the
client rather than asserted of the server.

The design is one sentence: **identical output is a property of having no inputs.** The screen takes no
props, reads no route state, no search params, no storage and no response body, so there is nothing for a
"new address" and an "already registered" journey to differ on. The suite proves that five ways, the
strongest being a comparison between a journey and *no journey at all* — a cold arrival at the URL renders
byte-identical markup to the end of a real submit.

Three things found along the way are worth a reviewer's attention more than the screen is:

1. **A double submit sends two requests** — two verification emails for one registration. Measured, not
   assumed. No task has authorised a guard, so it is **pinned at today's behaviour** (OQ-T43-11) rather than
   quietly fixed or quietly ignored.
2. **A test helper that typed nothing passed all eighteen cases.** The transport is mocked, so an empty form
   still draws a 202 and still navigates. The suite asserted no request body anywhere. Copying a sibling's
   hardened helper without copying the assertions that made it hardened leaves the same hole with better
   code in it.
3. **A comment is the only part of a suite with no instrument.** One said `register/sent` "still carries no
   element"; the assertion beneath it pins *membership* of the path set, which did not change, so it never
   noticed — through four reviews.

## Testing performed

| | |
|---|---|
| Whole suite | **2016 / 2016**, 38 files |
| Named suite | **23 / 23** |
| `oxlint` / `tsc --noEmit` | 0 / 0 |
| Mutation harness | **17/17 regressions killed**, 2/2 canaries dead, `survivors: ['TR15']`, **0 broken**, 1 safe-direction disagreement (`TR14`) |
| Independent verification | Kimi 2.7 **re-ran the harness itself** at Phase 8 and Phase 11 and reproduced the numbers row for row |
| Runs | Harness executed four times across Phases 7, 9, 10 and 11; each run changed something |

**Declared unfalsifiable, by design and recorded at the point of use:** `TR15` (`useEffect` →
`useLayoutEffect` has no observable effect) and the widened `role=` selectors from Kimi's F3 (AC11 forbids
`/<[a-z]/` in that file, so every mutant that would exercise them dies to a different assertion first).

## Specification references

| | |
|---|---|
| **Task** | `spec/frontend/tasks.md` **T47** — registration acknowledgement is identical |
| **Requirement** | **R2** (`requirements.md:12-13`; backend R2) |
| **LOCKED** | **L4** enumeration-safe UI · **L11** accessibility, i18n, managed focus, catalogue-only copy · **L14** SPA route set under `/app` |
| **Design** | **D7** every rendered catalogue key is measured |
| **Deviations** | D1, D2, D3, D4, D-T47-6, D-T47-7 — all ratified; see Phase 9 and Phase 12 |
| **Open for the owner** | **OQ-T43-11** (the double submit — the one real defect), **OQ-T47-4** (an onward link), **OQ-T47-7** (the heading's repeated opening), **FU-T47-2** (closed-task pins changed by their own maintenance tasks), **FU-T43-2** (no shared test utils, recorded a fifth time) |

**Phase 13 complete. T47 is ready for merge.**
