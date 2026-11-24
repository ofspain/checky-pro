#!/usr/bin/env node
import 'source-map-support/register';
import * as cdk from 'aws-cdk-lib';
import { DemoStack } from '../lib/demo-stack';

const app = new cdk.App();

// Required context, passed with -c or set in cdk.context.json. There is no default for sshCidr
// on purpose: a default would be 0.0.0.0/0, and SSH open to the internet is not a default anyone
// should get by forgetting to think about it.
const sshCidr = app.node.tryGetContext('sshCidr');
const keyName = app.node.tryGetContext('keyName');
if (!sshCidr || !keyName) {
  throw new Error(
    'Missing context. Deploy with:\n' +
      '  npx cdk deploy -c keyName=<ec2-key-pair> -c sshCidr=<your.ip.addr.ess/32>\n' +
      'Find your address with: curl -s https://checkip.amazonaws.com',
  );
}

new DemoStack(app, 'CheckyDemo', {
  env: {
    account: process.env.CDK_DEFAULT_ACCOUNT,
    region: process.env.CDK_DEFAULT_REGION ?? 'eu-west-1',
  },
  description: 'Public verification demo: one EC2 instance, three ECR repositories.',
  sshCidr,
  keyName,
  // t3.medium, not t3.small. Three JVMs, Kafka and Postgres share this box; measured against
  // the real images, payment-service alone took 188 seconds to start when all three booted at
  // once. 2 GB is not enough headroom for that, and an OOM during startup looks like a hang.
  instanceType: app.node.tryGetContext('instanceType') ?? 't3.medium',
});

app.synth();
