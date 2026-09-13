package com.tutorial.ai_challenge;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.AdvisorChain;
import org.springframework.ai.chat.client.advisor.api.BaseAdvisor;
import org.springframework.ai.chat.messages.Message;

public class LastPromptAdvisor implements BaseAdvisor {

	private final ThreadLocal<String> lastPrompt = new ThreadLocal<>();

	@Override
	public ChatClientRequest before(ChatClientRequest request, AdvisorChain chain) {
		StringBuilder sb = new StringBuilder();
		for (Message m : request.prompt().getInstructions()) {
			sb.append(m.getMessageType()).append(": ").append(m.getText()).append("\n\n");
		}
		lastPrompt.set(sb.toString());
		return request;
	}

	@Override
	public ChatClientResponse after(ChatClientResponse response, AdvisorChain chain) {
		return response;
	}

	@Override
	public int getOrder() {
		return 100;
	}

	public String lastPrompt() {
		return lastPrompt.get();
	}

	public void clear() {
		lastPrompt.remove();
	}

}
