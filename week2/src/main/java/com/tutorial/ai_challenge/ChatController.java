package com.tutorial.ai_challenge;

import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpSession;

import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ChatController {

	private record ChatMessageView(boolean fromUser, String text) {
	}

	private final ChatAgent agent;

	public ChatController(ChatAgent agent) {
		this.agent = agent;
	}

	@GetMapping("/chat")
	public String chat(HttpSession session, Model model) {
		String conversationId = ensureConversation(session);
		model.addAttribute("conversationId", conversationId);
		model.addAttribute("messages", toViews(agent.getMessages(conversationId)));
		Object error = session.getAttribute("error");
		if (error != null) {
			model.addAttribute("error", error);
			session.removeAttribute("error");
		}
		return "chat";
	}

	@PostMapping("/chat/new")
	public String newChat(HttpSession session) {
		String old = (String) session.getAttribute("conversationId");
		if (old != null) {
			agent.closeConversation(old);
		}
		session.setAttribute("conversationId", UUID.randomUUID().toString());
		return "redirect:/chat";
	}

	@PostMapping("/chat")
	public String send(@RequestParam String message, HttpSession session) {
		String conversationId = ensureConversation(session);
		try {
			agent.sendMessage(conversationId, message);
		}
		catch (RuntimeException e) {
			session.setAttribute("error", e.getMessage());
		}
		return "redirect:/chat";
	}

	private String ensureConversation(HttpSession session) {
		String conversationId = (String) session.getAttribute("conversationId");
		if (conversationId == null) {
			conversationId = UUID.randomUUID().toString();
			session.setAttribute("conversationId", conversationId);
		}
		return conversationId;
	}

	private List<ChatMessageView> toViews(List<org.springframework.ai.chat.messages.Message> messages) {
		return messages.stream()
				.map(m -> new ChatMessageView(m.getMessageType() == MessageType.USER, m.getText()))
				.toList();
	}

}
