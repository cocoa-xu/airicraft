package ai.moeru.airicraft.agent.llm;

import ai.moeru.airicraft.agent.goals.GoalSnapshot;
import ai.moeru.airicraft.agent.session.SessionMode;

public record PlannerAmbientContext(
	SessionMode sessionMode,
	String primaryInteractionPlayer,
	String activeGoalDescription
) {
	public static PlannerAmbientContext fromRequest(PlannerRequest request) {
		return new PlannerAmbientContext(
			request.sessionMode(),
			normalize(request.primaryInteractionPlayer()),
			describeGoal(request.activeGoal())
		);
	}

	private static String normalize(String value) {
		return value == null || value.isBlank() ? null : value;
	}

	private static String describeGoal(GoalSnapshot goal) {
		if (goal == null || goal.type() == null) {
			return null;
		}
		return switch (goal.type()) {
			case FOLLOW_PLAYER -> goal.targetPlayer() == null || goal.targetPlayer().isBlank()
				? "Follow the current player."
				: "Follow " + goal.targetPlayer() + ".";
		};
	}
}
