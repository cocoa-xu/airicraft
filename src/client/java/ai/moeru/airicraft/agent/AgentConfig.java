package ai.moeru.airicraft.agent;

public record AgentConfig(
	boolean verificationEnabled,
	boolean verificationAutoRunAll,
	LlmConfig llm
) {
	public static AgentConfig defaults() {
		return new AgentConfig(false, false, LlmConfig.defaults());
	}

	public record LlmConfig(
		String providerBaseUrl,
		String apiKey,
		String model,
		int requestTimeoutMillis,
		int maxRecentConversationTurns,
		boolean enableProactiveSocialMode
	) {
		public static LlmConfig defaults() {
			return new LlmConfig(
				"https://api.openai.com/v1",
				"",
				"",
				15_000,
				8,
				false
			);
		}

		public boolean isConfigured() {
			return providerBaseUrl != null
				&& !providerBaseUrl.isBlank()
				&& apiKey != null
				&& !apiKey.isBlank()
				&& model != null
				&& !model.isBlank();
		}
	}
}
