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
	public String ask(@RequestParam String question, Model model) {
		model.addAttribute("question", question);
		RagService.RagAnswer plain = ragService.ask(question, false);
		RagService.RagAnswer rag = ragService.ask(question, true);
		model.addAttribute("plainAnswer", plain.answer());
		model.addAttribute("ragAnswer", rag.answer());
		model.addAttribute("ragHits", rag.hits());
		return "rag";
	}

	@PostMapping("/rag/control")
	public String control(Model model) {
		model.addAttribute("controlRows", ragService.runControl());
		return "rag";
	}

}
