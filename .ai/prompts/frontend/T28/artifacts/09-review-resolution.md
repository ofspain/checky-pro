# frontend · T28 · Phase 9 — Review Resolution

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T28 — Unenrolled privileged refusal copy |
| **Model** | Human Approval (packet assembled by Claude Opus 5) |
| **Consumes** | `artifacts/07-self-review.md` + `artifacts/08-independent-review.md` |
| **Produces** | `artifacts/09-review-resolution.md` |

Seven Kimi findings (I1–I7) and seven of my own (S1–S7). They overlap on four. **All seven of Kimi's are
accepted**, two of them with corrections, and **one is enlarged**: I5 names a smaller instance of a problem
that turns out to be the most serious thing either review found.

Every cited `file:line` was checked against source before disposition. All four of Kimi's line citations are
accurate (613 `surfacesAfter`, 334 the derived key regex, 171 `FORBIDDEN_COPY_SOURCE`, 667 the AC2 block).

---

## I1 — ACCEPTED. Confirms S1, the surviving mutant

Kimi reaches the same conclusion I did from the same evidence, independently. `surfacesAfter()` builds its
`document` surface from `document.body` only, so MB04 (`document.title = params.get('error_description')`)
survived all 1335 tests.

**Fix in Phase 10.** Add `document.title` and `document.head.innerHTML` to the `document` surface; extend the
sentinel control to plant into the title, because a surface added to a sweep without a sentinel is a surface
nobody has proved is read. Then re-run MB04 — it must come back KILLED by T28's own file.

## I5 — ACCEPTED, AND ENLARGED. This is the most serious finding in either review

Kimi flags `\bcode\b` as too wide: "Postal code", "Reference code" would be caught. Correct. But checking the
spec for what else the pattern forbids turned up a much larger instance **Kimi did not find and I wrote**.

`FORBIDDEN_COPY_SOURCE` also contains `\brole\b`, `merchant`, `administrator`, `locked`, `lockout` and
`suspended`. Measured against `spec/frontend/tasks.md`:

| Pattern term | Specified copy it would forbid |
|---|---|
| `\brole\b` | tasks 88–89 — `getEffectiveRoles`, `assignRole` |
| `merchant` | tasks 28, 73 name MERCHANT; the admin area lists merchants |
| `administrator` | tasks 81–89 — the whole admin area (`## Phase 1 — Auth & account: admin`) |
| `suspended` | task 85 — `adminSuspendAccount` |
| `locked`, `lockout` | task 87 — `adminUnlockAccount` |
| `\bcode\b` | Kimi's instance: postal/reference/country codes |

**So six terms in my own pattern forbid copy the spec requires a later task to write.** That is precisely the
error class I rejected the other half of Kimi's F3 for at the Phase 4 gate — "a guard must not forbid what the
spec permits", T26's lesson — and I then committed a worse version of it in a different pattern. The frozen
brief's own Constraints section says it in the imperative: *"A scan must not fire on the controls this package
depends on."*

**Fix in Phase 10, and it is a design change rather than a narrowing.** Split the pattern in two, because the
two groups make genuinely different claims:

- **ALWAYS forbidden, anywhere in the catalogue** — the enrollment and second-factor vocabulary (`enroll`,
  `two-factor`, `mfa`, `authenticator`, `totp`, `otp`, `one-time`, `recovery`, `backup code`, `security key`,
  `passkey`, `webauthn`). None of these is permitted on any screen: R11's voluntary enrollment wizard is
  tasks 56–60's and does not exist, and when it does, its copy will need an entry in a permitted table with a
  reason. This group stays global.
- **Forbidden ON A FAILED SIGN-IN only** — `code`, `role`, `merchant`, `administrator`, `locked`, `lockout`,
  `suspended`, `not enrolled`. Scoped to the `signIn.*` and `callback.*` keys, which are the only copy a
  failed sign-in can show.

The scoped group is a **better statement of R10** than the global one was, not a weaker one. R10's claim is
that a failed sign-in discloses nothing about the account — not that the application may never use the word
"role". The global version was over-broad in a way that would have been discovered by task 81 failing a test
in `src/auth/`, which is the worst possible place to discover it.

**Plus the mechanism Kimi's `code` instance asks for:** a `PERMITTED_COPY` table on the catalogue scan, the
same shape as `PERMITTED_HITS`, empty today, with a control that exercises the accounting. A future "Postal
code" on a sign-in screen goes in that table with a reason.

## I3 — ACCEPTED in substance, REJECTED in placement

Kimi asks for `fetchCount` **in the `Outcome` struct**, asserted `0` for `error-response` and `1` for the other
three, "within the AC2 equivalence case".

**That would break AC2.** The struct is compared with one `toEqual` across all four kinds; a field that is `0`
for one and `1` for three makes the equivalence assertion fail by construction. Kimi's own recommendation
contradicts the case it is placed in.

The substance is right and the reason it is not a struct field is the interesting part: the `error=` path makes
no exchange because **an authorization error response carries no `code` to exchange** (RFC 6749 §4.1.2.1,
and `CallbackScreen.tsx:81-85` says so). That is a difference in the *mechanism*, not in anything the visitor
can observe — and AC2's claim is about what the visitor observes.

**Fix in Phase 10.** A separate per-kind table asserting the exchange count (`0`, `1`, `1`, `1`), outside the
equivalence struct, with a comment recording why it cannot be inside it. That closes the gap Kimi identified —
a retry on one kind and not another — without making the equivalence claim self-contradictory.

## I2 — ACCEPTED. Confirms S3

Two named constants for the two grammars, with the comment saying why the key grammar has no word boundaries.
Both controls kept.

## I4 — ACCEPTED

`window.name` persists across navigations and jsdom supports it, so it is a real surface and it is cheap. Added
to the sweep with its own sentinel. Strictly an addition beyond AC4's wording, which is the right direction for
a leak sweep.

## I6 — ACCEPTED. Confirms S2

The AC2 block's claim is renamed to what it proves — the three `state` shapes are indistinguishable when an
error is present — and `callbackWithMismatchedStateIsDiscarded.test.tsx` is cited as the owner of the ordering
itself.

**Recorded for Phase 12:** Kimi's **F1**, accepted at the Phase 4 gate, asked for the ordering to be covered.
It was **already covered before T28 was written**, by T18's `getItem` spy. The accept was not wrong, but it was
redundant, and the redundancy was only discovered by the mutation harness scoring MB12 and MB23
INHERITED-ONLY. That is the harness earning its cost on something no amount of reading would have found.

## I7 — ACCEPTED

The harness path and run command go in the file header. Documentation only.

---

## My own findings, final disposition

| # | Disposition |
|---|---|
| **S1** | = I1. Fixed in Phase 10. |
| **S2** | = I6. Fixed in Phase 10. |
| **S3** | = I2. Fixed in Phase 10. |
| **S4** | = I3. Fixed in Phase 10, outside the struct. |
| **S5** | `PERMITTED_HITS` empty — **no change.** The empty table is the measured truth and a control exercises the accounting. |
| **S6** | AC11's signpost table — **no change.** The label is the fix and it is applied. |
| **S7** | `document.cookie` / `indexedDB` out of reach — **no change**, and `window.name` joins the named list per I4. |

## Not accepted, and nothing

No finding from either review is rejected outright. I3 is re-placed and I5 is enlarged; everything else is
taken as written.

## Open Questions

All three resolved at the Phase 4 gate. Nothing new. **OQ1** (the live `/login` form has no `mfaCode` field
while the backend requires it in the same POST) remains open and remains **not T28's** — it needs a backend
task or an accepted limitation against R9.

## Scope confirmation

- **Files to create:** none beyond the test file already committed.
- **Files to modify:** `frontend/src/auth/sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy.test.tsx`
  and `.ai/prompts/frontend/T28/artifacts/07-mutation-harness.py` (MB04 re-run).
- **Files NOT to modify:** unchanged from the frozen brief. **No production change is indicated by either
  review** — both reviewers state this independently.

---

**Phase 9 complete. Approved to proceed.** Seven accepted findings, six of which change the test file. Phase 10
applies them and re-runs MB04 to confirm I1/S1 is closed.
