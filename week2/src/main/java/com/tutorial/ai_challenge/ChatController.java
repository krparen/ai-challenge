package com.tutorial.ai_challenge;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ChatController {

	private static final String CONVERSATION_COOKIE = "conversationId";

	private static final Pattern UUID_PATTERN = Pattern.compile(
			"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

	private record ChatMessageView(boolean fromUser, String text) {
	}

	private record StrategyOption(String name, String label) {
	}

	private List<StrategyOption> strategyOptions() {
		return List.of(
				new StrategyOption(ChatStrategy.FULL.name(), "Полная история"),
				new StrategyOption(ChatStrategy.WINDOW.name(), "Окно " + recentMessages),
				new StrategyOption(ChatStrategy.FACTS.name(), "Факты + окно " + recentMessages),
				new StrategyOption(ChatStrategy.SUMMARY.name(), "Сводка + окно " + recentMessages));
	}

	private String label(ChatStrategy strategy) {
		return strategyOptions().stream()
				.filter(o -> o.name().equals(strategy.name()))
				.map(StrategyOption::label)
				.findFirst()
				.orElse(strategy.name());
	}

	private final ChatAgent agent;

	private final ConversationRepository conversations;

	private final int recentMessages;

	public ChatController(ChatAgent agent, ConversationRepository conversations,
			@Value("${chat.recent-messages:10}") int recentMessages) {
		this.agent = agent;
		this.conversations = conversations;
		this.recentMessages = recentMessages;
	}

	@GetMapping("/chat")
	public String chat(@CookieValue(name = CONVERSATION_COOKIE, required = false) String conversationId,
			HttpServletResponse response, Model model) {
		String id = ensureConversation(conversationId, response);
		model.addAttribute("conversationId", id);
		model.addAttribute("messages", toViews(agent.getMessages(id)));
		model.addAttribute("historyStats", agent.getHistoryStats(id));
		model.addAttribute("conversationList", conversations.findAllByOrderByCreatedAtDesc());
		model.addAttribute("strategyOptions", strategyOptions());
		model.addAttribute("strategy", ChatStrategy.FULL.name());
		model.addAttribute("strategyLabel", label(ChatStrategy.FULL));
		conversations.findById(UUID.fromString(id)).ifPresent(c -> {
			model.addAttribute("strategy", c.getStrategy().name());
			model.addAttribute("strategyLabel", label(c.getStrategy()));
			model.addAttribute("facts", c.getFacts());
			model.addAttribute("promptTotal", c.getPromptTokens());
			model.addAttribute("completionTotal", c.getCompletionTokens());
			model.addAttribute("summaryPromptTotal", c.getSummaryPromptTokens());
			model.addAttribute("summaryCompletionTotal", c.getSummaryCompletionTokens());
			model.addAttribute("factsPromptTotal", c.getFactsPromptTokens());
			model.addAttribute("factsCompletionTotal", c.getFactsCompletionTokens());
			model.addAttribute("summaryCount", c.getSummaryMessageCount());
			model.addAttribute("recentMessages", recentMessages);
		});
		return "chat";
	}

	@PostMapping("/chat/new")
	public String newChat(@CookieValue(name = CONVERSATION_COOKIE, required = false) String conversationId,
			HttpServletResponse response) {
		if (conversationId != null && UUID_PATTERN.matcher(conversationId).matches()) {
			agent.closeConversation(conversationId);
			conversations.findById(UUID.fromString(conversationId)).ifPresent(Conversation::close);
		}
		startConversation(response);
		return "redirect:/chat";
	}

	@PostMapping("/chat")
	public String send(@RequestParam String message,
			@CookieValue(name = CONVERSATION_COOKIE, required = false) String conversationId,
			HttpServletResponse response, RedirectAttributes redirect) {
		String id = ensureConversation(conversationId, response);
		try {
			ChatAgent.AgentReply reply = agent.sendMessage(id, message);
			conversations.findById(UUID.fromString(id)).ifPresent(c -> {
				c.addUsage(reply.promptTokens(), reply.completionTokens());
				conversations.save(c);
			});
			redirect.addFlashAttribute("lastPrompt", reply.promptTokens());
			redirect.addFlashAttribute("lastCompletion", reply.completionTokens());
			String promptText = agent.lastPrompt();
			if (promptText != null) {
				redirect.addFlashAttribute("lastPromptText", promptText);
			}
			agent.clearLastPrompt();
		}
		catch (RuntimeException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/chat";
	}

	@PostMapping("/chat/strategy")
	public String strategy(@RequestParam String strategy,
			@CookieValue(name = CONVERSATION_COOKIE, required = false) String conversationId,
			RedirectAttributes redirect) {
		ChatStrategy parsed;
		try {
			parsed = ChatStrategy.valueOf(strategy);
		}
		catch (IllegalArgumentException e) {
			redirect.addFlashAttribute("error", "Неизвестная стратегия: " + strategy);
			return "redirect:/chat";
		}
		if (conversationId != null && UUID_PATTERN.matcher(conversationId).matches()) {
			conversations.findById(UUID.fromString(conversationId)).ifPresent(c -> {
				c.setStrategy(parsed);
				conversations.save(c);
			});
		}
		return "redirect:/chat";
	}

	@PostMapping("/chat/branch")
	public String branch(@RequestParam(name = "checkpoint", required = false) Integer checkpoint,
			@CookieValue(name = CONVERSATION_COOKIE, required = false) String conversationId,
			HttpServletResponse response, RedirectAttributes redirect) {
		if (conversationId == null || !UUID_PATTERN.matcher(conversationId).matches()) {
			redirect.addFlashAttribute("error", "Нет активного диалога — ветвить нечего");
			return "redirect:/chat";
		}
		String newId = UUID.randomUUID().toString();
		agent.branchConversation(conversationId, newId, checkpoint == null ? Integer.MAX_VALUE : checkpoint);
		Conversation branch = new Conversation(UUID.fromString(newId));
		branch.forkFrom(UUID.fromString(conversationId), checkpoint);
		conversations.save(branch);
		setConversationCookie(response, newId);
		return "redirect:/chat";
	}

	@PostMapping("/chat/switch")
	public String switchConversation(@RequestParam String conversationId,
			HttpServletResponse response, RedirectAttributes redirect) {
		if (!UUID_PATTERN.matcher(conversationId).matches()) {
			redirect.addFlashAttribute("error", "Некорректный id беседы");
			return "redirect:/chat";
		}
		Conversation conversation = conversations.findById(UUID.fromString(conversationId)).orElse(null);
		if (conversation == null) {
			redirect.addFlashAttribute("error", "Беседа не найдена");
			return "redirect:/chat";
		}
		if (conversation.getClosedAt() != null) {
			conversation.reopen();
			conversations.save(conversation);
		}
		setConversationCookie(response, conversationId);
		return "redirect:/chat";
	}

	private String ensureConversation(String conversationId, HttpServletResponse response) {
		if (conversationId != null && UUID_PATTERN.matcher(conversationId).matches()) {
			UUID id = UUID.fromString(conversationId);
			if (!conversations.existsById(id)) {
				conversations.save(new Conversation(id));
			}
			return conversationId;
		}
		return startConversation(response);
	}

	private String startConversation(HttpServletResponse response) {
		Conversation conversation = new Conversation(UUID.randomUUID());
		conversations.save(conversation);
		setConversationCookie(response, conversation.getId().toString());
		return conversation.getId().toString();
	}

	private void setConversationCookie(HttpServletResponse response, String conversationId) {
		Cookie cookie = new Cookie(CONVERSATION_COOKIE, conversationId);
		cookie.setPath("/");
		cookie.setMaxAge(7 * 24 * 60 * 60);
		response.addCookie(cookie);
	}

	private List<ChatMessageView> toViews(List<Message> messages) {
		return messages.stream()
				.map(m -> new ChatMessageView(m.getMessageType() == MessageType.USER, m.getText()))
				.toList();
	}

}
