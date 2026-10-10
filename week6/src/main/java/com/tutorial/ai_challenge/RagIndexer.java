package com.tutorial.ai_challenge;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RagIndexer {

	private final EmbeddingModel embeddingModel;
	private final JdbcTemplate jdbc;

	public RagIndexer(EmbeddingModel embeddingModel, JdbcTemplate jdbc) {
		this.embeddingModel = embeddingModel;
		this.jdbc = jdbc;
	}

	public record Chunk(String section, int chunkId, String content) {
	}

	public int index() throws Exception {
		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		Resource[] resources = resolver.getResources("classpath:/docs/*.md");
		List<Chunk> chunks = new ArrayList<>();
		for (Resource r : resources) {
			String md = new String(r.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			chunks.addAll(splitBySections(md));
		}
		jdbc.update("DELETE FROM doc_chunks");
		for (Chunk c : chunks) {
			float[] v = embeddingModel.embed(c.content());
			jdbc.update("INSERT INTO doc_chunks (section, chunk_id, content, embedding) VALUES (?, ?, ?, ?)",
					c.section(), c.chunkId(), c.content(), toJson(v));
		}
		return chunks.size();
	}

	private List<Chunk> splitBySections(String md) {
		List<Chunk> chunks = new ArrayList<>();
		String current = "(без раздела)";
		StringBuilder buf = new StringBuilder();
		int id = 0;
		for (String line : md.split("\n")) {
			if (line.matches("#+ .*")) {
				if (!buf.isEmpty()) {
					chunks.addAll(cut(current, id, buf.toString()));
					id = chunks.size();
					buf.setLength(0);
				}
				int level = line.indexOf(' ');
				String title = line.substring(level + 1).trim();
				current = level <= 2 ? title : current + " > " + title;
			}
			else {
				buf.append(line).append('\n');
			}
		}
		if (!buf.isEmpty()) {
			chunks.addAll(cut(current, id, buf.toString()));
		}
		return chunks;
	}

	private List<Chunk> cut(String section, int startId, String text) {
		List<Chunk> chunks = new ArrayList<>();
		int WINDOW = 1200;
		int OVERLAP = 150;
		int id = startId;
		for (int i = 0; i < text.length(); i += WINDOW - OVERLAP) {
			String piece = (text.substring(i, Math.min(text.length(), i + WINDOW)) + "\n(раздел: " + section + ")").trim();
			if (!piece.isEmpty()) {
				chunks.add(new Chunk(section, id++, piece));
			}
			if (i + WINDOW >= text.length()) {
				break;
			}
		}
		return chunks;
	}

	private static String toJson(float[] v) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < v.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(v[i]);
		}
		return sb.append(']').toString();
	}

}
