package com.tutorial.ai_challenge;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfig {

	@Bean
	public ChatMemory chatMemory(ChatMemoryRepository repository, ConversationRepository conversations,
			ChatClient.Builder builder,
			@Value("${chat.recent-messages:10}") int recentMessages) {
		ChatClient llm = builder.build();
		return new StrategyChatMemory(repository, conversations, llm, recentMessages);
	}

}
