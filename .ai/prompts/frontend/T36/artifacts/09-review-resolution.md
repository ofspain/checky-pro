<!-- MODEL: Claude Opus 5 — Phase 9 (Review Resolution) -->

# frontend · T36 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T36 — Sign-out ends the SAS session |
| **Consumes** | `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

Thirteen findings. **Five accepted and implemented**, **one accepted as a decision**, **two rejected on
evidence**, **five recorded with no action**. Every citation was checked against source first; two of Kimi's
premises were wrong, and checking them changed what I did about them.

## Score after resolution

```
regressions: 28/28 killed     (26 → 28; ES27 and ES28 added by this phase)
survivors:   none
broken:      none
```

| | |
|---|---|
| own suite | **79 passed (79)** — was 76 |
| whole suite | **1657 passed / 1 failed of 1658** — the known `oxlint` host case only |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |

---

## I3 — ACCEPTED. The real find, and it was a genuine survivor.

**Citation confirmed.** AC10's scan is `file.code.includes(END_SESSION_PATH)`. A module that writes
`import { END_SESSION_PATH } from './endSession'` and then navigates to the *variable* contains no literal
`/connect/logout`, so the scan passes it. The export is the bypass — and the export exists because AC16
compares the constant against the service-worker denylist, so it cannot simply be removed.

Kimi offers two remedies. **(b) is rejected**: keeping the constant internal and comparing literals in the
test would make the suite spell `/connect/logout` itself, and this file's header commits to the opposite —
"every needle is built at runtime from the imported production constants, so a rename breaks these cases
instead of passing them." **(a) is accepted**, as a scan over who imports `auth/endSession.ts` at all, with
`PERMITTED_IMPORTERS` empty and a failure message that hands the next author the right instruction: if you are
wiring the affordance, add yourself here and close OQ-T36-5.

**Measured, not assumed.** `ES27` plants exactly that bypass in `AppShell.tsx`. Run against the code **without**
the new scan it **SURVIVED** — `0/1 killed`. With the scan it dies, `own`, 2 failures. That is the only kind of
evidence that settles whether a finding was real.

Its control checks the needle against *both* import spellings (`'./endSession'` and `'../auth/endSession'`),
because a scan matching only the same-directory form would pass over precisely the sibling-directory module
most likely to add a sign-out button.

## I7 — remedy ACCEPTED, premise CORRECTED.

I7 says `document.title`, `window.name` and `history.state` are "the same seven surfaces T35's AC6 covers".
**They are not.** Measured: `grep -c 'document.title\|window.name\|history.state'` over that file returned
**0** before this phase. T35's AC6 sweeps storage operations and six console channels, and nothing else.

So this is not a parity gap between T36 and T35 — it is a gap **both** tasks had. The remedy is worth taking
anyway, and for a reason the finding does not give: **`window.name` survives a cross-origin navigation**, so a
token written there would outlive the very hand-over this module performs. The scan now covers all three in
`idToken.ts` and `endSession.ts`, and `ES28` proves it: `window.name = hint ?? ''` leaves every behavioural
case passing because the URL is still correct, and dies on the source scan.

Not extended to T35's modules: that is an edit to a closed task for a gap T36 did not introduce. Recorded as
**FU-T36-1**.

## I8 — ACCEPTED as a wording fix, not a wider scan.

Correct that `/https?:\/\/.../` sees neither a protocol-relative `//example.com` nor an exotic scheme. The
criterion is **narrowed to what the scan measures** — "spells no http or https host" — rather than the scan
being widened. Widening is the wrong trade here: a `//` needle over stripped source fires on a regex literal
and on a template expression, so it would need an exclusion list, and a scan with an exclusion list for false
positives is a scan that has stopped meaning anything. T35 learned the same lesson narrowing its `catch` ban
to `catch (`.

## I9 — ACCEPTED. Cheap, and it pins a different step than AC1 does.

AC1 pins a throwing `assign`. The URL is built as the *argument* to `assign`, so a throw inside
`URLSearchParams` happens strictly earlier — after `signOut()`, before the browser is asked to go anywhere.
Correct by construction; now also by measurement.

## I10 — ACCEPTED in the cheap form.

The two basename cases are renamed to begin **`URL DERIVATION ONLY:`**, so the limit is in the test name a
reader sees in a failure report rather than only in a comment they may not open.

## I6 — ACCEPTED, as the finding asks, and the reasoning recorded.

The `SIGN_OUT_SET` strengthening edits T35's test logic, and it stays. The justification is the one Kimi
states and it is the right one: the hardcoded set **had become false**. T35's case described itself as
guarding "the whole sign-out set" while measuring a frozen four, so a second module permitted to call all five
would have assembled a second sign-out one allowlist entry at a time and passed.

The line T36 draws between a permitted and a forbidden edit to a closed task's tests:

- **permitted** when the instrument has become *false* (this case) or must grow to admit new state
  (`INVENTORY`, `PERMITTED_CALLERS`, `DEFINERS`);
- **forbidden** when the instrument is merely *incomplete for something the new task owns* — which is why I2
  is rejected below.

## I2 — REJECTED. A duplicated assertion is a liability, not a second guard.

T19's `:1019` asserts `retainAccessToken` comes after the nonce check. I2 wants a mirror case for
`retainIdToken` in that same closed suite.

Checked what protection already exists: T36's AC14 pins the order structurally, pins the early return between
the two, has a control that catches a hoist, and asserts the retention appears in exactly one production
module. If a future refactor removes the line, AC14 fails with *"CallbackScreen.tsx does not retain the ID
token at all"* — which is perfectly legible about what broke and where.

So I2 buys **discoverability**, not protection: a reader looking for the callback screen's contract would
look in T19's suite. The cost is a second copy of an assertion in a second file, and the package already has a
standing lesson about that — `strip` is on its fourth copy and `watchHandover` its fifth (**OQ-T32-4**, nine
reviews). A duplicated assertion has to be changed twice, and the copy that is not changed becomes a false
statement in a closed suite. Against a failure mode that is already caught and already legible, that is the
worse trade.

The discoverability half is answered where it costs nothing: AC14's case cites
`callbackValidatesNonceAndKeepsAccessTokenInMemory.test.tsx:1019` by name, so the two are linked from the side
that T36 owns.

## I4 — REJECTED on the package's own convention, measured.

I4 wants `frontend/src/auth/idToken.test.ts`. **Every test file in this package is named after a task's named
test from `tasks.md`, never after a module** — `authorizeRequestsExactlyTheDeclaredScopes`,
`callbackValidatesNonceAndKeepsAccessTokenInMemory`, `returnTargetOutsideAllowlistFallsBackToHome`,
`sessionStorageHoldsOnlyTheL16Items`, and so on through all 29 files. `accessToken.ts` — a module of exactly
the same kind, holding exactly the same sort of state — has **no** `accessToken.test.ts`; task 21 covers it
through `tokensAreNeverWrittenToAnyStorage.test.tsx`.

`idToken.test.ts` would be the first module-named suite in the package, and it would break the property that
lets a reader map any test file to the task that owes it. The underlying worry — that a future rename has no
dedicated suite to fail first — is already false: AC5's structural case, AC9's three cases, AC15's declaration
count and AC14's one-place scan all name `auth/idToken.ts` by path and fail if it moves.

## I5 — RECORDED, refactor declined.

`postLogoutRedirectUri()` does mirror `authorizeRequest.ts:59-62`. Extracting a shared helper means either
editing `authorizeRequest.ts` (closed) or creating a "shared" utility that only one of the two callers uses —
which is a third spelling wearing the word *shared*. Recorded as **FU-T36-2** alongside OQ-T32-4, to be done
when a task is scoped to touch both.

## I1, I11, I12, I13 — recorded, no action.

**I1** is OQ-T36-5, seventh consecutive task: AC1–AC16 are verified by direct invocation only. The new import
scan (I3) is now also the handoff — its failure message tells whoever wires the affordance that OQ-T36-5 can
be closed. **I11** and **I13** confirm decisions already made. **I12** is the expected baseline growth.

## New mutants

| | |
|---|---|
| **ES27** | a third module imports `END_SESSION_PATH` and navigates to the variable — **survived before I3's scan**, dies after |
| **ES28** | the ID token is written to `window.name`, which survives the navigation |

## Follow-ups opened

- **FU-T36-1** — T35's and T33's modules are not swept for `document.title` / `window.name` /
  `history.state`. T36 closed this in its own two modules only.
- **FU-T36-2** — the de-slash derivation is duplicated between `endSession.ts` and `authorizeRequest.ts`.

**Phase 9 complete.** Next is **phase 10 — test generation and gap closure**.
