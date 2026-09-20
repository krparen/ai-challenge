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
@Table(name = "working_tasks")
public class WorkingTask {

	@Id
	private UUID id;

	@Column(name = "profile_id", nullable = false)
	private UUID profileId;

	@Column(name = "title", nullable = false)
	private String title;

	@Column(name = "state")
	private String state;

	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.STRING)
	private TaskStatus status = TaskStatus.ACTIVE;

	@Column(name = "stage", nullable = false)
	@Enumerated(EnumType.STRING)
	private TaskStage stage = TaskStage.PLANNING;

	@Column(name = "current_step")
	private String currentStep;

	@Column(name = "expected_action")
	private String expectedAction;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt = OffsetDateTime.now();

	protected WorkingTask() {
	}

	public WorkingTask(UUID id, UUID profileId, String title) {
		this.id = id;
		this.profileId = profileId;
		this.title = title;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProfileId() {
		return profileId;
	}

	public String getTitle() {
		return title;
	}

	public String getState() {
		return state;
	}

	public TaskStatus getStatus() {
		return status;
	}

	public TaskStage getStage() {
		return stage;
	}

	public void setStage(TaskStage stage) {
		this.stage = stage;
		this.updatedAt = OffsetDateTime.now();
	}

	public String getCurrentStep() {
		return currentStep;
	}

	public void setCurrentStep(String currentStep) {
		this.currentStep = currentStep == null || currentStep.isBlank() ? null : currentStep.strip();
	}

	public String getExpectedAction() {
		return expectedAction;
	}

	public void setExpectedAction(String expectedAction) {
		this.expectedAction = expectedAction == null || expectedAction.isBlank() ? null : expectedAction.strip();
	}

	public void writeState(String state) {
		this.state = state == null || state.isBlank() ? null : state.strip();
		this.updatedAt = OffsetDateTime.now();
	}

	public void activate() {
		this.status = TaskStatus.ACTIVE;
		this.updatedAt = OffsetDateTime.now();
	}

	public void pause() {
		this.status = TaskStatus.PAUSED;
		this.updatedAt = OffsetDateTime.now();
	}

	public void finish() {
		this.status = TaskStatus.DONE;
		this.updatedAt = OffsetDateTime.now();
	}

}
