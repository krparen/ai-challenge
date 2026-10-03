package com.tutorial.ai_challenge;

import java.util.ArrayList;
import java.util.List;

public record Chunk(String source, String title, String section, int chunkId, String content) {

	public interface Chunker {

		String name();

		List<Chunk> split(String fileName, String text);

	}

	static List<String> readSections(String text) {
		return List.of(text.split("\r?\n"));
	}

	static List<Chunk> fixedChunks(String source, String title, String section, String text, int window,
			int overlap) {
		List<Chunk> result = new ArrayList<>();
		String clean = text.strip();
		if (clean.isEmpty()) {
			return result;
		}
		int start = 0;
		while (start < clean.length()) {
			int end = Math.min(start + window, clean.length());
			if (end < clean.length()) {
				int breakPoint = clean.lastIndexOf("\n\n", end);
				if (breakPoint <= start) {
					breakPoint = clean.lastIndexOf("\n", end);
				}
				if (breakPoint > start + window / 2) {
					end = breakPoint;
				}
			}
			result.add(new Chunk(source, title, section, result.size(), clean.substring(start, end).strip()));
			if (end >= clean.length()) {
				break;
			}
			start = Math.max(end - overlap, start + 1);
		}
		return result;
	}

}
