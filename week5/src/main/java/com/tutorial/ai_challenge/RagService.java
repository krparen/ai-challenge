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

	public record Src(String source, String section, int chunkId) {
	}

	public record Quote(int chunkId, String text) {
	}

	public record StructJson(String answer, List<Src> sources, List<Quote> quotes) {
	}

	public record StructAnswer(String answer, List<Src> sources, List<Quote> quotes, boolean parsed) {
	}

	public record Verdicts(boolean hasSources, boolean sourcesValid, boolean hasQuotes, boolean quotesVerbatim,
			Boolean judgeConsistent, String judgeNote) {
	}

	public record PipelineResult(String name, String query, String answer, List<CandidateHit> candidates,
			int usedCount) {
	}

	public record NewResult(String name, String query, StructAnswer structured, Verdicts verdicts,
			List<CandidateHit> candidates, int usedCount) {
	}

	public record AskResult(String question, String rewritten, PipelineResult oldPipeline, NewResult newPipeline) {
	}

	public record ControlQuestion(String question, String expected, String marker, String section) {
	}

	public record ControlResult(ControlQuestion question, String oldAnswer, boolean oldOk,
			StructAnswer structured, boolean newOk, Verdicts verdicts, List<String> sections) {
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
		NewResult newPipeline = newPipeline(question, rewritten, mode, false);
		return new AskResult(question, rewritten, oldPipeline, newPipeline);
	}

	public PipelineResult oldPipeline(String question) {
		List<SearchService.Hit> hits = searchService.search("struct", question, 5);
		List<CandidateHit> candidates = hits.stream().map(h -> new CandidateHit(h, true, "использован")).toList();
		String answer = answerFromContext(question, hits);
		return new PipelineResult("Старый пайплайн: топ-5, свободный текст", question, answer, candidates,
				hits.size());
	}

	public NewResult newPipeline(String question, String rewritten, FilterMode mode, boolean judge) {
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

		String name = "Новый пайплайн: " + (rewritten != null ? "rewrite + " : "") + modeLabel(mode)
				+ ", топ-" + TOP_K_BEFORE + " → топ-" + TOP_K_AFTER + " + цитаты";

		if (selected.isEmpty()) {
			StructAnswer dontKnow = new StructAnswer(
					"Не знаю. В базе нет релевантного материала — уточните вопрос.", List.of(), List.of(), true);
			return new NewResult(name, query, dontKnow,
					new Verdicts(false, false, false, false, null, null), marked, 0);
		}

		StructAnswer structured = answerStructured(question, selected);
		Verdicts verdicts = verify(structured, selected);
		if (judge) {
			String[] j = judgeAnswer(question, structured);
			verdicts = new Verdicts(verdicts.hasSources(), verdicts.sourcesValid(), verdicts.hasQuotes(),
					verdicts.quotesVerbatim(), Boolean.parseBoolean(j[0]), j[1]);
		}
		return new NewResult(name, query, structured, verdicts, marked, selected.size());
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
		String prompt = """
				Ты — справочник по заклинаниям мира Тани Гроттер.
				Ответь на вопрос, опираясь ТОЛЬКО на приведённый контекст.
				Если ответа в контексте нет — честно скажи, что не нашёл, и не выдумывай.

				Контекст:
				%s
				Вопрос: %s
				""".formatted(contextOf(hits), question);
		return chatClient.prompt().user(prompt).call().content();
	}

	private StructAnswer answerStructured(String question, List<SearchService.Hit> hits) {
		String prompt = """
				Ты — справочник по заклинаниям мира Тани Гроттер.
				Ответь на вопрос, опираясь ТОЛЬКО на приведённый контекст.

				Верни ТОЛЬКО JSON без markdown-обёрток, строго по схеме:
				{"answer": "короткий ответ по-русски", "sources": [{"source": "...", "section": "...", "chunkId": 0}], "quotes": [{"chunkId": 0, "text": "дословная цитата из чанка"}]}

				Правила:
				- sources — чанки, на которые опирается ответ; копируй source/section/chunkId из заголовков контекста
				- quotes — не более 3 дословных фрагментов из этих чанков, подтверждающих ответ; копируй текст без изменений, без markdown
				- Если ответа в контексте нет или контекст не относится к вопросу — верни {"answer": "Не знаю. Уточните вопрос: <что именно уточнить>", "sources": [], "quotes": []}
				- Никакого текста, кроме JSON

				Контекст:
				%s
				Вопрос: %s
				""".formatted(contextOf(hits), question);
		String raw = chatClient.prompt().user(prompt).call().content();
		if (raw == null || raw.isBlank()) {
			return new StructAnswer("Пустой ответ модели", List.of(), List.of(), false);
		}
		try {
			int start = raw.indexOf('{');
			int end = raw.lastIndexOf('}');
			if (start < 0 || end <= start) {
				return new StructAnswer(raw.trim(), List.of(), List.of(), false);
			}
			StructJson json = mapper.readValue(raw.substring(start, end + 1), StructJson.class);
			return new StructAnswer(json.answer(), orEmpty(json.sources()), orEmpty(json.quotes()), true);
		}
		catch (Exception e) {
			return new StructAnswer(raw.trim(), List.of(), List.of(), false);
		}
	}

	private static <T> List<T> orEmpty(List<T> list) {
		return list == null ? List.of() : list;
	}

	private static String contextOf(List<SearchService.Hit> hits) {
		StringBuilder context = new StringBuilder();
		for (SearchService.Hit h : hits) {
			context.append("[источник: ").append(h.title())
					.append(" | раздел: ").append(h.section())
					.append(" | чанк #").append(h.chunkId())
					.append("]\n")
					.append(h.content())
					.append("\n\n");
		}
		return context.toString();
	}

	private Verdicts verify(StructAnswer structured, List<SearchService.Hit> selected) {
		boolean hasSources = !structured.sources().isEmpty();
		boolean sourcesValid = hasSources && structured.sources().stream()
				.allMatch(s -> selected.stream().anyMatch(h -> h.chunkId() == s.chunkId()));
		boolean hasQuotes = !structured.quotes().isEmpty();
		boolean quotesVerbatim = hasQuotes && structured.quotes().stream().allMatch(q ->
				selected.stream().filter(h -> h.chunkId() == q.chunkId())
						.anyMatch(h -> normalize(h.content()).contains(normalize(q.text())))
						|| selected.stream().anyMatch(h -> normalize(h.content()).contains(normalize(q.text()))));
		return new Verdicts(hasSources, sourcesValid, hasQuotes, quotesVerbatim, null, null);
	}

	static String normalize(String s) {
		if (s == null) {
			return "";
		}
		return s.toLowerCase()
				.replace('*', ' ')
				.replace('«', '"').replace('»', '"').replace('\"', '"').replace('"', '"')
				.replace('—', '-').replace('–', '-').replace('\u00A0', ' ')
				.replaceAll("\\s+", " ")
				.trim();
	}

	private String[] judgeAnswer(String question, StructAnswer structured) {
		StringBuilder quotes = new StringBuilder();
		for (Quote q : structured.quotes()) {
			quotes.append("- ").append(q.text()).append("\n");
		}
		if (quotes.isEmpty()) {
			return new String[] { "false", "цитат нет — согласованность не с чем проверять" };
		}
		String prompt = """
				Вопрос: %s

				Ответ ассистента:
				%s

				Цитаты из справочника, на которые он ссылается:
				%s
				Согласуется ли ответ с цитатами: следует ли он из цитат, не противоречит им и не добавляет фактов вне них?
				Начни ответ со слова ДА или НЕТ, затем одной фразой — почему.
				""".formatted(question, structured.answer(), quotes);
		String out = chatClient.prompt().user(prompt).call().content();
		boolean ok = out != null && out.trim().toLowerCase().startsWith("да");
		return new String[] { String.valueOf(ok), out == null ? "нет ответа судьи" : out.trim() };
	}

	public List<ControlResult> runControl(FilterMode mode) {
		List<ControlResult> results = new ArrayList<>();
		for (ControlQuestion q : controlQuestions) {
			PipelineResult oldP = oldPipeline(q.question());
			String rewritten = rewriteQuestion(q.question());
			NewResult newP = newPipeline(q.question(), rewritten, mode, true);
			results.add(new ControlResult(q,
					oldP.answer(), contains(oldP.answer(), q.marker()),
					newP.structured(), contains(newP.structured().answer(), q.marker()),
					newP.verdicts(),
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
