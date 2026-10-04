package com.tutorial.ai_challenge;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class RagService {

	public enum FilterMode {
		THRESHOLD, RERANK
	}

	public record CandidateHit(SearchService.Hit hit, boolean used, String note) {
	}

	public record PipelineResult(String name, String query, String answer, List<CandidateHit> candidates,
			int usedCount) {
	}

	public record AskResult(String question, String rewritten, PipelineResult oldPipeline,
			PipelineResult newPipeline) {
	}

	public record ControlQuestion(String question, String expected, String marker, String section) {
	}

	public record ControlResult(ControlQuestion question, String oldAnswer, boolean oldOk, String newAnswer,
			boolean newOk, List<String> sections) {
	}

	static final double MIN_SIMILARITY = 0.45;
	static final int TOP_K_BEFORE = 10;
	static final int TOP_K_AFTER = 4;

	private static final Pattern NUMBER = Pattern.compile("\\d+");

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

	public AskResult askBoth(String question, boolean rewrite, FilterMode mode) {
		PipelineResult oldPipeline = oldPipeline(question);
		String rewritten = rewrite ? rewriteQuestion(question) : null;
		PipelineResult newPipeline = newPipeline(question, rewritten, mode);
		return new AskResult(question, rewritten, oldPipeline, newPipeline);
	}

	public PipelineResult oldPipeline(String question) {
		List<SearchService.Hit> hits = searchService.search("struct", question, 5);
		List<CandidateHit> candidates = hits.stream().map(h -> new CandidateHit(h, true, "использован")).toList();
		String answer = answerFromContext(question, hits);
		return new PipelineResult("Старый пайплайн: топ-5, без фильтра", question, answer, candidates,
				hits.size());
	}

	public PipelineResult newPipeline(String question, String rewritten, FilterMode mode) {
		String query = rewritten != null && !rewritten.isBlank() ? rewritten : question;
		List<SearchService.Hit> candidates = searchService.search("struct", query, TOP_K_BEFORE);

		List<SearchService.Hit> selected;
		List<CandidateHit> marked;
		if (mode == FilterMode.THRESHOLD) {
			selected = candidates.stream().filter(h -> h.score() >= MIN_SIMILARITY).limit(TOP_K_AFTER).toList();
			marked = candidates.stream()
					.map(h -> new CandidateHit(h, selected.contains(h),
							h.score() >= MIN_SIMILARITY ? (selected.contains(h) ? "прошёл порог, использован"
									: "прошёл порог, сверх топ-" + TOP_K_AFTER) : "ниже порога " + MIN_SIMILARITY))
					.toList();
		}
		else {
			selected = rerank(query, candidates);
			marked = candidates.stream()
					.map(h -> new CandidateHit(h, selected.contains(h),
							selected.contains(h) ? "выбран реранкером" : "отклонён реранкером"))
					.toList();
		}

		String answer = selected.isEmpty()
				? "Все кандидаты отфильтрованы (порог " + MIN_SIMILARITY + ") — релевантного в базе не нашлось, отвечать не буду."
				: answerFromContext(question, selected);
		return new PipelineResult(
				"Новый пайплайн: " + (rewritten != null ? "rewrite + " : "") + modeLabel(mode)
						+ ", топ-" + TOP_K_BEFORE + " → топ-" + TOP_K_AFTER,
				query, answer, marked, selected.size());
	}

	private static String modeLabel(FilterMode mode) {
		return mode == FilterMode.THRESHOLD ? "порог similarity" : "LLM-реранк";
	}

	public String rewriteQuestion(String question) {
		String prompt = """
				Перепиши вопрос пользователя для векторного поиска по справочнику заклинаний мира Тани Гроттер.
				Используй лексику, которая могла бы встретиться в таком справочнике (типы заклинаний, эффекты, предметы).
				Не отвечай на вопрос — верни только переписанный поисковый запрос одной строкой, без кавычек и пояснений.

				Вопрос: %s
				""".formatted(question);
		String out = chatClient.prompt().user(prompt).call().content();
		return out == null ? null : out.trim().replaceAll("^[\"'«»]+|[\"'«»]+$", "").replaceAll("\\s+", " ");
	}

	private List<SearchService.Hit> rerank(String query, List<SearchService.Hit> candidates) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < candidates.size(); i++) {
			SearchService.Hit h = candidates.get(i);
			sb.append("[").append(i + 1).append("] раздел: ").append(h.section()).append("\n")
					.append(h.content()).append("\n\n");
		}
		String prompt = """
				Вопрос: %s

				Ниже кандидаты из векторного поиска — чанки справочника заклинаний.
				Оцени, какие кандидаты релевантны вопросу (содержат ответ или прямое указание на него).
				Верни номера релевантных кандидатов через запятую, максимум %d штук, самые релевантные первыми.
				В ответе — только номера, без пояснений.

				Кандидаты:
				%s
				""".formatted(query, TOP_K_AFTER, sb);
		String out = chatClient.prompt().user(prompt).call().content();
		LinkedHashSet<Integer> picked = new LinkedHashSet<>();
		if (out != null) {
			var m = NUMBER.matcher(out);
			while (m.find() && picked.size() < TOP_K_AFTER) {
				int n = Integer.parseInt(m.group());
				if (n >= 1 && n <= candidates.size()) {
					picked.add(n - 1);
				}
			}
		}
		if (picked.isEmpty()) {
			return candidates.stream().limit(TOP_K_AFTER).toList();
		}
		return picked.stream().map(candidates::get).toList();
	}

	private String answerFromContext(String question, List<SearchService.Hit> hits) {
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
		return chatClient.prompt().user(prompt).call().content();
	}

	public List<ControlResult> runControl(FilterMode mode) {
		List<ControlResult> results = new ArrayList<>();
		for (ControlQuestion q : controlQuestions) {
			PipelineResult oldP = oldPipeline(q.question());
			String rewritten = rewriteQuestion(q.question());
			PipelineResult newP = newPipeline(q.question(), rewritten, mode);
			results.add(new ControlResult(q,
					oldP.answer(), contains(oldP.answer(), q.marker()),
					newP.answer(), contains(newP.answer(), q.marker()),
					newP.candidates().stream().filter(CandidateHit::used)
							.map(c -> c.hit().section()).toList()));
		}
		return results;
	}

	private static boolean contains(String answer, String marker) {
		if (answer == null) {
			return false;
		}
		String low = answer.toLowerCase();
		return List.of(marker.split("\\|")).stream().anyMatch(m -> low.contains(m.toLowerCase()));
	}

}
