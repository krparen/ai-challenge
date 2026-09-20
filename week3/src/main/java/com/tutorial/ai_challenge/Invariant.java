package com.tutorial.ai_challenge;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "invariants")
public class Invariant {

	@Id
	private UUID id;

	@Column(name = "category", nullable = false)
	private String category;

	@Column(name = "text", nullable = false)
	private String text;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected Invariant() {
	}

	public Invariant(UUID id, String category, String text) {
		this.id = id;
		this.category = category;
		this.text = text;
	}

	public UUID getId() {
		return id;
	}

	public String getCategory() {
		return category;
	}

	public String getText() {
		return text;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

}
