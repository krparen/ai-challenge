package com.tutorial.ai_challenge;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkingTaskRepository extends JpaRepository<WorkingTask, UUID> {

	List<WorkingTask> findAllByOrderByCreatedAtDesc();

	List<WorkingTask> findByStatus(TaskStatus status);

}
