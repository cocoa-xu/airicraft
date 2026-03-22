package ai.moeru.airicraft;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PlayerViewService {
	public Map<String, Object> lookAt(double x, double y, double z) {
		MinecraftClient client = requireClient();
		if (client.world == null || client.player == null) {
			throw new PlayerViewException("world_not_loaded", "No world is currently loaded");
		}

		ClientPlayerEntity player = client.player;
		Vec3d eyePos = player.getEyePos();
		Vec3d target = new Vec3d(x, y, z);
		Vec3d delta = target.subtract(eyePos);
		double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		if (horizontalDistance < 1.0E-7 && Math.abs(delta.y) < 1.0E-7) {
			throw new PlayerViewException("invalid_request", "Target must differ from the current camera position");
		}

		float yaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
		float pitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontalDistance));
		pitch = Math.max(-90.0F, Math.min(90.0F, pitch));

		player.setAngles(yaw, pitch);
		player.setYaw(yaw);
		player.setPitch(pitch);
		player.setHeadYaw(yaw);
		player.setBodyYaw(yaw);
		player.lastYaw = yaw;
		player.lastPitch = pitch;
		player.renderYaw = yaw;
		player.lastRenderYaw = yaw;
		player.renderPitch = pitch;
		player.lastRenderPitch = pitch;

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("available", true);
		payload.put("worldLoaded", true);
		payload.put("target", Map.of(
			"x", x,
			"y", y,
			"z", z
		));
		payload.put("rotation", Map.of(
			"yaw", yaw,
			"pitch", pitch
		));
		return payload;
	}

	private static MinecraftClient requireClient() {
		MinecraftClient client = MinecraftClient.getInstance();
		if (client == null) {
			throw new PlayerViewException("minecraft_unavailable", "Minecraft client is not initialized");
		}
		return client;
	}

	public static final class PlayerViewException extends RuntimeException {
		private final String code;

		PlayerViewException(String code, String message) {
			super(message);
			this.code = code;
		}

		public String code() {
			return code;
		}
	}
}
