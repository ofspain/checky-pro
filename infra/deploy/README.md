# Deploying the verification demo to EC2

One instance, three ECR repositories, and an Ansible playbook. CDK provisions; Ansible configures.

This is **not** the planned production stack (`infra/README.md`: CDK stacks split by blast radius,
EKS, RDS Multi-AZ, MSK, KMS, CloudFront + WAF + ALB, one AWS account per environment). It is the
demo, sized for a demo, written as code rather than clicked into a console so the parts that
matter later already exist in git.

Where it differs, and why:

| Planned | Here | Why |
|---|---|---|
| EKS | Docker Compose on one box | One demo. Compose keeps the same service boundaries. |
| RDS Multi-AZ | Postgres container | Still one database per service, so the move is a connection string. |
| MSK | Kafka container | MSK costs more per month than this whole deployment. |
| CloudFront + WAF + ALB | Cloudflare (free plan) in front of Caddy | Free, and it gives the demo caching, a WAF and the only place rate limiting can live now that the origin is closed. An ALB is ~$16/month to serve one page. |
| Private subnets | Public subnet + security group | A private subnet needs a NAT gateway, which costs more than the instance. |

## What ends up on the box

Eight containers: `postgres`, `kafka`, the two Flyway migration jobs, `auth`, `crypto`, `payment`
and `edge`. **Only `edge` publishes ports.**

`auth` is not exposed and is not part of the demo, but it cannot be dropped: payment-service mints
a `client_credentials` token from it to call crypto-service's internal API. Without it, a check
cannot be made at all.

The source tree never reaches the box. Images are built and pushed from a developer machine or CI;
the box only pulls. The only repository content copied up is the landing page and the Flyway SQL,
which is a few kilobytes of text.

## Bringing it up

**If you are rebuilding the demo that already ran at `checkychain.com.ng`, read
[`REBUILD.md`](REBUILD.md) instead of this section.** It is four commands and one dashboard visit,
and it says which of the slow once-only steps below are already permanent — the Cloudflare zone, the
nameserver delegation and the Origin CA certificate all survive a teardown, so none of them is ever
done twice.

This section is the from-nothing path: a new account, or a different hostname.

```sh
# 1. Infrastructure. The defaults are the deployed configuration: Cloudflare-only ingress on
#    80/443, no SSH rule and no key pair at all, t4g.medium (Graviton/arm64).
cd infra/deploy/cdk
./cdk bootstrap                         # first time in this account/region only
./cdk deploy
#    Optional: -c sshCidr=$(curl -s https://checkip.amazonaws.com)/32 -c keyName=<key-pair>
#    for break-glass SSH. Both are needed together, and app.py refuses a /0 outright.
#    Optional: -c edge=public for a hostname with NO proxy in front - see the note below.
# Note the PublicIp, InstanceId, SsmTransferBucketName, MfaSeedKekArn and EcrRegistry outputs.

# 2. A Cloudflare zone for the hostname, with the registrar's nameservers pointed at it.
#    Allow for this taking up to a day: the registry, not the registrar, is authoritative, and
#    the registrar's panel reports success while the delegation has not moved. Check with
#      dig +norecurse @<registry nameserver> <your-domain> NS
#    Then set SSL/TLS mode to Full (strict), and create an Origin CA certificate covering the
#    apex and *.apex (SSL/TLS -> Origin Server). That step needs the zone to be ACTIVE, so it
#    cannot be done while still waiting. Save the pair to secrets/origin.{pem,key}; chmod 600
#    the key. Point both A records (@ and www) at PublicIp, proxied.

# 3. Configuration.
cd ..
cp env.example .env && $EDITOR .env     # .env is gitignored
bin/from-stack.sh                       # writes inventory.ini, fills in MFA_SEED_KEK_ARN

# 4. Images. The AWS CLI comes from the ansible toolchain image, so it needs no install.
bin/push-images.sh                      # REBUILD=1 to build them first
```

> **`edge=public` and the `tls` directive in `config/Caddyfile` are one decision, not two.**
> The default `edge=cloudflare` admits only Cloudflare's published ranges on 80 and 443, which is
> what makes the proxy mandatory rather than merely usual — the origin address is not a secret, it
> sits in certificate transparency logs and old DNS history. Those same rules are also what stops
> an ACME HTTP-01 challenge reaching port 80, which is why Caddy serves a pinned Origin CA
> certificate and performs no ACME at all. If you switch to `edge=public`, you must revert that
> `tls` directive in the same change, or the box has no usable certificate. Get it the wrong way
> round and the site breaks at the first *renewal*, about sixty days after a cutover that looked
> perfect.

## Every deploy after that

```sh
cd infra/deploy/ansible
./play
```

The playbook is safe to re-run; that is the point of it. It refuses to start if anything in `.env`
is missing, waits for the page to answer over HTTPS, and then **asserts that `/login`,
`/oauth2/authorize`, `/admin`, `/accounts`, `/api-keys`, `/app/`, `/api/v1/invoices`,
`/api/v1/receipts`, `/actuator/health` and `/userinfo` all return 404.** The check that matters is
the one performed after publishing.

Nothing is edited on the server. A change is made in git and the playbook runs again.

## How Ansible reaches the box

**Over SSM, not SSH.** `ansible_connection=community.aws.aws_ssm` opens a Session Manager
channel through the AWS API, so a deploy needs no key pair, no `ansible_user` and no inbound
port. The instance role already carries `AmazonSSMManagedInstanceCore`, which is the same thing
that makes *Connect* work in the EC2 console.

Two consequences worth knowing, because both look like bugs otherwise:

- **`ansible_host` is the instance ID**, not an IP address. SSM addresses instances, not hosts.
- **The connection needs an S3 bucket.** The Session Manager channel cannot carry files, so the
  plugin stages them through S3. The stack creates that bucket, blocks public access, expires
  objects after a day and empties it on `cdk destroy`.

The playbook runs with `become: true` for a reason that only shows up here: over SSM the session
is `ssm-user`, which is **not** in the docker group and cannot write to `/opt/checky`, whereas
over SSH it is `ec2-user`, which can do both. Escalating to root makes the two transports
identical, and files are still owned by `ec2-user` so a human who logs in can manage the stack.

**SSH is kept as break-glass**, port 22 open to the one address in `sshCidr`, for the case where
the SSM agent itself is the problem. It only works if the stack was deployed with `-c keyName=`;
`ansible/inventory.ini.example` has the exact override. If you never use it, nothing is lost by
leaving the key pair off — the console still gets you a shell.

## The toolchain

`./cdk` is the CDK and `ansible/play` is Ansible, each run in a container. It takes exactly what the real CLI takes — `./cdk synth`,
`./cdk deploy -c ...`, `./cdk diff` — and it builds its image the first time it is used.

It exists because the toolchain has two requirements that have nothing to do with the stacks:
the `cdk` CLI is a Node program even though the stacks are Python, and `aws-cdk-lib` only
publishes wheels for Python 3.11–3.13, so a newer interpreter silently falls back to building
jsii from source. On a machine with Python 3.15 that ran for over ten minutes without finishing.
Both are pinned in `Dockerfile` once, and CI runs the identical image.

Your AWS credentials are passed through: `~/.aws` is mounted read-only and `AWS_PROFILE`,
`AWS_REGION` and exported keys or session tokens are forwarded, so profiles and SSO work exactly
as they do on the host.

`ansible/play` exists for a sharper version of the same problem. The SSM path needs four things
that are each easy to get wrong on their own: Ansible, the `community.aws` collection for the
connection plugin, `boto3` for it to call AWS with, and AWS's `session-manager-plugin`, which is
not a Python package and is in no distribution's repositories. All four are pinned in
`ansible/Dockerfile`. It also carries the AWS CLI, so the ECR login needs no install either.
Arguments pass straight through: `./play --check`, `./play -v`, `./play --syntax-check`.

To run either natively instead: for the CDK, install Node and the CLI (`npm install -g aws-cdk`),
use Python 3.11–3.13 and `pip install -r requirements.txt`; for Ansible, install `ansible-core`,
`boto3`, the two collections and the Session Manager plugin yourself.

## Credentials

Nothing here reads a credential from the repository, and nothing needs one stored permanently.
Both wrappers mount `~/.aws` read-only if it exists and forward `AWS_PROFILE`, `AWS_REGION` and
any exported key, secret and session token, so profiles, SSO and short-lived STS sessions all
work the way they do on the host.

Prefer **temporary** credentials. `aws sts get-session-token`, an SSO login or an assumed role
all produce a set that expires on its own, which is the right shape for a demo deploy: there is
nothing left behind to leak or rotate afterwards.

## The chain

Three **different** Sepolia RPC providers, set in `.env`. This is the single most important
difference from the local stack, where all three point at one anvil node and therefore cannot
disagree — which makes `HELD` unreachable and the quorum a formality. With three real providers,
2-of-3 is a real vote, a genuine disagreement produces a genuine `HELD`, and every transaction is
checkable on sepolia.etherscan.io.

The chain is configured as `ETHEREUM` because that is what the `Chain` enum offers. Nothing in the
service is testnet-aware; it asks whatever it is pointed at.

`DEMO_TOKEN_ADDRESS` is the one ERC-20 the demo can name. Anything else verifies as
`UNKNOWN_TOKEN` — the amount is shown and the token deliberately is not. **Verify the address on
sepolia.etherscan.io before using it.**

## What was actually tested before this was committed

- `docker compose config` on `compose.prod.yaml`: valid, 8 services, only 80 and 443 published.
- `caddy validate` on the Caddyfile: valid, with automatic HTTP→HTTPS redirects.
- **The CDK stack synthesises.** Run on Python 3.12 with the VPC lookup stubbed (the lookup is
  the only part that needs an AWS account), `app.py` produced a CloudFormation template with the
  security group ingress exactly as intended, a 30 GB encrypted gp3 root volume, the three
  `checky-*` ECR repositories and the five outputs. *This was measured against the stack's first
  shape — open ingress, `t3.medium`, a required `sshCidr` — all three of which have since changed:
  ingress is now Cloudflare's ranges only, the instance is `t4g.medium` on Graviton, and SSH is
  off by default rather than mandatory. The `/0` refusal in `app.py` is still there and still
  tested.*
- **The whole stack run locally from `compose.prod.yaml` with the real images and no LocalStack**:
  migrations applied (crypto v10, payments v11), all three services started cleanly, and a check
  returned a correct RFC 9457 `503` after trying three unreachable providers — proving payment
  minted its token from auth and reached crypto.

Not tested, because it needs the AWS account: `cdk synth` past the VPC lookup, and the playbook
against a real host.

## Known limits

- **The receipt is not signed.** This path writes no `quorum_decisions` and no `observations` row,
  so it cannot feed attestation.
- **One box, no redundancy.** Restarting it is about four minutes of downtime; on a laptop the
  three JVMs took 188 seconds to start together, which is why the default instance is `t4g.medium`
  and not `t4g.small`.
- **No backups.** The Postgres volume is the only copy of the check history, it lives on a root
  EBS volume with `delete_on_termination=True`, and the stack is destroyed between demos — so
  every receipt goes with it. `REBUILD.md` says so where it will be read in time.
- **Rate limiting is not configured.** In-memory counters were the design when the box was
  directly reachable; now that only Cloudflare can reach it, the edge is the only place a limit
  can be enforced, and the rule has not been created. See `REBUILD.md` step 7.
