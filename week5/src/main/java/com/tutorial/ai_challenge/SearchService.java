package com.tutorial.ai_challenge;

import java.util.Comparator;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

	public record Hit(String source, String title, String section, int chunkId, double score, String content) {
	}

	public record ControlQuery(String query, String spell) {
	}

	public record CompareRow(String strategy, int docs, int chunks, double avgChars, long indexMs, int hits,
			int total) {
	}

	static final List<ControlQuery> CONTROL_QUERIES = List.of(
			new ControlQuery("чем превратить противника в червяка", "Быгус-гмыгус-тарагмыгус"),
			new ControlQuery("как вызвать боевую искру", "Искрис фронтис"),
			new ControlQuery("какое заклинание отделяет душу от тела", "Капут тынетут"),
			new ControlQuery("заклинание молчания", "Молчанус"),
			new ControlQuery("какое заклинание помогает от прыщей", "Угреостис"),
			new ControlQuery("заклинание снайпера, чтобы не промахиваться", "Лайперус Снайперус"),
			new ControlQuery("как выбить дверь магией", "Омонус всемлежатус"),
			new ControlQuery("катапультирующее заклинание", "Шмыглис-Фрыглис"));

	private final EmbeddingModel embeddingModel;
	private final DocChunkRepository repository;
	private final IndexingService indexingService;

	public SearchService(EmbeddingModel embeddingModel, DocChunkRepository repository,
			IndexingService indexingService) {
		this.embeddingModel = embeddingModel;
		this.repository = repository;
		this.indexingService = indexingService;
	}

	public List<Hit> search(String strategy, String query, int topK) {
		float[] queryVector = embeddingModel.embed(query);
		return repository.findByStrategy(strategy).stream()
				.map(c -> new Hit(c.getSource(), c.getTitle(), c.getSection(), c.getChunkId(),
						cosine(queryVector, c.getEmbedding()), c.getContent()))
				.sorted(Comparator.comparingDouble(Hit::score).reversed())
				.limit(topK)
				.toList();
	}

	public List<CompareRow> compare() {
		List<CompareRow> rows = new java.util.ArrayList<>();
		for (String strategy : List.of("fixed", "struct")) {
			IndexingService.Stats stats = indexingService.index(strategy);
			int hits = 0;
			for (ControlQuery q : CONTROL_QUERIES) {
				List<Hit> top = search(strategy, q.query(), 5);
				if (top.stream().anyMatch(h -> h.content().toLowerCase().contains(q.spell().toLowerCase()))) {
					hits++;
				}
			}
			rows.add(new CompareRow(strategy, stats.docs(), stats.chunks(), stats.avgChars(), stats.ms(), hits,
					CONTROL_QUERIES.size()));
		}
		return rows;
	}

	static double cosine(float[] a, float[] b) {
		double dot = 0;
		double normA = 0;
		double normB = 0;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
			normA += a[i] * a[i];
			normB += b[i] * b[i];
		}
		return dot / (Math.sqrt(normA) * Math.sqrt(normB));
	}

}
