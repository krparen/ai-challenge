package com.tutorial.ai_challenge;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentMemoryRepository extends JpaRepository<AgentMemory, UUID> {

	List<AgentMemory> findByProfileIdOrderByCreatedAtDesc(UUID profileId);

}
