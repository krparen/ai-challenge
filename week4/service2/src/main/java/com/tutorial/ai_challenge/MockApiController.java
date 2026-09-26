package com.tutorial.ai_challenge;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MockApiController {

	private final MockService mockService;

	public MockApiController(MockService mockService) {
		this.mockService = mockService;
	}

	@GetMapping("/mock/getColor")
	public String getColor() {
		return mockService.getColor();
	}

	@GetMapping("/mock/getNumberFromZeroToNine")
	public int getNumberFromZeroToNine() {
		return mockService.getNumber();
	}

}
