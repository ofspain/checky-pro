<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan). Escalated from Sonnet: the frozen amendment requires re-aiming a closed task's instruments, which is reasoning about what each assertion measures rather than transcription. -->

# frontend · T47 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T47 — Registration acknowledgement is identical |
| **Model** | Claude Opus 5 |
| **Consumes** | `artifacts/04-frozen-task-brief.md` |
| **Produces** | `artifacts/05-implementation-plan.md` |

**No code in this phase.** Plan only.

---

## Two findings that change the frozen brief's arithmetic

Both were found while planning the re-aim, by reading every site rather than grepping for the success copy.
Logged here rather than absorbed silently, per the standing guardrail.

### FINDING 1 — there are TEN sites, not nine, and the tenth must be INVERTED

`registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx:946` is

```
it('does not navigate: the location is still the registration path (D1, OQ-T43-5)', ...)
```

It asserts `mounted?.router.state.location.pathname` is still `/register` and that the form is still mounted.
The freeze counted nine because the Phase 4 survey grepped for `register.success`, and **this case never
mentions it** — it measures the absence of navigation, which is the exact behaviour T47 now introduces.

It is not re-aimable. It must be **inverted**, and the case's own comment is what authorizes that:

> "Navigating to `register/sent` would hand the visitor **a declared path with no element**, which is exactly
> what D1 chose against."

T43's D1 was a conditional decision, and the condition it named is *the path has no element*. **T47 is the
task that attaches the element**, so the premise is spent by design rather than overruled. The inverted case
keeps the same instrument — `router.state.location.pathname`, never `useLocation()`, which is T41's finding
and cost production code — and asserts the new destination.

**This is the strongest evidence available that OQ-T47-2 was decided correctly**: a closed task wrote down
the condition under which its own choice should flip, and T47 meets it exactly.

### FINDING 2 — the re-aim needs a seventh site the freeze did not authorize

`renderScreen()` (lines 95–111) builds a memory router with **one** route:

```tsx
createMemoryRouter([{ path: `/${REGISTER_PATH}`, element: <RegisterScreen /> }], ...)
```

After navigation that router has no match, so `container` would hold react-router's own fallback and the
acknowledgement sentence would render nowhere. **Asserting "the sentence is rendered at the destination" is
impossible in this suite without adding the destination route to this harness.**

The freeze says the file is in scope "for the six sites above and for nothing else". The harness is not one of
them. Recorded as **OQ-T47-5**, with a recommendation rather than a silent edit:

> **Recommended:** add `{ path: `/${REGISTER_SENT_PATH}`, element: <RegisterSentScreen /> }` to the harness
> router. It is mechanically entailed by the amendment the owner approved — "re-aim, never delete" cannot be
> satisfied otherwise — and it is a two-route harness, not a new claim.
>
> **The alternative** is to assert only the *location* in this suite and leave "the sentence renders there" to
> the named T47 suite. That is weaker in a specific way worth naming: it would let the destination be correct
> and empty, and no case in either file would notice, because the named suite mounts the screen directly.

Phase 6 should proceed on the recommendation. Phase 8 and the Phase 9 gate should confirm it.

---

## Files to create

| Path | Contents |
|---|---|
| `frontend/src/features/account/RegisterSentScreen.tsx` | The acknowledgement screen. |
| `frontend/src/features/account/registrationAcknowledgementIsIdenticalForExistingAndNewEmails.test.tsx` | The named suite. |

### `RegisterSentScreen.tsx` — signature and shape

```tsx
export function RegisterSentScreen(): ReactElement
```

No props (AC8). Composition, verbatim in structure:

```tsx
<Screen>
  <Heading ref={headingRef}>{t('registerSent.heading')}</Heading>
  <Text>{t('register.success')}</Text>
</Screen>
```

- `const headingRef = useRef<HTMLHeadingElement>(null)`; `useEffect(() => { headingRef.current?.focus(); }, [])`.
  The empty dependency array and the `?.` are both load-bearing for the same reasons `HomeScreen` records:
  `mountShell.tsx` renders inside `<StrictMode>`, so the effect runs twice in development and `focus()` on an
  already-focused element is a no-op; the ref is null on first render and after unmount.
- `tabIndex` is **not** passed. It lives inside `Heading` (`ui/index.ts` rule 2).
- **No `StatusRegion`.** The focus move is the announcement; a live region would announce the same words twice.
- **The header comment must record three things** so a later engineer does not "fix" them:
  1. `RegistrationAcknowledgement.message` is required by the contract and deliberately unread (D2).
  2. `register.success` is rendered here and not on `RegisterScreen`, and why (`catalogue.ts` reserved it).
  3. The screen takes no props and reads no router state, which is what makes a cold arrival work (AC8).

### The named suite — cases

| # | Case | AC |
|---|---|---|
| 1 | `/app/register/sent` through the real route table renders a non-empty screen | AC1 |
| 2 | Identical output for a **new** email and an **already-registered** one, compared as `outerHTML` after normalising nothing | AC2 |
| 3 | …with **different mock bodies** on the two runs, and a status the contract does not declare | AC2, trap 1 |
| 4 | The post-submit location is `REGISTER_SENT_PATH` in both cases | AC3 |
| 5 | `RegisterSentScreen` source reaches for neither `status` nor `body`, and sets no timer | AC4, AC5 |
| 6 | Both catalogue keys render, asserted as literal English | AC6, D7 |
| 7 | Focus is on the `<h1>` after a route-table arrival; the heading is the screen name | AC7 |
| 8 | …and it survives StrictMode double invocation | AC7 |
| 9 | CONTROL: `<body>` holds focus before the mount, so case 7 measures something | AC7 |
| 10 | Cold arrival with no preceding submit and no router state | AC8 |
| 11 | `Storage.prototype.setItem` / `getItem` are never called on this path | AC9, trap 2 |
| 12 | The screen renders only through barrel primitives (no lowercase tag of its own) | AC11 |

Case 2 compares **structure and text**, because R2 says "copy **and layout**". Comparing `textContent` alone
would pass for a screen that reordered its elements.

---

## Files to modify

### 1. `frontend/src/app/routes.tsx`

```ts
export const REGISTER_SENT_PATH = 'register/sent';
```

- Replace the literal `'register/sent'` in `PUBLIC_ROUTE_PATHS` with the constant. **The array's strings do not
  change**, so every membership and order pin still passes (AC10).
- Add to the attach chain: `if (path === REGISTER_SENT_PATH) return { path, element: <RegisterSentScreen /> };`
- **Correct the task-43 comment** that says "`register/sent` stays element-less because task 47 owns the
  acknowledgement". It is now false.

### 2. `frontend/src/i18n/catalogue.ts`

- Add `registerSent: { heading: 'Check your email' }`.
- **Amend the `register.success` comment.** It currently reads "The two things the screen says after the
  submit (task 43, D7)" and reserves the key for task 47. The reservation is now spent: the key is rendered by
  `RegisterSentScreen`, and `register.submitting` is the only thing `RegisterScreen` still says after a submit.
  A comment that still claims two is false the moment this ships.

### 3. `frontend/src/features/account/RegisterScreen.tsx`

```ts
const navigate = useNavigate();
const [status, setStatus] = useState<'idle' | 'submitting'>('idle');   // 'sent' removed
```

```ts
void createPublicAuthApi().POST('/accounts', { body: { email, password } })
  .then(() => navigate(`/${REGISTER_SENT_PATH}`, { replace: true }), () => setStatus('idle'));
```

- **Absolute, with the leading slash**, matching `CallbackScreen.tsx:100`'s `navigate(`/${SIGN_IN_PATH}`, …)`.
  A relative `navigate('register/sent')` from `/register` resolves against the current route and would land on
  `/register/register/sent`.
- **`replace: true`**, and the reason is not symmetry with `CallbackScreen`: Back from the acknowledgement must
  not return to a filled form whose resubmission would post the same address again. Pushing would make the
  browser's Back button a retry control nobody designed.
- The `StatusRegion` **stays**, and still renders on `'submitting'`. Only the success branch goes:
  ```tsx
  <StatusRegion>{status === 'submitting' ? t('register.submitting') : null}</StatusRegion>
  ```
  It must keep rendering unconditionally, because a closed case asserts the region exists *before* the submit
  with empty text and that the **same node** is filled rather than replaced.
- `useNavigate` is safe here: the only ban on it (`publicRouteSetIsExactlyTheDeclaredSet.test.tsx`) scopes to
  `SignedOutScreen`/`TextLink` (a `TextLink` must stay an anchor) and to the auth gate's `GUARD_FORBIDDEN`.
  Verified, because a ban that did cover this file would have blocked the frozen decision outright.
- **Three header-comment corrections.** The file currently says "NOTHING BRANCHES ON THE RESPONSE … The handler
  awaits the call and reports success", that "task 47 owns both the acknowledgement screen and `register/sent`",
  and (via the status type) that success is reported in place. The no-branching claim stays **true and must be
  restated precisely**: the handler still does not read the body, does not read the status, does not retry and
  adds no delay — it now navigates on settle instead of setting a state, and a navigation taken on *settle* is
  not a branch on the *response*.

### 4. `frontend/src/features/account/registrationAcceptsTwelveAndOneTwentyEightCodePoints.test.tsx`

**Ten sites. Nine re-aimed, one inverted, none deleted.** Per-site instructions, because "re-aim" is only
meaningful if each row's original claim is named and kept:

| Site | What it measures today | Re-aim |
|---|---|---|
| `516` | `register.success` is among the keys this screen renders | **Move** it out of this list and leave the other five. Add a line naming `RegisterSentScreen` as where it went, so the list does not simply shrink unexplained. |
| `533–544` | the success sentence renders from the catalogue as literal English, and does not distinguish an existing account | Keep the sentence assertions; assert them **at the destination** (`container.textContent` after navigation). The `not.toMatch(/already|exists|taken|new account/)` row is untouched — it reads the catalogue, not the DOM. |
| `820` ×3 (202 / 409 / 500) | **the screen does not branch on the response** — R2, L4 | Assert **both halves**: `router.state.location.pathname` is `/register/sent` for all three replies, **and** the sentence is rendered there. Keep the `not.toMatch(/\b(409|500|202)\b/)` row. This is the instrument AC12 names; it must still fail if the handler starts reading the status. |
| `883` | after a failed submit, a **retry** reaches the transport again and succeeds | The success half moves to the destination. Keep `expect(sent).toHaveLength(1)` exactly: the claim is that the form could submit again, and that is measured before any navigation. |
| `905–917` | the live region exists before the submit, starts empty, and is **filled rather than replaced** | Re-aim to the `idle → submitting` transition, which is the one that now happens in place. The same-node claim survives intact and is still the point; the success text moves out. |
| `935–943` | the in-flight word is shown, then the success one (D7) | Keep the `register.submitting` assertions unchanged — they still happen on this screen. The final success assertion moves to the destination. |
| `946–960` | **the screen does NOT navigate** (D1, OQ-T43-5) | **INVERT.** Assert the pathname IS `/register/sent` and the form IS unmounted. Rewrite the comment to record that D1's premise — "a declared path with no element" — was conditional, and that T47 attached the element. Keep `router.state.location.pathname`; do not reach for `useLocation()`. |
| `95–111` | the harness | **OQ-T47-5** — add the destination route. |

**Not to be touched in this file:** every R1 assertion — the advisory rows, the bound interpolation, the
code-point counting, the `escaped` tracking, the forbidden-shape source scans.

### 5. `frontend/src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx`

- `RENDER_SITES` 8 → 9: add `'features/account/RegisterSentScreen.tsx'` with a comment in the house form
  naming it as the ninth site and the second under `features/`.
- In the catalogue-keys pin, keep `register.success` **present** and add `registerSent.heading`. The pin counts
  keys and forbids a restyling quietly editing or dropping a string; the words are unchanged, so its claim is
  unharmed. The `:966-967` spelled-out pin on the exact sentence needs no change at all.

### 6. `frontend/src/app/homeRouteRendersLandingScreenAndManagesFocus.test.tsx`

- **Keep the four-entry loop.** Its stated reason stays true of its four screens.
- **Line 424**, the describe title "no OTHER screen takes focus" → must admit a second.
- **Line 462**, the assertion message "which only task 174's screen may do" → names the exception.
- **Add one case**: `RegisterSentScreen` moves focus to its heading when mounted **through the real route
  table**. A direct mount is the screen test; the route-table mount is the journey test (trap 4).

---

## Entities · repositories · services

None. No entity, no repository, no store, no query client, no new primitive. The screen is two catalogue
strings and a focus effect; the only moving part added anywhere is one `navigate` call.

---

## Execution order

Front-loaded so that nothing is ever edited twice, and so the suite is never green for the wrong reason.

| # | Step | Why here |
|---|---|---|
| 1 | `catalogue.ts` — add `registerSent.heading`, amend the `register.success` comment | The screen cannot render a key that does not exist. |
| 2 | `RegisterSentScreen.tsx` — create | Needed by both the route table and the harness. |
| 3 | `routes.tsx` — constant, array substitution, attach, comment | Makes `/app/register/sent` reachable; nothing before this can be tested through the real table. |
| 4 | `RENDER_SITES` 8 → 9 and the keys pin | A new render site fails the barrel scan the moment step 2 lands; fixing it here keeps the tree green at every commit boundary. |
| 5 | The named T47 suite | **Written against the screen BEFORE `RegisterScreen` changes.** The acknowledgement must be provable on a cold arrival on its own terms; if it is written after the navigation lands, a case that only passes because a submit preceded it is indistinguishable from one that holds on arrival. |
| 6 | `RegisterScreen.tsx` — navigate, drop the success branch, correct the comments | The behavioural change. The closed suite goes red here, deliberately and visibly. |
| 7 | The ten sites in the closed suite, in the table's order | Green again — and each row re-aimed with its claim named, never deleted. |
| 8 | The I9 guard: two text corrections and the new focus case | Last, because it asserts across both screens. |
| 9 | Full suite, `lint`, `typecheck` | Baseline is **1992 / 1992** (see the Phase 1 artifact); any failure is this task's. |

**Step 6 before step 7 is deliberate.** Running the closed suite between them produces the exact list of what
the change breaks, from the suite itself rather than from this plan. If that list is not the ten sites below,
this plan is wrong and Phase 6 must say so rather than make the list fit.

---

## Tests required

- `registrationAcknowledgementIsIdenticalForExistingAndNewEmails` — the named suite, 12 cases above.
- The ten re-aimed/inverted sites in the closed suite.
- One new case in the I9 guard.
- **AC12's obligation:** the three AC11 rows must be observed to fail against a `RegisterScreen` that branches
  on the response. Phase 7's harness is where that is measured; a row that cannot fail measures nothing.

## Open questions raised in this phase

| ID | Question | Recommendation |
|---|---|---|
| **OQ-T47-5** | May the harness at lines 95–111 gain the destination route — a seventh site the freeze did not name? | **Yes.** Entailed by "re-aim, never delete"; the alternative lets the destination be correct and empty with nothing noticing. |
| **OQ-T47-6** | The freeze says "nine re-aimed assertions" in AC12. There are ten sites and the tenth is inverted, not re-aimed. | Read AC12 as covering all ten. The substance — every claim kept, nothing retired — is unchanged. |

**Phase 5 complete.** Next is **Phase 6 — Implementation**.
