# frontend · T27 · Phase 6 — Implementation Notes

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T27 — SAS MFA challenge resumes at callback |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/05-implementation-plan.md` |
| **Produces** | `artifacts/06-implementation-notes.md` |

**Built:** `frontend/src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` — **47 cases**, eleven
`describe` blocks, one per acceptance criterion. **No production code changed anywhere.**

**Verification:** `tsc --noEmit` clean; `oxlint` clean on the new file; the file's own run is 47/47; the whole
suite is **1292 passed / 1 failed (1293 collected)**, the one failure being the known environmental
`src/api/lintRejectsHandWrittenBackendFetch.test.ts:118` (this host's `oxlint@1.87.0` prints no summary line).
T26 closed at 1245/1246, so the delta is exactly the 47 new cases and no inherited case broke.

---

## The seven measurements, and what each one changed

The plan front-loaded these because each one decided a design choice rather than confirming one. Five were
planned; two more were added once the first answers made them necessary.

### M1 — can one counter watch all four hand-over mechanisms? **Yes, on a stubbed `location`.**

`assign`, `replace`, the `href` **setter** and `window.open` are all observable once the whole `location`
object is replaced, which is what `vi.stubGlobal` does and what T16 already does. The real jsdom
`location.href` descriptor is `configurable: false` and **cannot** be patched in place — so a counter that
tried to instrument the live object would have silently covered three mechanisms out of four.

**Changed:** AC1 is asserted as a count of *hand-overs*, not of `assign` calls, and the control
(`CONTROL: the counter sees a navigation through any of the four mechanisms`) drives all four so the row above
it means what it says.

### M2 — what does the completed resumption look like? **Nothing, and this is the task's real finding.**

Measured after a successful callback:

| | |
|---|---|
| destination | `/app/home` |
| `container.innerHTML` | `""` |
| live regions in the document | **0** |
| `document.activeElement` | **`document.body`** |
| stored keys | `[]` |

The resumption is **silent and strands focus**, because `/app/home` is registered with no element (T24-D11).
AC9 as drafted in Phase 2 would have required focus management that no module performs, and meeting it means
production work T27 ruled out.

**Changed:** AC9 was already restated at the Phase 4 gate on the strength of this measurement. The
implementation splits it in two: the announcement **while** resuming is real and asserted; the completed
resumption is **pinned** as deviation **T27-D1**, with task 135 and T24-D11 named in the failure message and
an explicit instruction to delete the pin when either lands.

### M3 — does `strip()` leave only the permitted hits? **Yes, and it corrected the Phase 4 table.**

Through the real `strip()` over all 24 production modules, the MFA and `challenge` patterns leave exactly:
`app/routes.tsx` ×1 `mfa`; `auth/authorizeRequest.ts` ×3 `challenge`; `auth/pkce.ts` ×7 `challenge` across
three casings. **Phase 4's table was wrong in two places**, both because it was built from grep over raw
files: `SignInScreen.tsx:12`'s MFA-shaped hit is a comment, and `CallbackScreen.tsx`'s `challenge` is a
comment. Both vanish under stripping.

**Changed:** `PERMITTED_HITS` is counted, not just matched by name, so a fourth `challenge` in a
three-`challenge` file is unexplained — and a separate case fails if a permitted row goes **stale**, because a
table describing a tree that no longer exists is trusted by the next reader.

### M4 — is the response body sweepable? **Yes, and `codeExchange.ts:76` is itself the proof of indifference.**

The request is a real `Request` to `http://localhost:3000/oauth2/token`; the response body is the test's own
fixture, so the sweep is over what the SPA **received**. `codeExchange.ts:76` destructures `id_token` and
`access_token` and **discards every other field** — so `amr`, `acr`, `auth_time`, `mfa_method` and an
`acr_values` echo all arrive and are dropped. That is R9's second half from the SPA's side, visible in one
line of production code.

### M5 — does a second click reach the handler twice? **Only within one `act`.**

Two clicks in one `act` produce exactly one hand-over: the `inFlight` ref works. A **sequential** pair
produces zero further navigations — not because of the guard under test but because `inFlight` is **never
reset on success** by design (`SignInScreen.tsx:39`).

**Changed:** the second-click row drives both clicks inside one `act`, and says why in a comment. A sequential
arrangement would have passed against an implementation with no guard at all.

### M6 (added) — is the symmetry comparison's control genuinely non-blind? **Yes, in four of five fields.**

A wrong `nonce` discards: destination `/app/sign-in`, rich rendered copy, 1 live region, and the three PKCE
items **still in the store** (T23-D2). So the control differs in destination, text, stored keys and live
regions.

**Why this was needed:** M2 showed `text` is `""` on the success path, so the rendered-text half of AC4 would
have compared two empty strings — satisfied trivially. The control establishes that the field can differ, and
a separate case in the AC9 block earns the rendered-text claim at the one moment there is text to compare.

### M7 (added) — is anything announced at all? **Yes, mid-flight.**

With the exchange held open, the holding screen has exactly one live region, `aria-live="polite"`,
`role="status"`, carrying `callback.working`.

**Changed:** this is what makes T27-D1 a *gap* rather than a design. `arriveAndHold` was added so the
announcement can be observed, and a contrast case shows the **refused** resumption announces too — without
it, the pin would read as "resumption is silent by design", which is the wrong lesson.

---

## Two defects in my own first draft, both found by a control

Neither was a production fault. Both were cases that would have passed while proving less than they claimed.

### 1. The sentinel fixture's values are not all reachable the same way

The row asserting "the SPA received every sentinel" failed against a comment **three lines below it** that said
exactly why it would. Two sentinels are **ID-token claims**, so in the response body they appear only as
base64url text; two are **envelope fields** and appear as plain JSON. Asserting all four in one plain-text
search of the body is the same error T22-D2 records.

**Fixed** by splitting them into `ENVELOPE_NEEDLES` and `CLAIM_NEEDLES`: the envelope pair is asserted present
in the body text, the claim pair asserted **absent** from it and present in the decoded claims. The leak sweep
still looks for all four on every surface — only the proof that they *arrived* has to know where each lives.

### 2. A shared `g`-flagged regex used with `.test()`, and one regex doing two grammars

`CONTROL: the catalogue scan fires on the KEY as well as the value` failed on `signIn.enterTotp`, because the
copy pattern uses `\btotp\b` and a camelCase key has no word boundary before `Totp`. **A key is an
identifier; a value is prose.** One regex cannot be both: drop the boundaries and `otp` fires on ordinary
words in copy; keep them and every camelCase key escapes.

Fixing it exposed a second, latent defect: `MFA_IDENTIFIER_PATTERN` was a module-level `g`-flagged constant
used with `.test()` in two places. A `g` regex carries `lastIndex` between calls, so a shared one answers
differently on its second call — a case that passes alone and fails after a neighbour matched, or the reverse.
It had not bitten yet only because no key matched and `lastIndex` stays 0 on failure.

**Fixed** by replacing the constants with `mfaIdentifiers()` / `pkceChallenges()` factories and a non-global
`namesAnMfaIdentifier()`, and by giving `mfaShapedCopy` the two grammars explicitly. Recorded in the file at
both sites so neither is re-merged.

---

## One type fix

`Router` is exported as a **value** (a component) in react-router 8.4.0, so `type Router` is `TS2749`. The
router's type comes from `ReturnType<typeof createMemoryRouter>` — T26's arrangement, now matched.

---

## Deviations from the frozen brief

- **T27-D1 — focus is pinned, not managed.** Declared at the Phase 4 gate and implemented as described above.
  Owners: task 135 (`phase1ScreensPassAccessibilityChecks`, `tasks.md:217`) and the unbuilt `/app/home`
  screen (T24-D11).
- **No other deviation.** All eleven ACs are implemented as frozen; the ten required-test groups are present;
  no production module was touched; `Files NOT to Modify` was respected in full.

## Carried to Phase 7

1. Write and **commit** `artifacts/07-mutation-harness.py` *before* running it — it reverts with
   `git checkout`, which has destroyed uncommitted test work twice in this package.
2. The mutants the brief names, plus the one F1 exists for: **a second hand-over through `location.href`**.
3. The open question the file cannot answer: whether `text` being `""` on the success path weakens AC4 enough
   to want a different comparison unit. My reading is no — the other four fields carry it, and the AC9 block
   earns the text claim where text exists — but it is the first thing an independent reviewer should attack.

---

**Phase 6 complete.** 47 cases, no production change, suite 1292/1293.
