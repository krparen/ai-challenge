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
			@Value("${chat.compression-enabled:true}") boolean compressionEnabled,
			@Value("${chat.recent-messages:10}") int recentMessages) {
		ChatClient summarizer = builder.build();
		return new SummaryChatMemory(repository, conversations, summarizer, compressionEnabled, recentMessages);
	}

}
