package ai.moeru.airicraft.agent.goals;

import ai.moeru.airicraft.agent.dialogue.DialogueIntent;
import ai.moeru.airicraft.agent.dialogue.DialogueIntentType;
import ai.moeru.airicraft.agent.dialogue.DialogueResponse;
import ai.moeru.airicraft.agent.dialogue.DialogueRuntime;

import java.util.Locale;
import java.util.Optional;

public final class GoalDirector {
	private GoalSnapshot activeGoal;

	public void onAddressedChat(String senderName, String plainTextMessage, long tick, DialogueRuntime dialogueRuntime) {
		if (senderName == null || plainTextMessage == null || dialogueRuntime == null) {
			return;
		}

		String normalized = plainTextMessage.stripLeading();
		if (!normalized.regionMatches(true, 0, "@agent", 0, "@agent".length())) {
			return;
		}

		String command = normalized.substring("@agent".length()).trim().toLowerCase(Locale.ROOT);
		if (!command.startsWith("follow")) {
			return;
		}

		activeGoal = new GoalSnapshot(GoalType.FOLLOW_PLAYER, senderName, tick, "addressed_chat");
		dialogueRuntime.recordResponse(new DialogueResponse(
			"Following " + senderName + ".",
			new DialogueIntent(DialogueIntentType.SET_GOAL, GoalType.FOLLOW_PLAYER, senderName),
			tick
		));
	}

	public Optional<GoalSnapshot> activeGoal() {
		return Optional.ofNullable(activeGoal);
	}

	public void clearFollowGoal(String targetPlayer) {
		if (activeGoal == null || activeGoal.type() != GoalType.FOLLOW_PLAYER) {
			return;
		}
		if (targetPlayer == null || !targetPlayer.equals(activeGoal.targetPlayer())) {
			return;
		}
		activeGoal = null;
	}

	public void clear() {
		activeGoal = null;
	}
}
