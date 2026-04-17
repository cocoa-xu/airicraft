package ai.moeru.airicraft.agent.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PlannerPromptPolicyTest {
	@Test
	void systemPromptExplainsInventoryDeltaEvidenceUsesMissionGainNotAbsoluteInventory() {
		String prompt = PlannerPromptPolicy.systemPrompt(PlannerVisionMode.EXTERNAL_SUMMARY);

		assertTrue(prompt.contains("INVENTORY_DELTA_AT_LEAST"));
		assertTrue(prompt.contains("mission start") || prompt.contains("mission began"));
		assertTrue(prompt.contains("not absolute inventory") || prompt.contains("not the current total inventory"));
	}

	@Test
	void systemPromptTellsPlannerNotToRepeatCompletedCrafts() {
		String prompt = PlannerPromptPolicy.systemPrompt(PlannerVisionMode.EXTERNAL_SUMMARY);

		assertTrue(prompt.contains("craft_recipe task completed"));
		assertTrue(prompt.contains("Do not call craft_recipe again"));
		assertTrue(prompt.contains("next distinct craft_recipe"));
		assertTrue(prompt.contains("call clear_goal"));
		assertTrue(prompt.contains("unless the user explicitly requested a multi-step craft"));
	}

	@Test
	void systemPromptKeepsToolNarrationOutOfPlaintextContent() {
		String prompt = PlannerPromptPolicy.systemPrompt(PlannerVisionMode.EXTERNAL_SUMMARY);

		assertTrue(prompt.contains("Do not write narration as assistant content"));
		assertTrue(prompt.contains("assistant content must be empty or null"));
		assertTrue(prompt.contains("must be inspect_inventory.narration"));
	}

	@Test
	void systemPromptExplainsDropAndGiveItemToolsUseExactInventoryIds() {
		String prompt = PlannerPromptPolicy.systemPrompt(PlannerVisionMode.EXTERNAL_SUMMARY);

		assertTrue(prompt.contains("drop_items"));
		assertTrue(prompt.contains("give_player"));
		assertTrue(prompt.contains("inspect_inventory"));
		assertTrue(prompt.contains("itemCounts"));
		assertTrue(prompt.contains("namespaced itemId"));
		assertTrue(prompt.contains("4 blocks"));
		assertTrue(prompt.contains("accepted action tool"));
		assertTrue(prompt.contains("does not mean the action completed"));
		assertTrue(prompt.contains("TASK UPDATE"));
	}

	@Test
	void systemPromptDistinguishesStartupInventoryFromLatestToolFollowUp() {
		String prompt = PlannerPromptPolicy.systemPrompt(PlannerVisionMode.EXTERNAL_SUMMARY);

		assertTrue(prompt.contains("startup inspect_inventory"));
		assertTrue(prompt.contains("current inventory context"));
		assertTrue(prompt.contains("does not prevent calling another required tool"));
		assertTrue(prompt.contains("latest-request tool call"));
		assertTrue(prompt.contains("tool follow-up"));
	}
}
