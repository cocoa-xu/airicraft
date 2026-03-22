package ai.moeru.airicraft;

import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
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

	public void onClientTick(MinecraftClient client) {
		highlightManager.tick();
	}

	public void onWorldRender(WorldRenderContext context) {
		highlightManager.render(context);
	}

	public void shutdown() {
		highlightManager.clear();
		bridgeServer.stop();
	}
}
