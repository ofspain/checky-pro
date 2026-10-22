# frontend · T03 · Phase 12 — Specification Verification

Date: 2026-10-22. Consumes Phases 0–11. Verified against:
- `tasks.md` task 3;
- `design.md` L6, L9, L10 and §6;
- `requirements.md` R20;
- `package.md` §8;
- the FROZEN brief;
- `agents.md`, "API access".

## Phase 11 gaps (Kimi), resolved before verification

Kimi raised 8 gaps: 5 were accepted and 3 rejected. Only the test file changed; there was no production change.

| Gap | Decision | Change | Break caught |
|---|---|---|---|
| 1 `components` re-export untested | ACCEPTED | Case 3: `expectTypeOf<components['schemas']['AccountResponse']>().toEqualTypeOf<…/accounts/me 200 body>()`. Case 6 also asserts the entry's re-export line. | g (`components` dropped): typecheck plus case 6 |
| 2 Problem schemas | **REJECTED (third time; premise false).** `auth.yaml` has 0 `problem` occurrences and no 4xx/5xx responses (Phase 4 finding 2, Phase 9 Kimi 6). | None | — |
| 3 Assert the literal `baseUrl: ''` | **REJECTED (settled at Phase 4, finding 10).** The probe showed `''`, `'/'` and an omitted value send identical requests. The contract is the requested URL, and cases 4 and 5 assert it exactly; that catches every absolute or prefixed base (breaks d and e). A literal check would pin an implementation detail with no behavioural meaning. | None | — |
| 4 Only GET is exercised | ACCEPTED | New case 5: `POST /accounts` with a JSON body. It asserts the method, the exact same-origin URL, `Content-Type: application/json` and the body round-trip. Case 3 also type-checks the POST request body against `RegisterAccountRequest`. | d, e, i |
| 5 Hard-coded `tsc` path | ACCEPTED, adapted | `typescript` exports no `bin/tsc` (`ERR_PACKAGE_PATH_NOT_EXPORTED`, checked). So the compiler is resolved through `createRequire(frontend/package.json).resolve('typescript/package.json')` plus `bin/tsc`, which follows pnpm's layout whatever the hoisting. The `Version 7.0.2` assertion stays. | b |
| 6 Deep imports | **REJECTED (settled at Phase 4, finding 7).** The `exports` map makes deep imports fail to resolve. Phase 6 proved this with `TS2307`. T04 owns the lint rule. | None | — |
| 7 Client methods | ACCEPTED | Case 4: `GET`, `POST`, `PUT`, `PATCH`, `DELETE` and `use` are functions. | i (client narrowed to `GET`/`use`): typecheck plus cases 4 and 5 |
| 8 `generate` script untested | ACCEPTED | Case 1 asserts `scripts.generate` is exactly the invocation it reproduces. CI's drift step runs that script itself. | h (output path changed) |

## Final suite
- `generatedClientCompiles`: 6 cases.
- `routesResolveUnderAppPrefixOnly`: 9.
- `shellBuildsAndMounts`: 7.
- **22/22 passed.** `typecheck` clean, `build` clean, `pnpm -C libs/ts/api-client typecheck` (TS 7.0.2) clean.

All 9 mutation breaks fail at least one check. Each run included `pnpm -C frontend typecheck`, because the type-level
assertions fail there.

| Break | Typecheck | Failing cases |
|---|---|---|
| a) Hand edit in `auth.ts` | ok | 1 |
| b) Type error in `index.ts` | FAILED | 2 |
| c) `/accounts/me` removed | FAILED | 1, 3 |
| d) Absolute `baseUrl` | ok | 4, 5, 6 |
| e) `/api` `baseUrl` | ok | 4, 5 |
| f) `/userinfo` path added | ok | 1, 3 |
| g) `components` not re-exported | FAILED | 6 |
| h) `generate` output path changed | ok | 1 |
| i) Client narrowed to `GET`/`use` | FAILED | 4, 5 |

## Traceability matrix
| Requirement | Implemented? | Evidence (file:line) | Test? | Missing? | Deviation? |
|---|---|---|---|---|---|
| Task 3: generated from `auth.yaml` (L6, §6) | Yes | `libs/ts/api-client/package.json` `scripts.generate`; `src/generated/auth.ts` (generator header, 1375 lines) | `generatedClientCompiles.test.ts:62` (byte-identical regeneration plus script string) | No | No |
| Task 3: compiles | Yes | `libs/ts/api-client/tsconfig.json` (`strict`); the `typecheck` script runs TS 7.0.2 | `:83`; `frontend` `typecheck` through the workspace import | No | No |
| R20: typed `GET /accounts/me`, no SAS endpoint | Yes | `auth.ts` `paths['/accounts/me']`; no `/oauth2`, `/.well-known`, `/userinfo`, `/login`, `/logout` or `/connect` keys | `:98` | No | No (profile and `/userinfo` reads are T30/T31) |
| L6: every operation, callable client | Yes | 34 `operations` = 34 `operationId`s; `src/index.ts:8` `createAuthClient()` | `:98`, `:123`, `:146` | No | No |
| L9: single origin, relative | Yes | `index.ts` `baseUrl: ''` | `:123` (GET), `:146` (POST): exact `${origin}/…` | No | No |
| L10: no env, no secret | Yes | `index.ts` | `:160` | No | No |
| L6: generated in CI, drift fails | Yes | `.github/workflows/ci.yml:57-61`; `.gitattributes:6` (LF) | CI step; case 1 locally | No | No |
| Not hand-edited (§6) | Yes | README notice; generator header | `:62` (break a) | No | No |
| `agents.md`: RFC 9457 problem types | n/a for T03 | `auth.yaml` declares no error responses | — | No | Recorded: problem-body typing belongs to the T05–T08 wrapper |

Recorded deviations, all justified and none against a lock:
- `typescript@5.9.3` is a generator-only devDependency, because the generator needs the TS 5 compiler API.
- The api-client keeps `skipLibCheck: false` (Phase 9).
- RFC 9457 types are not generated, because the contract has none.

## Principal-engineer answers
1. **Fully complete?** Yes. `libs/ts/api-client` is generated from `auth.yaml` by a committed command, compiles under the
   project's TypeScript 7.0.2 `strict`, exposes every operation through a same-origin client, and is drift-checked in CI.
2. **Every acceptance criterion?** Yes. AC1–AC5 are automated. AC6 is the CI step, and case 1 reproduces it locally.
3. **Any lock violated?** No. L6, L9, L10 and L13 are upheld. No env key, no absolute URL, no hand-written backend fetch.
4. **Remaining risks:**
   - The generator depends on TS 5.9. If `openapi-typescript` ever drops `^5.x`, or the generated output stops
     type-checking under TS 7, cases 1 and 2 fail loudly; the tooling is then upgraded in its own change.
   - The contract has no error-response schemas, so T05–T08 type RFC 9457 bodies by their standard members. That is a
     contract gap the backend may want to close later. It is not a T03 blocker, and per the standing rule it stays open
     until it blocks something.
   - CI hasn't run the new step yet; it runs on the next PR or `main` push. The same commands pass in the container.
   - Only `auth.yaml` is generated. `payments.yaml` and `notifications.yaml` join when their tasks need them.

**PASS:** the generated client is reproducible from `auth.yaml`, compiles under TypeScript 7, calls the page origin only,
and all 22 tests pass, with all 9 mutation breaks caught.
