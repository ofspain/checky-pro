<!-- MODEL: Kimi 2.7 — Phase 3 (Design Challenge). -->

# frontend · T51 · Phase 3 — Design Challenge

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T51 — Resend acknowledgement is uniform |
| **Model** | Kimi 2.7 |
| **Consumes** | `artifacts/02-codebase-probe.md` |
| **Produces** | `artifacts/03-design-challenge.md` |

## The defect the probe found

T50 shipped a response-agnostic screen: the settle handler takes no argument and always calls `setStatus('acknowledged')` on resolve. But **`openapi-fetch` parses the response body before the screen's handler runs**. A `200` whose body is not valid JSON — an HTML edge page, a truncated response, a plain-text proxy answer — causes the promise to **reject with a `SyntaxError`**, and the screen renders the `'unsent'` retry line even though the server said the email was sent.

Eleven of the probe's twelve arms rendered identical acknowledgement markup. The twelfth — `200 NOT JSON` — rendered the form under *"We could not send that just now. Try again."*. That makes the rendering depend on the **shape of the response body**, which is exactly the enumeration oracle L4 exists to close.

The source scan T50 added forbids reading `response`, `data`, `error`, `.ok`, `.status`, `statusText`, `.json(`, `.text(` and `.message`. It is still green, because the parse happens inside the library, not in screen source. **Never reading the response was necessary and not sufficient.**

## The fix

### OQ-T51-5 — `parseAs: 'stream'` or catch the `SyntaxError`?

**Decision:** Add `parseAs: 'stream'` to the `POST` call.

```diff
-  .POST('/accounts/resend-verification', { body: { email } })
+  .POST('/accounts/resend-verification', { body: { email }, parseAs: 'stream' })
```

**Rationale:**
- The screen never reads the body, so telling the client not to parse it states the intent directly.
- The probe measured this fix against seven server arms (200 good JSON, 200 HTML, 200 truncated, 200 plain text, 200 no body, 500 HTML, 503 good JSON); all collapsed to the same 362-byte acknowledgement.
- A `catch` on `SyntaxError` would describe a symptom rather than the screen's actual contract (*"I do not care about the body"*).
- `tsc --noEmit` clean with the fix; the whole suite passes (2116/2116).

**Consequence:** The response body stream is left unconsumed. For a one-shot form submit this is negligible, but it is a behavioural change from today's implicit JSON parse. Recorded as OQ-T51-7.

---

## Open questions answered

### OQ-T51-1 — Which statuses to test

**Decision:** Test a representative set that covers HTTP success, HTTP errors, and body shapes:

- `200` with valid JSON body (declared response)
- `200` with a different JSON message
- `200` with empty body
- `200` with empty-string body
- `200` with HTML body (`text/html`)
- `200` with plain-text body
- `200` with truncated JSON
- `400`, `404`, `429`, `500`, `503`
- `204` no content
- transport rejection (dropped connection)

**Rationale:** The contract declares only `200`, but the screen acknowledges on any resolve. The bug is specifically about body parsing, so the test must include non-JSON bodies on success statuses. Error statuses are included to prove the acknowledgement is still uniform even if a proxy or future backend returns them.

### OQ-T51-2 — Raw vs normalised `outerHTML`

**Decision:** Compare `container.innerHTML` after the submit settles.

**Rationale:**
- The acknowledgement DOM contains no `useId`-generated ids (the form, which does contain them, is removed).
- `innerHTML` is stable across outcomes and avoids whitespace/attribute-order noise that raw `outerHTML` can carry.
- The probe's length-based comparison is replaced by exact string equality against a shared expected value.

### OQ-T51-3 — A differing-email arm

**Decision:** Include one case that submits a different email address and asserts the acknowledged DOM is identical.

**Rationale:** R5's uniformity is about server outcomes, not input values, but the screen should not leak the typed email into the rendered acknowledgement either. This is a cheap defensive case.

### OQ-T51-4 — Timing instrument

**Decision:** Do not add a separate timing instrument.

**Rationale:** T50 already asserts the acknowledgement does not appear while the request is in flight. T51's uniformity cases assert all outcomes settle to the same DOM. Together they cover L4's timing clause for this screen.

### OQ-T51-6 — Check the other public screens?

**Decision:** Out of T51 scope.

**Rationale:** `tasks.md` assigns T51 to the resend acknowledgement. `RegisterScreen` reads `response` only to navigate (it does not render it), and `VerifyScreen` reads `response.ok`; both have different failure paths. A separate task or hygiene pass can audit them. Recorded as an open question for the owner.

### OQ-T51-7 — Unconsumed response stream

**Decision:** Accept `parseAs: 'stream'` leaving the body unconsumed.

**Rationale:** The screen has no use for the body, and consuming a stream it does not read adds complexity with no benefit. For a one-shot public mutation this is acceptable. If the bundle later adds response-body logging or interceptors, this should be revisited.

---

## Screen change

Only one production line changes:

```tsx
void createPublicAuthApi()
  .POST('/accounts/resend-verification', { body: { email }, parseAs: 'stream' })
  .then(() => setStatus('acknowledged'), () => setStatus('unsent'));
```

No state machine change. No catalogue change. No new primitive.

## Test design

### Helper to port

`teardownCurrent()` from T49's suite must be added so a case can arrive twice and compare two independent renderings. T50's suite only unmounts in `afterEach`.

```ts
async function teardownCurrent(): Promise<void> {
  await act(async () => mounted!.root.unmount());
  mounted!.container.remove();
  mounted = undefined;
  router?.dispose?.();
  router = undefined;
}
```

### Uniformity cases

1. **Byte-identical acknowledgement across server outcomes.** For each status/body combination in the list above, arrive, send, and capture `container.innerHTML`. Assert every capture equals one shared expected string.
2. **The transport failure is still the only outlier.** Assert a rejecting transport renders the form + `resend.unsent`, not the acknowledgement.
3. **Different email, same acknowledgement.** Submit `other@example.test` and assert the acknowledged DOM is identical.
4. **The response body is still not read.** Keep T50's source scan green; add `parseAs` to the allowed source shape if the scan flags it.
5. **No new catalogue key.** The existing `ACK_TEXT` constant covers the acknowledgement.

### Harness rows to add

| Row | Mutant | Expected |
|---|---|---|
| TR24 | Remove `parseAs: 'stream'` | KILLED — a 200 with non-JSON body renders `unsent` instead of acknowledgement |
| TR25 | `parseAs: 'json'` | KILLED — same as TR24 |
| TR26 | Replace the uniform-acknowledgement equality with a `toContain` | KILLED — the T50 "renders nothing the server sent" case would still pass while the DOM differs |

---

## Files touched and why

| Path | Change | Why |
|---|---|---|
| `frontend/src/features/account/ResendVerificationScreen.tsx` | Add `parseAs: 'stream'` | The fix. |
| `frontend/src/features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx` | Add uniformity cases; port `teardownCurrent` | T51 owns the uniform-acknowledgement proof. |

## Files explicitly NOT touched

| Path | Why |
|---|---|
| `frontend/src/i18n/catalogue.ts` | No new string needed. |
| `frontend/src/app/routes.tsx` | No route change. |
| `frontend/src/app/ui/**` | No primitive change. |
| `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | No new render site or key. |
| `frontend/src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` | No new credential hit. |
| `frontend/src/features/account/verifyFailureShowsOneUniformMessage.test.tsx` | T49 handoff already replaced by T50. |

## Required tests mapped

| # | Test | AC / rule |
|---|---|---|
| 1 | A 200 with valid JSON renders the acknowledgement | R5, AC7 |
| 2 | A 200 with HTML, plain text, truncated JSON, empty body, empty string renders identical DOM | L4, uniformity |
| 3 | 400, 404, 429, 500, 503 render identical DOM to 200 | L4, uniformity |
| 4 | 204 no content renders identical DOM | L4, uniformity |
| 5 | A different email address renders identical acknowledged DOM | L4, input independence |
| 6 | A transport failure still renders `unsent` | D-T50-1 preserved |
| 7 | The response body is never read in source | AC9 |
| 8 | No new catalogue key is introduced | D7 |
| 9 | Whole suite, `oxlint`, `tsc --noEmit` green | AC15 |

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| `parseAs: 'stream'` is not supported by the generated client | The probe measured it compiles and runs; if it fails, catch the `SyntaxError` instead. |
| The fix changes behaviour for malformed bodies in a way that hides real errors | The screen's contract is to acknowledge on any HTTP response; transport failures still show `unsent`. |
| A future edit removes `parseAs: 'stream'` | Harness row TR24/25 will fail. |
| `innerHTML` comparison is unstable | The acknowledgement DOM has no generated ids; the helper arrives fresh for each arm. |

## Design complete

Next: Phase 4 — owner gate. The one-line fix and the uniformity cases above are ready for ratification before implementation begins.
