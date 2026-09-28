package com.themistra.notification.template;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * {@code findTopByNameAndChannelOrderByVersionDesc} is the only sanctioned call (Kimi Phase 3
 * Finding #6). {@code extends JpaRepository}, mirroring T04/T05/T08's own precedent, even though
 * the inherited {@code save}/{@code saveAll}/{@code delete} mutators are never called by anything
 * in this codebase - {@code templates} has no write API anywhere in this spec, and {@code V7}'s own
 * grant (SELECT only) makes any accidental mutator call fail at the DB layer regardless of what
 * this interface exposes in Java.
 */
interface TemplateRepository extends JpaRepository<Template, Long> {

    Optional<Template> findTopByNameAndChannelOrderByVersionDesc(String name, String channel);
}
