package com.tutorial.ai_challenge;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ChatController {

	private static final List<String> MODELS = List.of(
			"deepseek-v4-flash",
			"deepseek-v4-flash-vision-exp",
			"deepseek-v4-pro");

	private record CallStats(String content, long millis, int promptTokens, int completionTokens) {
	}

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
		model.addAttribute("maxTokens", 1000);
		model.addAttribute("stopSequence", "\\n\\n");
		model.addAttribute("thinking", false);
		model.addAttribute("temperature", 1.0);
		model.addAttribute("models", MODELS);
		model.addAttribute("model", "deepseek-v4-flash");
		return "compare";
	}

	@PostMapping("/compare")
	public String compare(@RequestParam String message,
			@RequestParam(required = false, defaultValue = "") String systemPrompt,
			@RequestParam(required = false, defaultValue = "1000") int maxTokens,
			@RequestParam(required = false, defaultValue = "") String stopSequence,
			@RequestParam(required = false, defaultValue = "false") boolean thinking,
			@RequestParam(required = false, defaultValue = "1.0") double temperature,
			@RequestParam(name = "model", required = false, defaultValue = "deepseek-v4-flash") String selectedModel,
			Model model) {
		model.addAttribute("message", message);
		model.addAttribute("systemPrompt", systemPrompt);
		model.addAttribute("maxTokens", maxTokens);
		model.addAttribute("stopSequence", stopSequence);
		model.addAttribute("thinking", thinking);
		model.addAttribute("temperature", temperature);
		model.addAttribute("model", selectedModel);
		try {
			CallStats baseline = call(message, DeepSeekChatOptions.builder(), null);

			DeepSeekChatOptions.Builder options = DeepSeekChatOptions.builder()
					.maxTokens(maxTokens)
					.temperature(temperature)
					.model(selectedModel);
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

			CallStats constrained = call(message, options, systemPrompt);

			model.addAttribute("baselineInfo", formatStats("deepseek-v4-flash", baseline));
			model.addAttribute("constrainedInfo", formatStats(selectedModel, constrained));
			model.addAttribute("baseline", baseline.content());
			model.addAttribute("constrained", constrained.content());
		}
		catch (Exception e) {
			model.addAttribute("error", e.getMessage());
		}
		return "compare";
	}

	private CallStats call(String message, DeepSeekChatOptions.Builder options, String systemPrompt) {
		long start = System.currentTimeMillis();
		ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
		if (systemPrompt != null && !systemPrompt.isBlank()) {
			spec = spec.system(systemPrompt);
		}
		ChatResponse response = spec
				.user(message)
				.options(options)
				.call()
				.chatResponse();
		long millis = System.currentTimeMillis() - start;
		Usage usage = response.getMetadata().getUsage();
		return new CallStats(
				response.getResult().getOutput().getText(),
				millis,
				usage.getPromptTokens(),
				usage.getCompletionTokens());
	}

	private String formatStats(String modelId, CallStats stats) {
		double[] prices = pricePerMillion(modelId);
		double cost = (stats.promptTokens() * prices[0] + stats.completionTokens() * prices[1]) / 1_000_000.0;
		return String.format("%s · %.1f с · токены: %d вход + %d генерация · ≈$%.6f (оценка off-peak)",
				modelId, stats.millis() / 1000.0, stats.promptTokens(), stats.completionTokens(), cost);
	}

	private double[] pricePerMillion(String modelId) {
		return "deepseek-v4-pro".equals(modelId)
				? new double[] { 0.66, 1.98 }
				: new double[] { 0.22, 0.66 };
	}

}
