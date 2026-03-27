package ai.moeru.airicraft.agent.control;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.Vec3d;

public final class MovementController {
	private static final long STUCK_TICKS = 20L;
	private static final double STUCK_DISTANCE_EPSILON = 0.15D;

	private boolean movingForward;
	private boolean sprinting;
	private boolean jumping;
	private boolean stuck;
	private long movingSinceTick = -1L;
	private Vec3d movementStartPos;

	public void moveForward(MinecraftClient client, boolean sprint, boolean jump, long tick) {
		if (client == null) {
			return;
		}

		ClientPlayerEntity player = client.player;
		if (player == null) {
			stop(client);
			return;
		}

		if (!movingForward) {
			movingSinceTick = tick;
			movementStartPos = new Vec3d(player.getX(), player.getY(), player.getZ());
			stuck = false;
		}

		movingForward = true;
		sprinting = sprint;
		jumping = jump;

		client.options.forwardKey.setPressed(true);
		client.options.backKey.setPressed(false);
		client.options.leftKey.setPressed(false);
		client.options.rightKey.setPressed(false);
		client.options.sprintKey.setPressed(sprint);
		client.options.jumpKey.setPressed(jump);
		player.setSprinting(sprint);

		updateStuckState(player, tick);
	}

	public void stop(MinecraftClient client) {
		movingForward = false;
		sprinting = false;
		jumping = false;
		stuck = false;
		movingSinceTick = -1L;
		movementStartPos = null;

		if (client == null) {
			return;
		}

		client.options.forwardKey.setPressed(false);
		client.options.backKey.setPressed(false);
		client.options.leftKey.setPressed(false);
		client.options.rightKey.setPressed(false);
		client.options.jumpKey.setPressed(false);
		client.options.sprintKey.setPressed(false);
		if (client.player != null) {
			client.player.setSprinting(false);
		}
	}

	public MovementStateSnapshot snapshot() {
		return new MovementStateSnapshot(movingForward, sprinting, jumping, stuck, movingSinceTick);
	}

	private void updateStuckState(ClientPlayerEntity player, long tick) {
		if (movementStartPos == null || movingSinceTick < 0L) {
			stuck = false;
			return;
		}
		if (tick - movingSinceTick < STUCK_TICKS) {
			stuck = false;
			return;
		}

		Vec3d currentPos = new Vec3d(player.getX(), player.getY(), player.getZ());
		double movedDistance = currentPos.distanceTo(movementStartPos);
		stuck = movedDistance < STUCK_DISTANCE_EPSILON;
		if (!stuck) {
			movingSinceTick = tick;
			movementStartPos = currentPos;
		}
	}
}
