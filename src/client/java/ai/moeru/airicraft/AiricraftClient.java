package ai.moeru.airicraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class AiricraftClient implements ClientModInitializer {
	private static final ClientRuntimeController RUNTIME_CONTROLLER = new ClientRuntimeController();

	@Override
	public void onInitializeClient() {
		ClientLifecycleEvents.CLIENT_STARTED.register(RUNTIME_CONTROLLER::onClientStarted);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> RUNTIME_CONTROLLER.onWorldLeave());
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> RUNTIME_CONTROLLER.shutdown());
	}
}
