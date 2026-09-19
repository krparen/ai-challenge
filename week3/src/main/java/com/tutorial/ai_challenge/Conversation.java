package com.tutorial.ai_challenge;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "conversations")
public class Conversation {

	@Id
	private UUID id;

	@Column(name = "profile_id")
	private UUID profileId;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "closed_at")
	private OffsetDateTime closedAt;

	@Column(name = "prompt_tokens", nullable = false)
	private long promptTokens;

	@Column(name = "completion_tokens", nullable = false)
	private long completionTokens;

	@Column(name = "extraction_prompt_tokens", nullable = false)
	private long extractionPromptTokens;

	@Column(name = "extraction_completion_tokens", nullable = false)
	private long extractionCompletionTokens;

	protected Conversation() {
	}

	public Conversation(UUID id, UUID profileId) {
		this.id = id;
		this.profileId = profileId;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProfileId() {
		return profileId;
	}

	public void setProfileId(UUID profileId) {
		this.profileId = profileId;
	}

	public OffsetDateTime getClosedAt() {
		return closedAt;
	}

	public long getPromptTokens() {
		return promptTokens;
	}

	public long getCompletionTokens() {
		return completionTokens;
	}

	public void addUsage(int promptTokens, int completionTokens) {
		this.promptTokens += promptTokens;
		this.completionTokens += completionTokens;
	}

	public long getExtractionPromptTokens() {
		return extractionPromptTokens;
	}

	public long getExtractionCompletionTokens() {
		return extractionCompletionTokens;
	}

	public void addExtractionUsage(int promptTokens, int completionTokens) {
		this.extractionPromptTokens += promptTokens;
		this.extractionCompletionTokens += completionTokens;
	}

	public void close() {
		this.closedAt = OffsetDateTime.now();
	}

}
