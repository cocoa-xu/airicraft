package ai.moeru.airicraft.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbodiedAgentRuntimeTest {
	@Test
	void suppressesRecentEchoOfAgentOwnPublicChat() {
		assertTrue(EmbodiedAgentRuntime.isAgentChatEcho(
			"Player918",
			"hello there",
			"Player918",
			"hello there",
			120L,
			100L
		));
	}

	@Test
	void doesNotSuppressDifferentOrStaleChat() {
		assertFalse(EmbodiedAgentRuntime.isAgentChatEcho(
			"Player918",
			"follow me",
			"Player918",
			"hello there",
			120L,
			100L
		));
		assertFalse(EmbodiedAgentRuntime.isAgentChatEcho(
			"Player918",
			"hello there",
			"Player918",
			"hello there",
			200L,
			100L
		));
		assertFalse(EmbodiedAgentRuntime.isAgentChatEcho(
			"magpie",
			"hello there",
			"Player918",
			"hello there",
			120L,
			100L
		));
	}
}
