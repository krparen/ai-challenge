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

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "closed_at")
	private OffsetDateTime closedAt;

	@Column(name = "prompt_tokens", nullable = false)
	private long promptTokens;

	@Column(name = "completion_tokens", nullable = false)
	private long completionTokens;

	@Column(name = "summary")
	private String summary;

	@Column(name = "summary_message_count", nullable = false)
	private int summaryMessageCount;

	@Column(name = "summary_prompt_tokens", nullable = false)
	private long summaryPromptTokens;

	@Column(name = "summary_completion_tokens", nullable = false)
	private long summaryCompletionTokens;

	protected Conversation() {
	}

	public Conversation(UUID id) {
		this.id = id;
	}

	public UUID getId() {
		return id;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
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

	public long getSummaryPromptTokens() {
		return summaryPromptTokens;
	}

	public long getSummaryCompletionTokens() {
		return summaryCompletionTokens;
	}

	public void addSummaryUsage(int promptTokens, int completionTokens) {
		this.summaryPromptTokens += promptTokens;
		this.summaryCompletionTokens += completionTokens;
	}

	public String getSummary() {
		return summary;
	}

	public int getSummaryMessageCount() {
		return summaryMessageCount;
	}

	public void writeSummary(String summary, int coveredMessageCount) {
		this.summary = summary;
		this.summaryMessageCount = coveredMessageCount;
	}

	public void close() {
		this.closedAt = OffsetDateTime.now();
	}

}
