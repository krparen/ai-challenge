package com.tutorial.ai_challenge;

import java.util.List;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class ChatController {

	private static final String COOKIE = "cid";

	private final ChatService chatService;

	public ChatController(ChatService chatService) {
		this.chatService = chatService;
	}

	@GetMapping("/chat")
	public String page(@CookieValue(name = COOKIE, required = false) String cid, Model model) {
		boolean fresh = cid == null || cid.isBlank();
		String conversationId = fresh ? chatService.newConversation() : cid;
		model.addAttribute("conversationId", conversationId);
		model.addAttribute("messages", fresh ? List.of() : chatService.history(conversationId));
		model.addAttribute("historySize", fresh ? 0 : chatService.historySize(conversationId));
		model.addAttribute("window", ChatConfig.WINDOW);
		return "chat";
	}

	@PostMapping("/chat/send")
	public String send(@CookieValue(name = COOKIE, required = false) String cid, @RequestParam String question,
			HttpServletResponse response) {
		String conversationId = cid == null || cid.isBlank() ? chatService.newConversation() : cid;
		setCookie(response, conversationId);
		chatService.ask(conversationId, question);
		return "redirect:/chat";
	}

	@PostMapping("/chat/new")
	public String newChat(HttpServletResponse response) {
		setCookie(response, chatService.newConversation());
		return "redirect:/chat";
	}

	private static void setCookie(HttpServletResponse response, String cid) {
		Cookie cookie = new Cookie(COOKIE, cid);
		cookie.setPath("/");
		cookie.setMaxAge(60 * 60 * 24 * 30);
		response.addCookie(cookie);
	}

}
