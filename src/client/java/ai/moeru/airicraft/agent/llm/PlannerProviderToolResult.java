package ai.moeru.airicraft.agent.llm;

public record PlannerProviderToolResult(String text, LlmImageAttachment imageAttachment) {
	public static PlannerProviderToolResult text(String text) {
		return new PlannerProviderToolResult(text, null);
	}

	public static PlannerProviderToolResult image(String text, LlmImageAttachment imageAttachment) {
		return new PlannerProviderToolResult(text, imageAttachment);
	}
}
