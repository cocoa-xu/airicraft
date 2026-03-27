package ai.moeru.airicraft.agent.llm;

import ai.moeru.airicraft.agent.goals.GoalType;

public record PlannerIntent(
	String type,
	GoalType goalType,
	String targetPlayer
) {
}
