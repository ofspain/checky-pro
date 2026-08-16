-- Supporting indexes for the scheduled cleanup job (T30, R40).
-- Additive only; V1-V5 remain untouched (L1).

CREATE INDEX IF NOT EXISTS idx_verification_tokens_expires_at
    ON verification_tokens(expires_at);

CREATE INDEX IF NOT EXISTS idx_refresh_token_family_revoked_at
    ON refresh_token_family(revoked_at)
    WHERE revoked_at IS NOT NULL;
