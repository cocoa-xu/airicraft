package ai.moeru.airicraft.agent.goals;

public record GoalSnapshot(
	GoalType type,
	String targetPlayer,
	long updatedTick,
	String source
) {
}
