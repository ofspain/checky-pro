# frontend · T08 · Phase 6 — Implementation Notes

Consumes `04-frozen-task-brief.md` and `05-implementation-plan.md`. Date: 2026-10-24, macOS host.

## What changed

One file: `frontend/src/api/rateLimitedResponseShowsGenericCopyWithoutReplay.test.tsx`. **No production file was
created or modified** (owner decision 2). T07's 29 cases are unedited.

| Change | Content | AC |
|---|---|---|
| Imports + `repoRoot` | `node:fs/promises`, `node:path`, and the `expect.getState().testPath` walk, copied from T05 | AC7 |
| `Sent.body` | `installTransport` now records each request's body (`''` for GET), inside `try/catch` recording `'<uncloneable>'` when a case makes `clone()` throw | AC2, AC4 |
| `describe('no POST replay', …)` | 18 cases (see below), nested inside the existing describe so the named test string is unchanged | AC1–AC9 |

### The cases
| Case | Content | AC |
|---|---|---|
| `it.each(PUBLIC_POSTS)` ×5 | each public POST gets a 429 → one transport call, `POST`, the expected path, **no bearer**, a non-empty body, and the T07 copy on screen | AC1, AC2 |
| `it.each(PROTECTED_POSTS)` ×4 | the four protected shapes — JSON body (`/api-keys`), sensitive body (`/accounts/me/password`), no body (`/accounts/me/mfa/totp`), path parameter (`/admin/accounts/{accountUuid}/activate`) — one call each, `Bearer token` | AC1, AC2 |
| no notice | all nine driven through `createAuthApi(boundary)` with no notice: still one call each | AC1 |
| no renewal on a 429 | a **renewal-capable** session: a 429 on a protected POST neither calls `renew()` nor re-sends | AC1, AC3 |
| the 401 re-send boundary | 401 → one renewal → one re-send → 429 on the re-send: exactly `['POST /api-keys Bearer old', 'POST /api-keys Bearer new']`, identical bodies, nothing afterwards, the copy shown. Carries the R23-scope rationale as a comment | AC3 |
| `clone()` throws | the 429 comes back unchanged, one call, body recorded as `'<uncloneable>'`, notice still raised | AC4 |
| middleware shape | `Object.keys(notice.middleware)` is exactly `['onResponse']`, and calling it returns `undefined` | AC5 |
| no timer, no resumption | unmounted: no `setTimeout`/`setInterval` while handling the 429, then an hour of faked time and a `dismiss()` add no transport call | AC6 |
| the drift guard | parses every `post:` out of `auth.yaml` and asserts the set equals `EXERCISED ∪ UNEXERCISED` | AC7 |
| the marker body | a marker string in `currentPassword` reaches the transport and nothing else: no console level, no storage, no cookie, no URL change, and not the rendered notice | AC8 |
| T05 parity | a 401 on a POST still renews once and re-sends once to a 201, with identical bodies and no notice shown | AC9 |

## Verification
- The file alone: **47/47** (T07's 29 + T08's 18). Whole suite: **145 passed, 1 failed (146)** — the failure is the
  pre-existing environmental `lintRejectsHandWrittenBackendFetch` AC4 case (this host's oxlint prints no summary
  line), unchanged by this task.
- `tsc --noEmit` exit 0 — including `api.POST('/accounts/me/mfa/totp')` with no init argument and the
  path-parameter call, so neither needed a cast or a `{}` placeholder. `oxlint` exit 0. `vite build` exit 0.
- **Mutations: 18/18 caught** (T07's 14 re-run plus four for T08). Each applied to the real file, suite run, then
  reverted with `git checkout --`:
  - **M15, the boundary treating 429 like 401** → 7 cases, including the renewal-capable one;
  - **M16, the 401 re-send sent twice** → 6 cases;
  - **M17, a timer-driven retry 1 s after a 429** → 2 cases (the timer spy and the hour of faked time);
  - **M18, `auth.yaml` gaining a POST** → exactly 1 case, the drift guard, and nothing else.
- `git status`: only the test file. `contracts/api/auth.yaml` is byte-identical after the M18 run.

## Deviations
1. **A case was added that the frozen brief did not list.** Designing M15 showed every T08 case used a client whose
   `renew()` rejects, which would **hide** a renewal attempt on a 429: the mutant would have passed. The new
   renewal-capable case closes that hole (plan step "A mutant that cannot be caught" anticipated it). It asserts
   strictly more than the brief required; nothing was weakened.
2. **A T07 helper was extended.** `installTransport` records a `body` field. T07's cases do not read the record's
   shape beyond the fields they already used, and all 29 were re-run green before any T08 case was written
   (plan step 1). The frozen brief's "T07's 29 cases stay exactly as they are" is satisfied — none was edited — but
   the shared helper is no longer T07-only, and phase 7 records that.
