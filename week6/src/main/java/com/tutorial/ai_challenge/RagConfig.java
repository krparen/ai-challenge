package com.tutorial.ai_challenge;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

	public static final String SYSTEM_PROMPT = """
			Ты — помощник по справочнику «Заклинания мира Тани Гроттер».
			Отвечай ТОЛЬКО по приведённому контексту из справочника.
			Формулы заклинаний указывай точно, как в контексте.
			Если ответа в контексте нет — честно скажи, что не нашёл, и не выдумывай.
			Отвечай кратко по-русски.
			""";

	@Bean
	public ChatClient localChatClient(OllamaChatModel model) {
		return ChatClient.builder(model).defaultSystem(SYSTEM_PROMPT).build();
	}

	@Bean
	public ChatClient cloudChatClient(DeepSeekChatModel model) {
		return ChatClient.builder(model).defaultSystem(SYSTEM_PROMPT).build();
	}

}
