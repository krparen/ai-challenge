package com.tutorial.ai_challenge;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

	@Bean
	public ChatClient ragChatClient(ChatClient.Builder builder) {
		return builder
				.defaultSystem("Ты полезный ассистент. Отвечай по-русски, кратко.")
				.build();
	}

}
