package com.themistra.auth.authz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

interface AccountRoleTemplateAssignmentRepository
        extends JpaRepository<AccountRoleTemplateAssignment, AccountRoleTemplateId> {

    boolean existsByIdAccountUuidAndIdRoleTemplateId(UUID accountUuid, Long roleTemplateId);

    @Modifying
    @Query("""
            delete from AccountRoleTemplateAssignment a
            where a.id.accountUuid = :accountUuid and a.id.roleTemplateId = :roleTemplateId
            """)
    void deleteByAccountUuidAndRoleTemplateId(
            @Param("accountUuid") UUID accountUuid, @Param("roleTemplateId") Long roleTemplateId);
}
