package com.tutorial.ai_challenge;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagConfig {

	public static final String CLOUD_SYSTEM_PROMPT = """
			Ты — помощник по справочнику «Заклинания мира Тани Гроттер».
			Отвечай ТОЛЬКО по приведённому контексту из справочника.
			Формулы заклинаний указывай точно, как в контексте.
			Если ответа в контексте нет — честно скажи, что не нашёл, и не выдумывай.
			Отвечай кратко по-русски.
			""";

	public static final String LOCAL_SYSTEM_PROMPT = """
			Ты — извлекатель данных из справочника «Заклинания мира Тани Гроттер».
			Отвечай СТРОГО по контексту из справочника, в формате:
			Формула: <точная формула из контекста, скопируй символ в символ>
			Действие: <одна фраза>
			Примечание: <одна фраза — почему запрещено / кто автор / что ещё есть в контексте>
			Если формулы в контексте нет — напиши ровно: Не нашёл в контексте
			Не рассуждай, не добавляй ничего от себя, формулу не перефразируй.
			""";

	public static final int LOCAL_NUM_CTX = 4096;
	public static final int LOCAL_NUM_PREDICT = 220;
	public static final double LOCAL_TEMPERATURE = 0.0;

	@Bean
	public ChatClient localChatClient(OllamaChatModel model) {
		return ChatClient.builder(model).build();
	}

	@Bean
	public ChatClient cloudChatClient(DeepSeekChatModel model) {
		return ChatClient.builder(model).build();
	}

	public static OllamaChatOptions.Builder localOptions() {
		return OllamaChatOptions.builder()
				.temperature(LOCAL_TEMPERATURE)
				.numPredict(LOCAL_NUM_PREDICT)
				.seed(42);
	}

}
