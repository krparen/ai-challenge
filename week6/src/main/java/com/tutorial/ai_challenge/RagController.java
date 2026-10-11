package com.tutorial.ai_challenge;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import tools.jackson.databind.ObjectMapper;

@Controller
public class RagController {

	public record ControlItem(String question, String marker) {
	}

	public record ControlRow(String question, String marker, boolean localHit, boolean cloudHit,
			double localSeconds, double cloudSeconds) {
	}

	public record AskResult(String question, String localAnswer, String cloudAnswer,
			double localSeconds, double cloudSeconds, List<RagSearchService.Hit> sources) {
	}

	private final RagIndexer indexer;
	private final RagSearchService search;
	private final ChatClient localChatClient;
	private final ChatClient cloudChatClient;
	private final org.springframework.jdbc.core.JdbcTemplate jdbc;

	public RagController(RagIndexer indexer, RagSearchService search,
			ChatClient localChatClient, ChatClient cloudChatClient,
			org.springframework.jdbc.core.JdbcTemplate jdbc) {
		this.indexer = indexer;
		this.search = search;
		this.localChatClient = localChatClient;
		this.cloudChatClient = cloudChatClient;
		this.jdbc = jdbc;
	}

	@GetMapping("/rag")
	public String page(Model model) {
		model.addAttribute("indexed", count());
		return "rag";
	}

	@PostMapping("/rag/index")
	public String index(Model model) {
		try {
			int n = indexer.index();
			model.addAttribute("message", "Проиндексировано чанков: " + n);
		}
		catch (Exception e) {
			model.addAttribute("error", "Ошибка индексации: " + e.getMessage());
		}
		model.addAttribute("indexed", count());
		return "rag";
	}

	@PostMapping("/rag/ask")
	public String ask(@RequestParam String question,
			@RequestParam(name = "mode", required = false) String mode, Model model) {
		boolean optimized = !"baseline".equals(mode);
		List<RagSearchService.Hit> hits = search.search(question, 6);
		String prompt = contextOf(hits) + "\n\nВопрос: " + question;
		Answer local = answer(localChatClient, prompt, optimized);
		Answer cloud = answer(cloudChatClient, prompt, false);
		model.addAttribute("result", new AskResult(question, local.text(), cloud.text(),
				local.seconds(), cloud.seconds(), hits));
		model.addAttribute("mode", mode);
		model.addAttribute("indexed", count());
		return "rag";
	}

	@PostMapping("/rag/control")
	public String control(@RequestParam(name = "mode", required = false) String mode, Model model) {
		boolean optimized = !"baseline".equals(mode);
		List<ControlItem> items = controlItems();
		List<ControlRow> rows = new ArrayList<>();
		double localTotal = 0, cloudTotal = 0;
		int localHits = 0, cloudHits = 0;
		int localTokens = 0, cloudTokens = 0;
		for (ControlItem item : items) {
			List<RagSearchService.Hit> hits = search.search(item.question(), 6);
			String prompt = contextOf(hits) + "\n\nВопрос: " + item.question();
			Answer local = answer(localChatClient, prompt, optimized);
			Answer cloud = answer(cloudChatClient, prompt, false);
			boolean localHit = contains(local.text(), item.marker());
			boolean cloudHit = contains(cloud.text(), item.marker());
			if (localHit) {
				localHits++;
			}
			if (cloudHit) {
				cloudHits++;
			}
			localTotal += local.seconds();
			cloudTotal += cloud.seconds();
			localTokens += local.tokens();
			cloudTokens += cloud.tokens();
			rows.add(new ControlRow(item.question(), item.marker(), localHit, cloudHit,
					local.seconds(), cloud.seconds()));
		}
		model.addAttribute("rows", rows);
		model.addAttribute("mode", mode);
		model.addAttribute("localHits", localHits);
		model.addAttribute("cloudHits", cloudHits);
		model.addAttribute("localAvg", Math.round(localTotal / rows.size() * 10) / 10.0);
		model.addAttribute("cloudAvg", Math.round(cloudTotal / rows.size() * 10) / 10.0);
		model.addAttribute("localTokens", localTokens);
		model.addAttribute("cloudTokens", cloudTokens);
		model.addAttribute("indexed", count());
		return "rag";
	}

	private record Answer(String text, double seconds, int tokens) {
	}

	private static Answer answer(ChatClient client, String prompt, boolean optimized) {
		var spec = client.prompt();
		if (optimized) {
			spec = spec.system(RagConfig.LOCAL_SYSTEM_PROMPT).options(RagConfig.localOptions());
		}
		else {
			spec = spec.system(RagConfig.CLOUD_SYSTEM_PROMPT);
		}
		spec = spec.user(prompt);
		long t = System.currentTimeMillis();
		var chatResponse = spec.call().chatResponse();
		double seconds = (System.currentTimeMillis() - t) / 1000.0;
		Integer completion = chatResponse.getMetadata().getUsage().getCompletionTokens();
		return new Answer(chatResponse.getResult().getOutput().getText().trim(), seconds,
				completion == null ? 0 : completion);
	}

	private static String contextOf(List<RagSearchService.Hit> hits) {
		StringBuilder sb = new StringBuilder("Контекст из справочника:\n");
		for (RagSearchService.Hit h : hits) {
			sb.append("[раздел: ").append(h.section()).append(" | чанк #").append(h.chunkId()).append("]\n")
					.append(h.content()).append("\n\n");
		}
		return sb.toString();
	}

	private static boolean contains(String answer, String marker) {
		for (String m : marker.split("\\|")) {
			if (answer.toLowerCase().contains(m.trim().toLowerCase())) {
				return true;
			}
		}
		return false;
	}

	private List<ControlItem> controlItems() {
		try {
			byte[] raw = new ClassPathResource("rag-control.json").getInputStream().readAllBytes();
			return List.of(new ObjectMapper().readValue(new String(raw, StandardCharsets.UTF_8), ControlItem[].class));
		}
		catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private int count() {
		Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM doc_chunks", Integer.class);
		return n == null ? 0 : n;
	}

}
