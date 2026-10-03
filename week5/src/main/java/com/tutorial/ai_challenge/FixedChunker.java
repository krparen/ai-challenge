package com.tutorial.ai_challenge;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class FixedChunker implements Chunk.Chunker {

	static final int WINDOW = 800;
	static final int OVERLAP = 100;

	@Override
	public String name() {
		return "fixed";
	}

	@Override
	public List<Chunk> split(String fileName, String text) {
		return Chunk.fixedChunks(fileName, fileName, "весь документ", text, WINDOW, OVERLAP);
	}

}
