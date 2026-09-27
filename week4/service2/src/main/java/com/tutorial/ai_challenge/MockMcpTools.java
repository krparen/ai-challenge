package com.tutorial.ai_challenge;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.execution.ToolCallResultConverter;

public class MockMcpTools {

	private final MockService mockService;
	private final SampleStore sampleStore;
	private final Path saveDir;

	public MockMcpTools(MockService mockService, SampleStore sampleStore, Path saveDir) {
		this.mockService = mockService;
		this.sampleStore = sampleStore;
		this.saveDir = saveDir;
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

	@Tool(name = "summarizeText", description = "Строит краткую сводку переданного текста: строки, слова, символы и сам текст",
			resultConverter = PlainTextConverter.class)
	public String summarizeText(@ToolParam(description = "Текст для сводки") String text) {
		String trimmed = text != null ? text.trim() : "";
		String[] lines = trimmed.isEmpty() ? new String[0] : trimmed.split("\r?\n");
		String[] words = trimmed.isEmpty() ? new String[0] : trimmed.split("\\s+");
		String shown = trimmed.isEmpty() ? "(пусто)" : trimmed;
		return "Сводка текста: строк " + lines.length + ", слов " + words.length + ", символов " + trimmed.length()
				+ ". Текст: «" + shown + "»";
	}

	@Tool(name = "saveToFile", description = "Сохраняет переданный текст в файл в каталоге saved и возвращает путь к файлу",
			resultConverter = PlainTextConverter.class)
	public String saveToFile(@ToolParam(description = "Текст для сохранения") String text) throws Exception {
		Files.createDirectories(saveDir);
		String name = "pipeline-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")) + ".txt";
		Path file = saveDir.resolve(name);
		Files.writeString(file, text != null ? text : "");
		return "Сохранено: " + file.toAbsolutePath() + " (" + Files.size(file) + " байт)";
	}

	public static class PlainTextConverter implements ToolCallResultConverter {

		@Override
		public String convert(Object result, Type returnType) {
			return String.valueOf(result);
		}

	}

}
