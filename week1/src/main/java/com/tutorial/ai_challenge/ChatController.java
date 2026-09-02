package com.tutorial.ai_challenge;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
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

	@GetMapping("/compare")
	public String compareForm(Model model) {
		model.addAttribute("systemPrompt",
				"Строго соблюдай формат ответа: в первом абзаце дай краткий прямой ответ, "
						+ "во втором абзаце приведи подробное пояснение.");
		model.addAttribute("maxTokens", 60);
		model.addAttribute("stopSequence", "\\n\\n");
		model.addAttribute("thinking", false);
		return "compare";
	}

	@PostMapping("/compare")
	public String compare(@RequestParam String message,
			@RequestParam(required = false, defaultValue = "") String systemPrompt,
			@RequestParam(required = false, defaultValue = "60") int maxTokens,
			@RequestParam(required = false, defaultValue = "") String stopSequence,
			@RequestParam(required = false, defaultValue = "false") boolean thinking,
			Model model) {
		model.addAttribute("message", message);
		model.addAttribute("systemPrompt", systemPrompt);
		model.addAttribute("maxTokens", maxTokens);
		model.addAttribute("stopSequence", stopSequence);
		model.addAttribute("thinking", thinking);
		try {
			String baseline = chatClient.prompt()
					.user(message)
					.call()
					.content();

			DeepSeekChatOptions.Builder options = DeepSeekChatOptions.builder()
					.maxTokens(maxTokens);
			if (thinking) {
				options.enableThinking();
			}
			else {
				options.disableThinking();
			}
			String stop = stopSequence.replace("\\n", "\n");
			if (!stop.isBlank()) {
				options.stop(List.of(stop));
			}

			ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
			if (!systemPrompt.isBlank()) {
				spec = spec.system(systemPrompt);
			}
			String constrained = spec
					.user(message)
					.options(options)
					.call()
					.content();

			model.addAttribute("baseline", baseline);
			model.addAttribute("constrained", constrained);
		}
		catch (Exception e) {
			model.addAttribute("error", e.getMessage());
		}
		return "compare";
	}

}
