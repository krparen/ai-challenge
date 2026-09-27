package com.tutorial.ai_challenge;

import java.lang.reflect.Type;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

public class MockMcpTools {

	private final MockService mockService;
	private final SampleStore sampleStore;

	public MockMcpTools(MockService mockService, SampleStore sampleStore) {
		this.mockService = mockService;
		this.sampleStore = sampleStore;
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

	@Tool(name = "getSummary", description = "Возвращает сводку по собранным фоновым сборщиком пробам цвета и числа",
			resultConverter = PlainTextConverter.class)
	public String getSummary() {
		SampleStore.Summary s = sampleStore.aggregate();
		StringBuilder colors = new StringBuilder();
		s.byColor().forEach((color, cnt) -> {
			if (colors.length() > 0) {
				colors.append(", ");
			}
			colors.append(color).append(" — ").append(cnt);
		});
		if (colors.length() == 0) {
			colors.append("нет данных");
		}
		String window = s.firstTs() != null
				? " с " + s.firstTs().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0)
						+ " по " + s.lastTs().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalTime().withNano(0)
				: "";
		return "Проб: " + s.total() + window
				+ ". Цвета: " + colors
				+ ". Число: среднее " + Math.round(s.avgNumber() * 10) / 10.0
				+ ", мин " + s.minNumber() + ", макс " + s.maxNumber() + ".";
	}

	public static class PlainTextConverter implements ToolCallResultConverter {

		@Override
		public String convert(Object result, Type returnType) {
			return String.valueOf(result);
		}

	}

}
