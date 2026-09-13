package com.tutorial.ai_challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.metadata.Usage;

public class SummaryChatMemory implements ChatMemory {

	private static final String SUMMARIZE_SYSTEM = """
			Ты сжимаешь историю чата в краткую сводку для передачи контекста.
			Сохраняй все факты, имена, числа, предпочтения и договорённости.
			Отвечай только сводкой, без пояснений.""";

	private final ChatMemoryRepository repository;

	private final ConversationRepository conversations;

	private final ChatClient summarizer;

	private final boolean compressionEnabled;

	private final int recentMessages;

	public SummaryChatMemory(ChatMemoryRepository repository, ConversationRepository conversations,
			ChatClient summarizer, boolean compressionEnabled, int recentMessages) {
		this.repository = repository;
		this.conversations = conversations;
		this.summarizer = summarizer;
		this.compressionEnabled = compressionEnabled;
		this.recentMessages = recentMessages;
	}

	@Override
	public void add(String conversationId, List<Message> messages) {
		if (messages.isEmpty()) {
			return;
		}
		List<Message> full = new ArrayList<>(repository.findByConversationId(conversationId));
		full.addAll(messages);
		repository.saveAll(conversationId, full);
	}

	@Override
	public List<Message> get(String conversationId) {
		List<Message> full = repository.findByConversationId(conversationId);
		if (!compressionEnabled || full.size() <= recentMessages) {
			return full;
		}
		List<Message> recent = full.subList(full.size() - recentMessages, full.size());
		String summary = summaryFor(conversationId, full.subList(0, full.size() - recentMessages));
		List<Message> prompt = new ArrayList<>();
		prompt.add(new SystemMessage("Сводка более ранней части диалога:\n" + summary));
		prompt.addAll(recent);
		return prompt;
	}

	@Override
	public void clear(String conversationId) {
		repository.deleteByConversationId(conversationId);
	}

	private String summaryFor(String conversationId, List<Message> oldMessages) {
		Conversation conversation = null;
		UUID id = parseId(conversationId);
		if (id != null) {
			conversation = conversations.findById(id).orElse(null);
		}
		if (conversation != null && oldMessages.size() <= conversation.getSummaryMessageCount()) {
			return conversation.getSummary();
		}
		String previous = conversation == null ? null : conversation.getSummary();
		SummaryResult result = summarize(previous, oldMessages);
		if (conversation != null) {
			conversation.writeSummary(result.text(), oldMessages.size());
			conversation.addSummaryUsage(result.promptTokens(), result.completionTokens());
			conversations.save(conversation);
		}
		return result.text();
	}

	private record SummaryResult(String text, int promptTokens, int completionTokens) {
	}

	private SummaryResult summarize(String previous, List<Message> oldMessages) {
		StringBuilder transcript = new StringBuilder();
		for (Message m : oldMessages) {
			transcript.append(m.getMessageType()).append(": ").append(m.getText()).append("\n");
		}
		String userPrompt = previous == null || previous.isBlank()
				? "Составь сводку диалога:\n\n" + transcript
				: "Предыдущая сводка:\n" + previous + "\n\nНовые сообщения диалога:\n" + transcript
						+ "\n\nОбъедини их в обновлённую сводку.";
		var response = summarizer.prompt()
				.system(SUMMARIZE_SYSTEM)
				.user(userPrompt)
				.call()
				.chatResponse();
		Usage usage = response.getMetadata().getUsage();
		return new SummaryResult(response.getResult().getOutput().getText(),
				usage.getPromptTokens(), usage.getCompletionTokens());
	}

	private static UUID parseId(String conversationId) {
		try {
			return UUID.fromString(conversationId);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

}
