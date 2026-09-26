package com.tutorial.ai_challenge;

import java.lang.reflect.Type;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

public class MockMcpTools {

	private final MockService mockService;

	public MockMcpTools(MockService mockService) {
		this.mockService = mockService;
	}

	@Tool(name = "getColor", description = "Возвращает случайный цвет: Синий, Зелёный или Красный",
			resultConverter = PlainTextConverter.class)
	public String getColor() {
		return mockService.getColor();
	}

	@Tool(name = "getNumber", description = "Возвращает случайное число от 0 до 9",
			resultConverter = PlainTextConverter.class)
	public int getNumber() {
		return mockService.getNumber();
	}

	public static class PlainTextConverter implements ToolCallResultConverter {

		@Override
		public String convert(Object result, Type returnType) {
			return String.valueOf(result);
		}

	}

}
