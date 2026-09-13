package com.tutorial.ai_challenge;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

@Component
public class ChatAgent {

	private final ChatClient chatClient;

	private final ChatMemory chatMemory;

	public ChatAgent(ChatClient.Builder builder, ChatMemory chatMemory) {
		this.chatClient = builder
				.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
				.build();
		this.chatMemory = chatMemory;
	}

	public String sendMessage(String conversationId, String userMessage) {
		return chatClient.prompt()
				.user(userMessage)
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
				.call()
				.content();
	}

	public List<Message> getMessages(String conversationId) {
		return List.copyOf(chatMemory.get(conversationId));
	}

	public void closeConversation(String conversationId) {
		chatMemory.clear(conversationId);
	}

}
