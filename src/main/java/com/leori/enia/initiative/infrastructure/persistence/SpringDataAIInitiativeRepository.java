package com.leori.enia.initiative.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

interface SpringDataAIInitiativeRepository
        extends JpaRepository<AIInitiativeJpaEntity, UUID> {
}
