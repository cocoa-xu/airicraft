package ai.moeru.airicraft.agent.llm;

import ai.moeru.airicraft.agent.dialogue.DialogueTurn;
import ai.moeru.airicraft.agent.goals.GoalSnapshot;
import ai.moeru.airicraft.agent.session.SessionMode;

import java.util.List;

public record PlannerRequest(
	long tick,
	SessionMode sessionMode,
	String primaryInteractionPlayer,
	GoalSnapshot activeGoal,
	List<DialogueTurn> recentTurns,
	String senderName,
	String message,
	String toolResult
) {
}
