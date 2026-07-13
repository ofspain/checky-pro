-- Two corrections to auth_audit (D-020), pre-data so a plain ALTER is safe:
--
-- 1. Rekey account_id -> account_uuid, same reasoning as V3/D-017: the audit module must not
--    resolve or hold the account module's internal bigint id. Dropping account_id also drops
--    its index (idx_auth_audit_account_time) automatically.
--
-- 2. ip INET -> VARCHAR(45): avoids depending on an unverified Hibernate/JDBC mapping for
--    Postgres's native inet type. This is an application audit log, not a network device
--    inventory — text storage loses no information we act on and removes a driver-version risk.

ALTER TABLE auth_audit DROP COLUMN account_id;
ALTER TABLE auth_audit ADD COLUMN account_uuid UUID REFERENCES accounts(account_uuid);
CREATE INDEX idx_auth_audit_account_uuid_time ON auth_audit(account_uuid, occurred_at);

ALTER TABLE auth_audit DROP COLUMN ip;
ALTER TABLE auth_audit ADD COLUMN ip VARCHAR(45);
