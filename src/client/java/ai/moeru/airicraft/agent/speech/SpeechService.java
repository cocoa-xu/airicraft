package ai.moeru.airicraft.agent.speech;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

public final class SpeechService {
	static final int MAX_CHAT_MESSAGE_LENGTH = 220;

	private long lastSpokenTick = -1L;
	private String lastSpokenText;

	public boolean speak(MinecraftClient client, String text, long tick) {
		if (client == null || text == null || text.isBlank()) {
			return false;
		}

		String sanitizedText = sanitizeForChat(text);
		if (sanitizedText.isBlank()) {
			return false;
		}

		ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
		if (networkHandler == null) {
			return false;
		}

		networkHandler.sendChatMessage(sanitizedText);
		lastSpokenTick = tick;
		lastSpokenText = sanitizedText;
		return true;
	}

	static String sanitizeForChat(String text) {
		if (text == null || text.isBlank()) {
			return "";
		}

		StringBuilder builder = new StringBuilder(text.length());
		boolean previousWhitespace = false;
		for (int i = 0; i < text.length(); i++) {
			char current = text.charAt(i);
			if (current == '\r' || current == '\n' || current == '\t') {
				current = ' ';
			}
			if (Character.isISOControl(current) || current == '§') {
				continue;
			}
			if (Character.isWhitespace(current)) {
				if (!previousWhitespace) {
					builder.append(' ');
					previousWhitespace = true;
				}
				continue;
			}

			builder.append(current);
			previousWhitespace = false;
		}

		String sanitized = builder.toString().strip();
		while (sanitized.startsWith("/")) {
			sanitized = sanitized.substring(1).stripLeading();
		}
		if (sanitized.length() > MAX_CHAT_MESSAGE_LENGTH) {
			sanitized = sanitized.substring(0, MAX_CHAT_MESSAGE_LENGTH).stripTrailing();
		}
		return sanitized;
	}

	public long lastSpokenTick() {
		return lastSpokenTick;
	}

	public String lastSpokenText() {
		return lastSpokenText;
	}

	public void clear() {
		lastSpokenTick = -1L;
		lastSpokenText = null;
	}
}
