package ai.moeru.airicraft.agent.follow;

public record FollowState(
	boolean goalActive,
	String targetPlayer,
	boolean targetNearby,
	boolean sameDimension,
	double distanceToTarget,
	double targetX,
	double targetY,
	double targetZ
) {
	public static FollowState idle() {
		return new FollowState(false, null, false, false, 0.0D, 0.0D, 0.0D, 0.0D);
	}
}
