package ai.moeru.airicraft.agent.speech;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SpeechServiceTest {
	@Test
	void sanitizeForChatCollapsesWhitespaceAndStripsLeadingSlash() {
		String sanitized = SpeechService.sanitizeForChat("  /test\n\nhello\tworld  ");

		assertEquals("test hello world", sanitized);
	}

	@Test
	void sanitizeForChatStripsFormattingCodeAndTruncatesLongMessages() {
		String longText = "§a" + "a".repeat(400);

		String sanitized = SpeechService.sanitizeForChat(longText);

		assertFalse(sanitized.contains("§"));
		assertEquals(SpeechService.MAX_CHAT_MESSAGE_LENGTH, sanitized.length());
	}
}
