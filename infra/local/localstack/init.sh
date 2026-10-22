#!/bin/bash
# LocalStack ready hook: the AWS resources the services expect, created on every start
# (LocalStack keeps nothing between runs). Local-only names.
set -euo pipefail
awslocal s3api create-bucket --bucket themistra-local-receipts --object-lock-enabled-for-bucket >/dev/null
awslocal s3api put-object-lock-configuration --bucket themistra-local-receipts \
  --object-lock-configuration '{"ObjectLockEnabled":"Enabled","Rule":{"DefaultRetention":{"Mode":"COMPLIANCE","Days":1}}}'
awslocal s3api create-bucket --bucket themistra-local-observations >/dev/null
# crypto-service signs receipt digests with ECDSA_SHA_256 (KmsSigner). The fixed id lets the
# crypto config name it before the key exists.
awslocal kms create-key --key-usage SIGN_VERIFY --key-spec ECC_NIST_P256 \
  --tags TagKey=_custom_id_,TagValue=00000000-0000-4000-8000-000000000001 >/dev/null
echo "themistra local AWS resources ready"
