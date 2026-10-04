package com.tutorial.ai_challenge;

import java.util.List;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

	public record SourceView(String source, String section, int chunkId) {
	}

	public record ChatMessageView(String role, String text, List<SourceView> sources) {
	}

	private static final String SYSTEM_PROMPT = """
			Ты — помощник по справочнику «Заклинания мира Тани Гроттер».
			Помогаешь пользователю вести дело, связанное со справочником: отвечает на вопросы о заклинаниях
			и удерживаешь цель разговора по мере его развития.
			Если к вопросу приложен контекст из справочника — опирайся на него; формулы заклинаний указывай точно.
			Если ответа в контексте нет — честно скажи, что не нашёл, и не выдумывай.
			Отвечай кратко по-русски.
			""";

	private final ChatClient chatClient;
	private final ChatMemory chatMemory;
	private final ChatMemoryRepository historyRepository;
	private final SearchService searchService;
	private final JdbcTemplate jdbc;

	public ChatService(ChatClient.Builder builder, ChatMemory chatMemory, ChatMemoryRepository historyRepository,
			SearchService searchService, JdbcTemplate jdbc) {
		this.chatClient = builder
				.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
				.build();
		this.chatMemory = chatMemory;
		this.historyRepository = historyRepository;
		this.searchService = searchService;
		this.jdbc = jdbc;
	}

	public String newConversation() {
		return UUID.randomUUID().toString();
	}

	public String ask(String conversationId, String question) {
		List<SearchService.Hit> hits = searchService.search("struct", question, 4);
		String system = SYSTEM_PROMPT;
		if (!hits.isEmpty()) {
			StringBuilder context = new StringBuilder("\n\nКонтекст из справочника:\n");
			for (SearchService.Hit h : hits) {
				context.append("[источник: ").append(h.title())
						.append(" | раздел: ").append(h.section())
						.append(" | чанк #").append(h.chunkId())
						.append("]\n")
						.append(h.content())
						.append("\n\n");
			}
			system += context;
		}
		String answer = chatClient.prompt()
				.system(system)
				.user(question)
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
				.call()
				.content();

		int assistantIndex = assistantCount(conversationId) - 1;
		for (SearchService.Hit h : hits) {
			jdbc.update(
					"INSERT INTO chat_message_sources (conversation_id, message_index, source, section, chunk_id) "
							+ "VALUES (?, ?, ?, ?, ?)",
					conversationId, assistantIndex, h.source(), h.section(), h.chunkId());
		}
		return answer;
	}

	public List<ChatMessageView> history(String conversationId) {
		List<Message> messages = historyRepository.findByConversationId(conversationId);
		long assistantTotal = messages.stream()
				.filter(m -> m.getMessageType() == MessageType.ASSISTANT)
				.count();
		List<List<SourceView>> sourcesByIndex = new java.util.ArrayList<>();
		for (int i = 0; i < assistantTotal; i++) {
			sourcesByIndex.add(new java.util.ArrayList<>());
		}
		if (assistantTotal > 0) {
			jdbc.query(
					"SELECT message_index, source, section, chunk_id FROM chat_message_sources "
							+ "WHERE conversation_id = ? ORDER BY id",
					rs -> {
						int idx = rs.getInt("message_index");
						if (idx >= 0 && idx < sourcesByIndex.size()) {
							sourcesByIndex.get(idx).add(new SourceView(rs.getString("source"),
									rs.getString("section"), rs.getInt("chunk_id")));
						}
					}, conversationId);
		}
		List<ChatMessageView> view = new java.util.ArrayList<>();
		int assistantIndex = 0;
		for (Message m : messages) {
			MessageType type = m.getMessageType();
			if (type == MessageType.USER) {
				view.add(new ChatMessageView("user", m.getText(), List.of()));
			}
			else if (type == MessageType.ASSISTANT) {
				view.add(new ChatMessageView("assistant", m.getText(), sourcesByIndex.get(assistantIndex)));
				assistantIndex++;
			}
		}
		return view;
	}

	public int historySize(String conversationId) {
		return historyRepository.findByConversationId(conversationId).size();
	}

	public void clear(String conversationId) {
		chatMemory.clear(conversationId);
		jdbc.update("DELETE FROM chat_message_sources WHERE conversation_id = ?", conversationId);
	}

	private int assistantCount(String conversationId) {
		return (int) historyRepository.findByConversationId(conversationId).stream()
				.filter(m -> m.getMessageType() == MessageType.ASSISTANT)
				.count();
	}

}
