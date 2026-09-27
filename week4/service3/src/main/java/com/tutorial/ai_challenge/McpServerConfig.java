package com.tutorial.ai_challenge;

import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

	@Bean
	public MethodToolCallbackProvider nameToolCallbackProvider() {
		return MethodToolCallbackProvider.builder()
				.toolObjects(new NameOfDayTools())
				.build();
	}

}
