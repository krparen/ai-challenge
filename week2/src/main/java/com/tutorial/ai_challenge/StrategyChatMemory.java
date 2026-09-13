package com.tutorial.ai_challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.metadata.Usage;

public class StrategyChatMemory implements ChatMemory {

	private static final String SUMMARIZE_SYSTEM = """
			Ты сжимаешь историю чата в краткую сводку для передачи контекста.
			Сохраняй все факты, имена, числа, предпочтения и договорённости.
			Отвечай только сводкой, без пояснений.""";

	private static final String FACTS_SYSTEM = """
			Ты хранитель важных фактов диалога: имена, цели, ограничения, предпочтения, решения, договорённости.
			Тебе даны текущие факты и новое сообщение диалога — верни обновлённый ПОЛНЫЙ список фактов.
			Формат: по одному факту на строку в виде «ключ: значение», без нумерации и пояснений.
			Устаревшие факты обновляй или убирай. Если фактов нет — верни пустой ответ.""";

	private final ChatMemoryRepository repository;

	private final ConversationRepository conversations;

	private final ChatClient llm;

	private final int recentMessages;

	public StrategyChatMemory(ChatMemoryRepository repository, ConversationRepository conversations,
			ChatClient llm, int recentMessages) {
		this.repository = repository;
		this.conversations = conversations;
		this.llm = llm;
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
		Conversation conversation = find(conversationId);
		if (conversation != null && conversation.getStrategy() == ChatStrategy.FACTS) {
			updateFacts(conversation, messages);
		}
	}

	@Override
	public List<Message> get(String conversationId) {
		List<Message> full = repository.findByConversationId(conversationId);
		Conversation conversation = find(conversationId);
		ChatStrategy strategy = conversation != null ? conversation.getStrategy() : ChatStrategy.FULL;
		return switch (strategy) {
			case WINDOW -> recent(full);
			case FACTS -> factsPrompt(conversation, full);
			case SUMMARY -> summaryPrompt(conversation, full);
			default -> full;
		};
	}

	@Override
	public void clear(String conversationId) {
		repository.deleteByConversationId(conversationId);
	}

	private List<Message> recent(List<Message> full) {
		if (full.size() <= recentMessages) {
			return full;
		}
		return new ArrayList<>(full.subList(full.size() - recentMessages, full.size()));
	}

	private List<Message> factsPrompt(Conversation conversation, List<Message> full) {
		List<Message> prompt = new ArrayList<>();
		String facts = conversation == null ? null : conversation.getFacts();
		if (facts != null && !facts.isBlank()) {
			prompt.add(new SystemMessage("Известные факты диалога:\n" + facts));
		}
		prompt.addAll(recent(full));
		return prompt;
	}

	private List<Message> summaryPrompt(Conversation conversation, List<Message> full) {
		if (full.size() <= recentMessages) {
			return full;
		}
		List<Message> recent = full.subList(full.size() - recentMessages, full.size());
		String summary = summaryFor(conversation, full.subList(0, full.size() - recentMessages));
		List<Message> prompt = new ArrayList<>();
		prompt.add(new SystemMessage("Сводка более ранней части диалога:\n" + summary));
		prompt.addAll(recent);
		return prompt;
	}

	private void updateFacts(Conversation conversation, List<Message> added) {
		Message user = lastOf(added, MessageType.USER);
		if (user == null) {
			return;
		}
		Message assistant = lastOf(added, MessageType.ASSISTANT);
		String currentFacts = conversation.getFacts();
		String userPrompt = "Текущие факты:\n"
				+ (currentFacts == null || currentFacts.isBlank() ? "(пока нет)" : currentFacts)
				+ "\n\nСообщение пользователя: " + user.getText()
				+ "\nОтвет ассистента: " + (assistant == null ? "(нет)" : assistant.getText())
				+ "\n\nВерни обновлённый список фактов.";
		var response = llm.prompt()
				.system(FACTS_SYSTEM)
				.user(userPrompt)
				.call()
				.chatResponse();
		Usage usage = response.getMetadata().getUsage();
		String facts = response.getResult().getOutput().getText();
		conversation.writeFacts(facts == null || facts.isBlank() ? null : facts.strip());
		conversation.addFactsUsage(usage.getPromptTokens(), usage.getCompletionTokens());
		conversations.save(conversation);
	}

	private Message lastOf(List<Message> messages, MessageType type) {
		Message result = null;
		for (Message m : messages) {
			if (m.getMessageType() == type) {
				result = m;
			}
		}
		return result;
	}

	private String summaryFor(Conversation conversation, List<Message> oldMessages) {
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
		var response = llm.prompt()
				.system(SUMMARIZE_SYSTEM)
				.user(userPrompt)
				.call()
				.chatResponse();
		Usage usage = response.getMetadata().getUsage();
		return new SummaryResult(response.getResult().getOutput().getText(),
				usage.getPromptTokens(), usage.getCompletionTokens());
	}

	private Conversation find(String conversationId) {
		UUID id = parseId(conversationId);
		return id == null ? null : conversations.findById(id).orElse(null);
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
