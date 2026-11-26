# Bringing the demo back up

The stack is torn down between demos because it costs about **USD 0.05/hour** — roughly $35 a
month — to leave running, and a demo box that nobody is looking at is pure burn.

This file is the thing to open next time. It exists because the expensive parts of the first
deployment were **not** the infrastructure: they were a 54-minute nameserver delegation, a
certificate that could only be issued once a zone went Active, and four values transcribed by hand
from CloudFormation outputs into two config files. **None of the first two ever has to happen
again.** The fourth is now a script.

---

## The short version

```sh
export AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=... AWS_DEFAULT_REGION=eu-west-1

cd infra/deploy/cdk  && ./cdk deploy          # ~4 min
cd ..                && bin/from-stack.sh     # writes inventory.ini, patches .env, prints the IP
#                       ^ then do the one manual step it prints: two Cloudflare A records
cd ..                && bin/push-images.sh    # ~2 min from local images, ~13 min if rebuilding
cd deploy/ansible    && ./play                # ~6 min, ends by proving the page works
```

About **twenty minutes**, most of it waiting for three JVMs to start. One dashboard visit.

---

## What survives a teardown — do not redo any of this

| Survives | Where it lives | Why it matters |
|---|---|---|
| **The Cloudflare zone** `checkychain.com.ng`, Active | Cloudflare account | This is the big one. The nameserver change at DomainKing took **54 minutes** to appear in the `.ng` registry, and a registrar panel reports success the whole time. It is done. The zone stays Active through any number of teardowns because it is a DNS fact, not an AWS one. |
| **Nameserver delegation** `curt` / `ligia` `.ns.cloudflare.com` | DomainKing (HostAfrica) + the `.ng` registry | Never touch this again. In particular **never click “Revert Nameserver Change”** in a DomainKing email — that undoes the migration. |
| **The two DNS records** (apex `@` and `www`), proxied | Cloudflare → DNS | The *records* survive. Only the IP **value** inside them is wrong after a rebuild. Keep the proxy on (orange cloud); grey-cloud exposes the origin and the security group will refuse the traffic anyway. |
| **SSL/TLS mode: Full (strict)** | Cloudflare → SSL/TLS → Overview | Anything less and the Origin CA certificate is never validated, so installing it proves nothing. |
| **The Origin CA keypair**, valid to **2041-11-22** | `infra/deploy/secrets/origin.pem` + `origin.key` (gitignored, mode 0600/0644) | Reusable verbatim, for fifteen years. It is bound to the hostname, not to the instance, so a new box uses the same pair. |
| **`infra/deploy/.env`** | gitignored, local, mode 0600 | All sixteen values, including the three Sepolia RPC URLs that were chosen by testing *from the box* (two candidates were rejected: `1rpc.io` returns 403 to AWS, thirdweb rate-limits). Only **one line** changes per rebuild — see below. |
| **CDK bootstrap** (`CDKToolkit`, bootstrapped 2026-05-21) | the AWS account | `./cdk bootstrap` is a once-per-account/region thing. Skip it. |
| **Local arm64 images** for `checky-{auth,crypto,payment}` | this laptop's Docker | **This is what makes a rebuild twenty minutes instead of forty.** `cdk destroy` deletes the ECR repositories (`empty_on_delete=True`), so the registry comes back empty — but the local images are re-pushed rather than rebuilt. |
| **Toolchain images** `checky-cdk`, `checky-ansible` | this laptop's Docker | Save about six minutes of image building. |
| **`cdk/cdk.context.json`** | gitignored, local | The cached default-VPC lookup. Harmless if lost; CDK re-looks-it-up. |

> ### ⚠ Never run `docker system prune`
> It is the single action that turns a twenty-minute rebuild into a forty-minute one. The three
> application images and the two toolchain images are all untagged-adjacent, dangling-ish and
> exactly the sort of thing a prune eats. The images are the only copy outside ECR, and ECR is
> deleted with the stack.

---

## What changes every rebuild — exactly four values

`cdk destroy` releases the Elastic IP, terminates the instance, deletes the transfer bucket and
schedules the KMS key for deletion. So four things come back different:

| Output | Goes to | Who does it |
|---|---|---|
| `PublicIp` | the two Cloudflare **A records** | **you**, in the dashboard — the one irreducibly manual step |
| `InstanceId` | `ansible/inventory.ini` → `ansible_host` | `bin/from-stack.sh` |
| `SsmTransferBucketName` | `ansible/inventory.ini` → `ansible_aws_ssm_bucket_name` | `bin/from-stack.sh` |
| `MfaSeedKekArn` | `.env` → `MFA_SEED_KEK_ARN` | `bin/from-stack.sh` |

`EcrRegistry` is the fifth output and it does **not** change: it is `<account>.dkr.ecr.<region>.amazonaws.com`,
and both halves are fixed.

**Why the KMS ARN changes but the alias does not conflict.** The key is created with
`RemovalPolicy.DESTROY`, which CloudFormation implements as *schedule deletion* — the key sits in
`PendingDeletion` for 30 days — while the **alias** `alias/checky-demo-mfa-seed-kek` is deleted
immediately. So a rebuild the same afternoon can claim the alias again without a collision, and the
new key has a new ARN. auth-service will not start without it (ADR-0003), so this line matters even
though nothing in the demo path signs in.

---

## What is gone for good

- **Every receipt.** The `postgres-data` volume lives on the instance's root EBS volume, which is
  `delete_on_termination=True`. There are no backups; the README has always said so. A rebuilt demo
  starts with an empty check history, which for a demo is fine — and is worth knowing *before* you
  promise someone a link to a check you ran last week.
- **Kafka's log.** Same volume, same fate. Nothing downstream consumes it in this slice.
- **The ECR images.** Re-pushed from local, not rebuilt — see above.

---

## The runbook

### 0. Credentials

```sh
export AWS_ACCESS_KEY_ID=... AWS_SECRET_ACCESS_KEY=... AWS_DEFAULT_REGION=eu-west-1
```

Exported, not written to `~/.aws`. Both wrappers forward exported keys, so profiles are optional.
Prefer temporary credentials (`aws sts get-session-token`, SSO, an assumed role): there is then
nothing left behind to leak or rotate when the demo is over.

### 1. Infrastructure — about four minutes

```sh
cd infra/deploy/cdk
./cdk deploy
```

No `-c` flags. The defaults are the deployed configuration: `edge=cloudflare` (ports 80 and 443
admit Cloudflare's published ranges only), no SSH rule, no key pair, `t4g.medium`. `./cdk bootstrap`
is **not** needed — the account is already bootstrapped.

> ### ⚠ If `cdk diff` or `cdk deploy` ever says `DemoInstance may be replaced`
> **Stop and read it.** A replaced instance is a new EBS volume, so the database goes with it. The
> known cause is editing the security group's `description`: `GroupDescription` is immutable in AWS,
> so changing it replaces the *group*, which repoints the instance's `SecurityGroupIds`. There is a
> comment in `demo_stack.py` saying not to correct that string. It means it.

### 2. Wire the outputs in — seconds

```sh
cd ..
bin/from-stack.sh
```

It reads the live CloudFormation outputs, writes `ansible/inventory.ini`, patches the one changed
line in `.env` (keeping a `.env.bak`), and prints the DNS step with the new address. It refuses to
run rather than guess if `.env` or `secrets/` is missing.

### 3. DNS — the one manual step, about one minute

Cloudflare → `checkychain.com.ng` → **DNS** → **Records**. Edit both:

| Type | Name | Content | Proxy |
|---|---|---|---|
| A | `@` | *the new `PublicIp`* | **Proxied** (orange) |
| A | `www` | *the new `PublicIp`* | **Proxied** (orange) |

Proxy status **on** for both. Grey-cloud would publish the origin address *and* fail, because the
security group admits Cloudflare's ranges and nothing else. Propagation is seconds — Cloudflare is
authoritative for this zone, so there is no registry wait and nothing to be patient about.

Nothing else in Cloudflare needs touching. SSL/TLS mode is already Full (strict) and stays that way.

### 4. Images — about two minutes

```sh
cd ../..          # repository root
infra/deploy/bin/push-images.sh
```

Logs in to the fresh ECR repositories and pushes the three local `linux/arm64` images as `latest`.
If a local image is missing it says which, and rebuilding that one is
`docker build -f services/<svc>/Dockerfile -t <registry>/checky-<svc>:latest .` from the repository
root — about four minutes each.

### 5. Configure and start — about six minutes

```sh
cd infra/deploy/ansible
./play
```

Idempotent by design; re-run it as often as you like. It refuses to start if anything in `.env` or
`secrets/` is missing, installs the architecture-matched compose plugin, ships the config, pulls,
starts, then **proves the result from outside**: it waits for the landing page to answer over HTTPS
*and contain our own copy* — not merely to return 200, because the apex once answered 200 from a
registrar placeholder — and then asserts that ten paths that must not be public all return 404.

The six minutes is three JVMs, Kafka and Postgres starting on a shared two-core box. Payment-service
alone took 188 seconds when all three booted together.

### 6. Check it yourself

```sh
curl -sI https://checkychain.com.ng | head -3          # 200, server: cloudflare
curl -s -o /dev/null -w '%{http_code}\n' --max-time 8 https://52.30.100.74/   # expect 000
```

The second one is the important one and it is easy to skip: the origin must be **unreachable**
directly. `000` means the connection never opened. Substitute the new IP. Then paste a real Sepolia
transaction hash into the page and confirm an `AGREED` verdict with a readable amount.

### 7. Rate limiting — still outstanding

**This was never done and it is not in the code**, because with the origin closed the edge is the
only place it can be enforced:

Cloudflare → **Security** → **WAF** → **Rate limiting rules** → Create:

- Field `URI Path` · operator `equals` · value `/api/v1/verifications`
- **10** requests per **1 minute**, counting by **IP**
- Action **Block**, duration **1 minute**

Until this exists, each unthrottled `POST /api/v1/verifications` costs three upstream RPC calls
against providers that rate-limit by IP — and the IP they see is Cloudflare's, not the caller's.
A rate limiting rule survives teardown, so this is a once-ever task.

### 8. Tearing it down again

```sh
cd infra/deploy/cdk
./cdk destroy
```

About three minutes. It removes the instance, the EIP, the three ECR repositories (emptied first),
the transfer bucket (emptied first) and the KMS alias, and schedules the key. It leaves the
Cloudflare zone, the DNS records, the delegation, the Origin CA keypair, `.env` and the local images
alone — which is exactly the set this file says survives.

The DNS records will point at a released address until the next rebuild. That is harmless: the name
resolves to Cloudflare either way, and Cloudflare returns **`error code: 522`** to anyone who visits.

Measured straight after the 2026-11-26 teardown: apex, `www` and plain HTTP all answer 522, and all
three take **about 20 seconds** — Cloudflare's origin-connect timeout. That matters when you check
it: `curl --max-time 8` reports `000` and looks like a DNS or TLS fault, when in fact the edge is
working exactly as intended and is simply still waiting on a dead origin. Give it 30 seconds.

If you would rather it said something deliberate, pause the zone (Overview → Advanced → Pause
Cloudflare on Site) or set the records to `192.0.2.1`, which is reserved for documentation and
routes nowhere.

**One residual cost.** The KMS key sits in `PendingDeletion` for 30 days (the 2026-11-26 teardown
scheduled it for 2026-12-26) and AWS bills $1/month for a key in that state — so roughly a dollar
trails each teardown. The alias is released immediately, which is what lets a rebuild reclaim it;
there is no reason to cancel the deletion.

---

## Things that have bitten, in one list

1. **A 200 is not an identity.** The landing gate originally checked only the status code. The apex
   pointed at a registrar placeholder that answered 200 to everything, so the gate would have
   reported a successful deploy against entirely the wrong server. It now asserts page content.
2. **A registrar's panel, email and support all report success** while the registry still delegates
   elsewhere. Only the registry is authoritative: `dig +norecurse @ns3.nic.net.ng checkychain.com.ng NS`.
3. **The Origin CA step cannot be done while waiting for nameservers.** Cloudflare only issues for an
   **Active** zone; before that it answers `Failed to validate requested hostname`. Not an issue on a
   rebuild — the zone is already Active — but worth knowing if the domain ever changes.
4. **Caddy performs no ACME any more.** `config/Caddyfile` has an explicit `tls` directive pointing at
   the Origin CA pair. With the origin closed an HTTP-01 challenge could never succeed, and it would
   have failed at the first *renewal*, about sixty days after a cutover that looked perfect. If you
   ever need `-c edge=public` (an sslip.io hostname with no proxy, say), that directive must be
   reverted in the same change — the two decisions are one decision.
5. **The compose plugin must match the architecture.** An x86_64 binary installs fine on this
   Graviton box, passes a file-exists check, and then fails as *"'compose' is not a docker command"*
   much later. Both user-data and the playbook now check that it *runs*.
6. **Ansible reads `src` files at task execution time**, not at play start. Editing a config file
   while a play is running ships a half-complete one.
7. **`./play --syntax-check` works; `./play ansible-playbook --syntax-check` does not.** The image's
   entrypoint already *is* `ansible-playbook`, so passing it again prints argparse help.
8. **`caddy validate` now needs the certificate files present**, because the `tls` directive
   names them. Mount `secrets/origin.pem` and `origin.key` into the container or it fails with
   `no such file or directory` — which looks like a syntax error and is not one.
9. **Caddy logs `no OCSP stapling ... no URL to issuing certificate` at startup.** Expected and
   harmless: a Cloudflare Origin CA certificate has no OCSP responder because it is only ever
   validated by Cloudflare, which issued it. `caddy validate` still reports *Valid configuration*.
10. **Cloudflare's IP ranges are pinned in `demo_stack.py`**, not fetched at synth. They change
   rarely but they do change, and a stale list fails **closed** — legitimate edge traffic refused,
   which is the safe direction but presents as an outage. Refresh with
   `curl -s https://www.cloudflare.com/ips-v4; curl -s https://www.cloudflare.com/ips-v6`.

---

## If this laptop is gone

Everything in the "survives" table above that lives in Cloudflare or the AWS account is fine. Three
things were local only:

- **`.env`** — rebuild it from `env.example`, which documents every variable and carries the measured
  guidance (which RPC providers work from AWS and which do not, and why the demo token is WETH rather
  than USDC). The passwords are random per deploy; nothing outside the box knows the old ones.
- **`secrets/origin.pem` + `origin.key`** — re-issue in about two minutes: Cloudflare → SSL/TLS →
  **Origin Server** → **Create Certificate**, covering `checkychain.com.ng` and `*.checkychain.com.ng`.
  The zone is Active, so this now works first time. Save the certificate to `origin.pem` and the
  private key to `origin.key`, and `chmod 600 origin.key`.
- **The local images** — rebuild from the repository, about four minutes each.

Nothing requires the old laptop. Nothing requires an account email: Let's Encrypt is not used at all
here, and Cloudflare's Universal SSL needs no contact address from us.
