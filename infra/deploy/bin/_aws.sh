#!/usr/bin/env sh
# The AWS CLI, run from the ansible toolchain image rather than installed on this machine.
#
# Sourced by the other scripts in this directory; not useful on its own. It exists because the
# whole point of ../cdk/cdk and ../ansible/play is that a rebuild needs nothing installed locally
# but Docker, and that promise is worthless if the runbook then says "and also install the AWS CLI".
# The ansible image already carries it for the ECR login, so it is reused here.
#
# Credentials are forwarded exactly as the two wrappers forward them: exported keys and session
# tokens, plus ~/.aws read-only if it happens to exist. Nothing is written anywhere.
IMAGE=checky-ansible

aws_cli() {
  if [ -z "$(docker images -q "$IMAGE" 2>/dev/null)" ]; then
    echo "The $IMAGE toolchain image is missing. Build it once with:" >&2
    echo "  (cd infra/deploy/ansible && docker build -t $IMAGE .)" >&2
    return 1
  fi
  _mount=""
  [ -d "$HOME/.aws" ] && _mount="-v $HOME/.aws:/root/.aws:ro"
  # shellcheck disable=SC2086
  docker run --rm $_mount \
    -e AWS_PROFILE -e AWS_REGION -e AWS_DEFAULT_REGION \
    -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_SESSION_TOKEN \
    --entrypoint aws "$IMAGE" "$@"
}
