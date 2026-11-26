"""One box, three image repositories, and the smallest network that makes them work."""

from aws_cdk import (
    CfnOutput,
    Duration,
    RemovalPolicy,
    Stack,
    aws_ec2 as ec2,
    aws_ecr as ecr,
    aws_iam as iam,
    aws_kms as kms,
    aws_s3 as s3,
)
from constructs import Construct

SERVICES = ("auth", "crypto", "payment")

# Cloudflare's published origin-facing ranges, fetched 2026-11-26 from
# https://www.cloudflare.com/ips-v4 and https://www.cloudflare.com/ips-v6.
#
# Hardcoded on purpose rather than fetched during synth. A synth that reaches the network is a
# synth that behaves differently depending on when and where it runs, and a deploy that cannot
# happen while that endpoint is down. The cost is that this list goes stale, so refresh it with:
#
#   curl -s https://www.cloudflare.com/ips-v4; curl -s https://www.cloudflare.com/ips-v6
#
# Verified when written rather than trusted: the edge addresses actually serving
# checkychain.com.ng at the time (188.114.96.0, 188.114.97.0) both fall inside 188.114.96.0/20
# below. A published list that did not contain the address in use would be the wrong list.
CLOUDFLARE_IPV4 = (
    "173.245.48.0/20",
    "103.21.244.0/22",
    "103.22.200.0/22",
    "103.31.4.0/22",
    "141.101.64.0/18",
    "108.162.192.0/18",
    "190.93.240.0/20",
    "188.114.96.0/20",
    "197.234.240.0/22",
    "198.41.128.0/17",
    "162.158.0.0/15",
    "104.16.0.0/13",
    "104.24.0.0/14",
    "172.64.0.0/13",
    "131.0.72.0/22",
)

CLOUDFLARE_IPV6 = (
    "2400:cb00::/32",
    "2606:4700::/32",
    "2803:f800::/32",
    "2405:b500::/32",
    "2405:8100::/32",
    "2a06:98c0::/29",
    "2c0f:f248::/32",
)


class DemoStack(Stack):
    """The demo's infrastructure.

    The real platform is planned as CDK stacks split by blast radius - network, data, security,
    edge, one per service, on EKS with RDS, MSK and KMS (infra/README.md). This is not that, and
    is not trying to be: it is the demo, sized for a demo, written so the parts that matter later
    are already expressed as code rather than clicked into a console.

    Deliberate choices, each of which the real stack makes differently:

    - **The default VPC is used, not a new one.** A private subnet needs a NAT gateway to pull
      images, which costs more per month than the instance. The box is public-subnet with a
      security group doing the work.
    - **Postgres and Kafka run as containers, not RDS and MSK.** One box, one demo. The compose
      file keeps one database per service, so moving to RDS later is a connection string.
    - **No load balancer.** Cloudflare's free plan sits in front, and Caddy terminates TLS on the
      box with a pinned Cloudflare Origin CA certificate rather than ACME (see the ingress rules
      below: closing the origin and dropping ACME are the same decision). That also gives the demo
      the only place a rate limit can now be enforced. An ALB with ACM is the production answer and
      costs about sixteen dollars a month to serve one page.
    """

    def __init__(
        self,
        scope: Construct,
        construct_id: str,
        *,
        ssh_cidr: str | None,
        key_name: str | None,
        edge: str,
        instance_type: str,
        **kwargs,
    ) -> None:
        super().__init__(scope, construct_id, **kwargs)

        vpc = ec2.Vpc.from_lookup(self, "DefaultVpc", is_default=True)

        # One repository per service. The box only ever pulls; images are built and pushed from a
        # developer machine or CI, so the source tree never lands on an internet-facing host.
        repositories = [
            ecr.Repository(
                self,
                f"Repo{service}",
                repository_name=f"checky-{service}",
                image_scan_on_push=True,
                removal_policy=RemovalPolicy.DESTROY,
                empty_on_delete=True,
                lifecycle_rules=[
                    ecr.LifecycleRule(max_image_count=5, description="Keep the last five images.")
                ],
            )
            for service in SERVICES
        ]

        # Ansible reaches this box over SSM rather than SSH, and the aws_ssm connection plugin
        # moves files through S3 because the Session Manager channel itself cannot carry them.
        # The objects are transient, so they are expired after a day and the bucket is emptied
        # when the stack is destroyed - otherwise `cdk destroy` leaves a bucket behind forever.
        transfer_bucket = s3.Bucket(
            self,
            "SsmTransferBucket",
            block_public_access=s3.BlockPublicAccess.BLOCK_ALL,
            enforce_ssl=True,
            encryption=s3.BucketEncryption.S3_MANAGED,
            removal_policy=RemovalPolicy.DESTROY,
            auto_delete_objects=True,
            lifecycle_rules=[
                s3.LifecycleRule(
                    expiration=Duration.days(1),
                    abort_incomplete_multipart_upload_after=Duration.days(1),
                )
            ],
        )

        # auth-service envelope-encrypts TOTP seeds with a KMS data key (ADR-0003) and refuses
        # to start outside the local profile without a key, rather than silently falling back to
        # its fixed local-dev key. That guard is right, so the demo gets a real key. Nothing in
        # the demo path signs in, so this is a startup requirement rather than a used feature -
        # but a service that will not boot is not much use either way.
        mfa_key = kms.Key(
            self,
            "MfaSeedKek",
            description="Checky demo: KEK for auth-service TOTP seed envelope encryption.",
            enable_key_rotation=True,
            removal_policy=RemovalPolicy.DESTROY,
            alias="alias/checky-demo-mfa-seed-kek",
        )

        security_group = ec2.SecurityGroup(
            self,
            "DemoSg",
            vpc=vpc,
            # DO NOT "correct" this string. A security group's GroupDescription is immutable in
            # AWS, so editing it replaces the group - which repoints the instance's
            # SecurityGroupIds and makes CloudFormation report the INSTANCE as "may be replaced".
            # A replaced instance is a new EBS volume, so the postgres-data volume and every
            # receipt in it would go with it. Making this sentence accurate is a cosmetic gain
            # worth precisely nothing against that, so the truth lives in the comment above the
            # ingress rules instead: in the default `edge=cloudflare` mode, 80 and 443 admit
            # Cloudflare's published ranges only, not "anywhere".
            description="Public demo: HTTP/HTTPS from anywhere, SSH from one address only.",
            allow_all_outbound=True,
        )
        # Who may reach the web ports at all. With Cloudflare in front, leaving these open to the
        # internet leaves the proxy entirely optional: anyone who learns the origin address - and
        # it is not a secret, it is in certificate transparency logs and old DNS history - can
        # skip the rate limiting, the WAF and the caching by talking to the box directly. The
        # firewall is what makes the edge mandatory rather than merely usual.
        #
        # This is also why Caddy no longer uses ACME. An HTTP-01 challenge needs Let's Encrypt to
        # reach port 80 HERE, which these rules now forbid, so the origin serves a Cloudflare
        # Origin CA certificate instead and renews nothing. The two decisions are the same
        # decision; changing one without the other breaks the site about sixty days later.
        if edge == "cloudflare":
            for cidr in CLOUDFLARE_IPV4:
                security_group.add_ingress_rule(
                    ec2.Peer.ipv4(cidr), ec2.Port.tcp(443), "HTTPS from Cloudflare"
                )
                security_group.add_ingress_rule(
                    ec2.Peer.ipv4(cidr), ec2.Port.tcp(80), "HTTP from Cloudflare (redirect)"
                )
            for cidr in CLOUDFLARE_IPV6:
                security_group.add_ingress_rule(
                    ec2.Peer.ipv6(cidr), ec2.Port.tcp(443), "HTTPS from Cloudflare (v6)"
                )
                security_group.add_ingress_rule(
                    ec2.Peer.ipv6(cidr), ec2.Port.tcp(80), "HTTP from Cloudflare (v6, redirect)"
                )
        else:
            # Direct-to-origin, for a hostname with no proxy in front - an sslip.io address while
            # a domain is still being arranged, say. Caddy then needs port 80 for ACME, so this
            # mode and the `tls` directive in config/Caddyfile are mutually exclusive.
            security_group.add_ingress_rule(
                ec2.Peer.any_ipv4(), ec2.Port.tcp(80), "HTTP (ACME + redirect)"
            )
            security_group.add_ingress_rule(ec2.Peer.any_ipv4(), ec2.Port.tcp(443), "HTTPS")
        # No SSH unless an address was named. Ansible connects over SSM and the console uses
        # Session Manager, so with no key pair on the box an open port 22 cannot be authenticated
        # against at all - it is surface with no use. Real break-glass for a broken agent is the
        # EC2 Serial Console, which needs no inbound port, or destroying and redeploying this
        # stack, which takes about four minutes and is the honest answer for a demo box.
        if ssh_cidr:
            security_group.add_ingress_rule(
                ec2.Peer.ipv4(ssh_cidr), ec2.Port.tcp(22), "SSH, one address only"
            )

        role = iam.Role(
            self,
            "DemoInstanceRole",
            assumed_by=iam.ServicePrincipal("ec2.amazonaws.com"),
            description="Pull demo images from ECR; reachable by Session Manager as a fallback.",
            managed_policies=[
                iam.ManagedPolicy.from_aws_managed_policy_name("AmazonSSMManagedInstanceCore")
            ],
        )
        # Pull only. The box has no business pushing an image.
        for repository in repositories:
            repository.grant_pull(role)
        role.add_to_policy(
            iam.PolicyStatement(actions=["ecr:GetAuthorizationToken"], resources=["*"])
        )
        # Both ends of the SSM file transfer: the box reads what Ansible uploaded and writes
        # back what Ansible fetches.
        transfer_bucket.grant_read_write(role)
        # GenerateDataKey and Decrypt, which is what envelope encryption needs. The containers
        # pick these credentials up from the instance metadata endpoint via the default chain.
        mfa_key.grant_encrypt_decrypt(role)

        # Everything the box needs before Ansible can reach it, and nothing else. Application
        # configuration is Ansible's job, so that changing it does not replace the instance.
        user_data = ec2.UserData.for_linux()
        user_data.add_commands(
            "set -eux",
            "dnf update -y",
            "dnf install -y docker python3 python3-pip",
            "systemctl enable --now docker",
            "usermod -aG docker ec2-user",
            "mkdir -p /usr/local/lib/docker/cli-plugins",
            # Architecture-matched, not hardcoded. A compose binary for the wrong architecture
            # still installs and still passes a file-exists check, then fails as "'compose' is
            # not a docker command" much later - which is exactly what an x86_64 download did on
            # this Graviton box. Ansible re-checks and repairs this, so a wrong one is survivable.
            'ARCH=$(uname -m)',
            "curl -fsSL https://github.com/docker/compose/releases/download/v2.32.1/"
            "docker-compose-linux-${ARCH} -o /usr/local/lib/docker/cli-plugins/docker-compose",
            "chmod +x /usr/local/lib/docker/cli-plugins/docker-compose",
            "install -d -o ec2-user -g ec2-user /opt/checky",
        )

        instance = ec2.Instance(
            self,
            "DemoInstance",
            vpc=vpc,
            vpc_subnets=ec2.SubnetSelection(subnet_type=ec2.SubnetType.PUBLIC),
            instance_type=ec2.InstanceType(instance_type),
            machine_image=ec2.MachineImage.latest_amazon_linux2023(
                cpu_type=ec2.AmazonLinuxCpuType.ARM_64
            ),
            security_group=security_group,
            role=role,
            # Omitted unless a key pair was named. Without one there is no SSH key on the box at
            # all, and break-glass access goes through the console or EC2 Instance Connect, which
            # pushes a temporary key. Session Manager does not use this either way.
            key_pair=(
                ec2.KeyPair.from_key_pair_name(self, "DemoKeyPair", key_name)
                if key_name
                else None
            ),
            user_data=user_data,
            block_devices=[
                ec2.BlockDevice(
                    device_name="/dev/xvda",
                    volume=ec2.BlockDeviceVolume.ebs(
                        30,
                        volume_type=ec2.EbsDeviceVolumeType.GP3,
                        encrypted=True,
                        delete_on_termination=True,
                    ),
                )
            ],
        )

        # A fixed address, so the DNS record survives a stop/start. Without it the public IP
        # changes and the certificate stops being issuable until DNS catches up.
        address = ec2.CfnEIP(self, "DemoEip", domain="vpc")
        ec2.CfnEIPAssociation(
            self,
            "DemoEipAssociation",
            allocation_id=address.attr_allocation_id,
            instance_id=instance.instance_id,
        )

        CfnOutput(
            self,
            "PublicIp",
            value=address.ref,
            description="Point your demo hostname at this address before running Ansible.",
        )
        CfnOutput(
            self,
            "EcrRegistry",
            value=f"{self.account}.dkr.ecr.{self.region}.amazonaws.com",
            description="ECR_REGISTRY for infra/deploy/.env",
        )
        CfnOutput(
            self,
            "MfaSeedKekArn",
            value=mfa_key.key_arn,
            description="MFA_SEED_KEK_ARN for infra/deploy/.env",
        )
        CfnOutput(
            self,
            "SsmTransferBucketName",
            value=transfer_bucket.bucket_name,
            description="SSM_BUCKET for ansible/inventory.ini",
        )
        CfnOutput(
            self,
            "InstanceId",
            value=instance.instance_id,
            description="ansible_host for the SSM connection, and what the console connects to.",
        )
        # Printed only when SSH was actually asked for, so the output cannot advertise a route
        # that no security group rule and no key pair would let anyone take.
        if ssh_cidr and key_name:
            CfnOutput(
                self,
                "SshCommand",
                value=f"ssh -i <{key_name}.pem> ec2-user@{address.ref}",
                description="Break-glass only. Ansible and the console both use SSM.",
            )
