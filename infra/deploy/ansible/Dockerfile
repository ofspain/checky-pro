# The Ansible toolchain, pinned, so that running it needs nothing on the host but Docker.
#
# The SSM path needs four things that are easy to get wrong separately: Ansible itself, the
# community.aws collection that provides the aws_ssm connection plugin, boto3 for it to call AWS
# with, and AWS's own session-manager-plugin binary, which is not a Python package and is not in
# any distribution's repositories. All four are pinned here, and CI runs the identical image.
FROM python:3.12-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        ca-certificates curl unzip openssh-client \
    && rm -rf /var/lib/apt/lists/*

RUN pip install --no-cache-dir \
        "ansible-core==2.18.1" \
        "boto3==1.35.90" \
        "botocore==1.35.90" \
    && ansible-galaxy collection install "community.aws:9.0.0" "amazon.aws:9.0.0"

# The Session Manager plugin, which the aws_ssm connection plugin shells out to. The URL is
# architecture-specific, hence the uname switch; both are AWS's own published packages.
RUN set -eux; \
    case "$(uname -m)" in \
      x86_64)  ARCH=64bit ;; \
      aarch64) ARCH=arm64 ;; \
      *) echo "unsupported architecture $(uname -m)" >&2; exit 1 ;; \
    esac; \
    curl -fsSL "https://s3.amazonaws.com/session-manager-downloads/plugin/latest/ubuntu_${ARCH}/session-manager-plugin.deb" \
      -o /tmp/smp.deb; \
    dpkg -i /tmp/smp.deb; \
    rm -f /tmp/smp.deb; \
    session-manager-plugin --version

# The AWS CLI, so that the ECR login for pushing images does not need a separate install either.
RUN set -eux; \
    case "$(uname -m)" in \
      x86_64)  AWSARCH=x86_64 ;; \
      aarch64) AWSARCH=aarch64 ;; \
    esac; \
    curl -fsSL "https://awscli.amazonaws.com/awscli-exe-linux-${AWSARCH}.zip" -o /tmp/awscli.zip; \
    unzip -q /tmp/awscli.zip -d /tmp; \
    /tmp/aws/install; \
    rm -rf /tmp/awscli.zip /tmp/aws; \
    aws --version

WORKDIR /play
ENTRYPOINT ["ansible-playbook"]
