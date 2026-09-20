package com.tutorial.ai_challenge;

import java.util.List;
import java.util.Map;

public enum TaskStage {

	PLANNING("планирование"),
	EXECUTION("реализация"),
	VALIDATION("проверка"),
	DONE("завершена");

	private static final Map<TaskStage, List<TaskStage>> ALLOWED = Map.of(
			PLANNING, List.of(EXECUTION),
			EXECUTION, List.of(VALIDATION, PLANNING),
			VALIDATION, List.of(DONE, EXECUTION),
			DONE, List.of());

	private final String label;

	TaskStage(String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public List<TaskStage> legalTargets() {
		return ALLOWED.get(this);
	}

	public boolean canTransitionTo(TaskStage target) {
		return target == this || ALLOWED.get(this).contains(target);
	}

}
