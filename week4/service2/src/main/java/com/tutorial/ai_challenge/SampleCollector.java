package com.tutorial.ai_challenge;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SampleCollector {

	private final MockService mockService;
	private final SampleStore sampleStore;

	public SampleCollector(MockService mockService, SampleStore sampleStore) {
		this.mockService = mockService;
		this.sampleStore = sampleStore;
	}

	@Scheduled(fixedRateString = "${mock.collect.interval-ms:5000}")
	public void collect() {
		sampleStore.insert(mockService.getColor(), mockService.getNumber());
		sampleStore.deleteOlderThan24h();
	}

}
