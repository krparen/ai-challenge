package com.tutorial.ai_challenge;

import java.util.List;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.tokenizer.JTokkitTokenCountEstimator;
import org.springframework.ai.tokenizer.TokenCountEstimator;
import org.springframework.stereotype.Component;

@Component
public class ChatAgent {

	public record AgentReply(String text, int promptTokens, int completionTokens) {
	}

	public record HistoryStats(int messages, int estimatedTokens) {
	}

	private final ChatClient chatClient;

	private final ChatMemory chatMemory;

	private final ChatMemoryRepository historyRepository;

	private final LastPromptAdvisor lastPromptAdvisor = new LastPromptAdvisor();

	private final TokenCountEstimator tokenCountEstimator = new JTokkitTokenCountEstimator();

	public ChatAgent(ChatClient.Builder builder, ChatMemory chatMemory, ChatMemoryRepository historyRepository) {
		this.chatClient = builder
				.defaultAdvisors(
						MessageChatMemoryAdvisor.builder(chatMemory).build(),
						lastPromptAdvisor)
				.build();
		this.chatMemory = chatMemory;
		this.historyRepository = historyRepository;
	}

	public AgentReply sendMessage(String conversationId, String userMessage) {
		var response = chatClient.prompt()
				.user(userMessage)
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
				.call()
				.chatResponse();
		String text = response.getResult().getOutput().getText();
		Usage usage = response.getMetadata().getUsage();
		return new AgentReply(text, usage.getPromptTokens(), usage.getCompletionTokens());
	}

	public String lastPrompt() {
		return lastPromptAdvisor.lastPrompt();
	}

	public void clearLastPrompt() {
		lastPromptAdvisor.clear();
	}

	public List<Message> getMessages(String conversationId) {
		return List.copyOf(historyRepository.findByConversationId(conversationId));
	}

	public HistoryStats getHistoryStats(String conversationId) {
		List<Message> full = historyRepository.findByConversationId(conversationId);
		int tokens = full.stream()
				.mapToInt(m -> tokenCountEstimator.estimate(m.getText()))
				.sum();
		return new HistoryStats(full.size(), tokens);
	}

	public void closeConversation(String conversationId) {
		chatMemory.clear(conversationId);
	}

}
