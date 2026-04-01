package ai.moeru.airicraft.agent.llm;

import ai.moeru.airicraft.agent.goals.GoalSnapshot;
import ai.moeru.airicraft.agent.session.SessionMode;

public record PlannerRequest(
	long tick,
	long timestampMs,
	SessionMode sessionMode,
	String primaryInteractionPlayer,
	GoalSnapshot activeGoal,
	String senderName,
	String message,
	String toolResult
) {
}
