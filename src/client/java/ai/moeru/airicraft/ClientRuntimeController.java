package ai.moeru.airicraft;

import ai.moeru.airicraft.agent.EmbodiedAgentRuntime;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;

public final class ClientRuntimeController {
	private final HighlightManager highlightManager = new HighlightManager();
	private final EmbodiedAgentRuntime agentRuntime = EmbodiedAgentRuntime.createDefault();
	private final ModBridgeServer bridgeServer = new ModBridgeServer(highlightManager, agentRuntime);

	public HighlightManager highlightManager() {
		return highlightManager;
	}

	public void onClientStarted(MinecraftClient client) {
		agentRuntime.onClientStarted(client);
		bridgeServer.start();
	}

	public void onWorldLeave() {
		agentRuntime.onWorldLeave();
		highlightManager.clear();
	}

	public void onClientTick(MinecraftClient client) {
		agentRuntime.onClientTick(client);
		highlightManager.tick();
	}

	public void onWorldRender(WorldRenderContext context) {
		highlightManager.render(context);
	}

	public void shutdown() {
		agentRuntime.shutdown();
		highlightManager.clear();
		bridgeServer.stop();
	}
}
