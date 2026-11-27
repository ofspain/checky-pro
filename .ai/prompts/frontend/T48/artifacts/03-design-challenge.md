<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# frontend · T48 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T48 — Verify success state |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Open questions answered

### OQ-T48-1 — One shared `VerifyScreen` for success and failure

**Decision:** Task 48 creates `frontend/src/features/account/VerifyScreen.tsx`. Task 49 extends the same file for the failure states.

**Rationale:**
- R3 success and R4 failure land on the **same route** (`/app/verify`). Two files cannot both own one route element.
- The precedent in this codebase is `CallbackScreen` (task 6 onward), which handles working, success and several discard states in one file. The act-on-arrival pattern, the `handled` ref, and the state-machine shape are all borrowed from it.
- A single screen keeps the token-reading logic, the one-POST guard, and the focus/announcement behaviour in one place. Task 48 owns the `success` branch; task 49 owns the `failure` branch(ces).
- The alternative — task 48 creating a success-only screen and task 49 replacing it — would make task 49 a destructive edit to a closed task, which T47 already showed is expensive to ratify.

**Consequence:** The file list in the Phase 2 brief is amended: one file is created, and task 49 will modify it rather than create a second one.

---

### OQ-T48-2 — POST the token on arrival

**Decision:** `VerifyScreen` calls `POST /accounts/verify-email` once on mount when a non-empty token is present.

**Rationale:**
- R3 reads as on-arrival: "When a visitor opens a verification link and the token is valid, the SPA shows the verified success state."
- `CallbackScreen` acts on arrival for the same reason: the visitor clicked a link and expects the result without an additional step.
- A one-time token behind a confirm control would still be consumed by anything that executes JavaScript, so the confirm control does not materially reduce the attack surface against malicious automation; rate-limiting and token design are the real mitigations.
- The contract already declares `POST /accounts/verify-email` with `security: []` and a 204 response, so the client shape is fixed.

**Security consequence recorded:** Email-scanning proxies or link previewers that execute JavaScript could consume the token before the human clicks. This is a product/security decision, not a client implementation defect. If the owner chooses a confirm control later, the change is localized to this screen.

---

### OQ-T48-3 — No token or empty token: no POST, screen stays in a non-success state

**Decision:** If `token` is missing or empty, `VerifyScreen` does not POST and remains in its initial non-success state. Task 49 will render that state as the uniform failure state.

**Rationale:**
- R3 covers a valid token; R4 covers the request failing. With no token there is no request, so neither requirement reaches the case.
- Task 48 must not invent failure copy (brief constraint). Rendering a blank or generic failure sentence would pre-empt task 49's ownership of R4.
- The screen does not crash, does not POST `{ token: '' }`, and leaves the state machine in a branch task 49 can own.

---

## Screen structure

```tsx
<Screen>
  <Heading ref={headingRef}>{t('verify.heading')}</Heading>
  <StatusRegion>{status === 'success' ? t('verify.success') : null}</StatusRegion>
</Screen>
```

- **Heading:** "Verify email" — focus moves here on arrival, naming the screen.
- **StatusRegion:** Empty while the POST is in flight; filled with the success message on 204. `StatusRegion` is mounted unconditionally so the fill is announced (primitive rule).
- **No props:** the screen reads `?token` from `useSearchParams`.

## State machine

```ts
type VerifyStatus = 'idle' | 'success';
```

Task 49 will extend this to include failure states. Task 48 keeps it minimal:

1. On mount, read `token` from `useSearchParams`.
2. If no token or empty token, return without posting.
3. Guard against double invocation with a `handled` ref, set **before** the POST starts (CallbackScreen pattern).
4. POST `{ token }` via `createPublicAuthApi().POST('/accounts/verify-email', ...)`.
5. On 204, set `status` to `'success'`.
6. No retry. No navigation. No storage write.

## Catalogue design

```ts
verify: {
  heading: 'Verify email',
  success: 'Your email has been verified.',
}
```

- No `verifying` key: the brief says task 48 covers success only, and a transient working message is not required by R3. The heading alone is shown while working.
- No failure key: task 49 owns failure copy.
- Every key is rendered and measured (D7).

## Files touched and why

| Path | Change | Why |
|---|---|---|
| `frontend/src/features/account/VerifyScreen.tsx` | Create | The verification screen; shared with task 49. |
| `frontend/src/features/account/verifyFromLinkShowsSuccessState.test.tsx` | Create | Named suite proving R3. |
| `frontend/src/app/routes.tsx` | Add `VERIFY_PATH`, attach element | L14: paths attached by constant. |
| `frontend/src/i18n/catalogue.ts` | Add `verify` group | L11; every user-facing string from catalogue. |
| `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | `RENDER_SITES` 9 → 10; union pin `toBe(22)` → `toBe(23)` | AC10; new render site must be deliberate. |
| `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | Attached-element case: 6 → 7 screens | AC11; the route table now has seven screens. |
| `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx` | `SCREENS_ATTACHED_SO_FAR` gains `verify` in route order | AC11; handoff instrument. |
| `frontend/src/auth/unauthenticatedRoutesRedirectToSignIn.test.tsx` | Update comment: four of eight → three of eight have no element | F5-style comment correction; no assertion changes. |

## Required tests mapped

| # | Test | AC / rule |
|---|---|---|
| 1 | A 204 through the real route table from `/app/verify?token=…` renders the success state | AC1, AC4 |
| 2 | The request body equals `{ token: <token from URL> }` | AC2, AC3 |
| 3 | Exactly one request across a StrictMode double mount | AC7 |
| 4 | No `Authorization` header | AC3, L6 |
| 5 | No storage write (spied on `Storage.prototype`) | AC8, L16 |
| 6 | Focus moves to the heading on arrival; control proves body is the default | AC6, L11 |
| 7 | Catalogue keys render as literal English; response body does not reach DOM | AC5, AC6 |
| 8 | Token with `+`, `%`, `/` survives round trip | AC2 |
| 9 | Inherited count/order/membership pins stay green | AC10, AC11 |

## Additional tests this design needs

| Test | Why |
|---|---|
| Source scan forbids `sessionStorage`, `localStorage`, direct `fetch`, `useNavigate`, etc. | AC8, module boundaries. |
| No token renders the same non-success structure task 49 will extend | OQ-T48-3. |
| The `handled` ref is set before the POST, not after | AC7, StrictMode. |

## Module boundaries and security

- `features/account/` imports **nothing** from `auth/`.
- Backend call goes through `createPublicAuthApi()` only (L6).
- The token is read from the URL, sent once, and stored nowhere (L16).
- No token is logged, rendered, or passed to analytics.

## Deviations / risks

| | |
|---|---|
| **D1** | **Shared file with task 49.** Task 49 will edit a file created by task 48. This is the chosen design; it avoids replacing a closed-task file. |
| **D2** | **POST on arrival.** Security consequence noted above; owner may override. |
| **D3** | **No working-state message.** The heading alone is shown while the POST is in flight. If the POST is slow, the screen is not blank but also does not say "Verifying…". Acceptable because R3 only covers the success state. |

## Open items carried forward

| | |
|---|---|
| **OQ-T48-4** | The contract declares no failure response for `verifyEmail`; recorded in Phase 1 and blocks task 49, not this task. |
| **OQ-T49-1** | What the failure states look like and whether they share the same screen (answered yes here, implemented in task 49). |

**Phase 3 complete.** Design decisions recorded for Phase 5 implementation.
