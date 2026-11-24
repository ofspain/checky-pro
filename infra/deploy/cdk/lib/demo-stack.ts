import * as cdk from 'aws-cdk-lib';
import * as ec2 from 'aws-cdk-lib/aws-ec2';
import * as ecr from 'aws-cdk-lib/aws-ecr';
import * as iam from 'aws-cdk-lib/aws-iam';
import { Construct } from 'constructs';

export interface DemoStackProps extends cdk.StackProps {
  /** The address range allowed to reach port 22. Never 0.0.0.0/0. */
  readonly sshCidr: string;
  /** An existing EC2 key pair in this region; Ansible connects with its private half. */
  readonly keyName: string;
  readonly instanceType: string;
}

/**
 * One box, three image repositories, and the smallest network that makes them work.
 *
 * The real platform is planned as CDK stacks split by blast radius - network, data, security,
 * edge, one per service, on EKS with RDS, MSK and KMS (infra/README.md). This is not that, and is
 * not trying to be: it is the demo, sized for a demo, written so the parts that matter later are
 * already expressed as code rather than clicked into a console.
 *
 * Deliberate choices, each of which the real stack makes differently:
 *
 * - **The default VPC is used, not a new one.** A private subnet needs a NAT gateway to pull
 *   images, which costs more per month than the instance. The box is public-subnet with a
 *   security group doing the work.
 * - **Postgres and Kafka run as containers, not RDS and MSK.** One box, one demo. The compose
 *   file keeps one database per service, so moving to RDS later is a connection string.
 * - **No load balancer.** Caddy terminates TLS on the box with Let's Encrypt, which is free and
 *   renews itself. An ALB with ACM is the production answer and costs about sixteen dollars a
 *   month to serve one page.
 */
export class DemoStack extends cdk.Stack {
  constructor(scope: Construct, id: string, props: DemoStackProps) {
    super(scope, id, props);

    const vpc = ec2.Vpc.fromLookup(this, 'DefaultVpc', { isDefault: true });

    // One repository per service. The box only ever pulls; images are built and pushed from a
    // developer machine or CI, so the source tree never lands on an internet-facing host.
    const repositories = ['auth', 'crypto', 'payment'].map(
      (service) =>
        new ecr.Repository(this, `Repo${service}`, {
          repositoryName: `checky-${service}`,
          imageScanOnPush: true,
          removalPolicy: cdk.RemovalPolicy.DESTROY,
          emptyOnDelete: true,
          lifecycleRules: [{ maxImageCount: 5, description: 'Keep the last five images.' }],
        }),
    );

    const securityGroup = new ec2.SecurityGroup(this, 'DemoSg', {
      vpc,
      description: 'Public demo: HTTP/HTTPS from anywhere, SSH from one address only.',
      allowAllOutbound: true,
    });
    // 80 is not optional: Let's Encrypt validates over it, and Caddy redirects it to 443.
    securityGroup.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(80), 'HTTP (ACME + redirect)');
    securityGroup.addIngressRule(ec2.Peer.anyIpv4(), ec2.Port.tcp(443), 'HTTPS');
    securityGroup.addIngressRule(ec2.Peer.ipv4(props.sshCidr), ec2.Port.tcp(22), 'SSH for Ansible');

    const role = new iam.Role(this, 'DemoInstanceRole', {
      assumedBy: new iam.ServicePrincipal('ec2.amazonaws.com'),
      description: 'Pull demo images from ECR; reachable by Session Manager as a fallback.',
      managedPolicies: [
        iam.ManagedPolicy.fromAwsManagedPolicyName('AmazonSSMManagedInstanceCore'),
      ],
    });
    // Pull only. The box has no business pushing an image.
    repositories.forEach((repository) => repository.grantPull(role));
    role.addToPolicy(
      new iam.PolicyStatement({
        actions: ['ecr:GetAuthorizationToken'],
        resources: ['*'],
      }),
    );

    // Everything the box needs before Ansible can reach it, and nothing else. Application
    // configuration is Ansible's job, so that changing it does not replace the instance.
    const userData = ec2.UserData.forLinux();
    userData.addCommands(
      'set -eux',
      'dnf update -y',
      'dnf install -y docker python3 python3-pip',
      'systemctl enable --now docker',
      'usermod -aG docker ec2-user',
      'mkdir -p /usr/local/lib/docker/cli-plugins',
      'curl -fsSL https://github.com/docker/compose/releases/download/v2.32.1/docker-compose-linux-x86_64' +
        ' -o /usr/local/lib/docker/cli-plugins/docker-compose',
      'chmod +x /usr/local/lib/docker/cli-plugins/docker-compose',
      'install -d -o ec2-user -g ec2-user /opt/checky',
    );

    const instance = new ec2.Instance(this, 'DemoInstance', {
      vpc,
      vpcSubnets: { subnetType: ec2.SubnetType.PUBLIC },
      instanceType: new ec2.InstanceType(props.instanceType),
      machineImage: ec2.MachineImage.latestAmazonLinux2023({
        cpuType: ec2.AmazonLinuxCpuType.X86_64,
      }),
      securityGroup,
      role,
      keyPair: ec2.KeyPair.fromKeyPairName(this, 'DemoKeyPair', props.keyName),
      userData,
      blockDevices: [
        {
          deviceName: '/dev/xvda',
          volume: ec2.BlockDeviceVolume.ebs(30, {
            volumeType: ec2.EbsDeviceVolumeType.GP3,
            encrypted: true,
            deleteOnTermination: true,
          }),
        },
      ],
    });

    // A fixed address, so the DNS record survives a stop/start. Without it the public IP changes
    // and the certificate stops being issuable until DNS catches up.
    const address = new ec2.CfnEIP(this, 'DemoEip', { domain: 'vpc' });
    new ec2.CfnEIPAssociation(this, 'DemoEipAssociation', {
      allocationId: address.attrAllocationId,
      instanceId: instance.instanceId,
    });

    new cdk.CfnOutput(this, 'PublicIp', {
      value: address.ref,
      description: 'Point your demo hostname at this address before running Ansible.',
    });
    new cdk.CfnOutput(this, 'EcrRegistry', {
      value: `${this.account}.dkr.ecr.${this.region}.amazonaws.com`,
      description: 'ECR_REGISTRY for infra/deploy/.env',
    });
    new cdk.CfnOutput(this, 'SshCommand', {
      value: `ssh -i <your-key.pem> ec2-user@${address.ref}`,
      description: 'Ansible uses this host; check it works before the playbook runs.',
    });
  }
}
