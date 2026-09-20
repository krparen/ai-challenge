package com.tutorial.ai_challenge;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InvariantRepository extends JpaRepository<Invariant, UUID> {

	List<Invariant> findAllByOrderByCreatedAtAsc();

}
