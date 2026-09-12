package com.tutorial.ai_challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

@Component
public class ChatAgent {

	private final ChatClient chatClient;

	private final Map<String, List<Message>> conversations = new ConcurrentHashMap<>();

	public ChatAgent(ChatClient.Builder builder) {
		this.chatClient = builder.build();
	}

	public String sendMessage(String conversationId, String userMessage) {
		List<Message> history = conversations.computeIfAbsent(conversationId, id -> new ArrayList<>());
		history.add(new UserMessage(userMessage));
		try {
			String reply = chatClient.prompt()
					.messages(history)
					.call()
					.content();
			history.add(new AssistantMessage(reply));
			return reply;
		}
		catch (RuntimeException e) {
			history.removeLast();
			throw e;
		}
	}

	public List<Message> getMessages(String conversationId) {
		List<Message> history = conversations.get(conversationId);
		return history == null ? List.of() : List.copyOf(history);
	}

	public void closeConversation(String conversationId) {
		conversations.remove(conversationId);
	}

}
