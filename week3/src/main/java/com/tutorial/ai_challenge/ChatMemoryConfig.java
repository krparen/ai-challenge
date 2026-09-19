package com.tutorial.ai_challenge;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatMemoryConfig {

	@Bean
	public ChatMemory chatMemory(MemoryService memoryService) {
		return new LayeredChatMemory(memoryService);
	}

}
