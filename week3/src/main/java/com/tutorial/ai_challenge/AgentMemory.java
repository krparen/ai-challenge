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
@Table(name = "agent_memory")
public class AgentMemory {

	@Id
	private UUID id;

	@Column(name = "profile_id", nullable = false)
	private UUID profileId;

	@Column(name = "fact_key", nullable = false)
	private String factKey;

	@Column(name = "fact_value", nullable = false)
	private String factValue;

	@Column(name = "status", nullable = false)
	@Enumerated(EnumType.STRING)
	private MemoryStatus status = MemoryStatus.PENDING;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	@Column(name = "decided_at")
	private OffsetDateTime decidedAt;

	protected AgentMemory() {
	}

	public AgentMemory(UUID id, UUID profileId, String factKey, String factValue, MemoryStatus status) {
		this.id = id;
		this.profileId = profileId;
		this.factKey = factKey;
		this.factValue = factValue;
		this.status = status;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProfileId() {
		return profileId;
	}

	public String getFactKey() {
		return factKey;
	}

	public String getFactValue() {
		return factValue;
	}

	public MemoryStatus getStatus() {
		return status;
	}

	public void confirm() {
		this.status = MemoryStatus.CONFIRMED;
		this.decidedAt = OffsetDateTime.now();
	}

	public void reject() {
		this.status = MemoryStatus.REJECTED;
		this.decidedAt = OffsetDateTime.now();
	}

}
