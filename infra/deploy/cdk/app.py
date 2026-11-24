#!/usr/bin/env python3
"""CDK entry point for the public verification demo."""
import os

import aws_cdk as cdk

from demo_stack import DemoStack

app = cdk.App()

# Required context, passed with -c or set in cdk.context.json. There is no default for ssh_cidr
# on purpose: the default would be 0.0.0.0/0, and SSH open to the internet is not something
# anyone should get by forgetting to think about it.
ssh_cidr = app.node.try_get_context("sshCidr")
key_name = app.node.try_get_context("keyName")
if not ssh_cidr or not key_name:
    raise SystemExit(
        "Missing context. Deploy with:\n"
        "  cdk deploy -c keyName=<ec2-key-pair> -c sshCidr=<your.ip.addr.ess/32>\n"
        "Find your address with: curl -s https://checkip.amazonaws.com"
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
    # t3.medium, not t3.small. Three JVMs, Kafka and Postgres share this box; measured against
    # the real images, payment-service alone took 188 seconds to start when all three booted at
    # once. 2 GB is not enough headroom for that, and an OOM during startup looks like a hang.
    instance_type=app.node.try_get_context("instanceType") or "t3.medium",
)

app.synth()
