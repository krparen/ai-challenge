package com.tutorial.ai_challenge;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
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

	private final ChatAgent agent;

	private final ConversationRepository conversations;

	public ChatController(ChatAgent agent, ConversationRepository conversations) {
		this.agent = agent;
		this.conversations = conversations;
	}

	@GetMapping("/chat")
	public String chat(@CookieValue(name = CONVERSATION_COOKIE, required = false) String conversationId,
			HttpServletResponse response, Model model) {
		String id = ensureConversation(conversationId, response);
		model.addAttribute("conversationId", id);
		model.addAttribute("messages", toViews(agent.getMessages(id)));
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
			agent.sendMessage(id, message);
		}
		catch (RuntimeException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
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
		Cookie cookie = new Cookie(CONVERSATION_COOKIE, conversation.getId().toString());
		cookie.setPath("/");
		cookie.setMaxAge(7 * 24 * 60 * 60);
		response.addCookie(cookie);
		return conversation.getId().toString();
	}

	private List<ChatMessageView> toViews(List<Message> messages) {
		return messages.stream()
				.map(m -> new ChatMessageView(m.getMessageType() == MessageType.USER, m.getText()))
				.toList();
	}

}
