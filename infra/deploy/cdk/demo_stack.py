"""One box, three image repositories, and the smallest network that makes them work."""

from aws_cdk import (
    CfnOutput,
    RemovalPolicy,
    Stack,
    aws_ec2 as ec2,
    aws_ecr as ecr,
    aws_iam as iam,
)
from constructs import Construct

SERVICES = ("auth", "crypto", "payment")


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
    - **No load balancer.** Caddy terminates TLS on the box with Let's Encrypt, which is free and
      renews itself. An ALB with ACM is the production answer and costs about sixteen dollars a
      month to serve one page.
    """

    def __init__(
        self,
        scope: Construct,
        construct_id: str,
        *,
        ssh_cidr: str,
        key_name: str,
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

        security_group = ec2.SecurityGroup(
            self,
            "DemoSg",
            vpc=vpc,
            description="Public demo: HTTP/HTTPS from anywhere, SSH from one address only.",
            allow_all_outbound=True,
        )
        # 80 is not optional: Let's Encrypt validates over it, and Caddy redirects it to 443.
        security_group.add_ingress_rule(
            ec2.Peer.any_ipv4(), ec2.Port.tcp(80), "HTTP (ACME + redirect)"
        )
        security_group.add_ingress_rule(ec2.Peer.any_ipv4(), ec2.Port.tcp(443), "HTTPS")
        security_group.add_ingress_rule(
            ec2.Peer.ipv4(ssh_cidr), ec2.Port.tcp(22), "SSH for Ansible"
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
            "curl -fsSL https://github.com/docker/compose/releases/download/v2.32.1/"
            "docker-compose-linux-x86_64 -o /usr/local/lib/docker/cli-plugins/docker-compose",
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
                cpu_type=ec2.AmazonLinuxCpuType.X86_64
            ),
            security_group=security_group,
            role=role,
            key_pair=ec2.KeyPair.from_key_pair_name(self, "DemoKeyPair", key_name),
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
            "SshCommand",
            value=f"ssh -i <your-key.pem> ec2-user@{address.ref}",
            description="Ansible uses this host; check it works before the playbook runs.",
        )
