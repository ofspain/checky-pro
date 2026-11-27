<!-- MODEL: Claude Opus 5 — Phase 6 (Implementation). -->

# frontend · T48 · Phase 6 — Implementation

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md`, `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation.md` |

```
suite 2042 / 2042  (39 files, was 2016 / 38)   ·   lint 0   ·   typecheck 0
named suite verifyFromLinkShowsSuccessState: 26 cases, green on its first run
every touched file ALSO run individually - see "verification" below
```

---

## What was built

| File | |
|---|---|
| `features/account/VerifyScreen.tsx` | **New.** ~55 lines of header over 30 of code. Two effects: focus unconditionally, verify only with a token. `handled` ref set after the empty-token return and before the request. |
| `features/account/verifyFromLinkShowsSuccessState.test.tsx` | **New, 26 cases.** |
| `app/routes.tsx` | `VERIFY_PATH` added and substituted for the identical literal; the attach branch. |
| `i18n/catalogue.ts` | The `verify` group, with the announcement mechanism and OQ-T48-5 recorded. |
| 5 test files | The pins — see the deviation below. |

## Three things the plan got wrong, and the measurements that corrected them

### 1 · The catalogue step was predicted green and was not

Step 1 was meant to leave the suite at 2016/2016 on the reasoning that *"a key nothing renders yet changes
nothing."* It failed immediately:

```
FAIL screensRenderThroughDesignSystemPrimitivesOnly > keeps the catalogue's key set exactly as it was (AC11)
AssertionError: expected [ 'callback.heading', …(26) ] to deeply equal [ 'callback.heading', …(24) ]
```

The plan had reasoned about the wrong instrument — the one checking every key **is rendered**. This pin is
**set equality against the catalogue itself**, so it fires the moment a key exists, rendered or not, two
steps earlier than expected. The pin was right and the plan was wrong; both are now recorded in that file's
own comment, because the next task to add a screen will make the same mistake otherwise.

### 2 · The blast radius is 5 test files, not 4 — and one was forbidden

**D-T48-1, NEEDS RATIFICATION.** Three files pin `RENDER_SITES`' length and the union **as source text**, by
regex against the other file's bytes:

| File | Pin |
|---|---|
| `app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx:352` | `/\.toBe\(22\)/` |
| `auth/unauthenticatedRoutesRedirectToSignIn.test.tsx:961,963` | `/toHaveLength\(9\)/`, `/\.toBe\(22\)/` |
| **`features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:613,615`** | the same two |

The third is a **T43 registration suite, which the freeze listed under *Files NOT to Modify***. The freeze
said that line was *"a prediction and T47 broke the same one"*, and it broke again — the second task running.
The edits are numbers and their assertion messages only: `9`→`10`, `22`→`23`. No claim changed, and the pins
worked exactly as designed, since a source-text pin **cannot** be satisfied except by editing the file that
holds it.

**This should stop being a surprise.** Two consecutive tasks have found the same class of site after the
freeze was written, which argues the *Files to Modify* list should be derived by running a probe rather than
by reading — which is what Phase 5 now does, and even that probe only surfaced three of the five, because
the other two are only reachable once `RENDER_SITES` actually changes.

### 3 · One required test was aimed at characters that cannot occur

The freeze required *"a token containing `+`, `%` and `/` survives the round trip."* Checked before writing
it: `VerificationTokenService.generateRawToken` (`:189-193`) is
`Base64.getUrlEncoder().withoutPadding().encodeToString(32 bytes)`, so the alphabet is **`A-Za-z0-9-_`** —
no `+`, no `/`, no `=`. A case asserting those characters survive would have been aimed at nothing, which is
how T37 lost a row.

**D-T48-2:** replaced by two cases that do measure something — a round trip of a real base64url token
including `-` and `_`, and a **latent-trap case** pinning what a `+` does today (it decodes to a space,
because `+` means space in a query string). That case passes now and exists so that a change to a
standard-alphabet encoder fails *there*, with the reason attached, rather than in production as an
unexplained verification failure.

## One addition outside the plan

`routesResolveUnderAppPrefixOnly.test.tsx`'s teardown gained `vi.unstubAllGlobals()`. The transport stub
Phase 5 required is the first global stub in that file, and without the unstub it would have outlived its
case and been handed to the nine that follow. **Nothing there detects such a leak**, which is the argument
for closing it rather than against: Phase 5's finding was a masked failure caused by exactly this, in the
other direction. Recorded as hygiene, not as a measured fix.

## Decisions inside the screen

- **`handled.current = true` after the empty-token return, before the request.** Both halves matter and the
  suite holds both: a source-order case asserts the guard precedes `.POST(`, and a StrictMode case asserts
  one request. Set first, a tokenless arrival would latch the guard and a later `params` change carrying a
  real token would do nothing.
- **Two effects.** Focus is unconditional; verifying is not. A case asserts focus moves even with no token,
  which is what keeps them separate.
- **`response.ok`, not `=== 204`.** Phase 7 will put a mutant on this. If nothing can tell the difference
  that is a gap to record, not to paper over.
- **The empty rejection arm is required.** openapi-fetch resolves on an HTTP error and rejects only on
  transport failure, so this arm catches the case that would otherwise be the unhandled rejection Phase 5
  measured failing a whole file silently.

## The limit this task ships, pinned

`idle` is the rendering for **three** situations — in flight, no token, failed — and task 48 cannot tell them
apart, because R4's one uniform failure message is task 49's and inventing its copy would pre-empt it. A case
named *"PINS A LIMIT"* asserts a rejected token and a tokenless arrival render **identically**, and proves
the rejected arm really sent a request so the comparison is not vacuous. **It must fail when task 49 lands**;
its comment says so and says to replace it rather than delete it.

## Verification

```
pnpm test       39 files, 2042 / 2042
lint            0
typecheck       0

each touched file alone (the whole-suite run masks unhandled rejections):
  routesResolveUnderAppPrefixOnly            10/10,  no Errors
  publicRouteSetIsExactlyTheDeclaredSet      49/49
  screensRenderThroughDesignSystemPrimitives 65/65
  homeRouteRendersLandingScreenAndManagesFocus 31/31
  unauthenticatedRoutesRedirectToSignIn      58/58
  registrationAcceptsTwelveAndOne…           45/45
  verifyFromLinkShowsSuccessState            26/26
```

## Deviations

| ID | |
|---|---|
| **D-T48-1** | Five test files touched, not four; one of them a T43 suite the freeze forbade. Numbers and assertion messages only. **Needs ratification at Phase 9.** |
| **D-T48-2** | The freeze's `+`/`%`/`/` round-trip test replaced, because the server cannot issue those characters. Two cases that measure something stand in its place. |
| **D1–D3** | From the freeze, unchanged: the file is shared with task 49; the POST runs on arrival (**R-T48-1**); there is no working-state message. |

**Phase 6 complete. Next is Phase 7 — Self-Review and the mutation harness.**
