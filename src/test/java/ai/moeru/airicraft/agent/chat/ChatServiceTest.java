package ai.moeru.airicraft.agent.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ChatServiceTest {
	@Test
	void sanitizeForChatCollapsesWhitespaceAndStripsLeadingSlash() {
		String sanitized = ChatService.sanitizeForChat("  /test\n\nhello\tworld  ");

		assertEquals("test hello world", sanitized);
	}

	@Test
	void sanitizeForChatStripsFormattingCodeAndTruncatesLongMessages() {
		String longText = "§a" + "a".repeat(400);

		String sanitized = ChatService.sanitizeForChat(longText);

		assertFalse(sanitized.contains("§"));
		assertEquals(ChatService.MAX_CHAT_MESSAGE_LENGTH, sanitized.length());
	}
}
