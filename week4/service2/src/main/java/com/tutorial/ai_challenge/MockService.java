package com.tutorial.ai_challenge;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;

@Service
public class MockService {

	private static final List<String> COLORS = List.of("Синий", "Зелёный", "Красный");

	public String getColor() {
		return COLORS.get(ThreadLocalRandom.current().nextInt(COLORS.size()));
	}

	public int getNumber() {
		return ThreadLocalRandom.current().nextInt(10);
	}

}
