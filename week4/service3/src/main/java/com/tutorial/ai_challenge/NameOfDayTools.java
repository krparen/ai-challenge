package com.tutorial.ai_challenge;

import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

public class NameOfDayTools {

	private static final List<String> NAMES = List.of("Андрей", "Борис", "Вениамин", "Григорий", "Дмитрий");

	@Tool(name = "getNameOfDay", description = "Возвращает случайное имя дня: Андрей, Борис, Вениамин, Григорий или Дмитрий",
			resultConverter = PlainTextConverter.class)
	public String getNameOfDay() {
		return NAMES.get(ThreadLocalRandom.current().nextInt(NAMES.size()));
	}

	public static class PlainTextConverter implements ToolCallResultConverter {

		@Override
		public String convert(Object result, Type returnType) {
			return String.valueOf(result);
		}

	}

}
