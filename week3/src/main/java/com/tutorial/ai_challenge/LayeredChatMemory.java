package com.tutorial.ai_challenge;

import java.util.List;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

public class LayeredChatMemory implements ChatMemory {

	private final MemoryService memory;

	public LayeredChatMemory(MemoryService memory) {
		this.memory = memory;
	}

	@Override
	public void add(String conversationId, List<Message> messages) {
		memory.onTurn(conversationId, messages);
	}

	@Override
	public List<Message> get(String conversationId) {
		return memory.buildContext(conversationId);
	}

	@Override
	public void clear(String conversationId) {
		memory.clearShortTerm(conversationId);
	}

}
