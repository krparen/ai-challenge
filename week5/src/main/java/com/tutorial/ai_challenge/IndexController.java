package com.tutorial.ai_challenge;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class IndexController {

	private static final List<String> STRATEGIES = List.of("fixed", "struct");

	private final IndexingService indexingService;
	private final SearchService searchService;
	private final DocChunkRepository repository;

	public IndexController(IndexingService indexingService, SearchService searchService,
			DocChunkRepository repository) {
		this.indexingService = indexingService;
		this.searchService = searchService;
		this.repository = repository;
	}

	public record StrategyInfo(String strategy, long chunks) {
	}

	@GetMapping("/index")
	public String page(Model model) {
		model.addAttribute("strategies", stats());
		return "index";
	}

	@PostMapping("/index/run")
	public String run(@RequestParam String strategy, Model model) {
		IndexingService.Stats stats = indexingService.index(strategy);
		model.addAttribute("runStats", "Стратегия " + strategy + ": документов " + stats.docs() + ", чанков "
				+ stats.chunks() + ", средний размер " + stats.avgChars() + " симв., время " + stats.ms() + " мс");
		model.addAttribute("strategies", stats());
		return "index";
	}

	@PostMapping("/index/search")
	public String search(@RequestParam String strategy, @RequestParam String query, Model model) {
		model.addAttribute("searchStrategy", strategy);
		model.addAttribute("query", query);
		model.addAttribute("hits", searchService.search(strategy, query, 5));
		model.addAttribute("strategies", stats());
		return "index";
	}

	@PostMapping("/index/compare")
	public String compare(Model model) {
		model.addAttribute("compareRows", searchService.compare());
		model.addAttribute("controlQueries",
				SearchService.CONTROL_QUERIES.stream().map(SearchService.ControlQuery::query).toList());
		model.addAttribute("strategies", stats());
		return "index";
	}

	private List<StrategyInfo> stats() {
		return STRATEGIES.stream()
				.map(s -> new StrategyInfo(s, repository.findByStrategy(s).size()))
				.toList();
	}

}
