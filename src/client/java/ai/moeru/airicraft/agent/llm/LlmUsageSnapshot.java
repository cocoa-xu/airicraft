package ai.moeru.airicraft.agent.llm;

public record LlmUsageSnapshot(
	Integer promptTokens,
	Integer completionTokens,
	Integer totalTokens
) {
	public static LlmUsageSnapshot unknown() {
		return new LlmUsageSnapshot(null, null, null);
	}

	public boolean hasPromptTokens() {
		return promptTokens != null && promptTokens.intValue() >= 0;
	}
}
