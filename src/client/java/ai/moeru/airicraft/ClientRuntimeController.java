package ai.moeru.airicraft;

import net.minecraft.client.MinecraftClient;

public final class ClientRuntimeController {
	private final HighlightManager highlightManager = new HighlightManager();
	private final ModBridgeServer bridgeServer = new ModBridgeServer(highlightManager);

	public HighlightManager highlightManager() {
		return highlightManager;
	}

	public void onClientStarted(MinecraftClient client) {
		bridgeServer.start();
	}

	public void onWorldLeave() {
		highlightManager.clear();
	}

	public void shutdown() {
		highlightManager.clear();
		bridgeServer.stop();
	}
}
