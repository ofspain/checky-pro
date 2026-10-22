# @themistra/api-client

The typed client every SPA backend call goes through (`spec/frontend/design.md` L6).

- `src/generated/` is generated from `contracts/api/auth.yaml` by
  [openapi-typescript](https://openapi-ts.dev). **Do not edit it by hand.** CI regenerates it and fails if the committed
  copy differs.
- `src/index.ts` is the only hand-written source. It re-exports the types and `createAuthClient()`, an
  [openapi-fetch](https://openapi-ts.dev/openapi-fetch/) client on relative, same-origin paths.

## Regenerate after a contract change

```sh
pnpm -C libs/ts/api-client generate
```

`typescript@5.9.3` is here only because the generator needs the TypeScript 5 compiler API. The project itself compiles
with TypeScript 7 (`frontend/`), and the generated code must type-check there. Note that `pnpm -C libs/ts/api-client exec tsc`
would run 5.9. To check the package with the project compiler, run
`frontend/node_modules/typescript/bin/tsc --noEmit -p libs/ts/api-client/tsconfig.json`.
