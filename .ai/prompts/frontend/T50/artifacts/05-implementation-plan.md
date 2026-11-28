<!-- MODEL: Claude Opus 5 — Phase 5 (Implementation Plan). No code. -->

# frontend · T50 · Phase 5 — Implementation Plan

| | |
|---|---|
| **Consumes** | `artifacts/04-frozen-task-brief.md` (**FROZEN**) |
| **Produces** | `artifacts/05-implementation-plan.md` |

The prompt's schema/dao/service/entity headings have no subject in an SPA; the equivalents are below.

---

## Files to create

| | |
|---|---|
| `src/features/account/ResendVerificationScreen.tsx` | The screen: four states, one POST, the acknowledgement replacing the form. |
| `src/features/account/resendVerificationAcknowledgementIsUniformAndNeedsNoSession.test.tsx` | The named suite (`package.md:109`), **shared with T51**. |

## Files to modify — the seven the probe measured

1. `src/i18n/catalogue.ts` — the flat `resend` group, seven keys.
2. `src/app/routes.tsx` — import the screen; attach it to the existing `RESEND_VERIFICATION_PATH`.
3. `src/app/routesResolveUnderAppPrefixOnly.test.tsx` — `'resend-verification'` **sixth**, in route order.
4. `src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` — seven → **eight**, including the `describe` name.
5. `src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` — `RENDER_SITES` **and** the catalogue key set.
6. `src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` — one `PERMITTED_CREDENTIAL_HITS` row.
7. `src/features/account/verifyFailureShowsOneUniformMessage.test.tsx` — replace T49's handoff case.

## Component surface

```ts
export function ResendVerificationScreen(): React.JSX.Element
```

The only export. No props — there is no value a caller could pass that would not be a way to make two
visitors' screens differ.

**Internal state**

```ts
const [email, setEmail]   = useState('');
const [status, setStatus] = useState<ResendStatus>('idle');   // 'idle' | 'submitting' | 'acknowledged' | 'unsent'
const acknowledgedRef     = useRef<HTMLHeadingElement>(null);
```

**Private functions**

| | |
|---|---|
| `submit(): void` | `setStatus('submitting')` — which also **clears a previous `'unsent'`** — then one `POST` with `{ email }`; resolve → `'acknowledged'`, reject → `'unsent'`. **Never reads the response.** |
| one `useEffect` | Moves focus to `acknowledgedRef` when `status === 'acknowledged'`, because the control that held focus has been removed. |

**Consumed modules:** `createPublicAuthApi` (`api/client.ts`) · `useTranslation` · the primitive barrel
(`Screen`, `Heading`, `Form`, `EmailField`, `SubmitButton`, `StatusRegion`, `Text`) · `RESEND_VERIFICATION_PATH`
is **not** imported here — the screen does not link to itself.

## Tests required (Phase 6 writes the named suite; Phase 10 extends it)

Grouped by frozen AC: **AC1/AC8** renders at the path through the real table, in place, no new route · **AC2/AC3/AC11**
no session, not behind the gate, no storage write and no token read (`Storage.prototype` spy **with a positive
control**) · **AC4/AC5/AC6** exactly one call, no `Authorization` even when a session exists, body `{email}`
only · **AC7** acknowledgement after resolve **whatever the status**, replacing the form · **AC9** catalogue
literals and a **source scan** forbidding any read of the response · **AC10** region text while submitting,
focus move on acknowledgement, Enter submits · **AC12** primitives only · **AC16** transport rejection → the
`unsent` line, form still mounted, **cleared by a new submit**.

## Execution order

Front-loaded so the compiler and the existing pins fail early rather than at the end:

1. **Catalogue** — the seven keys. Nothing renders yet; the key-set pin goes red, which is the expected signal.
2. **The screen** — states, submit, effect, structure. `tsc` is the first gate.
3. **Attach the route** — import + element. The three route/handoff pins now go red together.
4. **The six pin updates** — catalogue key set, `RENDER_SITES`, `SCREENS_ATTACHED_SO_FAR`, the seven→eight
   count, the credential-table row, and T49's handoff case **replaced**.
5. **The named suite** — written last, so it is written against the screen that exists rather than the one
   planned.
6. **Verify** — named suite alone, every touched file alone, then the whole suite, `oxlint`, `tsc`.
7. **Commit before any mutation run** — the harness reverts with `git checkout`, and uncommitted test edits
   have been destroyed silently twice in this project.

## Deviation flagged in advance

**The named suite is written in Phase 6, not Phase 10.** The prompt says tests belong to Phase 10, but every
frontend task from T43 to T49 has built its named suite in Phase 6 and used Phase 10 to close the gaps review
found — because the suite is what Phase 7's self-review and the mutation harness measure, and a self-review
with nothing to run is not one. Recorded here rather than discovered later.

**Phase 5 complete. Next is Phase 6 — Implementation.**
