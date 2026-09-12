package com.tutorial.ai_challenge;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChatController {

	@GetMapping("/chat")
	public String chat() {
		return "hello";
	}

}
