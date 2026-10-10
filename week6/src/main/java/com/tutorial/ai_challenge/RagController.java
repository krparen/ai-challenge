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
	public String ask(@RequestParam String question, Model model) {
		List<RagSearchService.Hit> hits = search.search(question, 6);
		String context = contextOf(hits);
		long t1 = System.currentTimeMillis();
		String local = localChatClient.prompt().user(context + "\n\nВопрос: " + question).call().content();
		double localSeconds = (System.currentTimeMillis() - t1) / 1000.0;
		long t2 = System.currentTimeMillis();
		String cloud = cloudChatClient.prompt().user(context + "\n\nВопрос: " + question).call().content();
		double cloudSeconds = (System.currentTimeMillis() - t2) / 1000.0;
		model.addAttribute("result", new AskResult(question, local.trim(), cloud.trim(), localSeconds, cloudSeconds, hits));
		model.addAttribute("indexed", count());
		return "rag";
	}

	@PostMapping("/rag/control")
	public String control(Model model) {
		List<ControlItem> items = controlItems();
		List<ControlRow> rows = new ArrayList<>();
		double localTotal = 0, cloudTotal = 0;
		int localHits = 0, cloudHits = 0;
		for (ControlItem item : items) {
			List<RagSearchService.Hit> hits = search.search(item.question(), 6);
			String prompt = contextOf(hits) + "\n\nВопрос: " + item.question();
			long t1 = System.currentTimeMillis();
			String local = localChatClient.prompt().user(prompt).call().content().trim();
			double localSeconds = (System.currentTimeMillis() - t1) / 1000.0;
			long t2 = System.currentTimeMillis();
			String cloud = cloudChatClient.prompt().user(prompt).call().content().trim();
			double cloudSeconds = (System.currentTimeMillis() - t2) / 1000.0;
			boolean localHit = contains(local, item.marker());
			boolean cloudHit = contains(cloud, item.marker());
			if (localHit) {
				localHits++;
			}
			if (cloudHit) {
				cloudHits++;
			}
			localTotal += localSeconds;
			cloudTotal += cloudSeconds;
			rows.add(new ControlRow(item.question(), item.marker(), localHit, cloudHit, localSeconds, cloudSeconds));
		}
		model.addAttribute("rows", rows);
		model.addAttribute("localHits", localHits);
		model.addAttribute("cloudHits", cloudHits);
		model.addAttribute("localAvg", Math.round(localTotal / rows.size() * 10) / 10.0);
		model.addAttribute("cloudAvg", Math.round(cloudTotal / rows.size() * 10) / 10.0);
		model.addAttribute("indexed", count());
		return "rag";
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
