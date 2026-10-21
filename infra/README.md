# Infrastructure — AWS CDK (TypeScript)

Stacks are split by blast radius (CloudFormation rollbacks are per-stack):

- `network` — VPC, subnets, endpoints, NAT with allowlisted RPC egress
- `data` — RDS Postgres (Multi-AZ), MSK, S3 (Object Lock buckets), ElastiCache
- `security` — KMS attestation keys + key policies (isolated: a bad deploy elsewhere can never touch these)
- `edge` — CloudFront, WAF, ALB
- one stack per service — EKS workloads, IAM role per service (least privilege;
  only the crypto-service role gets `kms:Sign` on the attestation key)

Environments `dev`/`staging`/`prod` are separate AWS accounts. No console-created resources.

## Database layout (requirement for the `data` stack)

One RDS Postgres instance (Postgres 13 or later), with **one database per service**: auth,
crypto, payment and notification each get their own database and their own `DB_URL`. No service
reads another service's schema; they share data only through Kafka.

This is required, not a preference. auth-service and notification-service both use the `citext`
extension, and Postgres allows one copy of an extension per database, in one schema. Each service's
migrations need it in their own schema. Both services' Flyway callbacks (`db/migration/beforeMigrate.sql`)
refuse a shared database with a clear message (open-issues sweep, 2026-10-20).

Each service's migrating role needs CREATE on its own database, and CREATEROLE for the runtime-role
migrations. It does not need superuser: `citext` is a trusted extension from Postgres 13.

