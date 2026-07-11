# Infrastructure — AWS CDK (TypeScript)

Stacks are split by blast radius (CloudFormation rollbacks are per-stack):

- `network` — VPC, subnets, endpoints, NAT with allowlisted RPC egress
- `data` — RDS Postgres (Multi-AZ), MSK, S3 (Object Lock buckets), ElastiCache
- `security` — KMS attestation keys + key policies (isolated: a bad deploy elsewhere can never touch these)
- `edge` — CloudFront, WAF, ALB
- one stack per service — EKS workloads, IAM role per service (least privilege;
  only the crypto-service role gets `kms:Sign` on the attestation key)

Environments `dev`/`staging`/`prod` are separate AWS accounts. No console-created resources.
