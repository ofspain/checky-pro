<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# frontend · T52 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T52 — Reset request is uniform (`passwordResetRequestAcknowledgementIsUniform`) |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Open questions decided

### OQ-T52-1 — Acknowledgement renders in place

**Decision:** The password-reset request screen acknowledges **in place**, on `/app/reset/request`, with no `/sent` sibling.

**Rationale:**
- `design.md` §4c's confirmed public path list contains **nine** entries and no reset-acknowledgement path. L14 defers to §4c by its own words.
- Q6 is resolved "confirmed as proposed", which is the same route shape resend uses (D-T50-2).
- Adding a `/sent` sibling would require an ADR under L14's rule and would conflict with the explicit Out scope of the brief.

The form will be replaced by acknowledgement text on the same route, matching `ResendVerificationScreen`.

### OQ-T52-2 — A dropped connection shows a neutral retry line

**Decision:** On transport failure, the screen keeps the form mounted and shows a neutral retry line in the live region.

**Rationale:**
- R14 names no failure behaviour; the only constraint is that the failure line must not leak account state.
- A transport failure is orthogonal to L4 (it says nothing about whether the address is registered).
- The established pattern in the same family is `ResendVerificationScreen`'s `unsent` line (D-T50-1), which was an explicit owner reversal of the "return to idle and show nothing" choice.
- A visitor who presses a button and sees the screen silently return to its previous state is given no information at all; the neutral retry line fixes that without violating R14 or L4.

### OQ-T52-3 — No link to the screen is added

**Decision:** This task does **not** add a "Forgot password?" link from `SignInScreen` or any other screen.

**Rationale:** `tasks.md` names no task that adds such a link, and the brief lists it as unresolved/out of scope. The screen is reachable by typing `/app/reset/request`. A future task may add the link.

---

## Screen design

`PasswordResetRequestScreen.tsx` mirrors the structure of `ResendVerificationScreen.tsx`, adjusted for the reset-request endpoint.

### State

```ts
const [email, setEmail] = useState('');
const [status, setStatus] = useState<'idle' | 'submitting' | 'acknowledged' | 'unsent'>('idle');
const headingRef = useRef<HTMLHeadingElement>(null);
```

### Effect

Focus the heading when the acknowledgement appears (L11):

```ts
useEffect(() => {
  if (status === 'acknowledged') headingRef.current?.focus();
}, [status]);
```

### Submit handler

```ts
function submit(): void {
  setStatus('submitting');
  void createPublicAuthApi()
    .POST('/accounts/password-reset-request', { body: { email }, parseAs: 'stream' })
    .then(() => setStatus('acknowledged'), () => setStatus('unsent'));
}
```

- The response is **never read**.
- `parseAs: 'stream'` is required (AC7, T175), so a non-JSON success body does not trigger the failure arm.
- `setStatus('submitting')` also clears a previous `'unsent'` state, preventing a stale retry line under a fresh attempt.

### Render

- One `<Heading>` whose text changes on acknowledgement (the same pattern D-T50-2 settled for resend).
- A `<Form>` with one `<EmailField>` and one `<SubmitButton>` while not acknowledged.
- Acknowledgement text in a `<Text>` replacing the form.
- A `<StatusRegion>` showing the `submitting` and `unsent` messages.

### Catalogue keys (`passwordResetRequest`)

| Key | English | Where shown |
|---|---|---|
| `heading` | `"Reset your password"` | Form state |
| `emailLabel` | `"Email address"` | Form |
| `submit` | `"Send reset link"` | Form |
| `submitting` | `"Sending…"` | Status region while in flight |
| `acknowledgedHeading` | `"Check your email"` | Acknowledged state, focused |
| `acknowledgedText` | `"If your email is registered, a reset link has been sent."` | Acknowledged state |
| `unsent` | `"We could not send that just now. Try again."` | Status region on transport failure |

All keys are flat (no nesting), consistent with every other group in `catalogue.ts` (D-T49-4).

---

## Route changes

Promote the existing literal `'reset/request'` to a named constant:

```ts
export const PASSWORD_RESET_REQUEST_PATH = 'reset/request';
```

Attach the screen in `appRoutes` with the house `// TASK 52` comment, in the same conditional branch style as the other public screens.

The literal `'reset'` stays untouched because the reset **confirmation** screen is task 53.

`routesResolveUnderAppPrefixOnly.test.tsx` hard-codes the public spellings and will continue to guard the path independently.

---

## Files to create

| Path | Purpose |
|---|---|
| `frontend/src/features/account/PasswordResetRequestScreen.tsx` | The screen |
| `frontend/src/features/account/passwordResetRequestAcknowledgementIsUniform.test.tsx` | The named test |

## Files to modify

| Path | Change |
|---|---|
| `frontend/src/app/routes.tsx` | Add `PASSWORD_RESET_REQUEST_PATH` constant; attach element in `appRoutes` |
| `frontend/src/i18n/catalogue.ts` | Add `passwordResetRequest` section |
| `frontend/src/api/generatedClientCallsDoNotParseBodiesTheyNeverRead.test.ts` | Add `features/account/PasswordResetRequestScreen.tsx` to the census as a non-reading streamer |

## Files NOT to modify

- `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx` — independent guard on the spelling.
- `frontend/src/features/account/VerifyScreen.tsx`, `RegisterScreen.tsx`, `ResendVerificationScreen.tsx` and their suites — siblings, not this task.
- `frontend/src/app/ui/**` — primitives already complete.
- `frontend/src/auth/SignInScreen.tsx` — no "forgot password" link in this task (OQ-T52-3).
- `spec/`, `contracts/`, `libs/`.

---

## Test design

The named test file follows the shape of `resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx`, but contains the full uniformity proof because T52 is the only task owning this named test.

### Structure

1. **Helpers** — copied/adapted from the resend suite:
   - `installBrowserRequest()`
   - `installTransport(status, body, contentType)`
   - `installNeverAnswering()`
   - `installTransportFailure()`
   - `installTypedTransport(status, body, contentType)`
   - `arrive(path, { strict? })`
   - `type(input, value)`
   - `send(container, email)`

2. **AC1/AC8 — screen exists and acknowledges in place**
   - Renders heading, email field, submit control.
   - The path is in `PUBLIC_ROUTE_PATHS` and resolves through the real route table.
   - Submitting does not navigate; pathname stays `/app/reset/request`.

3. **AC2/AC3/AC11 — no session, public route, nothing stored**
   - Renders with no session and no seeded storage.
   - Route is a direct child of the root, not behind `RequireSession`.
   - Writes nothing to `sessionStorage`/`localStorage`, with a control proving the spy intercepts.

4. **AC4/AC5/AC6 — one correct public call**
   - POSTs exactly once to `/accounts/password-reset-request` with body `{ email }`.
   - Carries no `Authorization` header even when session storage is seeded.
   - Sends exactly one request under `StrictMode`.

5. **AC7/AC8/AC9/AC10 — acknowledgement replaces the form**
   - Shows acknowledgement and removes form/field/button after resolve.
   - Shows no acknowledgement while in flight.
   - Focus moves to the heading on acknowledgement.
   - Uses design-system primitives only (no bare elements).

6. **Uniformity arms (AC4 baseline)** — a 13+ arm table comparing byte-identical markup across:
   - Declared `200` JSON
   - `200` with different `message`
   - `200` with no body
   - `200` with empty string body
   - `200` with HTML body
   - `200` with plain-text body
   - `200` with truncated JSON
   - `204`
   - `400`, `404`, `429`, `500`, `503`
   - `302` with body, `304`
   - For each: same `innerHTML`, same pathname, same focus target (`<h1>`), same region text.
   - Server-sent words never appear in the DOM.

7. **Transport failure (OQ-T52-2)**
   - Renders the neutral `unsent` line.
   - Keeps the form mounted.
   - Clears the line on a new submit.
   - Does not contain the acknowledgement text.

8. **AC6 — email goes nowhere**
   - Not in path, query string, hash.
   - Not in `sessionStorage`/`localStorage`.

9. **Source scan participation**
   - T175's census asserts the new call exists as a non-reader with `parseAs: 'stream'`.

---

## Acceptance criteria mapping

| AC | How held |
|---|---|
| AC1 | Single POST to `/accounts/password-reset-request`; one request under StrictMode. |
| AC2 | No `Authorization` header even with seeded session storage. |
| AC3 | Acknowledgement text comes from `en.passwordResetRequest` catalogue keys. |
| AC4 | 13+ arm byte-comparison across server/proxy/edge outcomes. |
| AC5 | Server `message` never appears in DOM; source scan forbids reading the response. |
| AC6 | Email not in URL or any storage; spies with controls. |
| AC7 | `parseAs: 'stream'` in the call; T175 census updated. |
| AC8 | No artificial delay; acknowledgement on resolve. |
| AC9 | One `<h1>` via `<Heading>`; focus to heading on acknowledgement; `StatusRegion` for in-flight. |
| AC10 | `Screen`, `Heading`, `Text`, `Form`, `EmailField`, `SubmitButton`, `StatusRegion` only. |
| AC11 | Public route set membership; direct child of root; resolves under `/app` only. |
| AC12 | Whole suite, `oxlint`, `tsc --noEmit` green. |
| AC13 | Every new case fails before the screen exists. |

---

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| "Forgot password" link is missing | OQ-T52-3 documented; future task owns it. |
| Acknowledgement wording implies account existence | Use the same neutral pattern as resend: "If your email is registered, a reset link has been sent." |
| Non-JSON success body triggers failure arm | `parseAs: 'stream'` plus T175 census. |
| Test duplicates resend utilities | Copy helpers again (FU-T43-2 still open); no shared test-util module. |
| Two `<h1>` elements | Only one `<Heading>` whose text changes (D-T50-2). |

---

## Design complete

Next: Phase 4 — owner gate. The screen, catalogue, route and named test are ready for ratification before implementation begins.
