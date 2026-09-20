package com.tutorial.ai_challenge;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskStateHistoryRepository extends JpaRepository<TaskStateHistory, UUID> {

	List<TaskStateHistory> findTop10ByTaskIdOrderByCreatedAtDesc(UUID taskId);

}
