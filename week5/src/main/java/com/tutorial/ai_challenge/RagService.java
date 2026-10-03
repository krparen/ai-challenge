package com.tutorial.ai_challenge;

import java.util.ArrayList;
import java.util.List;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class RagService {

	public record RagAnswer(String mode, String answer, List<SearchService.Hit> hits) {
	}

	public record ControlQuestion(String question, String expected, String marker, String section) {
	}

	public record ControlResult(ControlQuestion question, String plainAnswer, boolean plainOk, String ragAnswer,
			boolean ragOk, List<String> sections) {
	}

	private final ChatClient chatClient;
	private final SearchService searchService;
	private final ObjectMapper mapper;
	private final List<ControlQuestion> controlQuestions;

	public RagService(ChatClient chatClient, SearchService searchService, ObjectMapper mapper) {
		this.chatClient = chatClient;
		this.searchService = searchService;
		this.mapper = mapper;
		this.controlQuestions = loadControlQuestions();
	}

	private List<ControlQuestion> loadControlQuestions() {
		try (var in = new ClassPathResource("rag-control.json").getInputStream()) {
			return mapper.readValue(in, new TypeReference<List<ControlQuestion>>() {
			});
		}
		catch (Exception e) {
			throw new IllegalStateException("Не удалось прочитать rag-control.json", e);
		}
	}

	public List<ControlQuestion> controlQuestions() {
		return controlQuestions;
	}

	public RagAnswer ask(String question, boolean rag) {
		if (!rag) {
			String answer = chatClient.prompt()
					.user(question)
					.call()
					.content();
			return new RagAnswer("без RAG", answer, List.of());
		}
		List<SearchService.Hit> hits = searchService.search("struct", question, 5);
		StringBuilder context = new StringBuilder();
		for (SearchService.Hit h : hits) {
			context.append("[источник: ").append(h.title())
					.append(" | раздел: ").append(h.section())
					.append(" | чанк #").append(h.chunkId())
					.append("]\n")
					.append(h.content())
					.append("\n\n");
		}
		String prompt = """
				Ты — справочник по заклинаниям мира Тани Гроттер.
				Ответь на вопрос, опираясь ТОЛЬКО на приведённый контекст.
				Если ответа в контексте нет — честно скажи, что не нашёл, и не выдумывай.

				Контекст:
				%s
				Вопрос: %s
				""".formatted(context, question);
		String answer = chatClient.prompt()
				.user(prompt)
				.call()
				.content();
		return new RagAnswer("с RAG", answer, hits);
	}

	public List<ControlResult> runControl() {
		List<ControlResult> results = new ArrayList<>();
		for (ControlQuestion q : controlQuestions) {
			RagAnswer plain = ask(q.question(), false);
			RagAnswer rag = ask(q.question(), true);
			results.add(new ControlResult(q,
					plain.answer(), contains(plain.answer(), q.marker()),
					rag.answer(), contains(rag.answer(), q.marker()),
					rag.hits().stream().map(SearchService.Hit::section).toList()));
		}
		return results;
	}

	private static boolean contains(String answer, String marker) {
		return answer != null && answer.toLowerCase().contains(marker.toLowerCase());
	}

}
