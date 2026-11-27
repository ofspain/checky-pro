<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# frontend · T47 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T47 — Registration acknowledgement is identical |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Open questions answered

### OQ-T47-1 — Do not render the server's `message`

**Decision:** `RegisterSentScreen` renders only catalogue strings. The server's `RegistrationAcknowledgement.message` is left unread.

**Rationale:**
- **L11** locks the SPA to catalogue-only user-facing strings. A server-supplied message breaks that lock and moves copy ownership out of the client catalogue.
- **R2** says the acknowledgement is "identical copy **and layout**". If the client renders server text, the client cannot guarantee the copy is identical across the two cases; the guarantee would shift to the server, which is not what the task's named suite is asked to prove.
- **D7** requires every catalogue key the screen renders to be measured by a case. Server text has no catalogue key, so there is no client-side instrument to assert it.
- `RegisterScreen` already reads no body; the account-creation path is deliberately response-agnostic. Extending that agnosticism to the acknowledgement is consistent.

**Consequence:** The contract field `RegistrationAcknowledgement.message` remains required for the server but is intentionally unused by this client. This is a deliberate client/server boundary decision, not an oversight, and should be recorded in the screen's header comment so a future engineer does not "fix" it by rendering the body.

---

### OQ-T47-2 — `RegisterScreen` navigates to `register/sent` and drops the `StatusRegion` success state

**Decision:** After a 202 from `POST /accounts`, `RegisterScreen` calls `useNavigate()` to `register/sent` and no longer renders `register.success` in its local `StatusRegion`.

**Rationale:**
- **L14** requires `register/sent` to exist as a public route. A route that the form deliberately avoids is a missed affordance; filling it and navigating to it honours the route table's intent.
- **AC3** says "the post-submit destination is the same in both cases". "Destination" reads naturally as a location, not as a local state change. Navigating makes AC3 observable through the URL and the rendered screen.
- **R2 / L4** make the acknowledgement identical for new and existing emails. A dedicated screen is the cleanest place to centralise that identical output.
- The current in-place `StatusRegion` announcement exists because the route was empty (the `RegisterScreen` comment says so explicitly). That reason expires once `register/sent` has an element.

**What changes in closed tasks:**
- `frontend/src/features/account/RegisterScreen.tsx`: remove the `StatusRegion` success copy; add navigation after the 202 resolves.
- `frontend/src/app/routes.tsx`: attach `RegisterSentScreen` to `register/sent` by matching a new `REGISTER_SENT_PATH` constant.
- `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx`: update the false comments that say `register/sent` carries no element and that navigating there would hand the visitor a blank document.
- `frontend/src/features/account/RegisterScreen.tsx`: update the header comment that says the same.

**What does not change:** The form still does not branch on the response, does not read the body, and does not retry (R2, L4, AC4).

---

### OQ-T47-3 — `RegisterSentScreen` moves focus to its heading on mount

**Decision:** `RegisterSentScreen` focuses its `<h1>` on mount, following the pattern established by `HomeScreen`.

**Rationale:**
- **L11** requires managed focus. This is the first screen in the application that a visitor arrives at through a client-side navigation/redirect, so the justification used by the I9 guard — "none of them is an arrival from a redirect" — no longer covers every screen.
- The existing `Heading` primitive already makes `<h1>` focusable with `tabIndex={-1}` (task 174). The screen only needs to call `.focus()` in a `useEffect`.
- HomeScreen's AC2 cases provide the template: assert `document.activeElement` is the `<h1>` and that the heading text is the screen name.

**How the I9 guard is grown:**
The I9 loop in `homeRouteRendersLandingScreenAndManagesFocus.test.tsx` currently asserts four screens leave focus on `<body>` with the reason that none is a redirect arrival. `RegisterSentScreen` cannot join that loop because it does the opposite. The design is therefore a **reasoned exclusion**, not a fifth body-focus entry:

- Keep the existing four-entry loop, but rewrite its comment to say it covers screens that mount in place (direct arrival or initial render), not redirect arrivals.
- Add a new case in the same file — or in the named T47 suite — that asserts `RegisterSentScreen` moves focus to its heading when mounted through the real route table.
- This keeps the I9 guard's logic coherent: one loop for "leave focus alone", one assertion for "this redirect-arrival screen moves focus".

The brief's "five entries or a reasoned exclusion" is satisfied by the reasoned exclusion.

---

## Catalogue design

`RegisterSentScreen` renders two catalogue keys:

```ts
registerSent: {
  heading: 'Check your email',
  explanation: 'If your email address is not already registered, you will receive a message to finish creating your account.',
}
```

**Why this wording:**
- "Check your email" is neutral and identical for both cases.
- The explanation is also neutral: it does not disclose whether an account existed, and it does not promise a message to existing addresses. It mirrors the server's enumeration-safe design.
- The existing `register.success` key ('Check your email to finish creating your account.') is **not reused**, because navigating to a dedicated screen gives us a place to use clearer copy. Reusing `register.success` would also leave `RegisterScreen` with a key that no longer renders there, creating a dead catalogue entry (D7).

**Alternative considered:** Reuse `register.success` and add only a heading. Rejected because it would leave `register.success` rendered only on a screen that no longer mounts, and because the heading+explanation pattern matches every other static screen in the app (`signIn`, `signedOut`, `home`).

---

## Screen structure

```tsx
<Screen>
  <Heading>{t('registerSent.heading')}</Heading>
  <Text>{t('registerSent.explanation')}</Text>
</Screen>
```

- Uses `Screen`, `Heading`, and `Text` from the primitive barrel.
- No `StatusRegion`: the heading focus move is the announcement.
- No `TextLink` back to sign-in is required by the brief; adding one would introduce new catalogue keys and navigation behaviour that R2 does not need. It can be added later without breaking the acknowledgement contract.
- No props; renders correctly on cold arrival with no router state.

---

## Files touched and why

| Path | Change | Why |
|---|---|---|
| `frontend/src/features/account/RegisterSentScreen.tsx` | Create | The acknowledgement screen (AC1, AC2, R2). |
| `frontend/src/features/account/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx` | Create | Named suite proving identical output for new and existing emails (required test). |
| `frontend/src/app/routes.tsx` | Add `REGISTER_SENT_PATH`, attach element | L14: paths attached by constant, not literal. |
| `frontend/src/i18n/catalogue.ts` | Add `registerSent.heading` and `registerSent.explanation` | L11; each key rendered and measured (D7). |
| `frontend/src/features/account/RegisterScreen.tsx` | Navigate to `register/sent`; remove success `StatusRegion`; update header comment | OQ-T47-2; the route is no longer blank. |
| `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | Update false comments only | The assertions pin route membership/order and still pass; the prose now lies. |
| `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | `RENDER_SITES` 8 → 9 | New render site must be pinned deliberately (AC11). |
| `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | Reasoned exclusion for I9; add focus-movement case for `RegisterSentScreen` | OQ-T47-3; redirect arrivals manage focus. |

---

## Traps the suite must avoid

1. **Byte-identical mocks are vacuous.** The server returns 202 for both new and existing emails. The named suite must mock *different* bodies (or a non-202 the contract does not declare) and still assert identical rendered output.
2. **Negative storage assertions are vacuous without an intercept.** T42's instance-spy bug is the precedent. The T47 suite must prove storage is untouched by spying on `Storage.prototype.setItem` (which intercepts all writes) or by reading `length` after mount.
3. **Rendering server text would hide a copy drift.** The suite should assert the rendered text equals the catalogue literals, not the mocked response body.
4. **Focus on mount must be asserted through the real route table, not only a direct mount.** A direct mount is the screen test; the real-table test is the journey test.

---

## Acceptance-criteria mapping

| AC | How the design satisfies it |
|---|---|
| AC1 | `RegisterSentScreen` is attached to `/app/register/sent`. |
| AC2 | The screen renders the same two catalogue keys regardless of mock body. |
| AC3 | `RegisterScreen` navigates to the same `REGISTER_SENT_PATH` after 202. |
| AC4 | Neither `RegisterScreen` nor `RegisterSentScreen` reads status/body; no retry. |
| AC5 | No `setTimeout`, no artificial delay. |
| AC6 | Only `registerSent.heading` and `registerSent.explanation`; both asserted. |
| AC7 | Heading focus move asserted; no tab trap. |
| AC8 | `RegisterSentScreen` mounts with no props and no preceding submit. |
| AC9 | No storage read/write on this path. |
| AC10 | `PUBLIC_ROUTE_PATHS` unchanged; element attached via `REGISTER_SENT_PATH`. |
| AC11 | `RENDER_SITES` grown to 9; barrel-import scan still passes. |

---

## Deviations / risks

| | |
|---|---|
| **D1** | **Touching a closed task's file.** `RegisterScreen.tsx` and its test file are modified. The brief anticipated this as a possible outcome of OQ-T47-2. The changes are limited to navigation, removal of the now-redundant `StatusRegion` success state, and comment corrections. |
| **D2** | **Ignoring a required contract field.** `RegistrationAcknowledgement.message` is required but unread. This is a deliberate client design choice to keep L11 and D7. If the owner later wants server-controlled copy, the contract field is available and the change is localized. |
| **D3** | **No back link.** The screen has no call-to-action. This is acceptable because the brief does not require one and because a visitor can use the browser chrome. A future task can add a `TextLink` without breaking R2. |

---

## Open items carried forward

| | |
|---|---|
| **OQ-T47-4** | Should the acknowledgement include a sign-in link? Deferred; not needed for R2. |
| **FU-T43-2** | No shared test-util module; recorded a fourth time, not fixed here (brief scope). |

**Phase 3 complete.** Design decisions recorded for Phase 5 implementation.
