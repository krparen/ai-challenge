package com.tutorial.ai_challenge;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProfileAttributeRepository extends JpaRepository<ProfileAttribute, UUID> {

	List<ProfileAttribute> findByProfileIdOrderByCreatedAtAsc(UUID profileId);

}
