package ai.moeru.airicraft.agent.control;

public record MovementStateSnapshot(
	boolean movingForward,
	boolean sprinting,
	boolean jumping,
	boolean stuck,
	long movingSinceTick
) {
	public static MovementStateSnapshot idle() {
		return new MovementStateSnapshot(false, false, false, false, -1L);
	}
}
