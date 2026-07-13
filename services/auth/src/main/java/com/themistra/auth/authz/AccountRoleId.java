package com.themistra.auth.authz;

import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
public class AccountRoleId implements Serializable {

    private UUID accountUuid;
    private Long roleId;

    protected AccountRoleId() {
        // JPA only
    }

    public AccountRoleId(UUID accountUuid, Long roleId) {
        this.accountUuid = accountUuid;
        this.roleId = roleId;
    }

    public UUID getAccountUuid() {
        return accountUuid;
    }

    public Long getRoleId() {
        return roleId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AccountRoleId that)) return false;
        return Objects.equals(accountUuid, that.accountUuid) && Objects.equals(roleId, that.roleId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(accountUuid, roleId);
    }
}
