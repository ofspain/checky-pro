<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# frontend · T50 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T50 — Resend is public |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Open questions answered

### OQ-T50-1 — Must a transport failure also show the acknowledgement?

**Decision:** No. A transport failure returns the form to its idle state, with no acknowledgement shown.

**Rationale:**
- R5's "regardless of outcome" refers to the server's HTTP response: the contract declares one `200` that is returned whether or not the email matches an account.
- A transport failure means the request never reached the server, so claiming "check your email" would be false.
- This matches `RegisterScreen`'s response-agnostic design: resolve → navigate/acknowledge, reject → idle. The rejection arm is the same shape here.
- T51 owns the uniformity proof across server outcomes; T50 owns the single happy-path acknowledgement and the no-session proof.

**Consequence:** The state machine has three states: `'idle'`, `'submitting'`, `'acknowledged'`. The rejection arm sets `'idle'`.

---

### OQ-T50-2 — Is client-side email validation permitted?

**Decision:** No client-side validation. The form submits whatever the visitor typed, and the server is the authority.

**Rationale:**
- `Form` already sets `noValidate`, forbidding browser constraint bubbles.
- `EmailField` uses `type="email"` for the correct virtual keyboard and autofill semantics, but the value is submitted as-is.
- A local rejection would make the SPA's rendering differ by input, which L4 forbids.
- `RegisterScreen` follows the same pattern: advisory checks may mark a field, but the request is sent either way.

**Consequence:** The submit handler sends `{ email }` unconditionally. Any validation error from the server is invisible to the client — which is correct, because the contract promises a 200 regardless.

---

### OQ-T50-3 — Does the acknowledgement replace the form or sit beside it?

**Decision:** The acknowledgement replaces the form.

**Rationale:**
- L14 declares only `/app/resend-verification`; there is no `/sent` sibling, so the acknowledgement must render in place.
- Replacing the form prevents a second submission and makes the result unambiguous.
- A form kept beside the acknowledgement invites a retry that R5 does not speak to and that could be abused.
- The state machine naturally supports this: `'acknowledged'` renders the acknowledgement, the other two states render the form.

**Consequence:** Focus must be managed, because the submit button that held focus is removed.

---

### OQ-T50-4 — Announcement by live region or managed focus move?

**Decision:** Both, for different phases.

- **`'submitting'`** is announced through the existing `StatusRegion`, which is mounted empty and filled with `resend.submitting`. This is the same pattern as `RegisterScreen`.
- **`'acknowledged'`** moves focus to the acknowledgement heading, because the form (and the button that had focus) is replaced. The focus move is the announcement for the new content, matching `RegisterSentScreen`'s pattern.

**Rationale:**
- A live region alone would announce the acknowledgement, but focus would land on `document.body` when the button disappears.
- A focus move alone would not announce the in-flight "Sending…" state.
- Using the right mechanism for each phase keeps both keyboard and screen-reader users informed.

**Consequence:** The acknowledgement section contains a focusable `Heading` with a ref; an effect moves focus there when `status` becomes `'acknowledged'`.

---

## Screen structure

```tsx
<Screen>
  <Heading>{t('resend.heading')}</Heading>
  {status === 'acknowledged' ? (
    <>
      <Heading ref={acknowledgedRef}>{t('resend.acknowledgedHeading')}</Heading>
      <Text>{t('resend.acknowledgedText')}</Text>
    </>
  ) : (
    <Form onSubmit={submit}>
      <EmailField label={t('resend.emailLabel')} value={email} onChange={setEmail} />
      <SubmitButton>{t('resend.submit')}</SubmitButton>
    </Form>
  )}
  <StatusRegion>{status === 'submitting' ? t('resend.submitting') : null}</StatusRegion>
</Screen>
```

- **Heading:** "Resend verification email" — names the screen and is present in every state.
- **Form:** email field + submit button; replaced on acknowledgement.
- **Acknowledgement:** heading + body text; receives focus when it appears.
- **StatusRegion:** mounted unconditionally; filled only while submitting.

## State machine

```ts
type ResendStatus = 'idle' | 'submitting' | 'acknowledged';
```

1. Initial: `status = 'idle'`, `email = ''`.
2. On submit: `setStatus('submitting')`.
3. POST `{ email }` via `createPublicAuthApi().POST('/accounts/resend-verification', ...)`.
4. On resolve (any HTTP status): `setStatus('acknowledged')`.
5. On reject (transport failure): `setStatus('idle')`.
6. No retry logic. No navigation. No storage write. Response body unread.

## Catalogue design

```ts
resend: {
  heading: 'Resend verification email',
  emailLabel: 'Email address',
  submit: 'Send link',
  submitting: 'Sending…',
  acknowledgedHeading: 'Check your email',
  acknowledgedText: 'If your email is registered, a verification link has been sent.',
}
```

- `acknowledgedHeading` is intentionally the same sentence as `registerSent.heading` ("Check your email"), because the visitor action and outcome are the same. The freeze may choose to reuse the key; here it is a separate key to keep the `resend` group self-contained for T50, with a note that T51 may propose reuse.
- Every key is rendered and measured (D7).
- Keys are flat (T49 D-T49-4: a nested group crashes the copy scan).

## Files touched and why

| Path | Change | Why |
|---|---|---|
| `frontend/src/features/account/ResendVerificationScreen.tsx` | Create | The screen. |
| `frontend/src/features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx` | Create | Named suite (shared with T51). |
| `frontend/src/app/routes.tsx` | Attach element to `RESEND_VERIFICATION_PATH` | AC8, L14. |
| `frontend/src/i18n/catalogue.ts` | Add `resend` group | L11, D7. |
| `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx` | Add `'resend-verification'` to `SCREENS_ATTACHED_SO_FAR` in route order | AC14. |
| `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | Update screen count: 7 → 8 | AC14. |
| `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | Add `ResendVerificationScreen.tsx` to `RENDER_SITES`; add new catalogue keys to key-set pin | AC12, AC14, D-T49-3 precedent. |
| `frontend/src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` | Add `PERMITTED_CREDENTIAL_HITS` row for `ResendVerificationScreen.tsx`, `'a form element'`, `count: 1` | L2/L5 security pin. |
| `frontend/src/features/account/verifyFailureShowsOneUniformMessage.test.tsx` | Replace T49 handoff case | AC13. |

## Files explicitly NOT touched

| Path | Why |
|---|---|
| `frontend/src/app/ui/**` | All required primitives exist. |
| `frontend/src/api/client.ts` | `createPublicAuthApi()` already exists. |
| `frontend/src/auth/**` except the one table entry | No auth logic needed; the screen is public. |
| `frontend/src/features/account/RegisterScreen.tsx` · `RegisterSentScreen.tsx` · `VerifyScreen.tsx` | Out of scope. |

## Required tests mapped

| # | Test | AC / rule |
|---|---|---|
| 1 | The screen renders at `/app/resend-verification` through the real route table | AC1, AC8 |
| 2 | Submit calls `POST /accounts/resend-verification` exactly once through the generated client | AC4, AC6, L6 |
| 3 | No `Authorization` header, even when a session exists | AC5 |
| 4 | Body is `{ email: <typed value> }` | AC6 |
| 5 | Acknowledgement renders after the call settles | AC7 |
| 6 | No response body read; `RegistrationAcknowledgement.message` is ignored | AC9, D2 |
| 7 | The form is public — no token in memory, no `sessionStorage` read/write | AC2, AC3, AC11, L16 |
| 8 | Keyboard operation: implicit submit via Enter, focus management | AC10 |
| 9 | Catalogue keys render as literal English; no untranslated key reaches DOM | AC9, D7 |
| 10 | Primitives only — no lowercase tags, no `className`/`style` | AC12 |
| 11 | T49's handoff case replaced with an assertion that the resend form renders | AC13 |
| 12 | All seven measured pins updated | AC14 |
| 13 | Whole suite, `oxlint`, `tsc --noEmit` green | AC15 |

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| The acknowledgement is shown on a non-200 HTTP status | The handler does not read `response.ok`; it acknowledges on resolve, matching `RegisterScreen`'s response-agnostic shape. The contract declares only 200, so this is the production path. |
| The form can be resubmitted after acknowledgement | The form is replaced by the acknowledgement in `'acknowledged'` state. |
| Focus is lost when the button disappears | Focus moves to the acknowledgement heading via ref + effect. |
| The response body is rendered | Source scan forbids `.message`, `.json()`, `.text()`, and reading the response object. |
| Email leaks to storage, URL, or console | Cases spy on `Storage.prototype` and console methods; no navigation carries the email. |
| A new render site is not recorded | `ResendVerificationScreen.tsx` is added to `RENDER_SITES`. |
| The credential scan fails | `PERMITTED_CREDENTIAL_HITS` gains one row for the `<Form>` render site. |

## Design complete

Next: Phase 4 — owner gate. The decisions above are ready for ratification before implementation begins.
