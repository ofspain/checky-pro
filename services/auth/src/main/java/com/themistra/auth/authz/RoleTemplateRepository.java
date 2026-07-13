package com.themistra.auth.authz;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface RoleTemplateRepository extends JpaRepository<RoleTemplate, Long> {

    Optional<RoleTemplate> findByName(String name);

    boolean existsByName(String name);

    /** Templates assigned to the account, roles eager-fetched (RoleTemplate.roles is EAGER). */
    @org.springframework.data.jpa.repository.Query("""
            select t from RoleTemplate t
            join AccountRoleTemplateAssignment a on a.id.roleTemplateId = t.id
            where a.id.accountUuid = :accountUuid
            """)
    List<RoleTemplate> findAssignedToAccount(@org.springframework.data.repository.query.Param("accountUuid") UUID accountUuid);
}
