# frontend · T03 · Phase 9 — Review Resolution

Date: 2026-10-22. Consumes `07-self-review.md` and `08-independent-review.md` (Kimi 2.7, 7 findings). Every claim was
checked against the working tree at `5d1351a7`.

| Source | Comment | Decision | Change |
|---|---|---|---|
| Kimi 1 | `ci.yml` and the drift step are missing | **REJECTED (does not reproduce).** `.github/workflows/ci.yml` exists, and lines 58–61 hold the "Generate API client and fail on drift (L6)" step. Kimi's `glob .github/workflows/*` returned nothing, as in the T01 Phase 8 review (finding 9 there): its file search doesn't see `.github`. | None |
| Kimi 2 | The named test isn't written yet | **ACCEPTED, scheduled.** It is written in Phase 10. | Phase 10 |
| Kimi 3 | `skipLibCheck: false` in the api-client is brittle | **REJECTED, with a reason.** Kimi is right that `frontend/tsconfig.json` sets `skipLibCheck: true`. The stricter package setting is deliberate, though: it is the one place where TS 7.0.2 type-checks `openapi-fetch`'s and `openapi-typescript-helpers`' own declarations. That is the compiler-compatibility risk frozen finding 1 asks us to guard. If a patch release ever broke under TS 7, this check would fail first, and loudly, instead of showing up as a confusing consumer error. | None |
| Kimi 4 | No `.gitattributes` rule | **REJECTED (does not reproduce).** `.gitattributes:6` is `libs/ts/api-client/src/generated/** text eol=lf`. | None |
| Kimi 5 | No `typecheck` script | **ACCEPTED.** | `package.json`: `"typecheck": "../../../frontend/node_modules/typescript/bin/tsc --noEmit -p tsconfig.json"`, which runs TS 7.0.2. It passed in the container. The README now points to `pnpm -C libs/ts/api-client typecheck`. |
| Kimi 6 | No assertion that problem types are generated | **REJECTED (premise false; already settled at the Phase 4 gate, finding 2).** `auth.yaml` has zero `problem` occurrences and declares no 4xx/5xx responses, so there is nothing to generate or assert. Problem-body typing belongs to the T05–T08 wrapper. | None |
| Kimi 7 | `baseUrl` isn't guarded | **No change**, as Kimi concludes. The JSDoc explains it. Phase 10 asserts the exact requested URL, which is stronger than a source check (Phase 4, finding 10). | None |
| Self 1–6 | — | As recorded in Phase 7. Self 2 (two `tsc` binaries) is now also covered by Kimi 5's script. | — |

After resolution: `pnpm -C libs/ts/api-client typecheck` is clean under TS 7.0.2.
