package com.tutorial.ai_challenge;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ChatController {

	private final ChatClient chatClient;

	public ChatController(ChatClient.Builder builder) {
		this.chatClient = builder.build();
	}

	@GetMapping("/")
	public String index() {
		return "chat";
	}

	@PostMapping("/chat")
	public String chat(@RequestParam String message, Model model) {
		model.addAttribute("message", message);
		try {
			String answer = chatClient.prompt()
					.user(message)
					.call()
					.content();
			model.addAttribute("answer", answer);
		}
		catch (Exception e) {
			model.addAttribute("error", e.getMessage());
		}
		return "chat";
	}

}
