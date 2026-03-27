package ai.moeru.airicraft.agent.dialogue;

import ai.moeru.airicraft.agent.goals.GoalType;

public record DialogueIntent(
	DialogueIntentType type,
	GoalType goalType,
	String targetPlayer
) {
}
