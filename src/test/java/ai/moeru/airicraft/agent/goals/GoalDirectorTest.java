package ai.moeru.airicraft.agent.goals;

import ai.moeru.airicraft.agent.dialogue.DialogueIntentType;
import ai.moeru.airicraft.agent.dialogue.DialogueRuntime;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GoalDirectorTest {
	@Test
	void addressedFollowRequestCreatesFollowGoalAndDialogueResponse() {
		GoalDirector goalDirector = new GoalDirector();
		DialogueRuntime dialogueRuntime = new DialogueRuntime();

		goalDirector.onAddressedChat("Alice", "@agent follow me", 42L, dialogueRuntime);

		GoalSnapshot goal = goalDirector.activeGoal().orElseThrow();
		assertEquals(GoalType.FOLLOW_PLAYER, goal.type());
		assertEquals("Alice", goal.targetPlayer());
		assertEquals(DialogueIntentType.SET_GOAL, dialogueRuntime.lastResponse().orElseThrow().intent().type());
	}

	@Test
	void unrelatedChatDoesNotCreateGoal() {
		GoalDirector goalDirector = new GoalDirector();
		DialogueRuntime dialogueRuntime = new DialogueRuntime();

		goalDirector.onAddressedChat("Alice", "hello there", 42L, dialogueRuntime);

		assertTrue(goalDirector.activeGoal().isEmpty());
		assertTrue(dialogueRuntime.lastResponse().isEmpty());
	}

	@Test
	void clearFollowGoalOnlyClearsMatchingTarget() {
		GoalDirector goalDirector = new GoalDirector();
		DialogueRuntime dialogueRuntime = new DialogueRuntime();
		goalDirector.onAddressedChat("Alice", "@agent follow me", 42L, dialogueRuntime);

		goalDirector.clearFollowGoal("Bob");
		assertEquals("Alice", goalDirector.activeGoal().orElseThrow().targetPlayer());

		goalDirector.clearFollowGoal("Alice");
		assertTrue(goalDirector.activeGoal().isEmpty());
	}
}
