package com.themistra.auth.authz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface AccountRoleAssignmentRepository extends JpaRepository<AccountRoleAssignment, AccountRoleId> {

    boolean existsByIdAccountUuidAndIdRoleId(UUID accountUuid, Long roleId);

    @Modifying
    @Query("delete from AccountRoleAssignment a where a.id.accountUuid = :accountUuid and a.id.roleId = :roleId")
    void deleteByAccountUuidAndRoleId(@Param("accountUuid") UUID accountUuid, @Param("roleId") Long roleId);
}
