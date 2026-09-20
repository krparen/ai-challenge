package com.tutorial.ai_challenge;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ChatController {

	private static final String CONVERSATION_COOKIE = "conversationId";

	private static final String PROFILE_COOKIE = "profileId";

	private static final Pattern UUID_PATTERN = Pattern.compile(
			"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

	private record ChatMessageView(boolean fromUser, String text) {
	}

	private final ChatAgent agent;

	private final MemoryService memory;

	private final ConversationRepository conversations;

	private final ProfileRepository profiles;

	private final WorkingTaskRepository tasks;

	private final AgentMemoryRepository memories;

	private final ProfileAttributeRepository attributes;

	private final InvariantRepository invariants;

	public ChatController(ChatAgent agent, MemoryService memory, ConversationRepository conversations,
			ProfileRepository profiles, WorkingTaskRepository tasks, AgentMemoryRepository memories,
			ProfileAttributeRepository attributes, InvariantRepository invariants) {
		this.agent = agent;
		this.memory = memory;
		this.conversations = conversations;
		this.profiles = profiles;
		this.tasks = tasks;
		this.memories = memories;
		this.attributes = attributes;
		this.invariants = invariants;
	}

	@GetMapping("/")
	public String home() {
		return "redirect:/chat";
	}

	@GetMapping("/chat")
	public String chat(@CookieValue(name = CONVERSATION_COOKIE, required = false) String conversationId,
			@CookieValue(name = PROFILE_COOKIE, required = false) String profileId,
			HttpServletResponse response, Model model) {
		Profile profile = ensureProfile(profileId, response);
		Conversation conversation = ensureConversation(conversationId, profile, response);
		model.addAttribute("conversationId", conversation.getId().toString());
		model.addAttribute("profile", profile);
		model.addAttribute("profiles", profiles.findAllByOrderByCreatedAtAsc());
		model.addAttribute("activeTask", memory.activeTask());
		model.addAttribute("tasks", memory.allTasks());
		model.addAttribute("profileNames",
				profiles.findAll().stream().collect(Collectors.toMap(Profile::getId, Profile::getName)));
		model.addAttribute("stageTargets", memory.legalTargets(memory.activeTask()));
		model.addAttribute("taskHistory", memory.activeTask() == null
				? List.of()
				: memory.recentHistory(memory.activeTask().getId()));
		model.addAttribute("confirmedMemory", memory.confirmedMemory(profile.getId()));
		model.addAttribute("profileAttrs", memory.profileAttributes(profile.getId()));
		model.addAttribute("invariants", memory.allInvariants());
		model.addAttribute("messages", toViews(agent.getMessages(conversation.getId().toString())));
		model.addAttribute("historyStats", agent.getHistoryStats(conversation.getId().toString()));
		model.addAttribute("window", memory.windowSize());
		model.addAttribute("promptTotal", conversation.getPromptTokens());
		model.addAttribute("completionTotal", conversation.getCompletionTokens());
		model.addAttribute("extractionPromptTotal", conversation.getExtractionPromptTokens());
		model.addAttribute("extractionCompletionTotal", conversation.getExtractionCompletionTokens());
		return "chat";
	}

	@PostMapping("/chat")
	public String send(@RequestParam String message,
			@CookieValue(name = CONVERSATION_COOKIE, required = false) String conversationId,
			@CookieValue(name = PROFILE_COOKIE, required = false) String profileId,
			HttpServletResponse response, RedirectAttributes redirect) {
		Profile profile = ensureProfile(profileId, response);
		Conversation conversation = ensureConversation(conversationId, profile, response);
		try {
			ChatAgent.AgentReply reply = agent.sendMessage(conversation.getId().toString(), message);
			conversations.findById(conversation.getId()).ifPresent(c -> {
				c.addUsage(reply.promptTokens(), reply.completionTokens());
				conversations.save(c);
			});
			redirect.addFlashAttribute("lastPrompt", reply.promptTokens());
			redirect.addFlashAttribute("lastCompletion", reply.completionTokens());
			String promptText = agent.lastPrompt();
			if (promptText != null) {
				redirect.addFlashAttribute("lastPromptText", promptText);
			}
			agent.clearLastPrompt();
		}
		catch (RuntimeException e) {
			redirect.addFlashAttribute("error", e.getMessage());
		}
		return "redirect:/chat";
	}

	@PostMapping("/chat/new")
	public String newChat(@CookieValue(name = CONVERSATION_COOKIE, required = false) String conversationId,
			@CookieValue(name = PROFILE_COOKIE, required = false) String profileId,
			HttpServletResponse response) {
		Profile profile = ensureProfile(profileId, response);
		if (conversationId != null && UUID_PATTERN.matcher(conversationId).matches()) {
			agent.closeConversation(conversationId);
			conversations.findById(UUID.fromString(conversationId)).ifPresent(Conversation::close);
		}
		Conversation conversation = new Conversation(UUID.randomUUID(), profile.getId());
		conversations.save(conversation);
		setCookie(response, CONVERSATION_COOKIE, conversation.getId().toString());
		return "redirect:/chat";
	}

	@PostMapping("/profile/switch")
	public String switchProfile(@RequestParam String profileId,
			@CookieValue(name = CONVERSATION_COOKIE, required = false) String conversationId,
			HttpServletResponse response, RedirectAttributes redirect) {
		Profile profile = UUID_PATTERN.matcher(profileId).matches()
				? profiles.findById(UUID.fromString(profileId)).orElse(null)
				: null;
		if (profile == null) {
			redirect.addFlashAttribute("error", "Профиль не найден");
			return "redirect:/chat";
		}
		setCookie(response, PROFILE_COOKIE, profile.getId().toString());
		relinkConversation(conversationId, profile.getId());
		return "redirect:/chat";
	}

	@PostMapping("/profile/new")
	public String newProfile(@RequestParam String name,
			@CookieValue(name = CONVERSATION_COOKIE, required = false) String conversationId,
			HttpServletResponse response, RedirectAttributes redirect) {
		if (name == null || name.isBlank()) {
			redirect.addFlashAttribute("error", "Имя профиля не может быть пустым");
			return "redirect:/chat";
		}
		Profile profile = new Profile(UUID.randomUUID(), name.strip());
		profiles.save(profile);
		setCookie(response, PROFILE_COOKIE, profile.getId().toString());
		relinkConversation(conversationId, profile.getId());
		return "redirect:/chat";
	}

	@PostMapping("/profile/prefs")
	public String updatePrefs(@RequestParam(required = false) String style,
			@RequestParam(required = false) String format,
			@RequestParam(required = false) String restrictions,
			@CookieValue(name = PROFILE_COOKIE, required = false) String profileId) {
		Profile profile = findProfile(profileId);
		if (profile != null) {
			profile.updatePrefs(style, format, restrictions);
			profiles.save(profile);
		}
		return "redirect:/chat";
	}

	@PostMapping("/profile/attr/add")
	public String addProfileAttr(@RequestParam String attrKey, @RequestParam String attrValue,
			@CookieValue(name = PROFILE_COOKIE, required = false) String profileId) {
		Profile profile = findProfile(profileId);
		if (profile != null && attrKey != null && !attrKey.isBlank() && attrValue != null && !attrValue.isBlank()) {
			attributes.save(new ProfileAttribute(UUID.randomUUID(), profile.getId(),
					attrKey.strip(), attrValue.strip()));
		}
		return "redirect:/chat";
	}

	@PostMapping("/profile/attr/delete")
	public String deleteProfileAttr(@RequestParam String attrId,
			@CookieValue(name = PROFILE_COOKIE, required = false) String profileId) {
		ProfileAttribute a = attrId != null && UUID_PATTERN.matcher(attrId).matches()
				? attributes.findById(UUID.fromString(attrId)).orElse(null)
				: null;
		if (a != null && a.getProfileId().equals(profileUUID(profileId))) {
			attributes.delete(a);
		}
		return "redirect:/chat";
	}

	@PostMapping("/invariant/add")
	public String addInvariant(@RequestParam String category, @RequestParam String text) {
		if (category != null && !category.isBlank() && text != null && !text.isBlank()) {
			invariants.save(new Invariant(UUID.randomUUID(), category.strip(), text.strip()));
		}
		return "redirect:/chat";
	}

	@PostMapping("/invariant/delete")
	public String deleteInvariant(@RequestParam String invariantId) {
		if (invariantId != null && UUID_PATTERN.matcher(invariantId).matches()) {
			invariants.findById(UUID.fromString(invariantId)).ifPresent(invariants::delete);
		}
		return "redirect:/chat";
	}

	@PostMapping("/task/new")
	public String newTask(@RequestParam String title,
			@CookieValue(name = PROFILE_COOKIE, required = false) String profileId) {
		Profile profile = findProfile(profileId);
		if (profile != null && title != null && !title.isBlank()) {
			WorkingTask task = new WorkingTask(UUID.randomUUID(), profile.getId(), title.strip());
			tasks.save(task);
			memory.pauseOtherActiveTasks(task.getId());
		}
		return "redirect:/chat";
	}

	@PostMapping("/task/switch")
	public String switchTask(@RequestParam String taskId,
			@CookieValue(name = PROFILE_COOKIE, required = false) String profileId) {
		WorkingTask task = findTask(taskId);
		if (task != null) {
			task.activate();
			tasks.save(task);
			memory.pauseOtherActiveTasks(task.getId());
		}
		return "redirect:/chat";
	}

	@PostMapping("/task/deactivate")
	public String deactivateTask(@RequestParam String taskId,
			@CookieValue(name = PROFILE_COOKIE, required = false) String profileId) {
		WorkingTask task = findTask(taskId);
		if (task != null && task.getStatus() == TaskStatus.ACTIVE) {
			task.pause();
			tasks.save(task);
		}
		return "redirect:/chat";
	}

	@PostMapping("/task/close")
	public String closeTask(@RequestParam String taskId,
			@CookieValue(name = PROFILE_COOKIE, required = false) String profileId) {
		WorkingTask task = findTask(taskId);
		if (task != null) {
			task.finish();
			tasks.save(task);
		}
		return "redirect:/chat";
	}

	@PostMapping("/task/state")
	public String updateTaskState(@RequestParam String taskId, @RequestParam(required = false) String state,
			@CookieValue(name = PROFILE_COOKIE, required = false) String profileId) {
		WorkingTask task = findTask(taskId);
		if (task != null) {
			task.writeState(state);
			tasks.save(task);
		}
		return "redirect:/chat";
	}

	@PostMapping("/task/stage")
	public String changeStage(@RequestParam String taskId, @RequestParam String stage,
			@CookieValue(name = PROFILE_COOKIE, required = false) String profileId,
			RedirectAttributes redirect) {
		WorkingTask task = findTask(taskId);
		TaskStage target = null;
		try {
			target = TaskStage.valueOf(stage.toUpperCase());
		}
		catch (IllegalArgumentException e) {
			redirect.addFlashAttribute("error", "Неизвестный этап: " + stage);
			return "redirect:/chat";
		}
		if (task == null) {
			redirect.addFlashAttribute("error", "Задача не найдена");
			return "redirect:/chat";
		}
		String error = memory.applyTransition(task, target, "MANUAL", "ручной переход из UI");
		if (error != null) {
			redirect.addFlashAttribute("error", error);
		}
		return "redirect:/chat";
	}

	@PostMapping("/memory/confirm")
	public String confirmMemory(@RequestParam String memoryId,
			@CookieValue(name = PROFILE_COOKIE, required = false) String profileId) {
		AgentMemory m = findMemory(memoryId);
		if (m != null && m.getProfileId().equals(profileUUID(profileId))) {
			m.confirm();
			memories.save(m);
		}
		return "redirect:/chat";
	}

	@PostMapping("/memory/reject")
	public String rejectMemory(@RequestParam String memoryId,
			@CookieValue(name = PROFILE_COOKIE, required = false) String profileId) {
		AgentMemory m = findMemory(memoryId);
		if (m != null && m.getProfileId().equals(profileUUID(profileId))) {
			m.reject();
			memories.save(m);
		}
		return "redirect:/chat";
	}

	@PostMapping("/memory/add")
	public String addMemory(@RequestParam String factKey, @RequestParam String factValue,
			@CookieValue(name = PROFILE_COOKIE, required = false) String profileId) {
		Profile profile = findProfile(profileId);
		if (profile != null && factKey != null && !factKey.isBlank() && factValue != null && !factValue.isBlank()) {
			memories.save(new AgentMemory(UUID.randomUUID(), profile.getId(),
					factKey.strip(), factValue.strip(), MemoryStatus.CONFIRMED));
		}
		return "redirect:/chat";
	}

	@PostMapping("/memory/delete")
	public String deleteMemory(@RequestParam String memoryId,
			@CookieValue(name = PROFILE_COOKIE, required = false) String profileId) {
		AgentMemory m = findMemory(memoryId);
		if (m != null && m.getProfileId().equals(profileUUID(profileId))) {
			memories.delete(m);
		}
		return "redirect:/chat";
	}

	private Profile ensureProfile(String profileId, HttpServletResponse response) {
		Profile profile = findProfile(profileId);
		if (profile != null) {
			return profile;
		}
		List<Profile> all = profiles.findAllByOrderByCreatedAtAsc();
		profile = all.isEmpty() ? new Profile(UUID.randomUUID(), "Обычный") : all.get(0);
		profiles.save(profile);
		setCookie(response, PROFILE_COOKIE, profile.getId().toString());
		return profile;
	}

	private Conversation ensureConversation(String conversationId, Profile profile, HttpServletResponse response) {
		if (conversationId != null && UUID_PATTERN.matcher(conversationId).matches()) {
			UUID id = UUID.fromString(conversationId);
			Conversation conversation = conversations.findById(id).orElse(null);
			if (conversation != null) {
				if (!profile.getId().equals(conversation.getProfileId())) {
					conversation.setProfileId(profile.getId());
					conversations.save(conversation);
				}
				return conversation;
			}
			conversations.save(new Conversation(id, profile.getId()));
			return conversations.findById(id).orElseThrow();
		}
		Conversation conversation = new Conversation(UUID.randomUUID(), profile.getId());
		conversations.save(conversation);
		setCookie(response, CONVERSATION_COOKIE, conversation.getId().toString());
		return conversation;
	}

	private void relinkConversation(String conversationId, UUID newProfileId) {
		if (conversationId != null && UUID_PATTERN.matcher(conversationId).matches()) {
			conversations.findById(UUID.fromString(conversationId)).ifPresent(c -> {
				c.setProfileId(newProfileId);
				conversations.save(c);
			});
		}
	}

	private Profile findProfile(String profileId) {
		UUID id = profileUUID(profileId);
		return id == null ? null : profiles.findById(id).orElse(null);
	}

	private WorkingTask findTask(String taskId) {
		UUID id = taskId != null && UUID_PATTERN.matcher(taskId).matches() ? UUID.fromString(taskId) : null;
		return id == null ? null : tasks.findById(id).orElse(null);
	}

	private AgentMemory findMemory(String memoryId) {
		UUID id = memoryId != null && UUID_PATTERN.matcher(memoryId).matches() ? UUID.fromString(memoryId) : null;
		return id == null ? null : memories.findById(id).orElse(null);
	}

	private UUID profileUUID(String profileId) {
		return profileId != null && UUID_PATTERN.matcher(profileId).matches() ? UUID.fromString(profileId) : null;
	}

	private void setCookie(HttpServletResponse response, String name, String value) {
		Cookie cookie = new Cookie(name, value);
		cookie.setPath("/");
		cookie.setMaxAge(7 * 24 * 60 * 60);
		response.addCookie(cookie);
	}

	private List<ChatMessageView> toViews(List<Message> messages) {
		return messages.stream()
				.map(m -> new ChatMessageView(m.getMessageType() == MessageType.USER, m.getText()))
				.toList();
	}

}
