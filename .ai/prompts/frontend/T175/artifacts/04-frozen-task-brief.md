<!-- MODEL: Claude Opus 5 — Phase 4 (Frozen Task Brief). FROZEN. No code written. -->

# frontend · T175 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Consumes** | `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` — **FROZEN** |

**No code was written in this phase.** Kimi's design is **accepted in substance** — the two-line fix, both
screens in one unit, the three readers untouched, and a reader/non-reader scan. **Four deviations**, all
ratified **before implementation**, two of them because the design as written **cannot be built**.

---

## The production change — FROZEN

```diff
// VerifyScreen.tsx:100
- .POST('/accounts/verify-email', { body: { token } })
+ .POST('/accounts/verify-email', { body: { token }, parseAs: 'stream' })

// RegisterScreen.tsx:95
- .POST('/accounts', { body: { email, password } })
+ .POST('/accounts', { body: { email, password }, parseAs: 'stream' })
```

Measured in Phase 2: seven of seven rows on `VerifyScreen` including the fatal one (**a dropped connection
still renders the failure**), `tsc` clean, and the whole suite green with both applied.

---

## Deviations

### D-T175-1 — Kimi's test rows 1 and 2 **cannot be written**

The design asks for *"`204` with an HTML body"* and *"`204` with truncated JSON"*. **A `204` cannot carry a
body at all:**

```
status 204 with body: THROWS - Response constructor: Invalid response status code 204
status 205 with body: THROWS
status 304 with body: THROWS
status 200 with body: OK
status 202 with body: OK
```

204, 205 and 304 are null-body statuses, so those two fixtures are not constructible. **This is T51's TR26 in
another form** — there a harness row that could not die, here a case that cannot exist. **Both rows are
dropped**, leaving the `200` pair.

**And this sharpens what the defect actually is.** `auth.yaml` declares `204` for `verifyEmail`, and a `204`
has no body to fail parsing — so **the defect is not reachable by a bad body from the real service at all.**
It requires the `204` to have been *replaced* by something with a body: an edge interstitial, a proxy's error
page, a `200` from an intermediary. The bug is not *"the server sent bad JSON"*; it is *"something other than
the server answered, and the client called that an invalid token."*

### D-T175-2 — Kimi's scan would fail on day one, demanding `parseAs` on the three readers

The sketch says to *"inspect the `.then` callback"* and treat *"no parameter or does not reference `data`"* as
a non-reader requiring `parseAs`. **None of the three readers uses `.then`:**

```
codeExchange.ts:114    const { data } = await createOidcClient().POST('/oauth2/token', {
readProfile.ts:125     const answer = await api.GET('/userinfo', {
readAccount.ts:157     const answer = await api.GET('/accounts/me');
```

All three `await`. An awaited call **has no callback**, so by the sketch's own rule all three are non-readers
and the scan would demand `parseAs: 'stream'` on **exactly the three calls that must not have it** — three
false positives, and a scan that fails the moment it is written.

`codeExchange` also calls through **`createOidcClient()`**, a factory the sketch's enumeration does not
mention, so it would be missed by the call-site sweep and caught only by the (wrong) default.

**The invariant is kept; the mechanism is reframed.** The scan must classify by **whether the call's result is
consumed at all**, covering both shapes:

| Shape | Classification |
|---|---|
| `const { data } = await …` / `const x = await …` | **reader** — the result is bound |
| `.then((arg) => …)` where `arg` names `data` | **reader** |
| `void …POST(…).then(() => …)` or a settle handler not naming `data` | **non-reader** → must pass `parseAs: 'stream'` |

**And the scan must assert its own census**, the way T35's count pins do: the number of production call sites
it found, so a new factory or a new call shape cannot slip past by being invisible to the pattern rather than
by passing it. **A scan that finds nothing passes.**

### D-T175-3 — Two paths in the design do not exist

`frontend/src/features/profile/readProfile.ts` and `frontend/src/features/account/readAccount.ts` are both
wrong; the files are `src/profile/readProfile.ts` and `src/profile/readAccount.ts`. Corrected — they are in
the *not touched* table, and a "not touched" entry naming a non-existent path protects nothing.

### D-T175-4 — The R4-preservation cases go in T49's suite, not T48's

Kimi puts *"a genuine `400`/`500` still renders failure"* and *"a dropped connection still renders failure"*
in `verifyFromLinkShowsSuccessState`. **`tasks.md` names both suites**, and
`verifyFailureShowsOneUniformMessage` is the one whose subject is failure — it already owns the nine-status
loop those cases belong beside. Success claims to T48's suite, failure claims to T49's.

---

## Acceptance criteria — FROZEN

| AC | Criterion |
|---|---|
| **AC1** | `VerifyScreen`: a `200` with an **HTML** body and a valid token renders the **success** state. |
| **AC2** | `VerifyScreen`: a `200` with **truncated JSON** and a valid token renders the **success** state. |
| **AC3** | `VerifyScreen`: the declared `204` still renders success. |
| **AC4** | `VerifyScreen`: a genuine `400` and `500` still render the **uniform failure** (T49's suite). |
| **AC5** | `VerifyScreen`: a **dropped connection** still renders the uniform failure (T49's suite). **R4's *"any reason"* must keep meaning transport failure.** |
| **AC6** | `VerifyScreen`: T49's nine-status loop stays green — the fix changes no failure behaviour. |
| **AC7** | `RegisterScreen`: a `202` with an **HTML** body shows the acknowledgement. |
| **AC8** | `RegisterScreen`: a `202` with **truncated JSON** shows the acknowledgement. |
| **AC9** | `RegisterScreen`: the acknowledgement is **identical** across those arms and the declared one — T47's byte-comparison technique, since R2 says *"identical copy and layout"*. |
| **AC10** | The scan: every non-reading generated-client call passes `parseAs: 'stream'`; the three readers are **exempt and named**; **the census is asserted**. |
| **AC11** | **No new catalogue key**, no route change, no primitive change. |
| **AC12** | Whole suite, `oxlint`, `tsc --noEmit` green. |
| **AC13** | **Every new case fails before the fix.** |

## Files touched

| Path | |
|---|---|
| `features/account/VerifyScreen.tsx` | The option. |
| `features/account/RegisterScreen.tsx` | The option. |
| `features/account/verifyFromLinkShowsSuccessState.test.tsx` | AC1–AC3. |
| `features/account/verifyFailureShowsOneUniformMessage.test.tsx` | AC4–AC6. |
| `features/account/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx` | AC7–AC9. |
| `api/` — the scan | AC10. Exact file at Phase 5. |

**Not touched:** `ResendVerificationScreen.tsx` (T51), `auth/codeExchange.ts`, `profile/readProfile.ts`,
`profile/readAccount.ts` (readers), `catalogue.ts`, `routes.tsx`, `app/ui/**`.

## Open for the owner

| ID | |
|---|---|
| **OQ-T175-5** | **The error inverts here too**, as it did in T51: a visitor whose registration was blocked at the edge will now be told *"Check your email."* The same trade L4 already chose, and the same one `OQ-T51-8` records. |
| **OQ-T175-6** | `codeExchange.ts` is a **reader**, so a parse failure there is a genuine failure — but **what the visitor sees** when a token exchange gets an edge interstitial is not in this task's scope and was not measured. |

---

**FROZEN. Next is Phase 5 — Implementation Plan.**
