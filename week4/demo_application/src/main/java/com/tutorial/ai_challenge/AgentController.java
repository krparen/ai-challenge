package com.tutorial.ai_challenge;

import java.util.List;

import io.modelcontextprotocol.client.McpSyncClient;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AgentController {

	private final ChatClient chatClient;
	private final List<McpSyncClient> clients;

	public AgentController(ChatClient chatClient, List<McpSyncClient> clients) {
		this.chatClient = chatClient;
		this.clients = clients;
	}

	@GetMapping("/agent")
	public String page() {
		return "agent";
	}

	@PostMapping("/agent/ask")
	public String ask(@RequestParam String question, Model model) {
		model.addAttribute("question", question);
		for (McpSyncClient client : clients) {
			try {
				if (!client.isInitialized()) {
					client.initialize();
				}
			}
			catch (Exception e) {
				// сервер временно недоступен — модель увидит это по отсутствию инструментов
			}
		}
		try {
			String answer = chatClient.prompt()
					.user(question)
					.call()
					.content();
			model.addAttribute("answer", answer);
		}
		catch (Exception e) {
			StringBuilder msg = new StringBuilder();
			for (Throwable c = e; c != null; c = c.getCause()) {
				if (msg.length() > 0) {
					msg.append(" ← ");
				}
				msg.append(c.getClass().getSimpleName()).append(": ")
						.append(c.getMessage() != null ? c.getMessage() : c.toString());
			}
			model.addAttribute("error", msg.toString());
		}
		return "agent";
	}

}
