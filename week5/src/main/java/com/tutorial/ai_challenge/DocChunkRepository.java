package com.tutorial.ai_challenge;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DocChunkRepository extends JpaRepository<DocChunk, Long> {

	List<DocChunk> findByStrategyOrderByChunkId(String strategy);

	void deleteByStrategy(String strategy);

	List<DocChunk> findByStrategy(String strategy);

}
