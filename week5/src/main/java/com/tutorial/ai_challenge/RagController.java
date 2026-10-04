package com.tutorial.ai_challenge;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RagController {

	private final RagService ragService;

	public RagController(RagService ragService) {
		this.ragService = ragService;
	}

	@GetMapping("/rag")
	public String page() {
		return "rag";
	}

	@PostMapping("/rag/ask")
	public String ask(@RequestParam String question,
			@RequestParam(defaultValue = "true") boolean rewrite,
			@RequestParam(defaultValue = "rerank") String mode,
			Model model) {
		RagService.FilterMode filterMode = "threshold".equals(mode) ? RagService.FilterMode.THRESHOLD
				: RagService.FilterMode.RERANK;
		RagService.AskResult result = ragService.askBoth(question, rewrite, filterMode);
		model.addAttribute("question", question);
		model.addAttribute("result", result);
		model.addAttribute("mode", mode);
		model.addAttribute("rewrite", rewrite);
		return "rag";
	}

	@PostMapping("/rag/control")
	public String control(@RequestParam(defaultValue = "rerank") String mode, Model model) {
		RagService.FilterMode filterMode = "threshold".equals(mode) ? RagService.FilterMode.THRESHOLD
				: RagService.FilterMode.RERANK;
		model.addAttribute("controlRows", ragService.runControl(filterMode));
		model.addAttribute("controlMode", filterMode == RagService.FilterMode.THRESHOLD ? "порог" : "реранк");
		return "rag";
	}

}
