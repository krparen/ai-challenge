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
			Ты ведёшь рабочую память диалогового ассистента: задача движется по этапам конечного автомата
			PLANNING (планирование) → EXECUTION (реализация) → VALIDATION (проверка) → DONE (завершена);
			разрешены откаты VALIDATION → EXECUTION и EXECUTION → PLANNING.
			По текущему ходу диалога верни ответ строго в формате:
			STAGE: <новый этап задачи: PLANNING | EXECUTION | VALIDATION | DONE>
			STEP: <текущий шаг: что именно делается сейчас, одной фразой>
			ACTION: <ожидаемое действие: что должно произойти дальше, одной фразой>
			NOTES
			<остальные детали и договорённости по задаче: строки «ключ: значение»>""";

	private record ExtractResult(TaskStage stage, String step, String action, String notes,
			int promptTokens, int completionTokens) {
	}

	private final ChatMemoryRepository shortTermRepository;

	private final ConversationRepository conversations;

	private final ProfileRepository profiles;

	private final WorkingTaskRepository tasks;

	private final AgentMemoryRepository memories;

	private final ProfileAttributeRepository attributes;

	private final TaskStateHistoryRepository stageHistory;

	private final InvariantRepository invariants;

	private final ChatClient extractor;

	private final int window;

	private final boolean extractionEnabled;

	public MemoryService(ChatMemoryRepository shortTermRepository, ConversationRepository conversations,
			ProfileRepository profiles, WorkingTaskRepository tasks, AgentMemoryRepository memories,
			ProfileAttributeRepository attributes, TaskStateHistoryRepository stageHistory,
			InvariantRepository invariants, ChatClient.Builder builder,
			@Value("${memory.short-term.window:10}") int window,
			@Value("${memory.extraction-enabled:true}") boolean extractionEnabled) {
		this.shortTermRepository = shortTermRepository;
		this.conversations = conversations;
		this.profiles = profiles;
		this.tasks = tasks;
		this.memories = memories;
		this.attributes = attributes;
		this.stageHistory = stageHistory;
		this.invariants = invariants;
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
		addInvariantsBlock(prompt);
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

	public List<Invariant> allInvariants() {
		return invariants.findAllByOrderByCreatedAtAsc();
	}

	private void addInvariantsBlock(List<Message> prompt) {
		List<Invariant> list = allInvariants();
		StringBuilder sb = new StringBuilder("=== ИНВАРИАНТЫ ПРОЕКТА (нарушать запрещено) ===\n");
		if (list.isEmpty()) {
			sb.append("(инварианты не заданы)");
		}
		else {
			for (int i = 0; i < list.size(); i++) {
				Invariant invariant = list.get(i);
				sb.append("И").append(i + 1).append(" [").append(invariant.getCategory()).append("]: ")
						.append(invariant.getText()).append("\n");
			}
			sb.append("Правила: если запрос пользователя противоречит любому инварианту — откажись, явно назови номер и текст нарушенного инварианта и предложи альтернативу в рамках инвариантов. При предложении решений опирайся на применимые инварианты и ссылайся на них по номерам.");
		}
		prompt.add(new SystemMessage(sb.toString().strip()));
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
			sb.append("Этап: ").append(task.getStage()).append(" (").append(task.getStage().getLabel()).append(")\n");
			sb.append("Текущий шаг: ").append(orEmpty(task.getCurrentStep(), "(не определён)")).append("\n");
			sb.append("Ожидаемое действие: ").append(orEmpty(task.getExpectedAction(), "(не определено)")).append("\n");
			sb.append("Заметки:\n");
			sb.append(task.getState() == null || task.getState().isBlank() ? "(пусто)" : task.getState());
		}
		prompt.add(new SystemMessage(sb.toString()));
	}

	private String orEmpty(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
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
		if (task != null) {
			if (result.stage() != null && result.stage() != task.getStage()) {
				applyTransition(task, result.stage(), "LLM", "предложено извлекателем по ходу диалога");
			}
			task.setCurrentStep(result.step());
			task.setExpectedAction(result.action());
			if (result.notes() != null && !result.notes().isBlank()) {
				task.writeState(result.notes().strip());
			}
			tasks.save(task);
		}
		conversations.findById(conversation.getId()).ifPresent(c -> {
			c.addExtractionUsage(result.promptTokens(), result.completionTokens());
			conversations.save(c);
		});
	}

	/**
	 * Переводит задачу на новый этап через валидацию конечным автоматом.
	 *
	 * @return null при успехе, иначе сообщение об ошибке (переход отклонён и залогирован)
	 */
	public String applyTransition(WorkingTask task, TaskStage target, String driver, String note) {
		boolean legal = task.getStage().canTransitionTo(target);
		stageHistory.save(new TaskStateHistory(UUID.randomUUID(), task.getId(),
				task.getStage(), target, driver, legal,
				legal ? note : "ОТКЛОНЕНО FSM: переход " + task.getStage() + " → " + target + " недопустим"));
		if (!legal) {
			return "Переход запрещён автоматом: " + task.getStage() + " → " + target;
		}
		task.setStage(target);
		if (target == TaskStage.DONE) {
			task.finish();
		}
		tasks.save(task);
		return null;
	}

	public List<TaskStage> legalTargets(WorkingTask task) {
		return task == null ? List.of() : task.getStage().legalTargets();
	}

	public List<TaskStateHistory> recentHistory(UUID taskId) {
		return stageHistory.findTop10ByTaskIdOrderByCreatedAtDesc(taskId);
	}

	private ExtractResult extract(WorkingTask task, List<Message> added) {
		Message user = lastOf(added, MessageType.USER);
		Message assistant = lastOf(added, MessageType.ASSISTANT);
		StringBuilder request = new StringBuilder();
		if (task == null) {
			request.append("Активной задачи нет.\n\n");
		}
		else {
			request.append("Текущая задача: ").append(task.getTitle()).append("\n");
			request.append("Текущий этап: ").append(task.getStage()).append(" (")
					.append(task.getStage().getLabel()).append(")\n");
			request.append("Заметки по задаче:\n")
					.append(task.getState() == null || task.getState().isBlank() ? "(пусто)" : task.getState())
					.append("\n\n");
		}
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
		ExtractResult parsed = parseExtraction(response.getResult().getOutput().getText());
		return new ExtractResult(parsed.stage(), parsed.step(), parsed.action(), parsed.notes(),
				usage.getPromptTokens(), usage.getCompletionTokens());
	}

	private ExtractResult parseExtraction(String text) {
		if (text == null || text.isBlank()) {
			return new ExtractResult(null, null, null, null, 0, 0);
		}
		TaskStage stage = null;
		String step = null;
		String action = null;
		StringBuilder notes = new StringBuilder();
		boolean inNotes = false;
		for (String rawLine : text.split("\n")) {
			String line = rawLine.strip();
			if (line.isEmpty()) {
				continue;
			}
			String upper = line.toUpperCase();
			if (upper.startsWith("NOTES")) {
				inNotes = true;
				continue;
			}
			if (stage == null && upper.startsWith("STAGE:")) {
				stage = parseStage(line.substring(6));
				continue;
			}
			if (step == null && upper.startsWith("STEP:")) {
				step = line.substring(5).strip();
				continue;
			}
			if (action == null && upper.startsWith("ACTION:")) {
				action = line.substring(7).strip();
				continue;
			}
			if (inNotes) {
				notes.append(line).append("\n");
			}
		}
		String notesText = notes.toString().strip();
		return new ExtractResult(stage, step, action, notesText.isEmpty() ? null : notesText, 0, 0);
	}

	private TaskStage parseStage(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		String token = raw.strip().split("\\s+")[0].replaceAll("[^A-Za-z]", "");
		try {
			return TaskStage.valueOf(token.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			return null;
		}
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
