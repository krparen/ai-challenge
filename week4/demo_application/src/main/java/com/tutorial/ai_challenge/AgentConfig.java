package com.tutorial.ai_challenge;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

	@Bean
	public ChatClient chatClient(ChatClient.Builder builder, ToolCallbackProvider mcpToolCallbackProvider) {
		return builder
				.defaultSystem("""
						Ты — ассистент с доступом к MCP-инструментам двух серверов.
						Сервер name-api: getNameOfDay — случайное имя дня.
						Сервер mock-api: getColor — случайный цвет; getNumber — случайное число от 0 до 9;
						getSummary — сводка накопленных проб; summarizeText — краткая сводка текста;
						saveToFile — сохраняет текст в файл и возвращает путь.
						Выбирай инструменты сам, при необходимости вызывай несколько подряд.
						Отвечай по-русски, кратко упоминай, какие значения получили.
						""")
				.defaultToolCallbacks(mcpToolCallbackProvider)
				.build();
	}

}
