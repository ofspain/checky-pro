-- Rekeys account_roles / account_role_templates on accounts.account_uuid instead of the
-- internal accounts.id (D-017). The authz module must never resolve or hold the internal
-- bigint id (Account's own invariant: it never leaves the account module) — every other
-- module already addresses accounts purely by UUID (token 'sub', API paths). This migration
-- corrects V1 before any data exists in these tables to keep authz consistent with that rule.

DROP TABLE account_roles;
DROP TABLE account_role_templates;

CREATE TABLE account_roles (
    account_uuid        UUID        NOT NULL REFERENCES accounts(account_uuid) ON DELETE CASCADE,
    role_id             BIGINT      NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    granted_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by          UUID,
    PRIMARY KEY (account_uuid, role_id)
);

CREATE TABLE account_role_templates (
    account_uuid        UUID        NOT NULL REFERENCES accounts(account_uuid) ON DELETE CASCADE,
    role_template_id    BIGINT      NOT NULL REFERENCES role_templates(id) ON DELETE CASCADE,
    granted_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    granted_by          UUID,
    PRIMARY KEY (account_uuid, role_template_id)
);
