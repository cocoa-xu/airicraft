package ai.moeru.airicraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;

public final class HighlightManager {
	public String add(BlockPos pos) {
		var client = MinecraftClient.getInstance();
		if (client.worldRenderer == null) {
			throw new IllegalStateException("World renderer is not initialized");
		}

		client.worldRenderer.gameTestDebugRenderer.addMarker(pos, pos);
		return pos.getX() + "," + pos.getY() + "," + pos.getZ();
	}

	public void clear() {
		var client = MinecraftClient.getInstance();
		if (client.worldRenderer != null) {
			client.worldRenderer.gameTestDebugRenderer.clear();
		}
	}
}
