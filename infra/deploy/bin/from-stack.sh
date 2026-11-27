#!/usr/bin/env sh
# Wire the live CloudFormation outputs into the two files that name the box.
#
#   infra/deploy/bin/from-stack.sh
#
# Four values change every time the stack is rebuilt (see ../REBUILD.md). Three of them belong in
# files and are written here; the fourth is a DNS record only a human can set, and this prints it.
#
# This script exists because hand-copying four strings out of `cdk deploy` output was the single
# most error-prone step of the first deployment, and the failures it produces are indirect: a wrong
# instance ID is "no hosts matched", a wrong bucket is an opaque S3 permission error several layers
# inside a connection plugin, and a wrong KMS ARN is auth-service refusing to start.
#
# It only ever reads from AWS and writes to two local files. It changes no infrastructure.
set -eu

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY=$(CDPATH= cd -- "$HERE/.." && pwd)
STACK=${STACK:-CheckyDemo}
REGION=${AWS_DEFAULT_REGION:-${AWS_REGION:-eu-west-1}}

. "$HERE/_aws.sh"

# Refuse rather than guess. A rebuild with no .env produces a playbook that fails on its own
# pre-task assertion, which is survivable - but silently writing an inventory for a deploy that
# cannot possibly work wastes the four minutes the stack just spent coming up.
[ -f "$DEPLOY/.env" ] || {
  echo "error: $DEPLOY/.env is missing. Start from env.example - see ../REBUILD.md." >&2
  exit 1
}
HOSTNAME_FROM_ENV=$(sed -n 's/^PUBLIC_HOST=//p' "$DEPLOY/.env" | head -1)
for f in origin.pem origin.key; do
  [ -f "$DEPLOY/secrets/$f" ] || {
    echo "error: $DEPLOY/secrets/$f is missing." >&2
    echo "       Cloudflare -> SSL/TLS -> Origin Server -> Create Certificate, covering" >&2
    echo "       ${HOSTNAME_FROM_ENV:-your apex} and *.${HOSTNAME_FROM_ENV:-your apex}." >&2
    echo "       The zone must be ACTIVE first; before that Cloudflare answers" >&2
    echo "       'Failed to validate requested hostname'. See ../REBUILD.md." >&2
    exit 1
  }
done

echo "Reading outputs from $STACK in $REGION ..." >&2
# `set -e` would abort here on a missing stack, leaving only the AWS CLI's own ValidationError.
# That is accurate but says nothing about what to do, and "stack does not exist" is the NORMAL
# state between demos - this script is step 2 of a rebuild, so reaching it before step 1 is the
# single likeliest way to run it wrongly.
if ! OUTPUTS=$(aws_cli cloudformation describe-stacks \
  --stack-name "$STACK" --region "$REGION" \
  --query 'Stacks[0].Outputs[].[OutputKey,OutputValue]' --output text 2>&1); then
  case "$OUTPUTS" in
    *"does not exist"*)
      echo "error: the $STACK stack is not deployed, so it has no outputs to read." >&2
      echo "       This is step 2 of a rebuild. Run step 1 first:" >&2
      echo "         (cd $(dirname "$DEPLOY")/deploy/cdk && ./cdk deploy)   # ~4 min" >&2
      echo "       See ../REBUILD.md." >&2
      ;;
    *) echo "$OUTPUTS" >&2 ;;
  esac
  exit 1
fi

get() { printf '%s\n' "$OUTPUTS" | awk -v k="$1" '$1==k {print $2}'; }

PUBLIC_IP=$(get PublicIp)
INSTANCE_ID=$(get InstanceId)
SSM_BUCKET=$(get SsmTransferBucketName)
KEK_ARN=$(get MfaSeedKekArn)
REGISTRY=$(get EcrRegistry)

for pair in "PublicIp:$PUBLIC_IP" "InstanceId:$INSTANCE_ID" \
            "SsmTransferBucketName:$SSM_BUCKET" "MfaSeedKekArn:$KEK_ARN" \
            "EcrRegistry:$REGISTRY"; do
  case "$pair" in
    *:) echo "error: the stack did not report ${pair%:}. Is it finished deploying?" >&2; exit 1 ;;
  esac
done

# The inventory is rewritten whole rather than patched: it is derived entirely from these outputs,
# so there is nothing in it worth preserving across a rebuild.
cat > "$DEPLOY/ansible/inventory.ini" <<INV
# Written by infra/deploy/bin/from-stack.sh from the $STACK outputs. Gitignored: it names your box.
# ansible_host is an INSTANCE ID, not an address - SSM addresses instances, not hosts - and the
# bucket is there because the Session Manager channel cannot carry files on its own.

[demo]
checky-demo ansible_host=$INSTANCE_ID

[demo:vars]
ansible_connection=community.aws.aws_ssm
ansible_aws_ssm_bucket_name=$SSM_BUCKET
ansible_aws_ssm_region=$REGION
INV

# .env is hand-written and holds secrets, so only the one derived line is touched, and the previous
# file is kept. cp-then-edit rather than `sed -i`, whose -i semantics differ between GNU and BSD.
cp "$DEPLOY/.env" "$DEPLOY/.env.bak"
chmod 600 "$DEPLOY/.env.bak"
if grep -q '^MFA_SEED_KEK_ARN=' "$DEPLOY/.env.bak"; then
  sed "s|^MFA_SEED_KEK_ARN=.*|MFA_SEED_KEK_ARN=$KEK_ARN|" "$DEPLOY/.env.bak" > "$DEPLOY/.env"
else
  { cat "$DEPLOY/.env.bak"; echo "MFA_SEED_KEK_ARN=$KEK_ARN"; } > "$DEPLOY/.env"
fi
chmod 600 "$DEPLOY/.env"

PUBLIC_HOST=$HOSTNAME_FROM_ENV

cat <<REPORT

  Wrote  ansible/inventory.ini   instance $INSTANCE_ID
                                 bucket   $SSM_BUCKET
  Patched .env                   MFA_SEED_KEK_ARN=$KEK_ARN
                                 (previous file kept as .env.bak)
  Unchanged                      ECR_REGISTRY=$REGISTRY

  ------------------------------------------------------------------
  ONE MANUAL STEP, and nothing works until it is done.

  Cloudflare -> ${PUBLIC_HOST:-your zone} -> DNS -> Records. Set BOTH A records to:

        $PUBLIC_IP

    A   @      $PUBLIC_IP    Proxied (orange cloud)
    A   www    $PUBLIC_IP    Proxied (orange cloud)

  Proxy status must stay ON. Grey-cloud publishes the origin address and fails anyway,
  because the security group admits Cloudflare's ranges and nothing else.
  ------------------------------------------------------------------

  Then:  infra/deploy/bin/push-images.sh     (from anywhere)
         cd infra/deploy/ansible && ./play

REPORT
