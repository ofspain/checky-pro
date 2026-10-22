# Frontend

React + TypeScript, mobile-first PWA. API access exclusively through the generated
client in `libs/ts/api-client` — no hand-written fetch calls to backend routes.
Served via CloudFront; API calls go through the edge (WAF → ALB → ingress-nginx).

## Run and test locally

Everything runs in Docker (`infra/local/`, see its README); nothing is installed on the host.

```
infra/local/bin/up --frontend      # the whole platform plus this dev server, with hot reload
infra/local/bin/status --wait      # until every service is ready
```

The app is at http://localhost:5173/app/, the same origin as every API (the edge routes `/app` here).

```
docker compose -p themistra-local -f infra/local/compose.yaml exec frontend pnpm -C frontend test
docker compose -p themistra-local -f infra/local/compose.yaml exec frontend pnpm -C frontend build
```

