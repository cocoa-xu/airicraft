package ai.moeru.airicraft.agent.llm;

public interface LlmBackend {
	LlmCallResult<PlannerResponse> generate(LlmConversation conversation) throws LlmBackendException;

	void injectMockResponse(PlannerResponse response);

	void injectTimeout();

	boolean isConfigured();
}
