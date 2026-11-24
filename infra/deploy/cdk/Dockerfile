# The CDK toolchain, pinned, so that running it needs nothing on the host but Docker.
#
# It exists because the toolchain has two awkward requirements that have nothing to do with the
# stacks themselves: the cdk CLI is a Node program even though the stacks are Python, and
# aws-cdk-lib only publishes wheels for Python 3.11-3.13, so a newer interpreter silently falls
# back to building jsii from source. Both are pinned here once instead of being everybody's
# problem on every machine, and CI runs the identical image.
FROM node:22-bookworm-slim

# Debian 12 ships Python 3.11, which is inside aws-cdk-lib's supported range.
RUN apt-get update \
    && apt-get install -y --no-install-recommends python3 python3-venv ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN npm install -g aws-cdk@2.173.4 && npm cache clean --force

ENV VIRTUAL_ENV=/opt/venv
RUN python3 -m venv "$VIRTUAL_ENV"
ENV PATH="$VIRTUAL_ENV/bin:$PATH"

COPY requirements.txt /tmp/requirements.txt
RUN pip install --no-cache-dir -r /tmp/requirements.txt

# jsii prints a banner for every Node release it has not been tested against. The version is
# pinned in this image, so the banner is noise that hides real output.
ENV JSII_SILENCE_WARNING_UNTESTED_NODE_VERSION=1 \
    JSII_SILENCE_WARNING_DEPRECATED_NODE_VERSION=1 \
    JSII_SILENCE_WARNING_END_OF_LIFE_NODE_VERSION=1

WORKDIR /stack
ENTRYPOINT ["cdk"]
