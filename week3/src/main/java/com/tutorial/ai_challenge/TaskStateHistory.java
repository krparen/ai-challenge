package com.tutorial.ai_challenge;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "task_state_history")
public class TaskStateHistory {

	@Id
	private UUID id;

	@Column(name = "task_id", nullable = false)
	private UUID taskId;

	@Column(name = "from_stage", nullable = false)
	@Enumerated(EnumType.STRING)
	private TaskStage fromStage;

	@Column(name = "to_stage", nullable = false)
	@Enumerated(EnumType.STRING)
	private TaskStage toStage;

	@Column(name = "driver", nullable = false)
	private String driver;

	@Column(name = "accepted", nullable = false)
	private boolean accepted;

	@Column(name = "note")
	private String note;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected TaskStateHistory() {
	}

	public TaskStateHistory(UUID id, UUID taskId, TaskStage fromStage, TaskStage toStage,
			String driver, boolean accepted, String note) {
		this.id = id;
		this.taskId = taskId;
		this.fromStage = fromStage;
		this.toStage = toStage;
		this.driver = driver;
		this.accepted = accepted;
		this.note = note;
	}

	public UUID getId() {
		return id;
	}

	public UUID getTaskId() {
		return taskId;
	}

	public TaskStage getFromStage() {
		return fromStage;
	}

	public TaskStage getToStage() {
		return toStage;
	}

	public String getDriver() {
		return driver;
	}

	public boolean isAccepted() {
		return accepted;
	}

	public String getNote() {
		return note;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

}
