#!/usr/bin/env python3
"""CDK entry point for the public verification demo."""
import os

import aws_cdk as cdk

from demo_stack import DemoStack

app = cdk.App()

# Both optional, and the default is no SSH at all: no open port and no key on the box.
#
# Ansible connects over SSM and the console connects through Session Manager, so an SSH rule
# protects nothing here while still being a rule that goes stale - a home address changes, and
# the entry then names a stranger's. Supply both of these together if you ever want SSH back.
ssh_cidr = app.node.try_get_context("sshCidr")
key_name = app.node.try_get_context("keyName")

# The one thing worth refusing outright. An address is a deliberate choice; the whole internet
# on port 22 is almost always a slip, and it is the slip this project should not make quietly.
if ssh_cidr and ssh_cidr.strip().endswith("/0"):
    raise SystemExit(
        f"Refusing sshCidr={ssh_cidr}: that is SSH open to the internet.\n"
        "Pass a single address, for example -c sshCidr=$(curl -s https://checkip.amazonaws.com)/32"
    )

# This stack looks up the default VPC, which CDK can only do for a named account and region.
# Without credentials the lookup fails several layers down as a jsii stack trace about a
# "context provider", which says nothing useful to the person who simply has not logged in yet.
account = os.environ.get("CDK_DEFAULT_ACCOUNT")
if not account:
    raise SystemExit(
        "No AWS account resolved, so the default-VPC lookup cannot run.\n"
        "Configure credentials and try again, for example:\n"
        "  aws configure                 # or\n"
        "  export AWS_PROFILE=<profile>  # for an SSO or named profile"
    )

DemoStack(
    app,
    "CheckyDemo",
    env=cdk.Environment(
        account=account,
        region=os.environ.get("CDK_DEFAULT_REGION", "eu-west-1"),
    ),
    description="Public verification demo: one EC2 instance, three ECR repositories.",
    ssh_cidr=ssh_cidr,
    key_name=key_name,
    # t4g.medium: Graviton, arm64. Three JVMs, Kafka and Postgres share this box; measured
    # against the real images, payment-service alone took 188 seconds to start when all three
    # booted together, so 2 GB leaves no headroom and an OOM during startup looks like a hang.
    #
    # arm64 rather than x86_64 because every image here has an arm64 build (checked: postgres,
    # kafka, flyway 11, caddy) and the machines building them are Apple Silicon - so the builds
    # are native instead of QEMU-emulated, and the instance is cheaper besides.
    instance_type=app.node.try_get_context("instanceType") or "t4g.medium",
)

app.synth()
