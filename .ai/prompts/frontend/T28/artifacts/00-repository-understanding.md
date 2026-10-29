# frontend · T28 · Phase 0 — Repository Understanding

| | |
|---|---|
| **Service** | `frontend` |
| **Task** | T28 — Unenrolled privileged refusal copy |
| **Model** | Claude Opus 5 |
| **Produces** | `artifacts/00-repository-understanding.md` |

**Task (verbatim, `spec/frontend/tasks.md` task 28):** *Unenrolled privileged refusal copy. A SAS refusal for
an unenrolled MERCHANT or ADMIN shows the wrong-password copy. Verifies
`sasRefusalForUnenrolledPrivilegedAccountShowsWrongPasswordCopy`. Cites R10, L5.*

---

## The finding that shapes this task: the refusal never reaches the SPA

Read from `services/auth/` rather than assumed, because the task statement's "shows the wrong-password copy"
reads as though the SPA renders it.

**`TotpAuthenticationProvider` refuses at the AUTHENTICATION step, not the authorization step.** Line 100–101:

> `// R24: MERCHANT/ADMIN with no confirmed enrollment is blocked outright.`
> `throw new BadCredentialsException("Bad credentials");`

And every other failure mode in the same class throws **the identical exception with the identical message** —
unknown user (`:118-119`), bad password (`:125`), bad or missing TOTP (`:132`). Its own class comment says so
in terms, and records that the alternative was considered and rejected:

> *"Every failure mode below throws the identical `BadCredentialsException` with the identical message: wrong
> password, wrong/missing MFA code, and 'MERCHANT/ADMIN without confirmed enrollment' are deliberately
> indistinguishable to the caller (R24/R25/R29, L5 extended per `agents.md`'s enumeration-safe posture). A
> distinct 'please enroll' signal was considered and rejected: reachable only after a correct password, it
> would work as a password-correctness oracle for an attacker probing a known MERCHANT/ADMIN email — recorded
> as an accepted rollout gap, not fixed by weakening this uniformity."*

**A `BadCredentialsException` from an `AuthenticationProvider` during form login is a login failure.** Spring
Security's failure handler redirects to `/login?error`. The visitor **stays on the SAS-hosted login page and
never reaches `/app/callback`.** So the copy the task names is **SAS's own page copy**, which L2 puts on the
server, and R10's literal subject — "the SPA SHALL show the same uniform message" — is not reached by this
path at all.

**This is the same shape as T27's premise, one layer down.** T27 found the second factor is a field, not a
step. T28 finds the refusal is a login failure, not an authorization error. Both mean the SPA's verifiable
claim is about what it does **not** contain and **cannot** distinguish.

### What that leaves as genuinely SPA-observable — and one of them is new

| Claim | SPA-observable? | Owner |
|---|---|---|
| The refusal's own copy on `/login` | **No** — server-rendered, L2 | backend |
| The SPA never renders enrollment-aware copy or an enrollment-during-login path | **Yes**, structurally | **T28** |
| `/login` can never be answered by the SPA or its worker | **Yes**, cited | R57's task, cited by T27 |
| An authorization **error response** arriving at `/app/callback?error=…` discards uniformly | **Yes** — and this is the one path where a refusal *could* reach the SPA | partly T17/T18; **T28** owns the R10 reading |
| Every SPA-rendered identity error is identical across account states | **Yes** | R30 / task 58, cited |

## The error path, and how much of it is already owned

`CallbackScreen.tsx:81-104` handles an authorization error response:

- `const error = params.get('error')?.trim() ?? null;` (`:86`)
- `if (error !== null && error !== '') { discard(); return; }` (`:101-104`)
- Its comment already records the design: an error arrives **in place of** `code` (RFC 6749 §4.1.2.1), a URL
  carrying both is malformed, nothing in the package surfaces the error itself because L4 forbids copy that
  distinguishes one failure from another — **and it names this task**: *"task 28 maps even a SAS refusal onto
  the generic wrong-password copy."*

So the production code for T28 **already exists and already cites T28**. Measured coverage of `error=` across
the existing suite: `callbackWithMismatchedStateIsDiscarded` (4 mentions),
`callbackValidatesNonceAndKeepsAccessTokenInMemory` (2), and one each in
`stateSurvivesRedirectAndIsConsumedOnce`, `tokensAreNeverWrittenToAnyStorage`,
`returnTargetOutsideAllowlistFallsBackToHome`. **None of them is about R10's uniformity claim** — they use
`error=` as one more way to reach the discard path, which is a different question from "is a refusal
indistinguishable from a wrong password".

**No file named for this task exists:** `ls frontend/src/auth | grep -i refusal` is empty.

## The copy that exists, and its shape

`src/i18n/catalogue.ts` holds every user-facing string. The relevant ones:

- `signIn.discarded` — *"Your sign-in did not complete. Try again."* (`:18`), with its own comment: *"Neutral
  by requirement: it names no cause, so a missing parameter, an expired attempt and a forged link are
  indistinguishable to the visitor (L4)."*
- `signIn.failed` — *"Sign-in could not be started. Try again."* (`:15`) — a **different** sentence, for a
  hand-over that never left.
- `catalogue.ts:8-9` already states the rule: *"nothing here discloses whether an account exists or what the
  sign-in page will ask for (L4), and nothing promises a password step, because which factors are required is
  the server's to decide (L2)."*

`signInNotice.ts:13` is `export type SignInNotice = 'discarded'` — a **one-member union**, with a comment
calling it a forward-compatibility hook rather than a commitment, and noting that task 18 may add a value or
reuse this one *"since L4 forbids copy that distinguishes the two cases"*. T27 measured the consequence: the
notice is a token, not copy, so asserting one notice equals another cannot fail.

## The single `discard` closure, which is the mechanism under test

`CallbackScreen.tsx:89-99` is one closure shared by all five rejection paths, and its header (`:35-38`) says
why: *"A missing `state`, a mismatched `state`, a refused exchange and a bad `nonce` are four different faults
and one single outcome… They share the one `discard` closure below rather than four copies of it, so they
cannot drift apart (L4)."* It does three things: `clearAccessToken()`, `setSignInNotice('discarded')`,
`navigate('/sign-in', { replace: true })`.

So R10's SPA-side claim reduces to: **every way a refusal can present itself ends in that closure, and
nothing anywhere distinguishes it.**

## What T28 can reuse from T27

T27 built, three days of pipeline ago, instruments that fit this task almost exactly:

- the **catalogue scan** over every user-facing string, with two grammars (identifier keys, prose values) and
  a deliberate `verification code` omission already justified and held by a control;
- the **source scan** with the comment-stripped walker and the permitted-hit table;
- the `Outcome` struct and `arriveAt` harness for comparing two flows field by field;
- the measured facts: jsdom fires no click for a keydown; `/app/home` renders nothing; `document.cookie` is
  empty; the discard path lands on `/app/sign-in` with a live region and copy.

T28's new work is the **enrollment-specific** half: the words `enroll`, `enrollment`, `set up`, `register your
authenticator` are not in T27's MFA pattern, and they are exactly what R10 forbids.

## Environment

- `npx -y pnpm@12.14.0 exec …` from `frontend/`; **the exit code is the verdict**.
- Known environmental failure, pre-existing and unrelated:
  `src/api/lintRejectsHandWrittenBackendFetch.test.ts:118`.
- Suite baseline at T27 close: **1295 passed / 1 failed (1296)**.
- The local Docker stack is **down** (brought down at the owner's request on 2026-10-29);
  `infra/local/bin/up --frontend` restores it. Not needed for this task.

## Open questions for Phase 1

1. **Does R10 have any SPA-observable subject at all beyond absence and the `error=` path?** Phase 0's reading
   is no, and the task's value is pinning that the one reachable path is uniform.
2. **Does T28 own the `error=` discard, or does T17?** T17 owns "a callback with no usable `state`". The
   `error=` branch returns before the `state` check, so it is a distinct path; T28 should own the R10 reading
   of it and cite T17 for the rest.
3. **Is the backend's accepted rollout gap** (the password-correctness oracle, recorded in
   `TotpAuthenticationProvider`'s comment) in scope? No — it is server-side and already a recorded decision.

---

**Phase 0 complete.**
