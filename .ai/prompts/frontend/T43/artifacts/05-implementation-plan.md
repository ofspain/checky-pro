<!-- MODEL: Claude Sonnet — Phase 5 (Implementation Plan). No code. -->

# frontend · T43 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` · **Task** T43 — Registration accepts 12 characters |
| **Consumes** | `artifacts/04-freeze-task.md` (D1–D17) |
| **Produces** | `artifacts/05-implementation-plan.md` |

Nothing here adds a file the freeze did not authorize. The template's schema/DAO/repository/service sections
have no counterpart in a browser bundle; their place in the ordering is taken by **the primitive layer, then
the api factory, then the screen, then the route, then the instruments** — the same front-loading principle:
the things others depend on first.

## Files to create

| file | what |
|---|---|
| `app/ui/Form.tsx` | `<form>`, `onSubmit` with `preventDefault()` inside (**D13**), ref → `FORM` |
| `app/ui/EmailField.tsx` | `<label>` + `<input type="email">`, `useId()` inside, ref → the **input** (**D2**, **D12**) |
| `app/ui/PasswordField.tsx` | `<label>` + `<input type="password">`, same shape (**D2**, **D12**) |
| `app/ui/SubmitButton.tsx` | `<button type="submit">`, `ActionButton`'s tokens (**D2**, **D11**) |
| `features/account/RegisterScreen.tsx` | the screen — first file in the first `features/` directory (**D10**) |
| `features/account/passwordLength.ts` | `countCodePoints(value: string): number` (**D6**) |
| `features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx` | the named suite |

## Files to modify

| file | change |
|---|---|
| `app/ui/index.ts` | export the four; grow the header's count of eight/nine and say **why a field composes two elements** (**D3**) |
| `api/client.ts` | add `createPublicAuthApi(rateLimit?)` (**D4**) — `createAuthApi` untouched |
| `app/routes.tsx` | `REGISTER_PATH = 'register'`; attach `RegisterScreen` in the existing public `.map()` |
| `i18n/catalogue.ts` | the `register` group, six keys (**D7**) |
| `app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | D11's rows: 13 primitives, 12 bearer files, the two-tag exception, 8 render sites, union 21, four `REQUIRED_UTILITIES` rows, four ref-target rows, the `register.*` keys |
| `app/routesResolveUnderAppPrefixOnly.test.tsx` | `SCREENS_ATTACHED_SO_FAR` → five, `register` **third** |
| `app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx` | the I9 guard loop → four screens (**D5**); the union pin → 21 |
| `app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | whatever its probe walk and element list assert now that a fourth public path has an element |

## Public signatures

```
// app/ui — each via forwardRef, no className, no style, no id, no htmlFor, no type, no copy
Form({ children, onSubmit }: { children: ReactNode; onSubmit: () => void })            → HTMLFormElement
EmailField({ label, value, onChange }: FieldProps)                                      → HTMLInputElement
PasswordField({ label, value, onChange }: FieldProps)                                   → HTMLInputElement
SubmitButton({ children }: { children: ReactNode })                                     → HTMLButtonElement
type FieldProps = { label: ReactNode; value: string; onChange: (next: string) => void }

// api/client.ts
createPublicAuthApi(rateLimit?: RateLimitNoticeState): AuthApi

// features/account
countCodePoints(value: string): number
RegisterScreen(): ReactElement
```

`onChange` hands back the **string**, not the event: a primitive that leaked `ChangeEvent` would make every
consumer reach into `event.target`, which is the element the layer exists to hide.

## Private helpers

- inside `app/ui/`, a non-exported field body shared by the two field primitives (**D2**) — or two
  independent bodies if sharing costs more than it saves; the decision is phase 6's, the **export surface is
  frozen either way**.
- inside `RegisterScreen`, one submit handler: read state → call the client → set status. No other function.

## Entities · repositories · services

None of these exist in this service. Their equivalents: **`contracts/api/auth.yaml`'s
`RegisterAccountRequest`** (`required: [email, password]`) as the only data shape, **`@themistra/api-client`**
as the only transport, and **`i18n/catalogue.ts`** as the only source of words.

## Tests required

**The named suite**, `features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx`, built
so task 44 adds the 128 end without restructuring: the length cases drive a **table**, not a copied case.

| group | cases |
|---|---|
| **AC1** | the form renders: an email input, a password input, each with a programmatic label (`labels` → `control`), one submit control |
| **AC2** | `'a'.repeat(12)` is accepted: no advisory text in the document, nothing disabled, the submit reaches the transport |
| **AC3** | `'👍'.repeat(12)` — 12 code points, 24 UTF-16 units — accepted on the same terms; and `countCodePoints` agrees with the DOM's own `value.length` being 24 |
| **AC4** | an 11-code-point password **still reaches the transport** (advisory), with **no claim** about what is shown (**D16**) |
| **AC5** | exactly one request; `POST`; `/accounts`; JSON body carrying what was typed; **no `Authorization` header**; plus the source scan and its probe control (**D8**) |
| **AC6** | every rendered string equals its catalogue entry **and** its literal English; no `register.` key text in the DOM |
| **AC7** | the screen bears no lowercase JSX tag; the four new primitives are in the pinned lists (asserted through the instrument's own source, T42's shape) |
| **AC8** | both declared path sets unchanged; the screen renders with **no session** and reads no token |
| **AC9** | both fields and the control are keyboard-reachable; **Enter in a field submits** (the `type="submit"` claim, **D13**) |
| **AC10** | no `setItem` on either storage across the whole journey; no password in any surviving DOM attribute; the source reaches no `console`, no storage, no URL building (**D14**) |
| **AC11** | the handler does not read the response body or status: a 202 and a hypothetical non-202 produce the **same** rendered result, and no timer is used (**D15**) |
| **D1** | the `StatusRegion` is **pre-mounted** (present before submit, empty), then holds `register.success`; the screen does not navigate — `router.state.location.pathname` is unchanged |
| **D7** | `register.submitting` is visible while the transport's promise is unresolved (a deferred reply), so the key is measured rather than merely written |

**Instruments grown, not rewritten** — the five files above. Each change is additive; any case that would have
to be *rewritten* rather than *extended* is a signal the design is wrong, and gets reported instead.

**The harness** (thirteenth copy) per **D17**, including the row predicted **unfalsifiable** (removing the
advisory check changes nothing observable at the accepting end).

## Execution order

1. **The primitive layer first**, because the screen, the instruments and `tsc` all depend on it:
   `Form`, `EmailField`, `PasswordField`, `SubmitButton`, then `app/ui/index.ts`'s exports and header.
2. **`countCodePoints`** — one function, no dependencies, and the thing task 46 will come back to.
3. **`createPublicAuthApi`** in `api/client.ts`, beside the two existing factories.
4. **`RegisterScreen`**, composing 1–3: state, the advisory count, the submit, the pre-mounted status region.
5. **The catalogue group** (it can precede 4; keeping it here means the screen's keys are written once, in
   the file that renders them, and then added).
6. **`REGISTER_PATH` and the route attach** — the smallest change, and the one that makes the screen reachable.
7. **`tsc --noEmit`** — expected to fail until the two exhaustive `Record`s keyed by the primitive union have
   their four new rows. **This is the step the freeze predicts (§4)**, and it is cheaper than the suite.
8. **The instruments**, D11's eleven rows, one file at a time, re-running each file's own suite as it changes.
9. **The named suite**, written against the finished screen.
10. **Whole suite, `tsc`, `oxlint`** — then commit **before** the harness (`revert()` is `git checkout` plus
    `git clean` over `frontend/src/`).
11. **The harness**, 1 row per AC plus D1/D5/D13's rows and the pinned-list reversions.

Steps 1–6 are one commit; steps 8–9 are the second; the harness run is the third. Phase 6 reports what
actually failed at step 7 and step 8 against the freeze's prediction, including any instrument **nobody**
named — T42's phase 6 found four of those.
