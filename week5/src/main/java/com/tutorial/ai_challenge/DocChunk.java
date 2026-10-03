package com.tutorial.ai_challenge;

import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "doc_chunks")
@Getter
@Setter
public class DocChunk {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String strategy;

	@Column(nullable = false)
	private String source;

	@Column(nullable = false)
	private String title;

	private String section;

	@Column(name = "chunk_id", nullable = false)
	private int chunkId;

	@Column(nullable = false, columnDefinition = "text")
	private String content;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false, columnDefinition = "jsonb")
	private float[] embedding;

	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

}
