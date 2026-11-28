<!-- MODEL: Human Approval — Phase 4 (Freeze Task). -->

# frontend · T52 · Phase 4 — Frozen Task Brief

| | |
|---|---|
| **Consumes** | `artifacts/02-task-implementation-brief.md`, `artifacts/03-design-challenge.md` |
| **Produces** | `artifacts/04-frozen-task-brief.md` — **the only specification Phases 5–13 may use** |

Phase 3's three decisions are **RATIFIED**. Its file list is **CORRECTED** — it names three of six files, and the
three it misses are the three that will fail first.

---

## 1 · The three open questions, decided

| | Decision |
|---|---|
| **OQ-T52-1** | **The acknowledgement renders IN PLACE** on `/app/reset/request`, no `/sent` sibling. §4c's confirmed list has nine paths and no reset acknowledgement; **L14 defers to §4c by its own words**; Q6 is resolved *"confirmed as proposed"*. One `<Heading>` whose **text changes** — not two — because `Heading` renders an `<h1>` and a second would break a locked a11y rule (**D-T50-2**, which was forced by measurement, not preference) |
| **OQ-T52-2** | **A dropped connection shows a neutral retry line**, the form stays mounted. R14 names no failure behaviour, a transport failure is **orthogonal to L4** (it says nothing about whether the address is registered), and **D-T50-1** reversed the opposite choice on the sibling screen for a reason that applies verbatim: *a visitor who presses a button and sees the screen go back to how it looked has been told nothing at all* |
| **OQ-T52-3** | **No link is added.** `tasks.md` commissions none, and T52's scope is the form. The screen is reachable by URL only. **This is a known hole in the demo path, not an oversight** — recorded, not closed |

## 2 · The screen, frozen

```tsx
const [email, setEmail] = useState('');
const [status, setStatus] = useState<'idle' | 'submitting' | 'acknowledged' | 'unsent'>('idle');
const headingRef = useRef<HTMLHeadingElement>(null);

useEffect(() => { if (status === 'acknowledged') headingRef.current?.focus(); }, [status]);

function submit(): void {
  setStatus('submitting');                    // ALSO clears a previous 'unsent' — not redundant
  void createPublicAuthApi()
    .POST('/accounts/password-reset-request', { body: { email }, parseAs: 'stream' })
    .then(() => setStatus('acknowledged'), () => setStatus('unsent'));
}
```

**`response.ok` is deliberately not consulted** — the mirror of `VerifyScreen`, where R4 required failure to be
distinguished. Here one acknowledgement is the requirement, so **every resolved reply acknowledges**.
`parseAs: 'stream'` is mandatory (AC7): without it a `200` whose body is not valid JSON takes the reject arm,
which is the defect T51 and T175 each paid a task to fix.

**Announcement**: focus move for the acknowledgement, `StatusRegion` for `'submitting'` and `'unsent'`. **Not
interchangeable** — a live region alone leaves focus on `document.body` after the submit button unmounts; a
focus move alone says nothing while the request is in flight.

### Catalogue — `passwordResetRequest`, flat, seven keys

| Key | English |
|---|---|
| `heading` | `Reset your password` |
| `emailLabel` | `Email address` |
| `submit` | `Send reset link` |
| `submitting` | `Sending…` |
| `acknowledgedHeading` | `Check your email` |
| `acknowledgedText` | `If your email is registered, a reset link has been sent.` |
| `unsent` | `We could not send that just now. Try again.` |

Ratified as Phase 3 proposed: it is the **resend voice exactly**, which is correct for a sibling surface.
`'Check your email'` **duplicating `registerSent.heading` is fine and already true** of the catalogue — measured,
not assumed.

### Route

```ts
export const PASSWORD_RESET_REQUEST_PATH = 'reset/request';   // same string value, promoted
```

Attached in `appRoutes` with the house `// TASK 52` comment. `'reset'` stays an un-promoted literal — task 53's.

---

## 3 · Deviations from the Phase 3 design

### **D-T52-1 — Files to Modify is SIX, not three, and the three missed are the three that fail first**

Phase 3 lists `routes.tsx`, `catalogue.ts`, and T175's scan. **Measured, three more must change**, and each was
checked against source rather than reasoned about:

| File | Why it fails | Evidence |
|---|---|---|
| `src/app/screensRenderThroughDesignSystemPrimitivesOnly.test.tsx` | **At least THREE separate pins** | `:266` pins the two lists **at frozen sizes**; `:337` *"names every file that imports the primitive barrel"* and the new screen imports it; `:926` *"keeps the catalogue's key set **exactly as it was**"* — set equality, so **all seven keys** must be listed |
| `src/app/publicRouteSetIsExactlyTheDeclaredSet.test.tsx` | The element-attachment case **enumerates paths in its own title** and asserts *"**and to nothing else**"* | `:722`, grown by T50 — *"GROWN BY TASK 50, the fourth time"*. T52 is the fifth, **and the case title itself must change** |
| `src/auth/loginChallengeRunsInsideSasFlowAndResumesAtCallback.test.tsx` | The credential scan's form pattern is **`/<\s*form\b/gi` — case-insensitive**, so the `<Form>` **primitive** trips it. It has a per-file allow-list with `count: 1` rows | `:263` the pattern, `:309` *"TASK 50. The second `<Form>` RENDER SITE… mounting the primitive counts"*. **T52 is the third**, and needs a fourth row |

**This blind spot has now recurred five times, and the primitives file records the first four itself:**

> *"PHASE 3's F9 SAID THIS FILE NEEDED NO CHANGE and the freeze accepted that. It was wrong… the next task that
> adds a word will meet the same surprise."* — then T47 did it, then T48's plan, then **T49's frozen brief listed
> the whole file under "Files NOT to Modify"**, on a measurement *"right about the render-site pins… and blind to
> this one, sitting in the same file."*
>
> **"Knowing a file holds four pins is not knowing which one your change trips"** (D-T49-3).

Phase 3 is occurrence five and **this freeze is the first to catch it before implementation** rather than within
a minute of running the suite. The lesson is now operational, not advisory: **before the catalogue step and
before attaching the element, enumerate the pins in each file by case title and say which one each change
trips.**

**One open item this leaves:** `:279` refers to a *separate* instrument that *"checks every key is RENDERED"*,
distinct from the set-equality pin. **Its location is not yet established — Phase 5 must find it before writing
the catalogue step**, because all seven keys will have to be provably rendered, and a plan that assumes
otherwise is the exact mistake T48's plan made.

### **D-T52-2 — Phase 3's test-design headers mis-group the ACs; the mapping table governs**

Its section headers read *"AC4/AC5/AC6 — one correct public call"*, but AC4 is **byte-identical uniformity** and
AC6 is **the email reaches no URL or storage**. Its own final mapping table is correct and **is the authority**;
the section headers are discarded to prevent a Phase 6 that builds to the wrong grouping.

### **D-T52-3 — the helper list is trimmed to four**

Phase 3 names six, two of them the same thing (`installTransport(status, body, contentType)` and
`installTypedTransport(status, body, contentType)`). Frozen set: **`installBrowserRequest`**,
**`installTypedTransport(status, body, contentType)`**, **`installNeverAnswering`** (in-flight), and
**`installTransportFailure`** (rejects). **FU-T43-2 is restated, not worsened**: this is the fourth copy, and
there is still no shared test-util module.

---

## 4 · Acceptance criteria — unchanged from Phase 2

AC1–AC13 stand exactly as the TIB froze them. AC4 is the task: **the acknowledgement is byte-identical across
every outcome the server can return.** AC8 is L4's timing clause, testable only because `agents.md` bounds it to
**the SPA's own rendering**.

## 5 · Required tests — frozen

| | |
|---|---|
| **Named** | `passwordResetRequestAcknowledgementIsUniform` |
| **The uniformity table** | **At least 13 arms** across what a proxy or edge can return — valid JSON, a different `message`, no body, empty string, HTML, plain text, truncated JSON, `204`, `400`, `404`, `429`, `500`, `503`, `302`-with-body, `304`. Each compared on **markup, pathname, focus target and region text** |
| **The baseline, guarded** | Non-empty, **carries the expected text**, and **has no `<form`** — because a uniformly broken screen also renders identically (T51) |
| **Provenance** | The acknowledgement must be shown to come from the **catalogue**, not from a literal that happens to match (T45) |
| **The email** | Query string, hash, full URL, `sessionStorage`, `localStorage`, **each with a control proving the spy intercepts** — an instance spy on jsdom storage does **not** intercept, which made a T42 claim vacuous from birth (and it survived because the assertion was **negative**) |
| **The public claim** | No `Authorization` header **even with session storage seeded**; one case with no session at all |
| **Must not regress** | `routesResolveUnderAppPrefixOnly` (hard-codes the spellings at `:242` — an **independent** guard, unchanged), the design-system `h1` pins, the timer scan, T175's scan |

## 6 · Carried open items

**OQ-T52-3** (no link; a known demo-path hole), **FU-T43-2** (fourth helper copy), **OQ-T43-9** (a failure word
for `RegisterScreen`), **OQ-T175-5**, **OQ-T175-6**.

---

**FROZEN. Phase 5 — Implementation Plan — may begin, and must first locate the every-key-is-rendered
instrument (D-T52-1).**
