#!/usr/bin/env sh
# Push the three service images to the ECR repositories the stack just created.
#
#   infra/deploy/bin/push-images.sh              push whatever is local
#   REBUILD=1 infra/deploy/bin/push-images.sh    build each one first, then push
#
# `cdk destroy` DELETES the three ECR repositories - they are declared with
# `empty_on_delete=True` and `RemovalPolicy.DESTROY` - so a rebuilt stack has an empty registry and
# the playbook's `docker compose pull` fails with a manifest-unknown error that reads like a tag
# typo. The images therefore have to go up again every time.
#
# They do NOT have to be rebuilt every time. The local arm64 images from the last deploy are
# byte-identical to what ECR held, so a push is about two minutes against roughly thirteen for a
# rebuild. That is the entire reason ../REBUILD.md says never to run `docker system prune`.
set -eu

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DEPLOY=$(CDPATH= cd -- "$HERE/.." && pwd)
REPO=$(CDPATH= cd -- "$DEPLOY/../.." && pwd)
REGION=${AWS_DEFAULT_REGION:-${AWS_REGION:-eu-west-1}}
SERVICES="auth crypto payment"

. "$HERE/_aws.sh"

[ -f "$DEPLOY/.env" ] || { echo "error: $DEPLOY/.env is missing." >&2; exit 1; }
REGISTRY=$(sed -n 's/^ECR_REGISTRY=//p' "$DEPLOY/.env" | head -1)
[ -n "$REGISTRY" ] || { echo "error: ECR_REGISTRY is not set in $DEPLOY/.env." >&2; exit 1; }

# Checked before the login, so a missing image is reported in one second rather than after a push
# of the two that are present. An absent image is the one failure here that needs a human decision:
# rebuild it, or find out why it went missing.
MISSING=""
if [ -z "${REBUILD:-}" ]; then
  for s in $SERVICES; do
    if [ -z "$(docker images -q "$REGISTRY/checky-$s:latest" 2>/dev/null)" ] \
    && [ -z "$(docker images -q "checky-$s:latest" 2>/dev/null)" ]; then
      MISSING="$MISSING $s"
    fi
  done
fi
if [ -n "$MISSING" ]; then
  echo "error: no local image for:$MISSING" >&2
  echo "       Something pruned them. Rebuild and push in one go with:" >&2
  echo "         REBUILD=1 $0" >&2
  echo "       (about four minutes per service, from $REPO)" >&2
  exit 1
fi

echo "Logging in to $REGISTRY ..." >&2
aws_cli ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$REGISTRY"

for s in $SERVICES; do
  TARGET="$REGISTRY/checky-$s:latest"
  if [ -n "${REBUILD:-}" ]; then
    echo "==> building checky-$s" >&2
    # --platform is explicit: the box is Graviton, and an image built for the builder's
    # architecture by accident fails at runtime as "exec format error" inside the container,
    # long after the push reported success.
    docker build --platform linux/arm64 -f "$REPO/services/$s/Dockerfile" -t "$TARGET" "$REPO"
  elif [ -z "$(docker images -q "$TARGET" 2>/dev/null)" ]; then
    # Present under its short name only, which is what a local compose build leaves behind.
    docker tag "checky-$s:latest" "$TARGET"
  fi
  echo "==> pushing checky-$s" >&2
  docker push "$TARGET"
done

echo >&2
echo "All three pushed as :latest. Next: cd infra/deploy/ansible && ./play" >&2
echo "(Tagging by commit SHA instead of :latest is still an open item - see REBUILD.md.)" >&2
