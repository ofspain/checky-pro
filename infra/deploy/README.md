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
| CloudFront + WAF + ALB | Caddy with Let's Encrypt | Free, automatic, renews itself. An ALB is ~$16/month to serve one page. |
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

## Once

```sh
# 1. Infrastructure. sshCidr has no default on purpose - the default would be 0.0.0.0/0.
#    ./cdk runs the toolchain in a container, so nothing has to be installed on this machine.
cd infra/deploy/cdk
./cdk bootstrap                         # first time in this account/region only
./cdk deploy -c sshCidr=$(curl -s https://checkip.amazonaws.com)/32
#    Add -c keyName=<your-ec2-key-pair> only if you want an SSH key on the box as well.
#    Ansible does not need one, and neither does the console.
# Note the InstanceId, SsmTransferBucketName, PublicIp and EcrRegistry outputs.

# 2. DNS. Point your hostname's A record at PublicIp and wait for it to resolve.
#    Caddy cannot obtain a certificate before this is true.

# 3. Configuration.
cd ..
cp env.example .env && $EDITOR .env     # .env is gitignored
cp ansible/inventory.ini.example ansible/inventory.ini && $EDITOR ansible/inventory.ini

# 4. Images. The AWS CLI is in the ansible toolchain image, so it needs no install either.
docker run --rm -e AWS_PROFILE -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY \
  -e AWS_SESSION_TOKEN -v "$HOME/.aws:/root/.aws:ro" --entrypoint aws checky-ansible \
  ecr get-login-password --region <region> \
  | docker login --username AWS --password-stdin <EcrRegistry>
for s in auth crypto payment; do
  docker build -f services/$s/Dockerfile -t <EcrRegistry>/checky-$s:latest .   # from the repo root
  docker push <EcrRegistry>/checky-$s:latest
done
```

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
  security group ingress exactly as intended — 80 and 443 from `0.0.0.0/0`, 22 from the single
  supplied CIDR and nowhere else — a `t3.medium` with a 30 GB encrypted gp3 root volume, the
  three `checky-*` ECR repositories, and the `PublicIp`, `EcrRegistry` and `SshCommand` outputs.
  The missing-context guard was checked separately and refuses to synthesise without `sshCidr`.
- **The whole stack run locally from `compose.prod.yaml` with the real images and no LocalStack**:
  migrations applied (crypto v10, payments v11), all three services started cleanly, and a check
  returned a correct RFC 9457 `503` after trying three unreachable providers — proving payment
  minted its token from auth and reached crypto.

Not tested, because it needs the AWS account: `cdk synth` past the VPC lookup, and the playbook
against a real host.

## Known limits

- **Rate-limit counters are in memory**, so they are per instance. One instance is the design.
- **The receipt is not signed.** This path writes no `quorum_decisions` and no `observations` row,
  so it cannot feed attestation.
- **One box, no redundancy.** Restarting it is about four minutes of downtime; on a laptop the
  three JVMs took 188 seconds to start together, which is why the default instance is `t3.medium`
  and not `t3.small`.
- **No backups.** The Postgres volume is the only copy of the check history.
