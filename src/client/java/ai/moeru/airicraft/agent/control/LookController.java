package ai.moeru.airicraft.agent.control;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

public final class LookController {
	public void lookAt(MinecraftClient client, Vec3d target, float maxYawStep, float maxPitchStep) {
		if (client == null || client.player == null || target == null) {
			return;
		}

		ClientPlayerEntity player = client.player;
		Vec3d eyePos = player.getEyePos();
		Vec3d delta = target.subtract(eyePos);
		double horizontalDistance = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
		if (horizontalDistance < 1.0E-7D && Math.abs(delta.y) < 1.0E-7D) {
			return;
		}

		float targetYaw = (float) Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0F;
		float targetPitch = (float) -Math.toDegrees(Math.atan2(delta.y, horizontalDistance));
		targetPitch = MathHelper.clamp(targetPitch, -90.0F, 90.0F);

		float nextYaw = rotateToward(player.getYaw(), targetYaw, maxYawStep);
		float nextPitch = rotateToward(player.getPitch(), targetPitch, maxPitchStep);
		applyRotation(player, nextYaw, nextPitch);
	}

	private static float rotateToward(float current, float target, float maxStep) {
		float delta = MathHelper.wrapDegrees(target - current);
		float step = MathHelper.clamp(delta, -maxStep, maxStep);
		return current + step;
	}

	private static void applyRotation(ClientPlayerEntity player, float yaw, float pitch) {
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
	}
}
