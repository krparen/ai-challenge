package com.tutorial.ai_challenge;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IndexingService {

	public record Stats(String strategy, int docs, int chunks, double avgChars, long ms) {
	}

	private static final String CORPUS_PATTERN = "classpath:/docs/*.md";

	private final List<Chunk.Chunker> chunkers;
	private final EmbeddingModel embeddingModel;
	private final DocChunkRepository repository;

	public IndexingService(List<Chunk.Chunker> chunkers, EmbeddingModel embeddingModel,
			DocChunkRepository repository) {
		this.chunkers = chunkers;
		this.embeddingModel = embeddingModel;
		this.repository = repository;
	}

	@Transactional
	public Stats index(String strategy) {
		Chunk.Chunker chunker = chunkers.stream()
				.filter(c -> c.name().equals(strategy))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Нет стратегии: " + strategy));
		long t0 = System.currentTimeMillis();
		List<Resource> docs = docs();
		List<Chunk> chunks = new ArrayList<>();
		for (Resource doc : docs) {
			chunks.addAll(chunker.split(filename(doc), read(doc)));
		}
		List<float[]> vectors = embeddingModel.embed(chunks.stream().map(Chunk::content).toList());
		repository.deleteByStrategy(strategy);
		for (int i = 0; i < chunks.size(); i++) {
			Chunk c = chunks.get(i);
			DocChunk entity = new DocChunk();
			entity.setStrategy(strategy);
			entity.setSource(c.source());
			entity.setTitle(c.title());
			entity.setSection(c.section());
			entity.setChunkId(c.chunkId());
			entity.setContent(c.content());
			entity.setEmbedding(vectors.get(i));
			entity.setCreatedAt(OffsetDateTime.now());
			repository.save(entity);
		}
		double avg = chunks.stream().mapToInt(c -> c.content().length()).average().orElse(0);
		return new Stats(strategy, docs.size(), chunks.size(), Math.round(avg), System.currentTimeMillis() - t0);
	}

	private List<Resource> docs() {
		try {
			Resource[] resources = new PathMatchingResourcePatternResolver().getResources(CORPUS_PATTERN);
			return Arrays.stream(resources)
					.sorted(Comparator.comparing(IndexingService::filename))
					.toList();
		}
		catch (IOException e) {
			throw new IllegalStateException("Не найдены документы в classpath: " + CORPUS_PATTERN, e);
		}
	}

	private static String filename(Resource resource) {
		String name = resource.getFilename();
		return name != null ? name : "документ.md";
	}

	private String read(Resource resource) {
		try (InputStream in = resource.getInputStream()) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new IllegalStateException("Не удалось прочитать " + filename(resource), e);
		}
	}

}
