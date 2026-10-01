-- T14: notification_app needs exactly what ShedLock's own JDBC-template provider actually issues
-- against Postgres (confirmed directly against net.javacrumbs.shedlock:shedlock-sql-support's own
-- source - PostgresSqlStatementsSource/SqlStatementsSource) - an INSERT ... ON CONFLICT (name) DO
-- UPDATE ... WHERE lock_until <= :now to acquire a free/expired lock, and a plain
-- UPDATE ... WHERE name = :name AND lock_until <= :now to extend or release one it already holds.
-- SELECT is required too, even though the provider never runs a standalone SELECT itself - Postgres
-- requires SELECT on any column read by an UPDATE's own WHERE predicate (confirmed empirically: a
-- SELECT-less grant made the real UPDATE fail with "permission denied for table shedlock"). No
-- DELETE - a lock row is never removed, only ever extended or allowed to expire.

GRANT SELECT, INSERT, UPDATE ON notifications.shedlock TO notification_app;
