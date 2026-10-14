-- T08: payment_app needs exactly what ShedLock's own JDBC-template provider actually issues
-- against Postgres (mirrors notification-service's own identical, empirically-confirmed grant -
-- INSERT ... ON CONFLICT (name) DO UPDATE ... WHERE lock_until <= :now to acquire a free/expired
-- lock, and a plain UPDATE ... WHERE name = :name AND lock_until <= :now to extend or release one
-- it already holds. SELECT is required too - Postgres requires SELECT on any column read by an
-- UPDATE's own WHERE predicate. No DELETE - a lock row is never removed, only ever extended or
-- allowed to expire.

GRANT SELECT, INSERT, UPDATE ON payments.shedlock TO payment_app;
