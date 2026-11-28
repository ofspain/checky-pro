<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# frontend · T49 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T49 — Verify failure is uniform |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-task-implementation-brief.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## Open questions answered

### OQ-T49-1 — The resend action has nowhere to go

**Decision:** The failure state renders a neutral `TextLink` that navigates to `/app/resend-verification`.

**Rationale:**
- R4 requires a neutral resend action; dropping it because the destination is currently element-less would silently delete a requirement.
- The same situation existed for `register/sent` before task 47 attached an element. T47's resolution — attach the target later — kept the action visible to the visitor and the contract intact.
- The route `resend-verification` is already declared in `PUBLIC_ROUTE_PATHS`; tasks 50/51 will attach the element. The link uses the router basename-aware absolute form (`/resend-verification`), so it resolves to `/app/resend-verification` today and will land on the new screen once it exists.
- The action is a link, not a button, because the resend form lives on another screen and R4 says the action "navigates; it does not re-POST here."

**Consequence:** A visitor who follows the link today sees the blank document that the route currently renders. That is a known interim state, not a defect in this task.

---

### OQ-T49-2 — Whether a tokenless arrival renders the uniform failure message

**Decision:** A tokenless or empty-token arrival stays in the `idle` state and renders only the heading. It does **not** show the failure message.

**Rationale:**
- R4 is triggered when "the verification request fails". With no token, no request is made, so nothing has failed.
- T48's OQ-T48-3 said task 49 would own the no-token rendering decision; the brief ratifies that extension but explicitly notes that "no request was made, so nothing 'failed'".
- Rendering the failure sentence for a missing token would over-claim: the link might simply be incomplete, and the uniform message "This verification link is not valid" is a reasonable but not identical situation.
- The replaced T48 `PINS A LIMIT` case will assert the new, correct distinction: a rejected token renders failure copy, a tokenless arrival does not.

**Consequence:** A visitor who opens `/app/verify` with no query parameter still sees only "Verify email" and no way forward. That is a product gap, but it is outside R4's literal trigger and is left for future ownership to close.

---

### OQ-T49-3 — Where the shared failure copy lives

**Decision:** The message and action live under `verify.failure.message` and `verify.failure.action`.

**Rationale:**
- T48 already owns the `verify.*` group for this screen. Adding a nested `failure` object keeps related copy together and makes the reuse path obvious.
- Task 54 can import and render `verify.failure.message` without creating a new group or duplicating text.
- The key names describe the role, not the consumer: `failure.message` is reusable; `failure.action` is specific to this screen.

**Consequence:** Task 54 will import the message from the `verify` group. If the catalogue ever grows a dedicated shared-auth group, the move is a single key relocation plus a component/test update.

---

### OQ-T49-4 — Focus when the async result lands

**Decision:** Focus stays on the heading that received it on arrival. The failure is announced through the `StatusRegion` live region; focus is not moved again.

**Rationale:**
- T48 focuses the heading unconditionally on mount. Moving focus a second time when the result arrives would interrupt a screen-reader user who may already have started reading the page.
- The `StatusRegion` is mounted empty and filled on failure, which is the pattern that causes the content to be announced without a focus change.
- No package requirement says to move focus on async result, and doing so unprompted is itself an accessibility hazard.

**Consequence:** Keyboard focus and screen-reader announcement are handled by separate mechanisms: focus on the heading, announcement via the live region.

---

### OQ-T49-5 — What "neutral" constrains

**Decision:** The message and action must not name or imply any failure reason.

**Rationale:**
- L4 requires identical copy, redirects, styling and timing across bad, expired, used and deleted-account tokens and across account states.
- Words such as "expired", "used", "already verified", "deleted", "invalid" or "not found" would let a visitor distinguish two outcomes, which L4 forbids.
- The chosen message — "This verification link is not valid." — states the outcome without diagnosing it. The action — "Resend verification email" — is a plain next step.

**Consequence:** The source scan must forbid reads of `error`, `.title`, `.type`, `.detail` and `.status` inside `VerifyScreen.tsx`, because any branch that looks at the response body can be turned into a reason-specific message.

---

## Screen structure

```tsx
<Screen>
  <Heading ref={headingRef}>{t('verify.heading')}</Heading>
  <StatusRegion>
    {status === 'success' ? t('verify.success') : null}
    {status === 'failure' ? t('verify.failure.message') : null}
  </StatusRegion>
  {status === 'failure' ? (
    <TextLink to="/resend-verification">{t('verify.failure.action')}</TextLink>
  ) : null}
</Screen>
```

- **Heading:** "Verify email" — focus moves here on arrival and stays there.
- **StatusRegion:** Mounted empty; filled with success or failure copy when the request settles. The primitive's own rule makes the fill announce.
- **Failure action:** `TextLink` to `/resend-verification`. Rendered only in `failure`.
- **No props:** the screen reads `?token` from `useSearchParams`.

## State machine

```ts
type VerifyStatus = 'idle' | 'success' | 'failure';
```

Task 48 left `idle` covering three situations. Task 49 splits the failure branch out:

1. On mount, read `token` from `useSearchParams`.
2. If no token or empty token, return without posting; `status` stays `'idle'`.
3. Guard against double invocation with a `handled` ref, set **before** the POST starts (CallbackScreen pattern).
4. POST `{ token }` via `createPublicAuthApi().POST('/accounts/verify-email', ...)`.
5. On `response.ok`, set `status` to `'success'`.
6. On HTTP non-success (`response.ok === false`) **or** transport rejection, set `status` to `'failure'`.
7. No retry. No navigation. No storage write.

The key anti-regression guard is that `'idle'` **never renders failure copy**, so a visitor with a valid token cannot see a flash of failure before the 204 arrives.

## Catalogue design

```ts
verify: {
  heading: 'Verify email',
  success: 'Your email has been verified.',
  failure: {
    message: 'This verification link is not valid.',
    action: 'Resend verification email',
  },
}
```

- `verify.failure.message` is intentionally reusable for task 54 (R15).
- `verify.failure.action` is the label of the resend link.
- Every key is rendered and measured (D7).
- No `verifying` key: the brief keeps the in-flight state visually empty, and the heading alone carries the screen identity.

## Files touched and why

| Path | Change | Why |
|---|---|---|
| `frontend/src/features/account/VerifyScreen.tsx` | Modify | Split the state machine; render the failure branch and resend action. |
| `frontend/src/i18n/catalogue.ts` | Add `verify.failure.message` and `verify.failure.action` | L11; every user-facing string from catalogue. |
| `frontend/src/features/account/verifyFromLinkShowsSuccessState.test.tsx` | Replace `PINS A LIMIT` case | AC11: handoff case must fail and become uniform-failure assertions. |
| `frontend/src/features/account/verifyFailureShowsOneUniformMessage.test.tsx` | Create | Named suite proving R4. |

## Files explicitly NOT touched

| Path | Why |
|---|---|
| `frontend/src/app/routes.tsx` | Brief forbids it; `resend-verification` is already declared with no element. The action uses the basename-aware absolute literal `/resend-verification`. |
| `frontend/src/app/ui/**` | No new primitive needed. `TextLink` already exists and wraps `Link`. |
| `frontend/src/auth/**` | Module-boundary rule. |
| `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | FU-T48-1 check: `VerifyScreen.tsx` is already a render site; adding a primitive call inside it does not change the file-set union. |
| `frontend/src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | No new route or attached element. |
| `frontend/src/app/routesResolveUnderAppPrefixOnly.test.tsx` | No new route. |

## Required tests mapped

| # | Test | AC / rule |
|---|---|---|
| 1 | A 400 renders the uniform failure message from the catalogue | AC1 |
| 2 | 400, 404, 410, 429, 500 render identical DOM | AC2, L4 |
| 3 | A problem body with `type`/`title`/`detail` does not change the DOM | AC2, L4 |
| 4 | A transport rejection renders the same uniform failure | AC3 |
| 5 | The failure state carries a neutral resend link to `/app/resend-verification` | AC4, AC5 |
| 6 | A valid token shows no failure copy at any point before success | AC6 |
| 7 | The failure sentence is announced via `StatusRegion`, mounted before it has content | AC7 |
| 8 | Catalogue keys render as literal English; no untranslated key reaches the DOM | AC8, D7 |
| 9 | One call through `createPublicAuthApi()`; no `Authorization`; no bare `fetch` | AC9, L6 |
| 10 | In the failure state the token appears in no DOM, storage or console | AC10 |
| 11 | T48's `PINS A LIMIT` case replaced with uniform-failure assertions, not deleted | AC11 |
| 12 | Primitives only; no lowercase tag, no `className`/`style` | AC12 |
| 13 | 429 renders the same uniform failure as 400 | AC13 |
| 14 | Each touched file green alone; whole suite green | AC14 |
| 15 | Source scan forbids reads of `error`, `.title`, `.type`, `.detail`, `.status` in `VerifyScreen.tsx` | L4, enumeration safety |

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| A valid token flashes failure copy before success | The failure branch is rendered only when `status === 'failure'`; `status` starts at `'idle'` and moves to `'success'` only on `response.ok`. A dedicated case asserts no failure copy appears before success. |
| The screen reads the response body and branches | Source scan forbids `error`, `.title`, `.type`, `.detail`, `.status` in `VerifyScreen.tsx`. |
| The resend action varies by failure reason | Rendered only in `failure`; label is a catalogue constant; target is a constant path. |
| The token leaks into the failure DOM | Cases assert `innerHTML` does not contain the token in failure state, and no storage/console writes occur. |
| `TextLink` introduces a new render site | No: `TextLink` is used inside the existing `VerifyScreen.tsx`, which is already counted. |

## Design complete

Next: Phase 4 — owner gate. The decisions above are ready for ratification before implementation begins.
