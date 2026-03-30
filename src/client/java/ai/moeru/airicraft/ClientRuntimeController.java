package ai.moeru.airicraft;

import ai.moeru.airicraft.agent.EmbodiedAgentRuntime;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.MinecraftClient;

public final class ClientRuntimeController {
	private final HighlightManager highlightManager = new HighlightManager();
	private final FirstPersonScreenshotService screenshotService = new FirstPersonScreenshotService();
	private final EmbodiedAgentRuntime agentRuntime = EmbodiedAgentRuntime.createDefault(screenshotService);
	private final ModBridgeServer bridgeServer = new ModBridgeServer(highlightManager, agentRuntime, screenshotService);

	public HighlightManager highlightManager() {
		return highlightManager;
	}

	public EmbodiedAgentRuntime agentRuntime() {
		return agentRuntime;
	}

	public FirstPersonScreenshotService screenshotService() {
		return screenshotService;
	}

	public void onClientStarted(MinecraftClient client) {
		agentRuntime.onClientStarted(client);
		bridgeServer.start();
	}

	public void onWorldLeave() {
		screenshotService.failActiveCapture("capture_failed", "Screenshot capture was interrupted");
		agentRuntime.onWorldLeave();
		highlightManager.clear();
	}

	public void onClientTick(MinecraftClient client) {
		agentRuntime.onClientTick(client);
		highlightManager.tick();
	}

	public void onChatReceived(String senderName, String plainTextMessage) {
		agentRuntime.onChatReceived(senderName, plainTextMessage);
	}

	public void onWorldRender(WorldRenderContext context) {
		highlightManager.render(context);
	}

	public void onFirstPersonFrameRendered() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client != null) {
			screenshotService.onWorldRendered(client);
		}
	}

	public void shutdown() {
		screenshotService.failActiveCapture("capture_failed", "Screenshot capture was interrupted");
		agentRuntime.shutdown();
		highlightManager.clear();
		bridgeServer.stop();
	}
}
