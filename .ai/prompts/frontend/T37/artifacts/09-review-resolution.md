<!-- MODEL: Claude Opus 5 — Phase 9 (Review Resolution) -->

# frontend · T37 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T37 — Signed-out landing |
| **Consumes** | `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

Fourteen findings. **Six accepted and implemented**, **one rejected on a measurement**, **one rejected as a
decision**, **six recorded with no action**. Every citation was checked against source before it was
accepted — and one of Kimi's premises was wrong in a way that exposed a defect of mine it had not noticed.

## Score after resolution

```
regressions: 29/29 killed     (23 → 29; SL25-SL30 added by this phase)
survivors:   none
broken:      none
```

| Gate | Result |
|---|---|
| `vitest run src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | **26 passed (26)** |
| `vitest run` | **1688 passed / 1 failed of 1689** — the known `oxlint` host case |
| `tsc --noEmit` | **exit 0** |
| `oxlint src` | **exit 0** |

## Accepted and implemented

### I1 — the sweep could not see a read that renders the same thing · **the most important finding**

Confirmed at `:285-291`. The list held thirteen patterns and none of them was a token. Kimi's assessment
is the part that matters, and it is exactly right: **AC6 and AC13 compare rendered output, so they can
only see a read that CHANGES what renders.** A module that calls `accessToken()` and branches to the same
markup was invisible to the entire suite — on the screen whose central constraint is that it makes no
claim about the visitor's session.

Nine patterns added: the five token primitives, an import scan for `./accessToken` and `./idToken`, and
`document.title`, `window.name`, `history.state` — the three T36's AC8 sweeps, for the same reason.

**SL25** proves it was load-bearing: the screen reads the token and branches to identical markup, and the
only cases that see it are the two new ones. **SL26** (`document.title`) dies on the scan alone.

### I6 — one probe proved one pattern, and nothing about the other twelve

Confirmed at `:294-299`. The control planted `sessionStorage` and nothing else, so a typo in any other
pattern — a lower-case `sendbeacon`, a dropped word boundary — was unmeasured. This is the same class of
defect as my own control bugs in phase 7.

The scan and its control now iterate **one table of `(pattern, probe)` pairs**, so a pattern cannot arrive
without a probe that proves it fires, and the table's length is pinned so it cannot shrink unnoticed.

### I2 — AC8 checked one of the two tokens

Confirmed at `:302-313`. T36 introduced `idToken.ts` so `endSession()` can send an `id_token_hint`, which
makes the ID token session state this screen must leave alone too — and nothing retained one, so a mutant
calling `takeIdToken()` changed no assertion anywhere. AC8 now retains both and asserts both survive.

`takeIdToken` is the only reader `idToken.ts` publishes (T36 deliberately shipped no plain one, to
foreclose a read-before-clear window), so reading it *clears* it; it is therefore read last, and
`beforeEach`/`afterEach` drain it for the cases that do not. **SL27** dies on AC8 and the scan.

### I3 — importing the constant is not using it

Confirmed at `:360-368`: the case asserted no private declaration and that the import exists, and never
that the constant reaches the URL. **SL28** is the finding made concrete — `endSession.ts` imports
`SIGNED_OUT_PATH` so both inherited assertions pass, and spells `'signed-out'` where the URL is actually
built. The drift AC10 exists to prevent, wearing AC10's own fix as a disguise.

AC10 now scans the body of `postLogoutRedirectUri` for `${SIGNED_OUT_PATH}` and for the absence of the
literal, scoped to the function because the file's prose legitimately mentions `/app/signed-out`.

Worth recording: **T36's own suite does not catch SL28 either.** It asserts the URL by value, so
hardcoding the same value is invisible to it. T37's assertion is the only thing holding it.

### I5 — an anchor dressed as a button

Confirmed: AC5's scan forbids `<button` and says nothing about `role`. An `<a role="button">` is counted
as a link by "only one way out" while losing the affordances the `Link` was chosen for. The single anchor
must now have an `href` and no `role`, and `role=` joined the scan. **SL29** dies on both AC5 cases.

### I8 — the whitespace exactness is intended, and now says so

Accepted as documentation. AC11's equality is deliberately whitespace-exact; a `<br>` or a space added for
styling will fail it, and whoever changes the markup updates the expectation on purpose.

## I10 — accepted, and Kimi's premise was wrong in a useful way

Kimi recommended adding an assertion that `container.querySelector('main')` is not null. **That assertion
already existed**, at AC1. So the recommendation as written was unnecessary — and checking it found
something worse, which is that **my own phase 7 artifact was wrong about SL19.**

Phase 7 said SL19 "dies on a `TypeError`, not a case". Measured narrowly, it fails **eleven** cases and
the first of them is AC1's named assertion. So the landmark *is* pinned by a criterion. But the message it
fails with is:

> `the route resolved to nothing - before task 37 it had no element`

which is **false** under SL19: the route resolved perfectly, and the landmark is what went missing. One
assertion was carrying two different failures and naming only the first. Split in two, each with its own
message.

This is the third time in this task that a gap turned out to be in **what a failure would tell you**
rather than in what it catches — and the second time my own description of a mutant was wrong until it was
verified narrowly rather than by its kill count.

**Phase 7's artifact is annotated** with a pointer here rather than rewritten; it is the record of what
that phase concluded, including where it was mistaken.

## Rejected

### I12 — `NavLink` is already caught · rejected **on a measurement**

Kimi recommended forbidding `NavLink`. AC5 asserts the source matches `/<Link\s/`, and `<NavLink ` does
not contain `<Link` — so the existing assertion should already fail. Rather than accept a redundant
pattern or reject on reasoning, **SL30 plants it**: killed, by AC5's `is a real anchor` case alone.

The mutant is kept in the harness as the measurement that justified the rejection. If it ever survives,
the rejection was wrong and I12 reopens.

### I4 — the file name · rejected as a decision, for the third time

Raised in phase 3 (F6), settled in phase 4, raised again here. The house convention names a test file
after the named test in `tasks.md`, and `tasks.md:65` and `:71` give **task 37 and task 40 the same named
test** — `publicRouteSetIsExactlyTheDeclaredSet`. Renaming for T37 would either break the convention or
produce two files for one named test, and task 40 would then have to delete one.

The cost Kimi names is real, and the header is where it is paid: it states in its first twelve lines what
T37 owns and what task 40 must add. Adding the reminder to every case, as recommended, would put thirteen
copies of the header in the file. **Unchanged.**

## Recorded, no action

| | |
|---|---|
| **I7** | The basename behaviour is covered. Kimi's own recommendation is "none". |
| **I9** | No error boundary — consistent with `SignInScreen` and `CallbackScreen`; the route table attaches no error elements anywhere. Task 41's if it is anyone's. |
| **I11** | Confirms FU-T37-2. `routes.tsx:5` stays as it is: a comment in task 16's closed file. |
| **I13** | Sixth copy of T35's harness. **OQ-T32-4**, now ten reviews across five tasks. |
| **I14** | Kimi's own assessment is "no gap"; AC6 and AC12 together are complete. |

## A note on one control's coupling, since it looks like the bug I keep fixing

AC7's control fires on SL25, SL26 and SL27 as well as the scan. That is **not** the coupling phase 7 kept
correcting. The control's claim has two halves — the probe fires, *and* the clean source is clean — and the
second half is a genuine assertion about production, so a mutant that dirties the source should fail it,
with the message `the real source already trips X, so that entry proves nothing`. T35 and T36 use the same
shape.

## Open, unchanged by this phase

- **Nothing calls `endSession()`.** OQ-T36-5, eighth consecutive dormant task; every case is a direct arrival.
- **OQ-T37-1** — the copy is derived, not specified. Phase 9 strengthened what cannot change silently; it
  still cannot say the words are right.
- **FU-T37-1** — narrowed, not closed: the landmark is now named, but heading level and order remain pinned
  only by assertions about other things (SL20 dies on AC3's `querySelector('h1')`, SL21 on AC11's ordering).
- **FU-T37-2** — `routes.tsx:5`'s false consequence.
- **Task 38's "Blocked on Q15"** still looks mis-assigned, and task 38 is the only end-to-end proof of R19.

## Phase 10

Test generation, from this resolution.
