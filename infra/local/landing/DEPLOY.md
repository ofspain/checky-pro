# Serving the demo on a public URL

The landing page is public. **The rest of the local stack must never be.**

## Why there are two sites in the Caddyfile

`:5173` is the whole local platform — auth-service's `/login`, `/oauth2/*`, `/admin/*`,
`/accounts/*` and `/api-keys/*`, the merchant API, the notification stream and the SPA — all
running on local development credentials that protect nothing. Tunnelling that port would publish
an admin path and a token issuer.

`:8080` is the public surface and nothing else. Four routes, explicitly listed, and a `404` for
everything else:

| Route | What it is |
|---|---|
| `/` | the landing page |
| `/logo.png` | its only asset |
| `/r/*` | receipt links (the same file, which reads the id from its own path) |
| `/api/v1/verifications`, `/api/v1/verifications/*` | the check, and the receipt read |

A route cannot leak out by being added to the local edge. It has to be written into the public
site as well, on purpose.

## Publishing it

Any tunnel or ingress works, pointed at **`:8080`, never `:5173`**. Nothing needs installing:

```sh
docker run -d --name checky-tunnel \
  --network themistra-local_default --restart unless-stopped \
  cloudflare/cloudflared:latest tunnel --no-autoupdate --url http://edge:8080

docker logs checky-tunnel 2>&1 | grep -o 'https://.*trycloudflare.com'
```

Stop it with `docker rm -f checky-tunnel`.

A quick tunnel's URL is random and changes every restart. For a stable address on your own domain,
point ngrok or a named Cloudflare tunnel at the same port — the surface is identical either way.

## Check the surface after publishing, every time

```sh
URL=https://your-public-url
for p in login oauth2/authorize admin accounts api-keys app/ \
         api/v1/invoices api/v1/receipts notifications/x actuator/health userinfo; do
  printf '%-34s ' "/$p"; curl -s -o /dev/null -w '%{http_code}\n' "$URL/$p"
done
```

**Every line must say 404.** If one does not, take the tunnel down before anything else.

## What is actually exposed

- One unauthenticated write: the check. It causes one crypto-service read (which fans out to three
  providers) and one append-only row. Budgeted at 10 per client per minute.
- One unauthenticated read: a receipt, by a random UUID. Budgeted at 60 per client per minute.
- The table grants `INSERT` and `SELECT` only, so a stored answer cannot be altered or removed.
- No account, no email, no IP, no cookie, no wallet connection, nothing signed.

## Known limits of this deployment

- **The chain is a local anvil node.** Restarting it starts from genesis, and every hash verified
  before then becomes `NOT_FOUND`. That is accurate rather than broken, but it means a link shared
  on Monday may not verify on Tuesday.
- **Rate-limit counters are in memory**, so they are per instance. One instance is what this is
  sized for.
- **The three providers are one anvil node**, so `HELD` cannot occur here.
- **The receipt is not signed.**
