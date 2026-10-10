package com.tutorial.ai_challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.stereotype.Service;

@Service
public class ChatService {

	public record ChatMessageView(String role, String text) {
	}

	private static final String SYSTEM_PROMPT = """
			Ты — дружелюбный помощник по имени Qwen (локальная модель qwen2.5:3b).
			Отвечай кратко и по-русски.
			Помни, что обсуждали ранее в диалоге, и используй это в ответах.
			""";

	private final ChatClient chatClient;
	private final ChatMemory chatMemory;
	private final ChatMemoryRepository historyRepository;

	public ChatService(OllamaChatModel chatModel, ChatMemory chatMemory, ChatMemoryRepository historyRepository) {
		this.chatClient = ChatClient.builder(chatModel)
				.defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
				.build();
		this.chatMemory = chatMemory;
		this.historyRepository = historyRepository;
	}

	public String newConversation() {
		return UUID.randomUUID().toString();
	}

	public String ask(String conversationId, String question) {
		return chatClient.prompt()
				.system(SYSTEM_PROMPT)
				.user(question)
				.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId))
				.call()
				.content();
	}

	public List<ChatMessageView> history(String conversationId) {
		List<ChatMessageView> view = new ArrayList<>();
		for (Message m : historyRepository.findByConversationId(conversationId)) {
			MessageType type = m.getMessageType();
			if (type == MessageType.USER) {
				view.add(new ChatMessageView("user", m.getText()));
			}
			else if (type == MessageType.ASSISTANT) {
				view.add(new ChatMessageView("assistant", m.getText()));
			}
		}
		return view;
	}

	public int historySize(String conversationId) {
		return historyRepository.findByConversationId(conversationId).size();
	}

	public void clear(String conversationId) {
		chatMemory.clear(conversationId);
	}

}
