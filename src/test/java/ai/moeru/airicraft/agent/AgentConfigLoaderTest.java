package ai.moeru.airicraft.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentConfigLoaderTest {
	@Test
	void fromMapReadsVisionFieldsAndKeepsVisionDisabledWhenUnset() {
		AgentConfig defaults = AgentConfig.defaults();

		AgentConfig parsed = AgentConfigLoader.fromMap(Map.of(
			"providerBaseUrl", "https://example.test/v1",
			"apiKey", "test-key",
			"model", "planner-model",
			"visionProviderBaseUrl", "https://vision.example.test/v1",
			"visionApiKey", "vision-key",
			"visionRequestTimeoutMillis", 7777,
			"visionImageDetail", "high"
		), defaults);

		assertEquals("https://example.test/v1", parsed.llm().providerBaseUrl());
		assertEquals("test-key", parsed.llm().apiKey());
		assertEquals("planner-model", parsed.llm().model());
		assertEquals("https://vision.example.test/v1", parsed.llm().visionProviderBaseUrl());
		assertEquals("vision-key", parsed.llm().visionApiKey());
		assertEquals("", parsed.llm().visionModel());
		assertEquals(7777, parsed.llm().visionRequestTimeoutMillis());
		assertEquals("high", parsed.llm().visionImageDetail());
		assertFalse(parsed.llm().visionConfigured());
	}
}
