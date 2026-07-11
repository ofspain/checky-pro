# Frontend

React + TypeScript, mobile-first PWA. API access exclusively through the generated
client in `libs/ts/api-client` — no hand-written fetch calls to backend routes.
Served via CloudFront; API calls go through the edge (WAF → ALB → ingress-nginx).
