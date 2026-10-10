package com.tutorial.ai_challenge;

import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class RagSearchService {

	public record Hit(int chunkId, String section, String content, double score) {
	}

	private final EmbeddingModel embeddingModel;
	private final JdbcTemplate jdbc;

	public RagSearchService(EmbeddingModel embeddingModel, JdbcTemplate jdbc) {
		this.embeddingModel = embeddingModel;
		this.jdbc = jdbc;
	}

	public List<Hit> search(String query, int topK) {
		float[] qv = embeddingModel.embed(query);
		List<long[]> ids = new ArrayList<>();
		List<Hit> all = new ArrayList<>();
		jdbc.query("SELECT id, section, chunk_id, content, embedding FROM doc_chunks", rs -> {
			int id = rs.getInt("id");
			String section = rs.getString("section");
			int chunkId = rs.getInt("chunk_id");
			String content = rs.getString("content");
			float[] v = fromJson(rs.getString("embedding"));
			all.add(new Hit(chunkId, section, content, cosine(qv, v)));
			ids.add(new long[] { id });
		});
		all.sort((a, b) -> Double.compare(b.score(), a.score()));
		return all.subList(0, Math.min(topK, all.size()));
	}

	private static float[] fromJson(String json) {
		String[] parts = json.substring(1, json.length() - 1).split(",");
		float[] v = new float[parts.length];
		for (int i = 0; i < parts.length; i++) {
			v[i] = Float.parseFloat(parts[i]);
		}
		return v;
	}

	private static double cosine(float[] a, float[] b) {
		double dot = 0, na = 0, nb = 0;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
			na += a[i] * a[i];
			nb += b[i] * b[i];
		}
		return dot / (Math.sqrt(na) * Math.sqrt(nb) + 1e-10);
	}

}
