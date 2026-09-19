package com.tutorial.ai_challenge;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "profiles")
public class Profile {

	@Id
	private UUID id;

	@Column(name = "name", nullable = false)
	private String name;

	@Column(name = "style")
	private String style;

	@Column(name = "format")
	private String format;

	@Column(name = "restrictions")
	private String restrictions;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected Profile() {
	}

	public Profile(UUID id, String name) {
		this.id = id;
		this.name = name;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public String getStyle() {
		return style;
	}

	public String getFormat() {
		return format;
	}

	public String getRestrictions() {
		return restrictions;
	}

	public void updatePrefs(String style, String format, String restrictions) {
		this.style = blankToNull(style);
		this.format = blankToNull(format);
		this.restrictions = blankToNull(restrictions);
	}

	public boolean hasPrefs() {
		return style != null || format != null || restrictions != null;
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}

}
