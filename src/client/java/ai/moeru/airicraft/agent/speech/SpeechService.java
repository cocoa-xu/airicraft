package ai.moeru.airicraft.agent.speech;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;

public final class SpeechService {
	private long lastSpokenTick = -1L;
	private String lastSpokenText;

	public boolean speak(MinecraftClient client, String text, long tick) {
		if (client == null || text == null || text.isBlank()) {
			return false;
		}

		ClientPlayNetworkHandler networkHandler = client.getNetworkHandler();
		if (networkHandler == null) {
			return false;
		}

		networkHandler.sendChatMessage(text);
		lastSpokenTick = tick;
		lastSpokenText = text;
		return true;
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
