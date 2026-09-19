package com.tutorial.ai_challenge;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "profile_attributes")
public class ProfileAttribute {

	@Id
	private UUID id;

	@Column(name = "profile_id", nullable = false)
	private UUID profileId;

	@Column(name = "attr_key", nullable = false)
	private String attrKey;

	@Column(name = "attr_value", nullable = false)
	private String attrValue;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected ProfileAttribute() {
	}

	public ProfileAttribute(UUID id, UUID profileId, String attrKey, String attrValue) {
		this.id = id;
		this.profileId = profileId;
		this.attrKey = attrKey;
		this.attrValue = attrValue;
	}

	public UUID getId() {
		return id;
	}

	public UUID getProfileId() {
		return profileId;
	}

	public String getAttrKey() {
		return attrKey;
	}

	public String getAttrValue() {
		return attrValue;
	}

}
