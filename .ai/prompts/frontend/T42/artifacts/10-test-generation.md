<!-- MODEL: Claude Sonnet — Phase 10 (Test Generation). Tests only; no production code changed. -->

# frontend · T42 · Phase 10 — Test Manifest

| | |
|---|---|
| **Service** | `frontend` · **Task** T42 — Authenticated shell |
| **Consumes** | `artifacts/04-freeze-task.md` (AC1–AC15, D1–D11) · `artifacts/09-review-resolution.md` |
| **Produces** | `artifacts/10-test-generation.md` · `10-mutation-run.txt` |
| **Suite** | **1885 passed, 1 failed** (environmental) — 1881 → 1886 cases |
| **Harness** | twelfth copy, **28 rows** · **25/26 regressions killed**, canary killed, TE28 survived, 0 BROKEN · TE21 narrowed and re-run |

No production code was changed in this phase. Five cases were added, all inside
`describe('T42 — the authenticated layout')` in the named suite (D8), and six mutation rows were added to
find out whether they measure anything.

**Three of the five pin a limit rather than a feature.** A limit that is only described is indistinguishable
from an oversight six months later, and two of this package's three open questions exist because someone
wrote a sentence where a case belonged.

---

## The named test

`package.md` §8 names `unauthenticatedRoutesRedirectToSignIn` for R28. Task 41 owns the file; task 42
extends it under its own `describe` (D8), because *"a failure naming AC1 must say whose AC1 it is"* — the
collision task 40's phase 12 found after the fact.

| | case | verifies |
|---|---|---|
| 1 | AC1 renders the layout on every one of the eleven, including the two parameterised ones | **AC1** · R28's positive clause |
| 2 | AC2 nests the home screen INSIDE the layout rather than replacing it | **AC2** · L12 |
| 3 | AC3 leaves focus on the screen's heading, which the chrome must not take | **AC3** · L11, T27-D1 |
| 4 | AC8 renders exactly one h1, and takes no focus where there is no screen at all | **AC8** · L11 |
| 5 | AC9 is a `<header>` banner outside every landmark that would demote it | **AC9** · L11 |
| 6 | AC12 holds one keyboard-reachable control, labelled from the catalogue | **AC12** · R19 |
| 7 | AC13 hands over to SAS exactly once when the control is activated, through endSession | **AC13** · R19, R57 |
| 8 | AC13 reaches for none of the pieces endSession composes, by source | **AC13** · **AC10**'s amended clause · phase 8 I4 |
| 9 | CONTROL: the forbidden sweep above sees a planted reach, and does not already trip | the instrument in case 8 |
| 10 | AC14 signs this tab out and redirects it when a SIBLING tab signs out | **AC14** · R22's last clause |
| 11 | AC14 closes the listener on unmount, so an unmounted layout signs nothing out | **AC14** |
| 12 | AC4, AC15 renders no part of the chrome without a session, nor for an empty-string token | **AC4**, **AC15** · R28, T40 |
| 13 | AC5 adds no path: the layout is pathless and the two declared sets are untouched | **AC5** · L14 |
| 14 | AC6, AC10 says one word, takes it from the catalogue, and says nothing about who is reading | **AC6**, **AC10** · L11, R20, R22 |
| 15 | AC11 writes no storage and makes no network call while rendering | **AC11** · R29, L16 |
| 16 | AC7 names the layout and the ninth primitive in the lists that pin them | **AC7** · T173 §6e |
| 17 | OQ-T36-5: the end-session suite names this layout as its first permitted importer | **OQ-T36-5** (split out in phase 9, I8) |
| **18** | **AC14 under StrictMode: the sibling journey still works when the effect is double-invoked** | **AC14** · phase 8 **I5** |
| **19** | **AC14 leaves the return target pointing at where THIS tab was when the sibling signed out** | **AC14** · **D11**'s accepted consequence |
| **20** | **A TAB ON A PUBLIC PATH DOES NOT REACT to a sibling signing out, and that is a measured limit** | **OQ-T42-5** (new) · R22's reach |
| **21** | **the banner declares no utility that could clip the focus ring the primitives pin** | **AC9**'s ring clause · phase 8 **I6** |
| **22** | **the banner and the screen share one content column, which was a coincidence of two literals** | **AC9**/AC7's visual claim · phase 7 **F4**, phase 8 **I9** |

Rows 18–22 are this phase's. Rows 1–17 are phase 6's and phase 9's, listed so the mapping is complete.

**One AC is verified in its amended form, and phase 12 should know which.** **AC10**'s last clause reads
*"no `auth/` import"* (`02-task-implementation-brief.md:156`) — written before the owner closed OQ-T42-2 and
superseded by **D9**, which names the layout's import list as three auth modules and one `api` module and
makes the AC13 source allowlist the enforcement. Case 8 therefore asserts **exactly that list** and the
absence of every identity read, which is AC10's substance; the literal clause it replaces cannot be
satisfied by any layout that installs a cross-tab listener. The same stale wording is what phase 8's I2 and
I3 read as current, and the disposition is in `09-review-resolution.md`.

## The nine grown instruments, and what each holds for task 42

| instrument | task 42's claim in it |
|---|---|
| `publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | the probe walk descends **through** the layout and counts exactly one; the element list is `[RedirectToSignIn, RequireSession, AuthenticatedLayout]` |
| `routesResolveUnderAppPrefixOnly.test.tsx` | the path tree grows one pathless level inside the gate; `SCREENS_ATTACHED_SO_FAR` is untouched (AC5) |
| `returnTargetOutsideAllowlistFallsBackToHome.test.tsx` | the matched chain gains the intermediate level |
| `screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | nine primitives, eight tag bearers, seven render sites, union sixteen, `Banner`'s utilities and its `HEADER` ref target, `shell.signOut` in the key set (AC7) |
| `homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | the union pin at sixteen |
| `signOutClearsMemoryAndEndsSasSession.test.tsx` | `PERMITTED_IMPORTERS` names the layout — T36 wrote this list **empty**, for whichever task first called the sign-out path |
| `identityErrorsIndistinguishableAcrossAccountStates.test.tsx` | the signed-in document's text now begins with the chrome's label |
| `sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx` | the same |
| `loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` | AC3 — the discharged T27-D1 case passes unchanged **through** the new layer |

## The six rows added to measure rows 18–22

| row | mutant | predicts |
|---|---|---|
| **TE23** | the listener is hoisted to module scope, so it is not per-mount | `own` |
| **TE24** | the public routes are wrapped in the layout too | `own` |
| **TE25** | the banner gains `overflow-hidden`, which clips the control's focus ring | `own` |
| **TE26** | `Screen`'s content column is widened, so the chrome no longer lines up | `inherited` |
| **TE27** | `Screen`'s **gutter** changes, so the chrome and the screen have different edges | `own` |
| **TE28** | the receiving tab sets the flag **before** it signs out | `own` |

**TE23 cannot isolate StrictMode and the row says so.** Module state outlives every case in a file, so the
first `release()` makes every later mount deaf and the non-strict journey dies beside the strict one. The
regression is real; the uniqueness is not claimed. T174 hit the same limit (its F2) and recorded it rather
than inventing a row that looked sharper than it was.

**TE26 and TE27 exist to test a finding, not the code.** Writing case 22 meant reading T173's
`REQUIRED_UTILITIES`, and it pins `max-w-prose` on **`Screen`** and on **`Banner`** — two rows, one of which
phase 6 wrote with the comment *"`Screen`'s row above is the other half of it"*. If that is so, then the
drift phase 7's F4 and phase 8's I9 both described — *change one and nothing fails* — **was impossible before
either of them said it**, and the real hole is the **gutter** (`px-6`), which neither row names. TE26 widened
and TE27 nudged are the two halves of that question. A finding I wrote, a reviewer confirmed, and the
instrument contradicts is worth more than either of us agreeing.

**TE28 is a question in row form.** The layout's comment says the order is deliberate — *"`signOut()` first,
so this tab is clean before anything re-renders"*. Case 19 measures the net effect on the return target, so
the row is predicted `own`; a survivor would say the order claim is undefended, which is what D11 would then
have to carry as an open question instead of a sentence.

## What the six rows actually measured

**Five of the six died, and four of the five name the new case as the killer** — TE25 and TE27 with exactly
one new failure each in 1886 cases, which is a mutant isolating the hole it records. TE23 killed the strict
case, the non-strict journey and the return-target case together, exactly as the row said it would and no
more sharply. TE24 fails 29 cases across five files, which is what wrapping nine public routes in a layout
should look like.

### TE26 answers the F4 / I9 question, and the answer is "both, mislocated"

TE26 — `Screen`'s column widened — is killed by **two** cases: phase 10's new pair case **and** T173's
*"gives `Screen` the colour and type utilities it owns"*. So the drift **was** already caught on the
`max-w-prose` half, which is what reading the instrument suggested; F4 and I9 were both wrong about that.
**TE27 — the gutter — is killed by the new case and nothing else.** The hole was real and it was one
utility to the left of where two reviews put it. Predicting `inherited` for TE26 and `own` for TE27 was the
one way to find that out, and TE26's `own` verdict is the third instance of the collision in this run's
disagreement list, not a surprise.

### TE21 was killed, the kill was right, and the ROW was wrong

TE21 had survived at 22 rows and at 19 rows: the banner's padding is *"deliberately unpinned"* (T173's
decision — *"pinning the whole literal would turn every padding adjustment into a test edit"*). At 28 rows
it died, on phase 10's own new case, because that case pins **`px-6`** on both `Banner` and `Screen`.

That is not an instrument overreaching. The gutter is **half of the shared-column claim** — a chrome and a
screen with different horizontal edges do not line up — so it is now pinned deliberately, narrowly (three
utilities, not the literal) and **on the pair**, which means a gutter change has to move both files
together. What T173's decision was actually about is the **vertical** padding, and nothing pins that.

So the row was re-aimed at `pt-6` and re-run: **it survives, as predicted.** Recorded rather than quietly
edited, because a row whose prediction flips after a phase added a case is exactly the kind of change that
can be used to launder a score — and the appended re-run in `10-mutation-run.txt` carries its own header
saying what moved and why.

### TE28 survived: the layout's ordering comment claims something nothing requires

The row inverts the receiving tab's two statements — `setSiblingSignedOut(true)` before `signOut()` — and
**nothing in 1886 cases detects it.** The prediction was `own` and it was wrong, and the reason is worth more
than the row: React batches the state update, so **both statements run to completion before any re-render
regardless of their order**. The comment at `AuthenticatedLayout.tsx:62-66` says `signOut()` goes first
*"so this tab is clean before anything re-renders"* — which is true, and would be just as true the other way
round. It reads as a criterion and it is not one. `endSession()`'s own ordering **is** a criterion
(`endSession.ts:111-113`: a message posted and then closed on the navigation is a message nobody receives),
and this comment borrows that weight without earning it.

**No test can fix this, which is why it is not this phase's to fix.** Phase 10 changes no production code,
so the correction is recorded here and in the phase 11 input: the comment should say that the order is
*conventional* — local state cleared before the flag that reveals it — and that nothing depends on it. The
row stays predicted `own` and reported as a disagreement; relabelling it `unfalsifiable` after watching it
survive is how a harness stops measuring.

## What is still not measured, after this phase

- **A real second tab.** jsdom has one `BroadcastChannel` realm. Rows 10, 11, 18, 19 and 20 post from a
  channel in the same realm, which is what task 39's own suite does.
- **A real Tab keypress** (OQ-T174-5, inherited). AC9's keyboard reach is `tabindex` and tag, not a keypress.
- **Pixels.** AC7's utility pins hold tokens and the content column; nothing renders a layout and looks at it.
  Case 21 forbids the one utility that can take a ring away, which is a source claim about a visual effect.
- **StrictMode-specific idempotence**, as distinct from working at all (TE23's limit above).
- **The ten empty paths' future.** Rows 1 and 4 assert the chrome renders and takes no focus on a path with
  no screen; nothing says what should be there instead, because nothing should be yet (I11).
