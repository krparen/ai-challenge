package com.tutorial.ai_challenge;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MemoryService {

	private static final String EXTRACT_SYSTEM = """
			Ты обновляешь рабочую память диалогового ассистента по текущему ходу диалога.
			Верни ответ строго в формате:
			TASK
			<обновлённое ПОЛНОЕ состояние текущей задачи: строки «ключ: значение»>""";

	private record ExtractResult(String taskState, int promptTokens, int completionTokens) {
	}

	private final ChatMemoryRepository shortTermRepository;

	private final ConversationRepository conversations;

	private final ProfileRepository profiles;

	private final WorkingTaskRepository tasks;

	private final AgentMemoryRepository memories;

	private final ProfileAttributeRepository attributes;

	private final ChatClient extractor;

	private final int window;

	private final boolean extractionEnabled;

	public MemoryService(ChatMemoryRepository shortTermRepository, ConversationRepository conversations,
			ProfileRepository profiles, WorkingTaskRepository tasks, AgentMemoryRepository memories,
			ProfileAttributeRepository attributes, ChatClient.Builder builder,
			@Value("${memory.short-term.window:10}") int window,
			@Value("${memory.extraction-enabled:true}") boolean extractionEnabled) {
		this.shortTermRepository = shortTermRepository;
		this.conversations = conversations;
		this.profiles = profiles;
		this.tasks = tasks;
		this.memories = memories;
		this.attributes = attributes;
		this.extractor = builder.build();
		this.window = window;
		this.extractionEnabled = extractionEnabled;
	}

	public int windowSize() {
		return window;
	}

	// ---------- слой 1: краткосрочная память (текущий диалог) ----------

	public void appendShortTerm(String conversationId, List<Message> added) {
		if (added.isEmpty()) {
			return;
		}
		List<Message> full = new ArrayList<>(shortTermRepository.findByConversationId(conversationId));
		full.addAll(added);
		shortTermRepository.saveAll(conversationId, full);
	}

	public List<Message> shortTermWindow(String conversationId) {
		List<Message> full = shortTermRepository.findByConversationId(conversationId);
		if (full.size() <= window) {
			return full;
		}
		return new ArrayList<>(full.subList(full.size() - window, full.size()));
	}

	public void clearShortTerm(String conversationId) {
		shortTermRepository.deleteByConversationId(conversationId);
	}

	// ---------- сборка промпта из всех слоёв ----------

	public List<Message> buildContext(String conversationId) {
		List<Message> prompt = new ArrayList<>();
		Conversation conversation = findConversation(conversationId);
		if (conversation != null) {
			Profile profile = profiles.findById(conversation.getProfileId()).orElse(null);
			if (profile != null) {
				addProfileBlock(prompt, profile);
				addAttributeBlock(prompt, profile);
				addAgentMemoryBlock(prompt, profile);
			}
			addTaskBlock(prompt, activeTask());
		}
		prompt.add(new SystemMessage("=== КРАТКОСРОЧНАЯ ПАМЯТЬ (последние сообщения текущего диалога) ==="));
		prompt.addAll(shortTermWindow(conversationId));
		return prompt;
	}

	private void addProfileBlock(List<Message> prompt, Profile profile) {
		StringBuilder sb = new StringBuilder("=== ПРОФИЛЬ ПОЛЬЗОВАТЕЛЯ: предпочтения (соблюдай при ответах) ===\n");
		if (profile.hasPrefs()) {
			if (profile.getStyle() != null) {
				sb.append("Стиль: ").append(profile.getStyle()).append("\n");
			}
			if (profile.getFormat() != null) {
				sb.append("Формат: ").append(profile.getFormat()).append("\n");
			}
			if (profile.getRestrictions() != null) {
				sb.append("Ограничения: ").append(profile.getRestrictions()).append("\n");
			}
		}
		else {
			sb.append("(предпочтения пока не заданы)");
		}
		prompt.add(new SystemMessage(sb.toString().strip()));
	}

	public List<ProfileAttribute> profileAttributes(UUID profileId) {
		return attributes.findByProfileIdOrderByCreatedAtAsc(profileId);
	}

	private void addAttributeBlock(List<Message> prompt, Profile profile) {
		List<ProfileAttribute> attrs = profileAttributes(profile.getId());
		StringBuilder sb = new StringBuilder("=== ПРОФИЛЬ ПОЛЬЗОВАТЕЛЯ: данные ===\n");
		if (attrs.isEmpty()) {
			sb.append("(пока пусто)");
		}
		for (ProfileAttribute a : attrs) {
			sb.append("- ").append(a.getAttrKey()).append(": ").append(a.getAttrValue()).append("\n");
		}
		prompt.add(new SystemMessage(sb.toString().strip()));
	}

	private void addAgentMemoryBlock(List<Message> prompt, Profile profile) {
		List<AgentMemory> confirmed = confirmedMemory(profile.getId());
		StringBuilder sb = new StringBuilder("=== ПАМЯТЬ АГЕНТА (долговременная: решения и знания о пользователе) ===\n");
		if (confirmed.isEmpty()) {
			sb.append("(пока пусто)");
		}
		for (AgentMemory m : confirmed) {
			sb.append("- ").append(m.getFactKey()).append(": ").append(m.getFactValue()).append("\n");
		}
		prompt.add(new SystemMessage(sb.toString().strip()));
	}

	private void addTaskBlock(List<Message> prompt, WorkingTask task) {
		StringBuilder sb = new StringBuilder("=== РАБОЧАЯ ПАМЯТЬ: текущая задача ===\n");
		if (task == null) {
			sb.append("(нет активной задачи)");
		}
		else {
			sb.append("Задача: ").append(task.getTitle()).append("\n");
			sb.append("Состояние задачи:\n");
			sb.append(task.getState() == null || task.getState().isBlank() ? "(пока пусто)" : task.getState());
		}
		prompt.add(new SystemMessage(sb.toString()));
	}

	// ---------- слой 2: рабочая память (текущая задача, общий пул для всех профилей) ----------

	public WorkingTask activeTask() {
		List<WorkingTask> active = tasks.findByStatus(TaskStatus.ACTIVE);
		return active.isEmpty() ? null : active.get(0);
	}

	public List<WorkingTask> allTasks() {
		return tasks.findAllByOrderByCreatedAtDesc();
	}

	public void pauseOtherActiveTasks(UUID exceptTaskId) {
		for (WorkingTask t : tasks.findByStatus(TaskStatus.ACTIVE)) {
			if (!t.getId().equals(exceptTaskId)) {
				t.pause();
				tasks.save(t);
			}
		}
	}

	// ---------- слой 3: долговременная память агента ----------

	public List<AgentMemory> confirmedMemory(UUID profileId) {
		return memories.findByProfileIdOrderByCreatedAtDesc(profileId).stream()
				.filter(m -> m.getStatus() == MemoryStatus.CONFIRMED)
				.toList();
	}

	// ---------- ход диалога: сохранить и извлечь ----------

	public void onTurn(String conversationId, List<Message> added) {
		appendShortTerm(conversationId, added);
		if (!extractionEnabled || added.isEmpty()) {
			return;
		}
		Conversation conversation = findConversation(conversationId);
		if (conversation == null) {
			return;
		}
		Profile profile = profiles.findById(conversation.getProfileId()).orElse(null);
		if (profile == null) {
			return;
		}
		WorkingTask task = activeTask();
		ExtractResult result = extract(task, added);
		if (task != null && result.taskState() != null && !result.taskState().isBlank()) {
			task.writeState(result.taskState());
			tasks.save(task);
		}
		conversations.findById(conversation.getId()).ifPresent(c -> {
			c.addExtractionUsage(result.promptTokens(), result.completionTokens());
			conversations.save(c);
		});
	}

	private ExtractResult extract(WorkingTask task, List<Message> added) {
		Message user = lastOf(added, MessageType.USER);
		Message assistant = lastOf(added, MessageType.ASSISTANT);
		StringBuilder request = new StringBuilder();
		request.append("Текущая задача: ").append(task == null ? "(нет активной задачи)" : task.getTitle()).append("\n");
		request.append("Состояние задачи:\n")
				.append(task == null || task.getState() == null || task.getState().isBlank() ? "(пусто)"
						: task.getState())
				.append("\n\n");
		request.append("Ход диалога:\n");
		if (user != null) {
			request.append("ПОЛЬЗОВАТЕЛЬ: ").append(user.getText()).append("\n");
		}
		if (assistant != null) {
			request.append("АССИСТЕНТ: ").append(assistant.getText()).append("\n");
		}
		var response = extractor.prompt()
				.system(EXTRACT_SYSTEM)
				.user(request.toString())
				.call()
				.chatResponse();
		Usage usage = response.getMetadata().getUsage();
		String taskState = parseTask(response.getResult().getOutput().getText());
		return new ExtractResult(taskState, usage.getPromptTokens(), usage.getCompletionTokens());
	}

	private String parseTask(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		StringBuilder state = new StringBuilder();
		boolean inTask = false;
		for (String rawLine : text.split("\n")) {
			String line = rawLine.strip();
			if (line.isEmpty()) {
				continue;
			}
			if (line.startsWith("TASK")) {
				inTask = true;
				continue;
			}
			if (inTask) {
				state.append(line).append("\n");
			}
		}
		String result = state.toString().strip();
		return result.isEmpty() ? null : result;
	}

	private Message lastOf(List<Message> messages, MessageType type) {
		Message result = null;
		for (Message m : messages) {
			if (m.getMessageType() == type) {
				result = m;
			}
		}
		return result;
	}

	private Conversation findConversation(String conversationId) {
		try {
			return conversations.findById(UUID.fromString(conversationId)).orElse(null);
		}
		catch (IllegalArgumentException e) {
			return null;
		}
	}

}
