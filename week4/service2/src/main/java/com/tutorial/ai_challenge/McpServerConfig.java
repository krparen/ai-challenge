package com.tutorial.ai_challenge;

import java.nio.file.Path;

import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class McpServerConfig {

	@Bean
	public MethodToolCallbackProvider mockToolCallbackProvider(MockService mockService, SampleStore sampleStore,
			@Value("${mock.save.dir:saved}") String saveDir) {
		return MethodToolCallbackProvider.builder()
				.toolObjects(new MockMcpTools(mockService, sampleStore, Path.of(saveDir)))
				.build();
	}

}
