package com.tutorial.ai_challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class StructureChunker implements Chunk.Chunker {

	static final int MAX_SECTION = 1200;

	private static final Pattern HEADER = Pattern.compile("^(#{1,6})\\s+(.*)$");

	@Override
	public String name() {
		return "struct";
	}

	@Override
	public List<Chunk> split(String fileName, String text) {
		List<Chunk> result = new ArrayList<>();
		String[] lines = text.split("\r?\n");
		List<String> path = new ArrayList<>();
		StringBuilder body = new StringBuilder();
		for (String line : lines) {
			Matcher m = HEADER.matcher(line);
			if (m.matches()) {
				flush(result, fileName, path, body);
				int level = m.group(1).length();
				while (path.size() >= level) {
					path.remove(path.size() - 1);
				}
				path.add(m.group(2).strip());
			}
			else {
				body.append(line).append('\n');
			}
		}
		flush(result, fileName, path, body);
		return result;
	}

	private void flush(List<Chunk> result, String fileName, List<String> path, StringBuilder body) {
		String section = path.isEmpty() ? "вступление" : String.join(" > ", path);
		String content = body.toString().strip();
		body.setLength(0);
		if (content.isEmpty()) {
			return;
		}
		if (content.length() <= MAX_SECTION) {
			result.add(new Chunk(fileName, fileName, section, result.size(), content));
		}
		else {
			for (Chunk part : Chunk.fixedChunks(fileName, fileName, section, content, MAX_SECTION, 150)) {
				result.add(new Chunk(fileName, fileName, section, result.size(), part.content()));
			}
		}
	}

}
