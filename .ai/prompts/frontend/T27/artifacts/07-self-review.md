# frontend · T27 · Phase 7 — Self Review

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T27 — SAS MFA challenge resumes at callback |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/06-implementation-notes.md` |
| **Produces** | `artifacts/07-self-review.md`, `artifacts/07-mutation-harness.py` |

**Mutation score: 22 of 22 killed — 21 by T27's own file, 1 by an inherited case (by design, see S1).**
Harness committed before every run, at `.ai/prompts/frontend/T27/artifacts/07-mutation-harness.py`; run with
`python3 -I .ai/prompts/frontend/T27/artifacts/07-mutation-harness.py [MA<n>]` from the repository root.
Baseline on this host: the nine-file auth set is 757/757 exit 0 (the one known environmental failure lives in
`src/api/` and is outside this harness's file list); T27's own file is 47/47 exit 0.

---

## The mutation table

| Mutant | Verdict | Caught by |
|---|---|---|
| MA01 a one-time-code input beside the button | KILLED | own |
| MA02 a password input | KILLED | own |
| MA03 the screen grows a form | KILLED | own |
| MA04 a recovery-code textarea | KILLED | own |
| MA05 a TOTP verifier ships as a function | KILLED | own |
| MA06 a recovery-code normaliser ships | KILLED | own |
| MA07 an authenticator prompt behind a dormant branch | KILLED | own |
| MA08 the callback navigates elsewhere when `amr` names two factors | KILLED | own |
| MA09 the callback renders different copy when MFA was used | KILLED | own |
| MA10 the callback discards a single-factor sign-in | KILLED | own |
| MA11 an `acr_values` parameter is added | KILLED | own |
| MA12 a `claims` parameter requests an `amr` claim | KILLED | own |
| MA13 a `prompt` parameter steers the SAS flow | KILLED | own |
| MA14 the explanation promises an authenticator step | KILLED | own |
| MA15 a catalogue entry for an MFA failure | KILLED | own |
| MA16 the callback copy names a one-time code | KILLED | own |
| **MA17 a SECOND hand-over through `location.href`** | **KILLED** | **own** |
| MA18 the hand-over moves to `window.open` | KILLED | **INHERITED-ONLY (T16)** |
| MA19 the `inFlight` guard is dropped | KILLED | own |
| MA20 the `amr` claim is written to sessionStorage | KILLED | own |
| MA21 the whole token response is kept | KILLED | own |
| **MA22 written to escape: a computed key behind a dead condition** | **KILLED** | **own** |

**MA17 is the mutant Kimi F1 exists for**, and it is killed by T27's own file. A second navigation through
`location.href` *alongside* the existing `assign` leaves an `assign`-only spy reporting exactly one call. The
counter sees two hand-overs and three cases fail. That single row is the entire justification for rewording
AC1 from the method to the event.

**MA22 was written to escape**, in T26's tradition: an MFA marker stored under a computed key
(`'themistra.auth.' + 'mfa' + '_marker'`), through a computed method name (`'set' + 'Item'`), behind
`navigator.userAgent.includes('ZZZ-NEVER-MATCHES')` — dormant, so no runtime instrument can see it, and
alias-shaped, so a `setItem` text search cannot either. The **identifier scan** catches it, because the thing
it keys on is the *name of the factor*, not the call that stores it. T26 reached the same conclusion from the
other direction and it holds here.

---

## S1 · MA18 is inherited-only, and that is the designed split, not a gap

**Measured directly, not inferred.** Under MA18 (`globalThis.open(url, '_self')` replacing
`location.assign`), T27's own file is **47/47 green** and T16 fails **8 cases**.

T27 passes because it is asserting the right thing. AC1 was deliberately reworded at the Phase 4 gate from
"one `location.assign`" to "exactly one hand-over, counted across every mechanism", and
`window.open(url, '_self')` **is** one full-page navigation to the authorize URL. The count is one; the target
is right; R9 and L2 are satisfied. The mechanism itself is T16's claim — it asserts `location.assign`
specifically, and it fires.

That division is exactly what F1's resolution asked for:

- an **addition** through another mechanism is T27's to catch — MA17, `own`;
- a **replacement** of the mechanism is T16's to pin — MA18, inherited.

**I am not widening T27 to catch MA18.** Doing so would duplicate T16's assertion and would re-couple this
file to one method, which is the coupling F1 was raised to remove. Recorded here so a reader of the table
does not read `INHERITED-ONLY` as a hole.

## S2 · Two defects in my own draft, both surfaced by a control rather than by reasoning

Reported in full in Phase 6 and summarised here because they are the evidence that the controls work:

1. **The sentinel fixture's values are not all reachable the same way.** Two sentinels are ID-token claims
   and appear in the response body only as base64url text; two are envelope fields and appear as plain JSON.
   The "SPA received every sentinel" row failed against a comment three lines below it that said exactly why
   it would — the same distinction T22-D2 records. Split into `ENVELOPE_NEEDLES` and `CLAIM_NEEDLES`.
2. **A shared `g`-flagged regex used with `.test()`, and one regex serving two grammars.** A key is an
   identifier (`signIn.enterTotp`, camelCase, no word boundary); a value is prose (where boundaries stop
   `otp` firing on ordinary words). One pattern cannot be both. Fixing it exposed that the shared `g` regex
   carried `lastIndex` between `.test()` calls — latent, and it had not bitten only because no key matched.

## S3 · A vacuity hole in my own file, found by re-reading against its own header

`the rendered copy on every screen this file drives comes from the catalogue` built a list of rendered
strings and asserted that none was unknown to the catalogue. **If the selector had matched nothing, the list
would be empty, the filter empty, and the case would pass having read nothing** — precisely the shape this
file's header names as the dominant risk, in this file's own case.

**Fixed:** the case now asserts the selector found at least four nodes (heading, explanation, button, status
region) before filtering. The lesson is the uncomfortable one: writing the warning into the header did not
stop me writing the defect forty cases later. Only re-reading each `toEqual([])` against "what if the input
were empty?" found it.

**I audited every other `toEqual([])` in the file for the same shape.** Each is guarded: the source scans by
`expect(targets.length).toBeGreaterThan(0)` and the walker's own refusal; the import scan by
`expect(specifiers.length).toBeGreaterThan(0)`; the manifest by `declaredDependencies()` throwing; the
catalogue by `expect(entries.length).toBeGreaterThan(0)`; the leak sweep by the two cases that prove the
sentinels arrived and that a planted one is found; the identifier scan by the sibling case that holds the
permitted table's counts exactly.

## S4 · The weakest case in the file, named rather than hidden

`names each instrument and the case that proves it fires` (AC11's roll-up) asserts
`instruments).toHaveLength(11)` over a table written in the same file. **It cannot fail for the right
reason** — it is documentation that a reader must delete a row from if they delete a control, not a
measurement. The real proof of each instrument is the control beside it, and those are the eleven rows the
table points at. I kept it because every control in this package that was ever "tidied away" was one nothing
pointed at; but it should be read as a signpost, not as evidence.

## S5 · One field of the symmetry struct is empty for both sides, and the brief should say so plainly

`Outcome.text` is `''` on the success path, because the resumption lands on `/app/home`, which has no element
(T24-D11). Two empty strings compare equal trivially, so **the rendered-text half of AC4 is not earned by
the symmetry rows** — it is earned in the AC9 block, where the holding screen's announcement is compared
across fixtures at the one moment there is text to compare.

This is recorded in a comment on the symmetry case itself and is the first thing an independent reviewer
should attack. My reading is that the struct is still the right unit: the other four fields (destination,
stored keys, request body, live-region count) carry the claim, the blindness control shows all four can
differ, and a comparison unit that silently dropped `text` would be worse than one that carries it empty with
an explanation. But a reviewer may reasonably want the holding-screen comparison folded INTO the symmetry
block rather than sitting in AC9's.

## S6 · What the file states it cannot do, and I have not quietly narrowed

- **No real browser round trip.** jsdom performs no navigation (T23-D5). The hand-over is a counter plus
  structure; `oidcRoundTripEndToEnd` has no suite and no owning task, and remains the package's largest
  verification gap.
- **It cannot see inside the SAS flow**, by R9's own design — which is the point: neither can the SPA.
- **The forbidden-package and identifier lists are enumerative.** A named list cannot prove the absence of a
  library nobody thought of. Stated in the file beside the list, as T26 did for its surfaces.
- **`amr`/`acr` are not in `contracts/api/token-claims.md`** (OQ3). Every fixture claim proves *indifference*
  only; no case asserts any of them is present.

## S7 · Carried forward, none of it T27's to fix

1. **OQ1 — the SAS login page has no `mfaCode` field.** No custom `loginPage`, no templates directory, no
   login template in the repository; SAS serves its default generated page with username and password only,
   while the backend accepts `mfaCode` when it arrives. **A real browser cannot complete an MFA sign-in
   today.** Owner: backend task or an accepted limitation recorded against R9.
2. **T27-D1 — the resumption is silent and strands focus on `<body>`.** Owners: task 135
   (`phase1ScreensPassAccessibilityChecks`, `tasks.md:217`) and the unbuilt `/app/home` screen (T24-D11).
3. **`contracts/api/token-claims.md` is wrong about `email` and `name`** and silent on `amr`/`acr`.
4. **The phase 13 template's stale `Claude Opus 4.8 (1M context)` trailer**, now in a sixth consecutive task.
   The fix belongs in `.ai/generate.py`.

---

## Verification at the close of Phase 7

- `tsc --noEmit` — clean.
- `oxlint` on the new file — clean.
- T27's own file — **47/47**, exit 0.
- Whole suite — **1292 passed / 1 failed (1293)**, the one failure being the known environmental
  `src/api/lintRejectsHandWrittenBackendFetch.test.ts:118`.
- Mutation — **22/22 killed**, 21 by T27's own file.
- Production source — **unchanged**; `git status` clean after every harness run.

**Phase 7 complete.** Ready for the Phase 8 independent review.
