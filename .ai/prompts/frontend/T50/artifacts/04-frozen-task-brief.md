<!-- MODEL: Human Approval — Phase 4 (Freeze Task Brief). FROZEN 2026-11-28. -->

# frontend · T50 · Phase 4 — FROZEN Task Brief

| | |
|---|---|
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` |
| **Status** | **FROZEN** — owner-approved 2026-11-28. Implementation works from this document. |

**T50 — Resend is public.** *"The resend form works without a session."* Verifies
`resendVerificationAcknowledgementIsUniformAndNeedsNoSession`. Cites **R5**.

---

## Owner decisions at this gate

### D-T50-1 · A transport failure shows a neutral retry line — **reversing Kimi's OQ-T50-1**

Kimi designed the rejection arm to return the form to `'idle'` with **nothing shown**, matching
`RegisterScreen.tsx:96`. **The owner reversed it.** A dropped connection now sets a fourth state, `'unsent'`,
which renders one neutral line in the status region while **the form stays mounted**, so retrying is one
click.

> **This is the second consecutive task where the owner has reversed a "render nothing" choice**, and the
> first — **D-T49-2**, the tokenless verify arrival — is why: a silent screen is the defect the demo rehearsal
> had already found once, and Kimi's own T49 phase 3 had called that one *"a product gap … left for future
> ownership to close."*

**Why it does not weaken R5, and does not breach L4.** The server returns **one `200` for every account
state**, so **every server outcome still renders the identical acknowledgement** — the uniformity R5 asks for
is untouched, and T51's proof is unaffected. L4 polices *unknown or missing, locked, suspended and deleted
accounts* and *bad, expired and used tokens*; **a dropped connection is orthogonal to all of them** and
reveals nothing about whether the email matches an account. The new line is therefore a rendering that varies
by **transport**, never by **account state**.

**What it costs**, recorded so nobody discovers it later: this surface now has two possible messages where R5
speaks of one, so **T51 must assert that the `unsent` line appears for a transport failure and for nothing
else** — not merely that the acknowledgement is uniform.

### Ratified as designed

| OQ | Decision |
|---|---|
| **OQ-T50-2** | **No client-side email validation.** The form submits whatever was typed. **Verified against source:** `Form.tsx:41` sets `noValidate`, `EmailField.tsx:41` sets `type="email"` **inside** the primitive, and `RegisterScreen.tsx:91-92` computes its advisory and then states *"The advisory result is deliberately NOT consulted here: R1 sends the form either way."* Kimi's reading of the house pattern is exact. |
| **OQ-T50-3** | **The acknowledgement replaces the form.** L14's verbatim list has no `/sent` sibling, so it renders in place; replacing prevents a second send and makes the result unambiguous. |
| **OQ-T50-4** | **Both mechanisms, one per phase.** `'submitting'` is announced through the `StatusRegion`; `'acknowledged'` moves focus to its own heading, because the control that held focus is removed. Each established separately here — `RegisterScreen`'s region and `RegisterSentScreen`'s *"THE FOCUS MOVE IS THE ANNOUNCEMENT."* |
| **Catalogue keys** | **A separate `resend` group**, not a reuse of `registerSent.heading`, even though both say *"Check your email"*. **Measured: no pin forbids two keys sharing a sentence.** T47's precedent cuts the other way only where a **reservation** existed (`register.success` was reserved for its screen in the catalogue itself); **no reservation exists for resend**, and coupling two features' copy to one key would make either owner's edit silently change the other's screen. |

## Frozen design

### State machine — **four states**

```ts
type ResendStatus = 'idle' | 'submitting' | 'acknowledged' | 'unsent';
```

1. Initial `'idle'`, `email = ''`.
2. On submit → `'submitting'`. **This also clears a previous `'unsent'`**, so a stale retry line cannot sit
   beneath a new in-flight attempt. (The same requirement T49 met with `setStatus('idle')` before its POST,
   and the same line a later reader would delete as redundant.)
3. `POST /accounts/resend-verification` with `{ email }` through `createPublicAuthApi()`.
4. **On resolve, whatever the HTTP status → `'acknowledged'`.** The handler **does not read `response.ok`**:
   the contract declares one `200`, and branching on status is the one thing that could make this surface
   reveal an outcome. *(The mirror image of T49, where R4 required distinguishing failure and `ok` was the
   only thing read.)*
5. **On reject → `'unsent'`** (D-T50-1). The form stays mounted.
6. No retry logic, no navigation, no storage write, **the response body never read**.

### Structure

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
  <StatusRegion>
    {status === 'submitting' ? t('resend.submitting') : null}
    {status === 'unsent' ? t('resend.unsent') : null}
  </StatusRegion>
</Screen>
```

The region is **mounted unconditionally and filled only when there is text** — a live region mounted *with*
content announces nothing (T49's AC7).

### Catalogue — flat (T49 **D-T49-4**: a nested group crashes the copy scan)

```ts
resend: {
  heading: 'Resend verification email',
  emailLabel: 'Email address',
  submit: 'Send link',
  submitting: 'Sending…',
  acknowledgedHeading: 'Check your email',
  acknowledgedText: 'If your email is registered, a verification link has been sent.',
  unsent: 'We could not send that just now. Try again.',
}
```

**Seven keys, all rendered and all measured (D7).** `unsent` is D-T50-1's.

## Files to create

`src/features/account/ResendVerificationScreen.tsx` ·
`src/features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx` (**shared with T51**)

## Files to modify — **seven, measured by probe**

`app/routes.tsx` (attach the element to the existing constant) · `i18n/catalogue.ts` ·
`app/routesResolveUnderAppPrefixOnly.test.tsx` (`SCREENS_ATTACHED_SO_FAR`, **sixth, in route order**) ·
`app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` (**seven → eight**, and the count is in the `describe`
**name**) · `app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` (**two pins**: `RENDER_SITES` and the
catalogue key set) · `auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` (**one
`PERMITTED_CREDENTIAL_HITS` row** — `'a form element'`, `count: 1`; the scan's pattern is case-insensitive, so
`<Form>` counts, which is why `RegisterScreen` already has that row. **Table entry only.**) ·
`features/account/verifyFailureShowsOneUniformMessage.test.tsx` (**replace T49's handoff case**).

> The probe that produced this list ran this task's real shape — screen, keys and attachment — and gave **six
> failures across five files**, where T49's TR23 had named three. **A file absent from this list is absent
> because it stayed green under measurement, not because it was assumed to.** That sentence exists because
> T49's freeze named a file untouchable and a pin inside it fired anyway.

## Files NOT to modify

`app/ui/**` · `api/client.ts` · `auth/**` except the one table row · `RegisterScreen.tsx` ·
`RegisterSentScreen.tsx` · `VerifyScreen.tsx` · any other task's named suite · `spec/**` · `contracts/**` ·
`services/**`.

## Acceptance criteria — **FROZEN**

**AC1** a form with an email field and a submit control at `/app/resend-verification` · **AC2** renders with
**no session** · **AC3** **not** behind `RequireSession` · **AC4** submit calls the endpoint **exactly once**
through the generated public client · **AC5** **no `Authorization` header**, even when a session exists ·
**AC6** body is `{email}`, the typed value and nothing else · **AC7** the acknowledgement shows after the call
**resolves**, whatever the status, and **replaces the form** · **AC8** in place; no new route declared ·
**AC9** catalogue-only strings and **the response body never read** · **AC10** `'submitting'` announced in the
region, `'acknowledged'` announced by a focus move, keyboard-operable · **AC11** no `sessionStorage` write, no
token read, the email in no URL and no console · **AC12** primitives only — no lowercase tags, no
`className`/`style` · **AC13** T49's handoff case **replaced**, not deleted · **AC14** all seven pins updated ·
**AC15** whole suite, `oxlint`, `tsc --noEmit` green · **AC16 (D-T50-1)** a **transport rejection** renders the
neutral `unsent` line with the form still mounted, and a **new submit clears it**.

**Explicitly NOT an acceptance criterion:** that the acknowledgement is identical across server outcomes.
**That is T51**, on this same suite. T50 renders one acknowledgement and does not prove its uniformity.

## Open questions carried

| ID | |
|---|---|
| **OQ-T50-5** | **For T51:** it must assert the `unsent` line appears for a transport failure **and for nothing else**, alongside one acknowledgement across every server outcome. D-T50-1 created this; T51 owns it. |
| **OQ-T50-6** | A `'200'` is the only declared response, so acknowledging on any resolve means a `500` would also say *"Check your email."* Accepted: branching on status is the one thing that could leak an outcome, and the house pattern (`RegisterScreen`) is response-agnostic. Recorded because it is a deliberate falsehood in an undeclared case. |
| **R-T48-1** | The gateway hazard, unchanged. This screen is the remedy a burnt-token visitor is sent to. |
| **FU-T49-1** | **Fixed 2026-11-28** — the generator now gives phases 6, 9, 10 and 11 the earlier artifact they need. |

**FROZEN. Next is Phase 5 — Implementation Plan.**
