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

	@Column(name = "branch_of")
	private UUID branchOf;

	@Column(name = "branch_point")
	private Integer branchPoint;

	@Column(name = "strategy", nullable = false)
	@Enumerated(EnumType.STRING)
	private ChatStrategy strategy = ChatStrategy.FULL;

	@Column(name = "facts")
	private String facts;

	@Column(name = "facts_prompt_tokens", nullable = false)
	private long factsPromptTokens;

	@Column(name = "facts_completion_tokens", nullable = false)
	private long factsCompletionTokens;

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

	public void reopen() {
		this.closedAt = null;
	}

	public void forkFrom(UUID parentId, Integer branchPoint) {
		this.branchOf = parentId;
		this.branchPoint = branchPoint;
	}

	public UUID getBranchOf() {
		return branchOf;
	}

	public Integer getBranchPoint() {
		return branchPoint;
	}

	public ChatStrategy getStrategy() {
		return strategy;
	}

	public void setStrategy(ChatStrategy strategy) {
		this.strategy = strategy;
	}

	public String getFacts() {
		return facts;
	}

	public void writeFacts(String facts) {
		this.facts = facts;
	}

	public long getFactsPromptTokens() {
		return factsPromptTokens;
	}

	public long getFactsCompletionTokens() {
		return factsCompletionTokens;
	}

	public void addFactsUsage(int promptTokens, int completionTokens) {
		this.factsPromptTokens += promptTokens;
		this.factsCompletionTokens += completionTokens;
	}

}
